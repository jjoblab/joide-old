package jo.joide.ide.editeur;

import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import jo.celeris.android.host.popups.PopupsEditeur;
import jo.celeris.android.host.spi.CheminDoc;
import jo.celeris.android.host.spi.DiagnosticHote;
import jo.celeris.android.host.spi.GraviteDiagnostic;
import jo.celeris.android.view.EditorTheme;
import jo.celeris.android.view.EditorThemes;
import jo.celeris.android.view.EditorView;
import jo.celeris.android.widgets.PopupLayer;
import jo.celeris.core.lsp.types.DiagnosticLsp;
import jo.celeris.core.text.TextSession;
import jo.joide.ide.langages.MoteursLangage;

/**
 * La scène d'édition de JoIDE (lot D) : la vue d'éditeur et sa couche
 * de popups de la brique celeris, montées ici et exposées à
 * l'application sous des types du CADRE Android uniquement —
 * {@link View} et {@link Context} (ADR-044 : aucun type de brique ne
 * quitte le module).
 *
 * <p>Les deux vues se posent SUPERPOSÉES dans un même conteneur
 * (FrameLayout) : {@link #vue()} dessous, {@link #couchePopups()}
 * dessus — la couche ne prend jamais le focus. Le langage se détecte
 * du nom de fichier (analyseurs écrits de la brique : Java, Kotlin,
 * XML, JSON…) ; les diagnostics arrivent des moteurs du lot C par le
 * pont de session, poussés à la bande d'erreurs, à l'info-bulle et à
 * la gouttière ; la complétion, le survol et la signature passent par
 * les popups (le serveur passe premier, le coach de repli attend).</p>
 *
 * <p>Thread UI : toutes les méthodes de cette classe se appellent du
 * fil UI ; l'analyse vit hors fil UI dans le pont (contrat de
 * threading de la brique).</p>
 */
public final class SceneEditeur implements AutoCloseable {

    private final EditorView editeur;
    private final PopupLayer couche;
    private final SessionLangageCeleris pont;
    private final MoteursLangage moteurs;

    private TextSession session;
    private PopupsEditeur popups;
    private Consumer<String> auditeurEdition;
    private EditorTheme theme = EditorThemes.SOMBRE;
    private float tailleTexteSp = 14f;
    private String uriOuvert = "";
    private String nomAffiche = "";

    /**
     * @param contexte le contexte Android (l'activité hôte)
     * @param moteurs  les moteurs de langage du lot C
     */
    public SceneEditeur(Context contexte, MoteursLangage moteurs) {
        this.editeur = new EditorView(contexte);
        this.couche = new PopupLayer(contexte);
        this.moteurs = Objects.requireNonNull(moteurs);
        this.session = TextSession.of("", System::nanoTime);
        this.pont = new SessionLangageCeleris(moteurs,
                ExecutorPopups.de(this.editeur));
        this.editeur.setSession(session);
        this.editeur.setTheme(theme);
        this.pont.poserAuditeurDiagnostics(this::surDiagnostics);
    }

    /** La vue d'éditeur (à poser SOUS la couche de popups). */
    public View vue() {
        return editeur;
    }

    /** La couche porte-popups (à poser SUR l'éditeur, jamais le focus). */
    public View couchePopups() {
        return couche;
    }

    /**
     * Ouvre un document : nouvelle session d'édition, langage détecté
     * du nom de fichier, popups re-branchés, diagnostics demandés.
     *
     * @param uri        l'uri du document (celle des moteurs, lot C)
     * @param nomAffiche le nom de fichier affiché (« Main.java »)
     * @param contenu    le contenu complet initial
     */
    public void ouvrir(String uri, String nomAffiche, String contenu) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(nomAffiche);
        Objects.requireNonNull(contenu);
        if (popups != null) {
            popups.detacher();
        }
        this.uriOuvert = uri;
        this.nomAffiche = nomAffiche;
        this.session = TextSession.of(contenu, System::nanoTime);
        editeur.setSession(session);
        var langue = Langages.de(nomAffiche);
        editeur.setLanguage(langue);
        popups = new PopupsEditeur(editeur, session, couche, theme,
                tailleTexteSp, new CoachJoide());
        popups.setNomFichier(nomAffiche);
        popups.brancherServeur(pont, uri, langue);
        session.addEditListener((debut, fin, retire, insere) -> {
            Consumer<String> present = auditeurEdition;
            if (present != null) {
                present.accept(session.document().text());
            }
        });
    }

    /** Le texte complet courant du document ouvert. */
    public String texte() {
        return session.document().text();
    }

    /**
     * L'auditeur d'édition : le texte complet après chaque édition
     * (appelé sur le fil UI).
     *
     * @param auditeur le consommateur du texte, ou null pour retirer
     */
    public void poserAuditeurEdition(Consumer<String> auditeur) {
        this.auditeurEdition = auditeur;
    }

    /** La taille de police (sp). */
    public void taillePolice(float sp) {
        this.tailleTexteSp = sp;
        editeur.setFont(new jo.celeris.android.view.FontSpec("mono", sp));
        if (popups != null) {
            popups.surTailleTexte(sp);
        }
    }

    /** Le thème : sombre (défaut) ou clair. */
    public void theme(boolean sombre) {
        this.theme = sombre ? EditorThemes.SOMBRE : EditorThemes.CLAIR;
        editeur.setTheme(theme);
        if (popups != null) {
            popups.surTheme(theme);
        }
    }

    /** Le retour à la ligne automatique. */
    public void retourLigne(boolean actif) {
        editeur.setSoftWrap(actif);
    }

    /** Les numéros de ligne. */
    public void numerosLignes(boolean visibles) {
        editeur.setNumerosVisibles(visibles);
    }

    /**
     * Les moteurs sous-jacents (synchronisation, état de l'index…).
     *
     * @return la façade du lot C
     */
    public MoteursLangage moteurs() {
        return moteurs;
    }

    /** Ferme la scène : popups détachés, pont fermé (idempotent). */
    @Override
    public void close() {
        if (popups != null) {
            popups.detacher();
            popups = null;
        }
        pont.close();
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    /** Diagnostics poussés par le pont (déjà sur le fil UI). */
    private void surDiagnostics(String uri, List<DiagnosticLsp> bruts) {
        PopupsEditeur present = popups;
        if (present == null || !uri.equals(uriOuvert)) {
            return;
        }
        CheminDoc chemin;
        try {
            chemin = CheminDoc.de("/" + nomAffiche);
        } catch (RuntimeException e) {
            chemin = CheminDoc.racine();
        }
        List<DiagnosticHote> hotes = new ArrayList<>();
        List<Integer> lignesErreur = new ArrayList<>();
        for (DiagnosticLsp brut : bruts) {
            boolean erreur = brut.gravite() == DiagnosticLsp.ERREUR;
            hotes.add(new DiagnosticHote(chemin,
                    brut.plage().debut().ligne() + 1,
                    brut.plage().debut().colonne() + 1,
                    erreur ? GraviteDiagnostic.ERREUR
                            : GraviteDiagnostic.AVERTISSEMENT,
                    brut.message()));
            if (erreur) {
                lignesErreur.add(brut.plage().debut().ligne());
            }
        }
        present.setDiagnostics(hotes);
        int[] lignes = new int[lignesErreur.size()];
        for (int i = 0; i < lignes.length; i++) {
            lignes[i] = lignesErreur.get(i);
        }
        editeur.marquesGouttiere().setErreursImportees(lignes);
        editeur.invalidate();
    }

    /** L'exécuteur de publication : le fil UI via la vue. */
    private static final class ExecutorPopups implements Executor {

        private final EditorView vue;

        private ExecutorPopups(EditorView vue) {
            this.vue = vue;
        }

        static Executor de(EditorView vue) {
            return new ExecutorPopups(vue);
        }

        @Override
        public void execute(Runnable commande) {
            vue.post(commande);
        }
    }
}
