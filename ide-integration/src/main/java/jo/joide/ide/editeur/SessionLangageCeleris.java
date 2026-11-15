package jo.joide.ide.editeur;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import jo.celeris.android.host.popups.LanguageSession;
import jo.celeris.android.widgets.CompletionDocModel;
import jo.celeris.android.widgets.SignatureAideModel;
import jo.celeris.core.lsp.types.CorrectifLsp;
import jo.celeris.core.lsp.types.DiagnosticLsp;
import jo.celeris.core.lsp.types.EditionEspaceLsp;
import jo.celeris.core.lsp.types.EditionTexteLsp;
import jo.celeris.core.lsp.types.IndiceLsp;
import jo.celeris.core.lsp.types.LieuLsp;
import jo.celeris.core.lsp.types.ListeCompletion;
import jo.celeris.core.lsp.types.ElementCompletion;
import jo.celeris.core.lsp.types.OptionsFormatageLsp;
import jo.celeris.core.lsp.PositionsLsp;
import jo.celeris.core.lsp.types.PlageLsp;
import jo.celeris.core.lsp.types.PliLsp;
import jo.celeris.core.lsp.types.PreparationRennomageLsp;
import jo.celeris.core.lsp.types.SemanticTokensLsp;
import jo.celeris.core.lsp.types.SurlignageLsp;
import jo.celeris.core.lsp.types.SymboleLsp;
import jo.celeris.core.text.Document;
import jo.joide.ide.langages.DiagnosticIde;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.ide.langages.PropositionCompletion;

/**
 * Le pont de langage : la session celeris (contrat que la couche de
 * popups consomme — complétion, survol, signature, diagnostics
 * POUSSÉS) implémentée AU-DESSUS des moteurs JoIDE du lot C. L'éditeur
 * ne connaît jamais code-lsp, les moteurs ne connaissent jamais
 * celeris — les deux ne se rencontrent que ici.
 *
 * <p>L'analyse vit hors fil UI (contrat de threading de la brique) :
 * {@link #synchroniser} rend immédiatement, le travail part sur un fil
 * démon dédié avec FUSION des frappes (le dernier texte gagne, les
 * diagnostics intermédiaires ne sont jamais publiés), et les
 * diagnostics arrivent à l'auditeur sur le fil fourni à la
 * construction (le fil UI de la scène). Les requêtes (complétion,
 * survol, signature) rendent des {@link CompletableFuture} complétés
 * hors fil UI.</p>
 *
 * <p>Couverture du protocole non servie en P8 (symboles, plis,
 * formatage, surlignages, renommage, correctifs, indices, jetons,
 * références) : les VIDES honnêtes du domaine — jamais une exception
 * (backlog).</p>
 */
final class SessionLangageCeleris implements LanguageSession {

    /** Les moteurs du lot C. */
    private final MoteursLangage moteurs;

    /** L'exécuteur de l'analyse (fil démon, file séquencée). */
    private final ExecutorService analyse;

    /** Le fil de publication des diagnostics (le fil UI de la scène). */
    private final java.util.concurrent.Executor publication;

    /** L'auditeur des diagnostics (posé par la couche de popups). */
    private volatile BiConsumer<String, List<DiagnosticLsp>> auditeur;

    /** Le dernier texte connu par uri (fusion des frappes). */
    private final Map<String, String> textes = new ConcurrentHashMap<>();

    /** Le texte en attente de diagnostic (le dernier gagne). */
    private final AtomicReference<java.util.Map.Entry<String, String>> enAttente =
            new AtomicReference<>();

    SessionLangageCeleris(MoteursLangage moteurs,
            java.util.concurrent.Executor publication) {
        this.moteurs = Objects.requireNonNull(moteurs);
        this.publication = Objects.requireNonNull(publication);
        this.analyse = Executors.newSingleThreadExecutor(runnable -> {
            Thread fil = new Thread(runnable, "joide-analyse-editeur");
            fil.setDaemon(true);
            return fil;
        });
    }

    @Override
    public void ouvrir(String uri, String langue, String texte) {
        textes.put(uri, texte);
        analyse.execute(() -> {
            try {
                moteurs.ouvrir(uri, texte);
                publierDiagnostics(uri, texte);
            } catch (RuntimeException e) {
                // Le contrat du protocole : jamais d'exception vers la
                // couche de popups — l'analyse suivante retentera.
            }
        });
    }

    @Override
    public void synchroniser(String uri, String texte) {
        textes.put(uri, texte);
        enAttente.set(Map.entry(uri, texte));
        analyse.execute(this::diagnosticEnAttente);
    }

    @Override
    public boolean sertCompletion() {
        return true;
    }

    @Override
    public CompletableFuture<ListeCompletion> completion(String uri,
            Document doc, int caret) {
        return CompletableFuture.supplyAsync(() -> {
            List<PropositionCompletion> propositions = moteurs.completer(
                    uri, doc.text(), caret);
            List<ElementCompletion> elements = new ArrayList<>();
            for (PropositionCompletion p : propositions) {
                elements.add(new ElementCompletion(p.libelle(),
                        genreLsp(p.nature()), p.detail(),
                        p.detail(), null, null,
                        p.texteInsertion(), false));
            }
            return ListeCompletion.de(false, elements);
        }, analyse);
    }

    @Override
    public CompletableFuture<CompletionDocModel> survol(
            String uri, Document doc, String symbole, int caret) {
        return CompletableFuture.supplyAsync(() -> {
            var survol = moteurs.survol(uri, doc.text(), caret);
            if (survol.isEmpty()) {
                return null;
            }
            var riche = survol.orElseThrow();
            return CompletionDocModel.of(symbole,
                    riche.signature()
                            + (riche.documentation() == null ? ""
                                    : "\n\n" + riche.documentation()));
        }, analyse);
    }

    @Override
    public CompletableFuture<SignatureAideModel> signature(
            String uri, Document doc, int caret) {
        return CompletableFuture.supplyAsync(() -> {
            var aide = moteurs.signature(uri, doc.text(), caret);
            if (aide.isEmpty() || aide.orElseThrow().entrees().isEmpty()) {
                return null;
            }
            var signature = aide.orElseThrow();
            List<SignatureAideModel.Entree> entrees = new ArrayList<>();
            for (var entree : signature.entrees()) {
                entrees.add(new SignatureAideModel.Entree(
                        entree.libelle(), entree.parametres(), null));
            }
            return SignatureAideModel.of(entrees,
                    signature.signatureActive(),
                    signature.parametreActif());
        }, analyse);
    }

    @Override
    public void poserAuditeurDiagnostics(
            BiConsumer<String, List<DiagnosticLsp>> auditeur) {
        this.auditeur = Objects.requireNonNull(auditeur);
    }

    // ------------------------------------------------------------------
    // Couverture du protocole non servie en P8 : les vides du domaine
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<List<SymboleLsp>> symboles(String uri) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<PliLsp>> plis(String uri) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<EditionTexteLsp>> formatage(
            String uri, OptionsFormatageLsp options) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<EditionTexteLsp>> formatagePlage(
            String uri, PlageLsp plage, OptionsFormatageLsp options) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<SurlignageLsp>> surlignages(
            String uri, Document doc, int caret) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<PlageLsp>> plagesSelection(
            String uri, Document doc, int caret) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<PreparationRennomageLsp> preparerRennomage(
            String uri, Document doc, int caret) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<EditionEspaceLsp> rennomage(
            String uri, Document doc, int caret, String nouveauNom) {
        return CompletableFuture.completedFuture(EditionEspaceLsp.VIDE);
    }

    @Override
    public CompletableFuture<List<CorrectifLsp>> correctifs(
            String uri, PlageLsp plage) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<IndiceLsp>> indices(String uri,
            PlageLsp plage) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<SemanticTokensLsp> jetons(String uri) {
        return CompletableFuture.completedFuture(
                SemanticTokensLsp.de(null, List.of()));
    }

    @Override
    public CompletableFuture<SemanticTokensLsp> jetonsPlage(String uri,
            PlageLsp plage) {
        return CompletableFuture.completedFuture(
                SemanticTokensLsp.de(null, List.of()));
    }

    @Override
    public CompletableFuture<List<LieuLsp>> references(String uri,
            Document doc, int caret) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void close() {
        analyse.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    /** Diagnostique le dernier texte en attente (fusion des frappes). */
    private void diagnosticEnAttente() {
        Map.Entry<String, String> courant = enAttente.getAndSet(null);
        if (courant == null) {
            return;
        }
        String uri = courant.getKey();
        String texte = courant.getValue();
        try {
            moteurs.modifier(uri, texte);
            publierDiagnostics(uri, texte);
        } catch (RuntimeException e) {
            // Jamais d'exception vers la couche de popups.
        }
    }

    /** Tire les diagnostics du moteur et les pousse à l'auditeur. */
    private void publierDiagnostics(String uri, String texte) {
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(uri,
                texte);
        Document document = Document.of(texte);
        List<DiagnosticLsp> convertis = new ArrayList<>();
        for (DiagnosticIde diagnostic : diagnostics) {
            convertis.add(new DiagnosticLsp(
                    PositionsLsp.plage(document, diagnostic.debut(),
                            diagnostic.fin()),
                    graviteLsp(diagnostic.gravite()),
                    diagnostic.message(), diagnostic.code(),
                    "joide"));
        }
        List<DiagnosticLsp> figee = List.copyOf(convertis);
        publication.execute(() -> {
            BiConsumer<String, List<DiagnosticLsp>> present = auditeur;
            if (present != null) {
                present.accept(uri, figee);
            }
        });
    }

    /** Gravité JoIDE → gravité du protocole LSP. */
    private static int graviteLsp(DiagnosticIde.Gravite gravite) {
        return switch (gravite) {
            case ERREUR -> DiagnosticLsp.ERREUR;
            case AVERTISSEMENT -> DiagnosticLsp.AVERTISSEMENT;
            default -> 3;
        };
    }

    /** Nature des moteurs → genre CompletionItemKind du protocole. */
    private static int genreLsp(String nature) {
        return switch (nature) {
            case "METHODE" -> 2;
            case "CHAMP" -> 5;
            case "VARIABLE", "PARAMETRE" -> 6;
            case "CLASSE", "CONSTANTE_ENUM" -> 7;
            case "INTERFACE" -> 8;
            case "PAQUET" -> 9;
            case "MOT_CLE" -> 14;
            case "EXTRAIT" -> 15;
            case "ANNOTATION" -> 10;
            case "BALISE" -> 7;
            case "ATTRIBUT" -> 5;
            default -> 1;
        };
    }
}
