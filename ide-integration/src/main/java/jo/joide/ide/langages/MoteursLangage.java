package jo.joide.ide.langages;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin;
import jo.codeide.lsp.index.ServiceIndexDisque;
import jo.codeide.lsp.index.VueIndex;
import jo.codeide.lsp.java.EnvironnementCompilation;
import jo.codeide.lsp.java.MoteurJava;
import jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur;
import jo.codeide.lsp.model.AssociationFichier;
import jo.codeide.lsp.model.ModuleLsp;
import jo.codeide.lsp.model.ProjetLsp;
import jo.lspxml.api.ClasspathXml;
import jo.lspxml.api.MoteurLangageXml;
import jo.lspxml.api.ServiceMoteurLangageXml;
import jo.lspxml.api.SessionLangageXml;
import jo.joide.ide.sync.LanguageModelSnapshot;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.model.Workspace;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.SupervisorKt;

/**
 * Les moteurs de langage derrière une façade JoIDE (lot C du câblage
 * P8, ADR-044) : Java (moteur embarqué nourri par contextes de module),
 * Kotlin (sessions étanches par module), XML (session de l'espace) —
 * plus l'index disque CIDX RÉUTILISÉ (§6.8.5 : jamais d'index JoIDE
 * tant qu'aucune mesure ne montre que CIDX ne tient pas le budget).
 *
 * <p>Cycle : {@link #synchroniser(Workspace)} résout les externes,
 * publie un nouvel instantané immuable (lot B) PUIS re-nourrit les
 * moteurs — un contexte par module actif du moteur Java, sessions
 * Kotlin re-alimentées, classpath XML reposé, union indexable
 * reconstruite. Les requêtes sont en TIRAGE : l'hôte détient les
 * tampons et passe le texte complet du document à chaque appel.</p>
 *
 * <p>L'API publique ne montre que des types JoIDE ; les types des
 * briques restent dans ce module (ADR-044). Thread-safe : les moteurs
 * acceptent des appels de tout fil, la synchronisation est sérialisée.</p>
 */
public final class MoteursLangage implements AutoCloseable {

    private final LanguageModelSync sync;
    private final Path stockageIndex;

    /** Moteur Java : environnement multi-modules nourri par l'hôte. */
    private final EnvironnementCompilation environnement;

    /** Sessions Kotlin par module. */
    private final SessionsKotlin kotlin = new SessionsKotlin();

    /** Moteur et session XML de l'espace. */
    private final MoteurLangageXml moteurXml;
    private final SessionLangageXml sessionXml;

    /** Portée des coroutines de construction de l'index (annulable). */
    private final CoroutineScope porteeIndex;

    /** Instantané brut courant (jamais muté, remplacé à chaque sync). */
    private final AtomicReference<ProjetLsp> projet =
            new AtomicReference<>();

    /** CIDX : service, adaptateur courant, budget alloué. */
    private ServiceIndexDisque serviceIndex;
    private AdaptateurIndexNoms adaptateur;
    private long budgetOctets;
    private String empreinteUnion = "";

    /** Verrou du nourrissage (synchronisation sérialisée). */
    private final Object verrou = new Object();

    /** Plafond d'attente du double-tampon moteur après sync (ms). */
    private static final long BORNE_RECONSTRUCTIONS_MS = 10_000L;

    /** Fermé ? Toute requête après fermeture est un état vide honnête. */
    private volatile boolean ferme;

    /**
     * @param sync          synchronisation du modèle de langage (lot B)
     * @param stockageIndex répertoire de stockage CIDX (partagé entre
     *                      projets, mutualisé par empreinte de contenu)
     */
    public MoteursLangage(LanguageModelSync sync, Path stockageIndex) {
        this.sync = Objects.requireNonNull(sync);
        this.stockageIndex = Objects.requireNonNull(stockageIndex);
        this.environnement = new EnvironnementCompilation(null, "17");
        this.environnement.setRouteurFichierModule(this::moduleDe);
        this.moteurXml = ServiceMoteurLangageXml.INSTANCE.unique();
        this.sessionXml = moteurXml.creerSession();
        this.porteeIndex = CoroutineScopeKt.CoroutineScope(
                SupervisorKt.SupervisorJob(null).plus(
                        Dispatchers.getIO()));
    }

    /**
     * Synchronise le projet : résolution, publication de l'instantané,
     * re-nourrissage complet des moteurs.
     *
     * @param workspace modèle du workspace
     * @return la vue JoIDE de l'instantané publié
     */
    public LanguageModelSnapshot synchroniser(Workspace workspace) {
        LanguageModelSnapshot vue = sync.synchroniser(workspace);
        ProjetLsp brut = vue.raw();
        projet.set(brut);
        synchronized (verrou) {
            pousserMoteurs(brut);
            alignerIndex(brut);
            kotlin.renourrir(brut);
            nourrirXml(brut);
            // Le moteur Java sert l'ANCIEN moteur pendant la fenêtre de
            // double-tampon (son mémo répondrait avec l'ancien classpath) :
            // borner l'attente du swap pour que la requête SUIVANTE voie
            // les nouveaux contextes — 10 s de plafond, en pratique des
            // millisecondes ; au dépassement, les requêtes fonctionnent
            // quand même (rejeu d'époque côté moteur).
            environnement.attendreReconstructions(BORNE_RECONSTRUCTIONS_MS);
        }
        return vue;
    }

    /** Vue du dernier instantané publié, ou vide avant la première. */
    public Optional<LanguageModelSnapshot> snapshot() {
        return sync.snapshot();
    }

    /**
     * Ouvre un document (sessions à état : Kotlin, XML).
     *
     * @param uri   URI du document ({@code file:///…} ou chemin absolu)
     * @param texte contenu complet
     */
    public void ouvrir(String uri, String texte) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.KOTLIN) {
            sessionKotlin(uri).ouvrir(uri, texte);
        } else if (langage == Langage.XML) {
            sessionXml.ouvrirDocument(uri, texte);
        }
    }

    /**
     * Modifie un document déjà ouvert (texte complet).
     *
     * @param uri   URI du document
     * @param texte contenu complet
     */
    public void modifier(String uri, String texte) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.KOTLIN) {
            sessionKotlin(uri).modifier(uri, texte);
        } else if (langage == Langage.XML) {
            sessionXml.modifierDocument(uri, texte);
        } else if (langage == Langage.JAVA) {
            String chemin = cheminLogique(uri);
            environnement.calculerFraiche(chemin,
                    moteur -> {
                        moteur.invalidateParseCache(chemin);
                        return null;
                    });
        }
    }

    /**
     * Ferme un document (les sessions à état l'oublient).
     *
     * @param uri URI du document
     */
    public void fermerDocument(String uri) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.KOTLIN) {
            sessionKotlin(uri).fermer(uri);
        } else if (langage == Langage.XML) {
            sessionXml.fermerDocument(uri);
        }
    }

    /**
     * Diagnostics d'un document.
     *
     * @param uri   URI du document
     * @param texte contenu complet
     * @return les diagnostics du moteur propriétaire (vide pour un
     *         fichier hors langage servi — jamais une exception)
     */
    public List<DiagnosticIde> diagnostiquer(String uri, String texte) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.JAVA) {
            String chemin = cheminLogique(uri);
            List<jo.codeide.lsp.java.diagnostics.Diagnostic> bruts =
                    environnement.calculerFraiche(chemin,
                            moteur -> moteur.diagnose(chemin, texte));
            List<DiagnosticIde> out = new ArrayList<>();
            for (var brut : bruts) {
                out.add(new DiagnosticIde(
                        graviteJava(brut.getSeverite()),
                        brut.getMessage(), brut.getPlage().getDebutOffset(),
                        brut.getPlage().getFinOffset(), brut.getCode()));
            }
            return out;
        }
        if (langage == Langage.KOTLIN) {
            List<DiagnosticIde> out = new ArrayList<>();
            for (var brut : sessionKotlin(uri).diagnostics(uri)) {
                out.add(new DiagnosticIde(
                        switch (brut.getSeverite()) {
                            case ERREUR -> DiagnosticIde.Gravite.ERREUR;
                            case AVERTISSEMENT ->
                                DiagnosticIde.Gravite.AVERTISSEMENT;
                            default -> DiagnosticIde.Gravite.INFO;
                        },
                        brut.getMessage(), brut.getPlage().getDebut(),
                        brut.getPlage().getFin(),
                        brut.getCode().getCode()));
            }
            return out;
        }
        if (langage == Langage.XML) {
            List<DiagnosticIde> out = new ArrayList<>();
            for (var brut : sessionXml.diagnostics(uri)) {
                out.add(new DiagnosticIde(
                        brut.getSeverite()
                                == jo.lspxml.api.SeveriteXml.ERREUR
                                ? DiagnosticIde.Gravite.ERREUR
                                : DiagnosticIde.Gravite.AVERTISSEMENT,
                        brut.getMessage(), brut.getDebut(), brut.getFin(),
                        brut.getCode()));
            }
            return out;
        }
        return List.of();
    }

    /**
     * Survol du symbole au décalage.
     *
     * @param uri    URI du document
     * @param texte  contenu complet
     * @param offset décalage (UTF-16) du symbole
     * @return le survol, ou vide si le moteur n'en sert pas
     */
    public Optional<SurvolIde> survol(String uri, String texte,
            int offset) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.JAVA) {
            String chemin = cheminLogique(uri);
            var riche = environnement.calculerFraiche(chemin,
                    moteur -> moteur.survolRiche(chemin, texte, offset));
            return riche == null ? Optional.empty()
                    : Optional.of(new SurvolIde(riche.getSignature(),
                            riche.getDocumentation(),
                            riche.getOrigine()));
        }
        if (langage == Langage.KOTLIN) {
            var survol = sessionKotlin(uri).survol(uri, offset);
            return survol == null ? Optional.empty()
                    : Optional.of(new SurvolIde(survol.getSignature(),
                            survol.getDocumentation(),
                            survol.getOrigine()));
        }
        if (langage == Langage.XML) {
            var contenu = sessionXml.survol(uri, offset);
            return contenu == null ? Optional.empty()
                    : Optional.of(new SurvolIde(contenu.getValeur(),
                            null, null));
        }
        return Optional.empty();
    }

    /**
     * Complétion au décalage.
     *
     * @param uri    URI du document
     * @param texte  contenu complet
     * @param offset décalage (UTF-16) du curseur
     * @return les propositions du moteur propriétaire (vide sinon)
     */
    public List<PropositionCompletion> completer(String uri, String texte,
            int offset) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.JAVA) {
            String chemin = cheminLogique(uri);
            List<jo.codeide.lsp.java.interne.ItemCompletion> items =
                    environnement.calculerFraiche(chemin,
                            moteur -> moteur.complete(chemin, texte,
                                    offset));
            List<PropositionCompletion> out = new ArrayList<>();
            for (var item : items) {
                out.add(new PropositionCompletion(item.getLibelle(),
                        item.getNature().name(),
                        item.getTexteInsertion() == null
                                ? item.getLibelle()
                                : item.getTexteInsertion(),
                        item.getDetail(), item.getAutoImportFqn()));
            }
            return out;
        }
        if (langage == Langage.KOTLIN) {
            List<PropositionCompletion> out = new ArrayList<>();
            for (var p : sessionKotlin(uri).completion(uri, offset)) {
                out.add(new PropositionCompletion(p.getLibelle(),
                        p.getNature().name(),
                        p.getTexteInsertion() == null ? p.getLibelle()
                                : p.getTexteInsertion(),
                        p.getDetail(), p.getFqcnImport()));
            }
            return out;
        }
        if (langage == Langage.XML) {
            List<PropositionCompletion> out = new ArrayList<>();
            for (var p : sessionXml.completion(uri, offset)) {
                out.add(new PropositionCompletion(p.getLibelle(),
                        p.getGenre().name(), p.getLibelle(),
                        p.getDetail(), null));
            }
            return out;
        }
        return List.of();
    }

    /**
     * Définitions du symbole au décalage.
     *
     * @param uri    URI du document
     * @param texte  contenu complet
     * @param offset décalage (UTF-16) du symbole
     * @return les cibles de navigation (vide si aucune)
     */
    public List<CibleNavigation> definition(String uri, String texte,
            int offset) {
        exigeOuvert();
        Langage langage = Langage.de(uri);
        if (langage == Langage.JAVA) {
            String chemin = cheminLogique(uri);
            List<jo.codeide.lsp.java.navigation.CibleNavigation> cibles =
                    environnement.calculerFraiche(chemin,
                            moteur -> moteur.definition(chemin, texte,
                                    offset));
            List<CibleNavigation> out = new ArrayList<>();
            for (var cible : cibles) {
                out.add(new CibleNavigation(
                        cible.getGenre().name(), cible.getUri(),
                        cible.getNomSimple(),
                        cible.getPlage().getDebutOffset(),
                        cible.getPlage().getFinOffset()));
            }
            return out;
        }
        if (langage == Langage.KOTLIN) {
            List<CibleNavigation> out = new ArrayList<>();
            for (var cible : sessionKotlin(uri).definition(uri, offset)) {
                out.add(new CibleNavigation("SOURCE_PROJET",
                        cible.getUri(), null,
                        cible.getPlage().getDebut(),
                        cible.getPlage().getFin()));
            }
            return out;
        }
        if (langage == Langage.XML) {
            List<CibleNavigation> out = new ArrayList<>();
            for (var cible : sessionXml.definitions(uri, offset)) {
                out.add(new CibleNavigation("SOURCE_PROJET",
                        cible.getUri(), null, cible.getDebut(),
                        cible.getFin()));
            }
            return out;
        }
        return List.of();
    }

    /**
     * État de l'index disque CIDX — phases honnêtes (CONSTRUCTION,
     * PRET, PARTIEL, ERREUR), jamais un « prêt » menteur.
     */
    public EtatIndex etatIndex() {
        AdaptateurIndexNoms courant = adaptateur;
        if (courant == null) {
            return EtatIndex.initial();
        }
        var etat = courant.vue().getEtat().getValue();
        return new EtatIndex(etat.getPhase().name(), etat.getTotal(),
                etat.getFaits(), List.copyOf(etat.getAvertissements()),
                List.copyOf(etat.getErreurs()));
    }

    /** Ferme tout ce qui a été monté (idempotent). */
    @Override
    public void close() {
        if (ferme) {
            return;
        }
        ferme = true;
        synchronized (verrou) {
            environnement.dispose();
            kotlin.toutFermer();
            sessionXml.close();
            if (adaptateur != null) {
                adaptateur.fermer();
                adaptateur = null;
            }
            CoroutineScopeKt.cancel(porteeIndex,
                    new java.util.concurrent.CancellationException(
                            "fermeture JoIDE"));
            serviceIndex = null;
        }
    }

    // ------------------------------------------------------------------
    // Nourrissage (sous verrou, à chaque synchronisation)
    // ------------------------------------------------------------------

    private void pousserMoteurs(ProjetLsp brut) {
        Map<String, ModuleLsp> parNom =
                new java.util.HashMap<>();
        for (ModuleLsp module : brut.getModules()) {
            parNom.put(module.getNom(), module);
        }
        for (ModuleLsp module : brut.getModules()) {
            if (module.getIgnore()) {
                continue;
            }
            environnement.remplacerContexteModule(
                    module.getNom(),
                    racinesDe(module, parNom),
                    jarsDe(module),
                    module.getCompatibiliteSource(),
                    contratAndroid(module));
        }
    }

    private void alignerIndex(ProjetLsp brut) {
        UnionIndexable union = UnionIndexable.de(brut);
        long budgetVoulu = union.budgetAuto();
        boolean serviceARecree = serviceIndex == null
                || budgetVoulu > budgetOctets;
        if (serviceARecree) {
            budgetOctets = Math.max(budgetOctets, budgetVoulu);
            serviceIndex = new ServiceIndexDisque(
                    stockageIndex.toFile(), porteeIndex,
                    Dispatchers.getIO(),
                    ServiceIndexDisque.parallelismeParDefaut(),
                    budgetOctets);
        }
        boolean unionChangee =
                !empreinteUnion.equals(union.empreinte());
        if (serviceARecree || unionChangee) {
            if (adaptateur != null) {
                adaptateur.fermer();
            }
            VueIndex vue = serviceIndex.vuePour(union.instantane());
            adaptateur = new AdaptateurIndexNoms(vue);
            empreinteUnion = union.empreinte();
            environnement.setIndexNoms(adaptateur);
            serviceIndex.construire(vue);
        }
    }

    private void nourrirXml(ProjetLsp brut) {
        File dossierPlateforme = null;
        List<File> racinesSources = new ArrayList<>();
        for (ModuleLsp module : brut.getModules()) {
            if (module.getIgnore()) {
                continue;
            }
            if (dossierPlateforme == null && module.getAndroidJar()
                    != null) {
                File parent = new File(module.getAndroidJar())
                        .getParentFile();
                if (parent != null && parent.isDirectory()) {
                    dossierPlateforme = parent;
                }
            }
            var android = module.getAndroid();
            if (android == null) {
                continue;
            }
            for (String res : android.getDossiersRes()) {
                File dossier = new File(res).getParentFile();
                if (dossier != null && !racinesSources.contains(dossier)) {
                    racinesSources.add(dossier);
                }
            }
        }
        ClasspathXml classpath = new ClasspathXml(dossierPlateforme,
                racinesSources,
                adaptateur == null ? null : vueNoms(adaptateur),
                List.of());
        moteurXml.definirClasspath(classpath);
        sessionXml.definirClasspath(classpath);
    }

    /** Couture FQCN du classpath pour la garde « balise inconnue » XML. */
    private jo.lspxml.api.VueNomsXml vueNoms(
            AdaptateurIndexNoms index) {
        return new jo.lspxml.api.VueNomsXml() {
            @Override
            public List<String> nomsCommencantPar(String prefixe,
                    int limite) {
                List<String> fqcns = new ArrayList<>();
                for (String nom : index.nomsParPrefixe(prefixe,
                        Math.max(1, limite))) {
                    fqcns.addAll(index.fqnsForSimpleName(nom));
                }
                return fqcns;
            }
        };
    }

    // ------------------------------------------------------------------
    // Dérivations du modèle (mêmes règles que l'hôte de référence)
    // ------------------------------------------------------------------

    /** Racines : principal + générés du module + frères dépendus. */
    private List<java.nio.file.Path> racinesDe(ModuleLsp module,
            Map<String, ModuleLsp> parNom) {
        List<java.nio.file.Path> racines = new ArrayList<>();
        for (String dossier : module.getSources().getPrincipal()) {
            racines.add(java.nio.file.Paths.get(dossier));
        }
        for (String dossier : module.getSources().getGeneres()) {
            racines.add(java.nio.file.Paths.get(dossier));
        }
        for (var dep : module.getDependancesModules()) {
            ModuleLsp frere = parNom.get(dep.getCible());
            if (frere != null) {
                for (String dossier : frere.getSources().getPrincipal()) {
                    racines.add(java.nio.file.Paths.get(dossier));
                }
            }
        }
        return racines;
    }

    /** Jars existants du module + plateforme android.jar. */
    private List<java.nio.file.Path> jarsDe(ModuleLsp module) {
        List<java.nio.file.Path> jars = new ArrayList<>();
        for (var entree : module.getClasspath().getEntrees()) {
            if (entree.getKind() == jo.codeide.lsp.model.KindEntree.JAR
                    && entree.getExiste()) {
                jars.add(java.nio.file.Paths.get(entree.getChemin()));
            }
        }
        if (module.getAndroidJar() != null) {
            jars.add(java.nio.file.Paths.get(module.getAndroidJar()));
        }
        return jars;
    }

    /** Contrat Android du moteur Java, miroir de la facette du modèle. */
    private ContratAndroidMoteur contratAndroid(ModuleLsp module) {
        var android = module.getAndroid();
        if (android == null) {
            return null;
        }
        List<java.nio.file.Path> dossiersRes = new ArrayList<>();
        for (String res : android.getDossiersRes()) {
            dossiersRes.add(java.nio.file.Paths.get(res));
        }
        var features = android.getBuildFeatures();
        return new ContratAndroidMoteur(
                android.getNamespace() == null ? ""
                        : android.getNamespace(),
                android.getApplicationId(), android.getMinSdk(),
                android.getTargetSdk(), android.getCompileSdk(),
                dossiersRes,
                android.getManifest() == null ? null
                        : java.nio.file.Paths.get(android.getManifest()),
                features.getViewBinding(), features.getDataBinding(),
                features.getBuildConfig(),
                android.getNonTransitiveRClass(),
                android.getNonFinalResIds(),
                android.getResValues(), android.getBuildConfigFields());
    }

    /** Module propriétaire d'un fichier (routeur du moteur Java). */
    private String moduleDe(String chemin) {
        ProjetLsp courant = projet.get();
        if (courant == null) {
            return null;
        }
        ModuleLsp module = AssociationFichier.INSTANCE.associer(courant,
                cheminLogique(chemin));
        return module == null ? null : module.getNom();
    }

    /** Session Kotlin du module propriétaire du fichier. */
    private SessionLangageKotlin sessionKotlin(String uri) {
        return kotlin.sessionDe(projet.get(), moduleDe(uri));
    }

    /** URI → chemin logique (séparateurs « / », sans préfixe file://). */
    private static String cheminLogique(String uri) {
        String chemin = uri;
        if (chemin.startsWith("file://")) {
            chemin = chemin.substring("file://".length());
        }
        return chemin.replace('\\', '/');
    }

    private void exigeOuvert() {
        if (ferme) {
            throw new IllegalStateException(
                    "language engines already closed");
        }
    }

    private static DiagnosticIde.Gravite graviteJava(
            jo.codeide.lsp.java.diagnostics.Severite severite) {
        return switch (severite) {
            case ERREUR -> DiagnosticIde.Gravite.ERREUR;
            case AVERTISSEMENT -> DiagnosticIde.Gravite.AVERTISSEMENT;
            default -> DiagnosticIde.Gravite.INFO;
        };
    }

    /** Langage servi pour une URI (routage par extension). */
    private enum Langage {
        JAVA, KOTLIN, XML, AUTRE;

        static Langage de(String uri) {
            String chemin = uri.toLowerCase();
            if (chemin.endsWith(".java")) {
                return JAVA;
            }
            if (chemin.endsWith(".kt") || chemin.endsWith(".kts")) {
                return KOTLIN;
            }
            if (chemin.endsWith(".xml")) {
                return XML;
            }
            return AUTRE;
        }
    }
}
