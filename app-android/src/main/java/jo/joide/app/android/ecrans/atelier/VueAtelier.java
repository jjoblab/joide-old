package jo.joide.app.android.ecrans.atelier;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jo.joide.app.android.AgentService;
import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence;
import jo.joide.app.android.atelier.DecisionConfiguration;
import jo.joide.app.android.atelier.EcrituresFusionnees;
import jo.joide.app.android.atelier.EtatApercu;
import jo.joide.app.android.atelier.EtatAtelier;
import jo.joide.app.android.atelier.EtatFeuille;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.atelier.EtatPopups;
import jo.joide.app.android.atelier.EtatProblemes;
import jo.joide.app.android.atelier.VueApercu;
import jo.joide.app.android.atelier.VueArbre;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.app.android.atelier.agent.VueAgent;
import jo.joide.app.android.console.EtatConsole;
import jo.joide.app.android.console.VueConsole;
import jo.joide.app.android.databinding.ActivityAtelierBinding;
import jo.joide.app.android.databinding.TiroirAgentPousseBinding;
import jo.joide.app.android.databinding.VueApercuZoneBinding;
import jo.joide.app.android.databinding.VueArbreZoneBinding;
import jo.joide.app.android.logcat.FluxLogcat;
import jo.joide.app.android.parametres.PreferencesAndroid;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.ui.Fab;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.PortePopups;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.TiroirPousse;
import jo.joide.app.android.ui.atelier.BarreOnglets;
import jo.joide.app.android.ui.atelier.BarreSymboles;
import jo.joide.app.android.ui.atelier.FeuilleBasse;
import jo.joide.app.android.ui.atelier.FilAriane;
import jo.joide.app.android.ui.atelier.FragmentSection;
import jo.joide.app.android.ui.atelier.ListeJournal;
import jo.joide.app.android.ui.atelier.ListeLogcat;
import jo.joide.app.android.ui.atelier.ListeProblemes;
import jo.joide.app.android.ui.atelier.TiroirFichiers;
import jo.joide.ide.apercu.ApercuLayouts;
import jo.joide.ide.editeur.SceneEditeur;

/**
 * Lot A6 (ADR-085 décisions 1, 2 et 3) — LA VUE D'ÉCRAN DE
 * L'ATELIER : la coordonnatrice du binding racine
 * ({@code activity_atelier.xml}, + {@code layout-w720dp} — le grand
 * écran n'est plus assemblé en code, {@code ContexteUi} et
 * {@code construireUi()} ont disparu). Elle porte les états d'UI, le
 * CÂBLAGE des zones (les auditeurs restent ici : l'écran reste la
 * coque — thème, cycle de vie, intents, retour), les mises à jour de
 * l'habillage (volets, FAB, verdicts) et les zones DIFFÉRÉES par
 * {@code ViewStub} (console, aperçu, tiroir agent — montées à la
 * demande). Elle implémente {@link HoteAtelier} : les contrôleurs ne
 * référencent plus l'écran, la session vit dans {@link SessionAtelier}.
 *
 * <p>Lot A7 (ADR-085 décisions 12 et 13) : elle porte le
 * PORTE-POPUPS de l'atelier (le voile du layout racine, les menus
 * ancrés et les cartes centrées — l'écran les ferme en
 * {@code onPause}/{@code onDestroy}) et son état pur
 * {@link EtatPopups} (l'ordre du retour : un popup ouvert se ferme EN
 * PREMIER ; les brouillons de saisie). Le découpage de
 * {@code ControleurAgent} y vit : les contrôleurs MODELS, USAGES,
 * HISTORIQUE et CONNEXIONS sont montés ici et passés au contrôleur
 * de l'agent.</p>
 *
 * <p>Lot A8 (ADR-085 décision 10 — cycle de vie et configuration) :
 * la géométrie ne détruit plus l'atelier. Le manifest porte les
 * changements de GÉOMÉTRIE seuls ; {@link #surChangementConfiguration}
 * ferme les popups, ré-inflamme LA RACINE SEULE si la classe de
 * disposition change (compact ↔ {@code layout-w720dp} — la décision
 * pure {@link DecisionConfiguration} décide, testée JVM) et
 * RÉATTACHE le puits (la scène celeris survit, jamais reconstruite,
 * {@link SessionAtelier} n'est pas touché). L'état d'UI minimal se
 * sauve/restaure par {@link #sauverEtat(Bundle)} /
 * {@link #restaurer(Bundle)} (export de l'{@link EtatAtelier} et des
 * brouillons) pour les recréations RESTANTES (thème, langue,
 * police). La garde de cycle de vie des callbacks asynchrones se
 * pose UNE fois dans {@link #executerSurFilUi(Runnable)} (I8).</p>
 *
 * <p>Cette classe n'est PAS une vue Android : c'est la collaboratrice
 * de la coque, construite avec le binding monté. La doctrine P8 reste
 * : l'UI est mince, la logique vit dans les états purs
 * ({@code EtatAtelier}, {@code EtatFeuille}, {@code EtatConsole}…),
 * ici on traduit et on déroule.</p>
 */
final class VueAtelier implements HoteAtelier {

    /** Les clés Bundle de l'état d'UI minimal (lot A8 — la coque
     *  traduit, l'état pur ne touche jamais Bundle). */
    private static final String CLE_ONGLETS = "joide.atelier.onglets";
    private static final String CLE_MODIFIES = "joide.atelier.modifies";
    private static final String CLE_ACTIF = "joide.atelier.actif";
    private static final String CLE_TIROIR = "joide.atelier.tiroir";
    private static final String CLE_CONSOLE = "joide.atelier.console";
    private static final String CLE_APERCU_PLEIN = "joide.atelier.apercu";
    private static final String CLE_VOLET = "joide.atelier.volet";
    private static final String CLE_PROJET = "joide.atelier.projet";
    private static final String CLE_SECTION = "joide.atelier.section";
    private static final String CLE_BROUILLONS_CLES =
            "joide.atelier.brouillons.cles";
    private static final String CLE_BROUILLONS_VALEURS =
            "joide.atelier.brouillons.valeurs";

    private final EcranAtelier ecran;
    /** Le binding racine — RÉ-INFLABLE (lot A8) : la géométrie change,
     *  le layout la suit, la vue garde le même champ. */
    private ActivityAtelierBinding binding;

    private final Graphite.Palette palette;
    private final Reglages.Source sourceReglages;
    private final EtatAtelier etat;
    private final EtatConsole etatConsole = new EtatConsole();
    private final EtatApercu etatApercu = new EtatApercu();
    private final EtatProblemes etatProblemes = new EtatProblemes();
    private final EtatJournal etatJournal = new EtatJournal();
    private final EtatLogcat etatLogcat = new EtatLogcat();
    private EtatAgentChat etatAgent = new EtatAgentChat();

    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-atelier");
                fil.setDaemon(true);
                return fil;
            });
    private final ExecutorService ecrivain =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread fil = new Thread(runnable, "joide-ecrivain");
                fil.setDaemon(true);
                return fil;
            });

    /** La session (montage, fermeture, état de l'IDE monté). */
    private final SessionAtelier session;

    /** Le porte-popups de l'atelier (menus ancrés, cartes centrées
     *  sur le voile du layout racine — lot A7, décision 12). */
    private final PortePopups popups;

    /** L'état des popups (ordre du retour, brouillons — lot A7). */
    private final EtatPopups etatPopups = new EtatPopups();

    /** Les contrôleurs (l'écran ne les connaît que par la vue). */
    private final ControleurBuild controleurBuild;
    private final ControleurAgent controleurAgent;
    private final ControleurModelesAgent controleurModelesAgent;
    private final ControleurEdition controleurEdition;
    private final ControleurFichiers controleurFichiers;

    // ---- les états d'arbre et d'agent partagés avec les contrôleurs
    private final Set<String> dossiersOuverts = new LinkedHashSet<>();
    private String filtreTiroir = "";
    private final List<PieceJointe> piecesAgent = new ArrayList<>();
    private final List<PieceJointe> piecesEnAttenteAgent =
            new ArrayList<>();
    private String messageEnAttenteAgent;
    private AgentService.AgentBinder agent;
    private boolean buildEnCours;

    // ---- les zones différées : l'aperçu et le tiroir agent par
    // ViewStub ; les sections de la feuille par FRAGMENTS (lot C3 —
    // le registre des fragments montés ; l'étiquette retrouve
    // l'instance après une restauration du FragmentManager, la
    // section MONTRÉE se garde pour éviter les transactions vides)
    private VueConsole vueConsole;
    private final EnumMap<EtatFeuille.Section, FragmentSection>
            fragmentsFeuille = new EnumMap<>(EtatFeuille.Section.class);
    private EtatFeuille.Section sectionMontreeFeuille;
    private VueApercu vueApercu;
    private TiroirPousse tiroirAgent;
    private VueAgent vueAgent;
    private VueArbre vueArbre;

    // ---- l'UI volatile, re-posée à toute ré-inflation de racine
    // ---- (lot A8) : la vérité vit dans les états et la session ; ces
    // ---- champs portent ce qu'aucun état ne porte
    /** Le texte de la bannière de compatibilité (null : jamais posée). */
    private String texteBanniereCompat;
    /** Le dernier titre de l'état vide du puits (id de ressource). */
    private int titreEtatVide = R.string.ide_chargement;
    /** La dernière aide de l'état vide du puits (texte posé). */
    private CharSequence aideEtatVide = "";

    VueAtelier(EcranAtelier ecran, ActivityAtelierBinding binding,
               Graphite.Palette palette) {
        this.ecran = ecran;
        this.binding = binding;
        this.palette = palette;
        sourceReglages = PreferencesAndroid.source(ecran);
        etat = new EtatAtelier(EtatAtelier.estGrandEcran(
                ecran.getResources().getConfiguration().screenWidthDp));
        session = new SessionAtelier(ecran, this);
        // le porte-popups : le voile déclaré DERNIER enfant du layout
        // racine couvre tout (les deux tailles) — les cartes centrées
        // vivent dans leur PROPRE fenêtre au-dessus (lot A7)
        popups = new PortePopups(binding.voilePopup);
        controleurBuild = new ControleurBuild(this);
        controleurModelesAgent = new ControleurModelesAgent(this);
        ControleurUsagesAgent controleurUsagesAgent =
                new ControleurUsagesAgent(this);
        ControleurHistoriqueAgent controleurHistoriqueAgent =
                new ControleurHistoriqueAgent(this);
        ControleurConnexionsMcp controleurConnexionsMcp =
                new ControleurConnexionsMcp(this);
        controleurAgent = new ControleurAgent(this,
                controleurModelesAgent, controleurUsagesAgent,
                controleurHistoriqueAgent, controleurConnexionsMcp);
        controleurEdition = new ControleurEdition(this);
        controleurFichiers = new ControleurFichiers(this);
        // la bannière de compatibilité : le fond est un MÉLANGE calculé
        // (alerte 12 % sur fond — décision 8 : couleur dynamique), le
        // reste vit dans le layout
        binding.banniereCompat.setBackgroundColor(Graphite.melange(
                palette.alerte, 0.12f, palette.fond));
        monterVueArbre();
        cabler();
    }

    // ------------------------------------------------------------------
    // Le câblage des zones (les auditeurs : la vue d'écran garde le
    // rôle de l'écran d'hier, les corps sont inchangés)
    // ------------------------------------------------------------------

    private void monterVueArbre() {
        // l'arborescence : UNE instance (vue_arbre_zone), posée dans le
        // conteneur de la disposition courante — le cadre du tiroir
        // fichiers en compact, la colonne amarrée en grand écran
        vueArbre = VueArbreZoneBinding.inflate(
                ecran.getLayoutInflater()).getRoot();
        ViewGroup conteneur = etat.grandEcran() && binding.colonneArbre != null
                ? binding.colonneArbre
                : binding.tiroirFichiers.cadreArbre();
        conteneur.addView(vueArbre,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        // le menu « Nouveau » de l'arbre s'ancre à sa tête : le
        // PORTE-POPUPS de l'atelier le porte (registre partagé — le
        // retour système le ferme en premier, lot A7)
        vueArbre.poserPopups(popups);
        vueArbre.setAuditeur(new VueArbre.AuditeurArbre() {
            @Override
            public void surDossier(String chemin, boolean ouvrir) {
                if (ouvrir) {
                    dossiersOuverts.add(chemin);
                } else {
                    dossiersOuverts.remove(chemin);
                }
                majArbre();
            }

            @Override
            public void surFichier(String chemin) {
                controleurEdition.ouvrirFichier(chemin, null);
            }

            @Override
            public void surNouveau(boolean fichier) {
                controleurFichiers.proposerNouveau(fichier);
            }

            @Override
            public void surMenuContextuel(String chemin,
                                          boolean dossier) {
                controleurFichiers.montrerMenuContextuel(chemin, dossier);
            }
        });
    }

    private void cabler() {
        // ---- la barre supérieure (lot L2) : les auditeurs restent
        // ici, l'état reste EtatAtelier
        binding.barreHaut.binding.btnTiroirFichiers.setOnClickListener(
                vue -> {
                    etat.ouvrirTiroir();
                    if (binding.tiroir != null) {
                        binding.tiroir.ouvrir();
                    }
                });
        binding.barreHaut.binding.piluleIndex.setOnClickListener(
                vue -> controleurFichiers.montrerDetailIndex());
        binding.barreHaut.binding.cadreBoutonAgent.setOnClickListener(
                vue -> controleurAgent.ouvrirAgent());
        binding.barreHaut.binding.boutonOptions.setOnClickListener(
                vue -> ouvrirMenu());

        // ---- la bande d'onglets (RecyclerView fermable, lot A6)
        binding.barreOnglets.setAuditeur(new BarreOnglets.Auditeur() {
            @Override
            public void surActivation(String chemin) {
                etat.activer(chemin);
                controleurEdition.montrerFichier(chemin, null);
            }

            @Override
            public void surFermeture(String chemin) {
                controleurEdition.fermerOnglet(chemin);
            }

            @Override
            public void surApercu() {
                etat.basculerApercuPleinEcran();
                majVolets();
                if (session.apercu != null && session.scene != null
                        && FichiersProjet.estMiseEnPage(
                        session.fichierCourant)) {
                    session.apercu.rendreSansDelai(
                            session.scene.texte());
                }
            }
        });

        // ---- la barre de symboles (§3.5) : visible seulement quand
        // le clavier est affiché (détecteur d'insets COMPAT — correctif
        // C5 du lot A1)
        binding.barreSymboles.setAuditeur(new BarreSymboles.Auditeur() {
            @Override
            public void inserer(String texte) {
                controleurEdition.insererSymbole(texte);
            }

            @Override
            public void commenterLigne() {
                controleurEdition.commenterLigneCourante();
            }

            @Override
            public void dupliquerLigne() {
                controleurEdition.dupliquerLigneCourante();
            }

            @Override
            public void deplacerCurseur(int delta) {
                if (session.scene != null) {
                    session.scene.deplacerCurseur(delta);
                }
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.getRoot(), (vue, insets) -> {
                    boolean clavier = insets.getInsets(
                            WindowInsetsCompat.Type.ime()).bottom > 0;
                    binding.barreSymboles.poserVisible(clavier);
                    binding.fab.setVisibility(clavier ? View.GONE
                            : View.VISIBLE);
                    return insets;
                });

        // ---- la feuille basse (lot L3 puis C3) : les SECTIONS vivent
        // en fragments (FragmentSection), montées par transaction
        // dans contenu_feuille — la feuille demande, l'écran monte
        binding.feuille.setAuditeur(new FeuilleBasse.Auditeur() {
            @Override
            public void surOuvrirProbleme(String chemin, int ligne) {
                controleurEdition.ouvrirFichier(chemin, ligne);
            }

            @Override
            public void surPipeline() {
                etat.deplierConsole();
                majVolets();
            }

            @Override
            public void surSection(EtatFeuille.Section section) {
                monterSectionFeuille(section);
            }

            @Override
            public void surLogcat(boolean actif) {
                // ADR-084 : le relais de journal (brique utilisateur,
                // permission signature) pousse dans FluxLogcat ; la
                // section lit le tampon quand elle est ouverte
                if (!actif) {
                    FluxLogcat.tampon().poserAuditeur(null);
                    return;
                }
                FluxLogcat.tampon().poserAuditeur(
                        controleurEdition.auditeurFluxLogcat);
                controleurEdition.viderFluxDansEtatLogcat();
            }
        });
        binding.feuille.boutonProblemeSuivant().setOnClickListener(
                vue -> {
                    // §3.6 : le ↓ saute à l'erreur suivante SANS
                    // déplier
                    EtatProblemes.Probleme suivant = etatProblemes.suivant(
                            session.fichierCourant == null ? ""
                                    : session.fichierCourant,
                            controleurEdition.ligneCourante());
                    if (suivant != null) {
                        controleurEdition.ouvrirFichier(suivant.chemin(),
                                suivant.ligne());
                    }
                });
        binding.feuille.cibleProblemes().setOnClickListener(vue -> {
            EtatProblemes.Probleme premier =
                    etatProblemes.premierDuFichier(
                            session.fichierCourant == null ? ""
                                    : session.fichierCourant);
            if (premier != null) {
                controleurEdition.ouvrirFichier(premier.chemin(),
                        premier.ligne());
            }
        });

        // ---- le rail du grand écran (la rangée amarrée) : arbre
        // visible par défaut, console par l'état
        if (binding.boutonRailArbre != null) {
            binding.boutonRailArbre.setOnClickListener(vue -> {
                boolean visible = binding.colonneArbre.getVisibility()
                        != View.VISIBLE;
                binding.colonneArbre.setVisibility(
                        visible ? View.VISIBLE : View.GONE);
                binding.boutonRailArbre.setCouleur(
                        visible ? palette.accent
                                : palette.texteSecondaire);
            });
        }
        if (binding.boutonRailConsole != null) {
            binding.boutonRailConsole.setOnClickListener(vue -> {
                etat.basculerConsole();
                majVolets();
            });
        }

        // ---- le tiroir fichiers (écran compact)
        if (binding.tiroirFichiers != null) {
            binding.tiroirFichiers.setAuditeur(
                    new TiroirFichiers.Auditeur() {
                        @Override
                        public void surNouveau(boolean fichier) {
                            controleurFichiers.proposerNouveau(fichier);
                        }

                        @Override
                        public void surToutReduire() {
                            dossiersOuverts.clear();
                            majArbre();
                        }

                        @Override
                        public void surLocaliser() {
                            controleurFichiers.localiserFichierActif();
                        }

                        @Override
                        public void surFiltre(String texte) {
                            filtreTiroir = texte;
                            majArbre();
                        }
                    });
        }

        // ---- le FAB Construire (pilule pendant le build, lot J)
        binding.fab.setAuditeur(new Fab.AuditeurFab() {
            @Override
            public void surLancer() {
                controleurBuild.construire();
            }

            @Override
            public void surOuvrirConsole() {
                etat.deplierConsole();
                majVolets();
            }

            @Override
            public void surArreter() {
                controleurBuild.arreterBuild();
            }
        });
    }

    // ------------------------------------------------------------------
    // Lot C3 — la feuille en FRAGMENTS : le montage par transaction
    // (paresseux comme le ViewStub d'hier, sans vue déplacée)
    // ------------------------------------------------------------------

    /**
     * Monte (ou montre) le FRAGMENT d'une section de la feuille dans
     * {@code contenu_feuille} : paresseux (rien n'existe tant que la
     * section n'est pas demandée), la transaction cache les autres
     * (instances conservées, coût par changement borné).
     * {@code commitNow} : la vue existe AU RETOUR — le câblage et la
     * re-pose d'état suivent (jamais une section muette). L'étiquette
     * retrouve l'instance après une restauration du FragmentManager
     * (recréation de thème/langue/police) : JAMAIS une double pose.
     */
    private void monterSectionFeuille(EtatFeuille.Section section) {
        if (sectionMontreeFeuille == section
                && fragmentsFeuille.containsKey(section)) {
            return;
        }
        FragmentManager gestionnaire = ecran.getSupportFragmentManager();
        FragmentTransaction transaction = gestionnaire.beginTransaction();
        FragmentSection cible = fragmentsFeuille.get(section);
        if (cible == null) {
            cible = (FragmentSection) gestionnaire.findFragmentByTag(
                    FragmentSection.etiquette(section));
            if (cible == null) {
                cible = FragmentSection.cree(section);
                transaction.add(R.id.contenu_feuille, cible,
                        FragmentSection.etiquette(section));
            }
            fragmentsFeuille.put(section, cible);
        }
        for (FragmentSection fragment : fragmentsFeuille.values()) {
            transaction.hide(fragment);
        }
        transaction.show(cible);
        transaction.commitNow();
        sectionMontreeFeuille = section;
    }

    /**
     * La console se monte EN RÉSERVE (ajoutée cachée) : le premier
     * événement de build l'exige (le verdict vit dans sa vue) même
     * si une AUTRE section est visible — la feuille décide seule de
     * ce qui se MONTRÉ. Paresseuse comme hier : jamais pendant le
     * chargement de session.
     */
    private void assurerConsoleMontee() {
        if (fragmentsFeuille.containsKey(EtatFeuille.Section.CONSOLE)) {
            return;
        }
        FragmentManager gestionnaire = ecran.getSupportFragmentManager();
        FragmentSection console = (FragmentSection) gestionnaire
                .findFragmentByTag(
                        FragmentSection.etiquette(EtatFeuille.Section
                                .CONSOLE));
        if (console == null) {
            console = FragmentSection.cree(EtatFeuille.Section.CONSOLE);
            FragmentTransaction transaction = gestionnaire
                    .beginTransaction();
            transaction.add(R.id.contenu_feuille, console,
                    FragmentSection.etiquette(EtatFeuille.Section.CONSOLE));
            transaction.hide(console);
            transaction.commitNow();
        }
        fragmentsFeuille.put(EtatFeuille.Section.CONSOLE, console);
    }

    /**
     * Lot C3 : la vue d'une section vient d'être créée (fragment
     * monté ou restauré) — câblage des auditeurs et RE-POSE de
     * l'état courant : le symptôme du terrain (« aucune vue »)
     * meurt ici, chaque section arrive déjà remplie.
     */
    void surVueSection(EtatFeuille.Section section, View vue) {
        switch (section) {
            case CONSOLE -> {
                vueConsole = (VueConsole) vue;
                vueConsole.setAuditeur(new VueConsole.AuditeurConsole() {
                    @Override
                    public void surArreter() {
                        controleurBuild.arreterBuild();
                    }

                    @Override
                    public void surVider() {
                        confirmerVidage();
                    }

                    @Override
                    public void surFiltre(EtatConsole.Filtre filtre) {
                        etatConsole.setFiltre(filtre);
                        majConsole();
                    }

                    @Override
                    public void surPhase(
                            jo.joide.app.android.ConsoleBuild.Phase phase) {
                        etatConsole.setFiltrePhase(phase);
                        majConsole();
                    }

                    @Override
                    public void surOuvrir(String cible) {
                        controleurEdition.ouvrirCible(cible);
                    }
                });
                majConsole();
            }
            case PROBLEMES -> {
                ListeProblemes liste = (ListeProblemes) vue;
                binding.feuille.poserListeProblemes(liste);
                liste.setAuditeur(new ListeProblemes.Auditeur() {
                    @Override
                    public void surOuvrir(String chemin, int ligne) {
                        controleurEdition.ouvrirFichier(chemin, ligne);
                    }
                });
                controleurEdition.reposerFeuille();
            }
            case JOURNAL -> {
                binding.feuille.poserListeJournal((ListeJournal) vue);
                controleurEdition.reposerFeuille();
            }
            case LOGCAT -> {
                binding.feuille.poserListeLogcat((ListeLogcat) vue);
                // le flux démarre AU MONTAGE aussi (le tap du rail ne
                // le démarrait pas — la section restait muette) : le
                // tampon se vide dans l'état, liste et verdict suivent
                FluxLogcat.tampon().poserAuditeur(
                        controleurEdition.auditeurFluxLogcat);
                controleurEdition.viderFluxDansEtatLogcat();
                controleurEdition.reposerFeuille();
            }
        }
    }

    /**
     * Lot C3 : la vue d'une section meurt (ré-inflation de racine,
     * fermeture) — le registre s'oublie, le flux logcat lâche
     * l'auditeur (jamais une vue morte alimentée).
     */
    void surSectionDetruite(EtatFeuille.Section section) {
        switch (section) {
            case CONSOLE -> vueConsole = null;
            case PROBLEMES -> binding.feuille.poserListeProblemes(null);
            case JOURNAL -> binding.feuille.poserListeJournal(null);
            case LOGCAT -> {
                binding.feuille.poserListeLogcat(null);
                FluxLogcat.tampon().poserAuditeur(null);
            }
        }
    }

    /** Monte la zone d'aperçu (plein écran ou volet droit demandé) et
     *  rejoue le dernier texte de mise en page : le rendu arrivé avant
     *  le montage n'est pas perdu. */
    private void monterApercu() {
        if (vueApercu != null || binding.stubApercu == null) {
            return;
        }
        vueApercu = VueApercuZoneBinding.bind(
                binding.stubApercu.inflate()).getRoot();
        // le menu des dimensions de l'aperçu s'ancre à sa puce : le
        // PORTE-POPUPS de l'atelier le porte (registre partagé,
        // lot A7)
        vueApercu.poserPopups(popups);
        vueApercu.setAuditeur(new VueApercu.AuditeurApercu() {
            @Override
            public void surRafraichir() {
                if (session.apercu != null && FichiersProjet.estMiseEnPage(
                        session.fichierCourant)) {
                    session.apercu.rendreSansDelai(
                            session.scene.texte());
                }
            }

            @Override
            public void surPleinEcran() {
                etat.basculerApercuPleinEcran();
                majVolets();
            }

            @Override
            public void surFermer() {
                etat.fermerVoletApercu();
                majVolets();
            }

            @Override
            public void surDensite(int densite) {
                etatApercu.setDensite(densite);
                reconfigurerApercu();
            }

            @Override
            public void surNuit(boolean nuit) {
                etatApercu.setNuit(nuit);
                reconfigurerApercu();
            }

            @Override
            public void surDimensions(int largeurDp, int hauteurDp) {
                etatApercu.setDimensions(largeurDp, hauteurDp);
                reconfigurerApercu();
            }
        });
        // le rendu d'avant-montage n'est pas perdu : le dernier texte
        // de mise en page repart au moteur (l'état a suivi depuis le
        // début, le perchoir seul était absent)
        if (session.apercu != null && session.xmlCourant != null) {
            session.apercu.rendreSansDelai(session.xmlCourant);
        }
    }

    /** Monte le tiroir agent (première ouverture) : le tiroir poussé
     *  droite et la vue agent de son stub. */
    private TiroirPousse monterTiroirAgent() {
        if (tiroirAgent != null || binding.stubTiroirAgent == null) {
            return tiroirAgent;
        }
        tiroirAgent = (TiroirPousse) binding.stubTiroirAgent.inflate();
        vueAgent = TiroirAgentPousseBinding.bind(
                tiroirAgent).vueAgent;
        tiroirAgent.setBordDroit(true);
        vueAgent.poserAuditeur(new VueAgent.Auditeur() {
            @Override
            public void envoyer(String message) {
                controleurAgent.envoyerAgent(message);
            }

            @Override
            public void arreter() {
                controleurAgent.arreterTour();
            }

            @Override
            public void repondrePermission(boolean accordee) {
                controleurAgent.repondrePermissionAgent(accordee);
            }

            @Override
            public void repondreQuestion(int index, int choix) {
                controleurAgent.repondreQuestionAgent(index, choix);
            }

            @Override
            public void poserMode(jo.joide.agent.Permissions.Mode mode) {
                controleurAgent.poserMode(mode);
            }

            @Override
            public void ouvrirMode() {
                // lot A7 : le menu ANCRÉ des modes (la puce d'en-tête
                // ne décide RIEN du contenu — décision 12)
                controleurAgent.ouvrirMenuMode();
            }

            @Override
            public void ouvrirModele() {
                controleurModelesAgent.ouvrir();
            }

            @Override
            public void ouvrirMenuAgent() {
                controleurAgent.ouvrirMenuAgentOptions();
            }

            @Override
            public void ouvrirAjouts() {
                controleurAgent.ouvrirAjoutsAgent();
            }

            @Override
            public void ouvrirUsages() {
                // lot C4 : la bande d'avertissement tapée ouvre l'écran
                // Usages (la manière éprouvée du bandeau de limite)
                controleurAgent.ouvrirUsages();
            }

            @Override
            public void retirerPiece(int index) {
                controleurAgent.retirerPiece(index);
            }
        });
        return tiroirAgent;
    }

    // ------------------------------------------------------------------
    // Les mises à jour (les corps d'EcranAtelier d'hier, inchangés)
    // ------------------------------------------------------------------

    /** La bannière de compatibilité Gradle (mode P7/P8). */
    void poserBanniere(String texte) {
        texteBanniereCompat = texte;
        binding.banniereCompat.setText(texte);
        binding.banniereCompat.setVisibility(View.VISIBLE);
    }

    /** Démarre le chargement de session sur le fil de fond. */
    void demarrerSession(java.nio.file.Path racine,
                         String fichierInitial) {
        session.demarrer(racine, fichierInitial);
    }

    /**
     * L'état vide du puits EST l'état sans onglet actif — UNE seule
     * source de vérité (lot U) : l'ouverture, la fermeture du dernier
     * onglet, le chargement et l'erreur passent tous par ici ; jamais
     * un puits muet derrière un éditeur absent.
     */
    @Override
    public void majPuits() {
        boolean sansOnglet = etat.actif() == null;
        binding.etatVidePuits.setVisibility(
                sansOnglet ? View.VISIBLE : View.GONE);
        binding.vueEditeur.setVisibility(
                sansOnglet ? View.GONE : View.VISIBLE);
    }

    @Override
    public void majVolets() {
        majPuits();
        binding.feuille.poserDeplie(etat.consoleDepliee());
        // §3.7 : le FAB disparaît dès que la feuille dépasse la barre
        // (le clavier le cache aussi, via les insets)
        binding.fab.setVisibility(etat.consoleDepliee()
                ? View.GONE : View.VISIBLE);
        if (binding.boutonRailConsole != null) {
            binding.boutonRailConsole.setCouleur(etat.consoleDepliee()
                    ? palette.accent : palette.texteSecondaire);
        }
        // la section VISIBLE de la feuille se monte à la première
        // OUVERTURE (lot C3 : paresseux — repliée et sans build, elle
        // ne sert pas ; le montage par transaction remplace le stub
        // d'hier, SANS vue déplacée)
        if (etat.consoleDepliee()) {
            monterSectionFeuille(binding.feuille.section());
        }
        boolean plein = etat.apercuPleinEcran();
        boolean miseEnPage = session.fichierCourant != null
                && FichiersProjet.estMiseEnPage(session.fichierCourant);
        boolean voletDroitVisible = etat.grandEcran()
                && etat.voletApercu() && miseEnPage && !plein;
        if ((plein || voletDroitVisible) && vueApercu == null) {
            monterApercu();
        }
        if (vueApercu == null) {
            return;
        }
        if (plein) {
            if (vueApercu.getParent() != binding.voletPleinEcran) {
                detacher(vueApercu);
                binding.voletPleinEcran.addView(vueApercu,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
            }
        } else if (voletDroitVisible && binding.voletDroit != null) {
            if (vueApercu.getParent() != binding.voletDroit) {
                detacher(vueApercu);
                binding.voletDroit.addView(vueApercu,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT));
            }
        }
        binding.voletPleinEcran.setVisibility(
                plein ? View.VISIBLE : View.GONE);
        if (binding.voletDroit != null) {
            binding.voletDroit.setVisibility(
                    voletDroitVisible ? View.VISIBLE : View.GONE);
        }
        vueApercu.maj(etatApercu, session.fichierCourant == null ? null
                : Arborescence.nomAffiche(session.fichierCourant));
    }

    private static void detacher(View vue) {
        if (vue.getParent() instanceof android.view.ViewGroup parent) {
            parent.removeView(vue);
        }
    }

    @Override
    public void majFab() {
        binding.fab.setContentDescription(ecran.getString(
                etat.buildEnCours()
                        ? R.string.atelier_desc_arreter_build
                        : R.string.atelier_desc_construire));
        if (etat.buildEnCours() && etat.build() != null) {
            binding.fab.setEtat(true, ecran.getString(
                    R.string.console_etat_encours,
                    etat.build().phase() == null
                            ? ecran.getString(R.string.console_phase_aucune)
                            : etat.build().phase()));
        } else {
            binding.fab.setEtat(false, null);
        }
        buildEnCours = etat.buildEnCours();
    }

    @Override
    public void majVerdictConsole() {
        // lot L3 (ADR-083) : la zone vivante console — verdict coloré
        // selon l'état, détail = dernier bilan, pipeline de 3 segments
        String libelle = etatConsole.libelleCourt();
        int couleur;
        String verdict;
        switch (etatConsole.etat()) {
            case EN_COURS -> {
                couleur = palette.accent;
                verdict = "● " + libelle;
            }
            case REUSSI -> {
                couleur = palette.reussite;
                verdict = "✓ " + libelle;
            }
            case ECHOUE -> {
                couleur = palette.erreur;
                verdict = "✕ " + libelle;
            }
            case INTERROMPU -> {
                couleur = palette.alerte;
                verdict = "■ " + libelle;
            }
            default -> {
                couleur = palette.texteSecondaire;
                verdict = ecran.getString(R.string.console_etat_vide);
            }
        }
        boolean enCours = etatConsole.etat() == EtatConsole.Etat.EN_COURS;
        boolean resolutionOk = false;
        boolean compilationOk = false;
        boolean compilationErreur = false;
        boolean assemblageAtteint = false;
        boolean assemblageErreur = false;
        for (EtatConsole.PhaseEtat phase : etatConsole.phases()) {
            if (phase.phase() == jo.joide.app.android.ConsoleBuild.Phase.PREPARATION) {
                resolutionOk = phase.commencee() && !enCours;
            } else if (phase.phase()
                    == jo.joide.app.android.ConsoleBuild.Phase.COMPILATION) {
                compilationOk = phase.commencee() && !enCours;
                compilationErreur = etatConsole.etat()
                        == EtatConsole.Etat.ECHOUE;
            } else if (phase.phase()
                    == jo.joide.app.android.ConsoleBuild.Phase.ASSEMBLAGE) {
                assemblageAtteint = phase.commencee();
                assemblageErreur = etatConsole.etat()
                        == EtatConsole.Etat.ECHOUE && assemblageAtteint;
            }
        }
        binding.feuille.poserVerdictConsole(verdict, couleur,
                String.valueOf(legendeDernierBilan()), enCours,
                resolutionOk, compilationOk, compilationErreur,
                assemblageAtteint, assemblageErreur);
    }

    private CharSequence legendeDernierBilan() {
        List<jo.joide.app.android.ConsoleBuild.Ligne> lignes =
                etatConsole.lignes();
        for (int i = lignes.size() - 1; i >= 0; i--) {
            String texte = lignes.get(i).texte();
            if (texte.startsWith("build #") && texte.contains(" — ")) {
                return texte.substring(texte.indexOf(" — ") + 3);
            }
        }
        return null;
    }

    @Override
    public void majConsole() {
        // la console se monte à la première mise à jour (paresseuse :
        // jamais pendant le chargement de session) — EN RÉSERVE si
        // une autre section est visible, la feuille décide seule de
        // ce qui se MONTRÉ
        assurerConsoleMontee();
        if (vueConsole == null) {
            return;
        }
        vueConsole.maj(etatConsole,
                session.racineProjet == null ? null
                        : String.valueOf(
                        session.racineProjet.getFileName()));
        majVerdictConsole();
    }

    @Override
    public void reconfigurerApercu() {
        if (session.apercu == null || session.configApercuBase == null) {
            return;
        }
        session.apercu.configurer(session.configApercuBase
                .avecDensite(etatApercu.densite())
                .avecDimensions(etatApercu.largeurPx(),
                        etatApercu.hauteurPx())
                .avecModeNuit(etatApercu.nuit()));
        if (vueApercu != null) {
            vueApercu.maj(etatApercu,
                    session.fichierCourant == null ? null
                            : Arborescence.nomAffiche(
                            session.fichierCourant));
        }
    }

    /** Pose le rendu LayoutLib tel quel, mis à l'échelle du volet. */
    private void poserRendu(View rendu) {
        if (vueApercu == null) {
            return;
        }
        FrameLayout perchoir = vueApercu.conteneurRendu();
        perchoir.removeAllViews();
        int largeurMesuree = rendu.getMeasuredWidth();
        int hauteurMesuree = rendu.getMeasuredHeight();
        final int largeur = largeurMesuree <= 0
                ? etatApercu.largeurPx() : largeurMesuree;
        final int hauteur = hauteurMesuree <= 0
                ? etatApercu.hauteurPx() : hauteurMesuree;
        perchoir.addView(rendu, new FrameLayout.LayoutParams(largeur,
                hauteur, Gravity.CENTER));
        rendu.post(() -> {
            float echelle = Math.min(1f,
                    Math.min(perchoir.getWidth() / (float) largeur,
                            perchoir.getHeight() / (float) hauteur));
            rendu.setScaleX(echelle);
            rendu.setScaleY(echelle);
            rendu.setPivotX(0f);
            rendu.setPivotY(0f);
            perchoir.requestLayout();
        });
        perchoir.requestLayout();
    }

    /** Compte les MockView de l'arbre rendu (classes inconnues). */
    private static int compterMockViews(View racine) {
        int[] compte = new int[1];
        parcourir(racine, compte, new int[]{0});
        return compte[0];
    }

    private static void parcourir(View vue, int[] compte, int[] garde) {
        if (garde[0]++ > 2000) {
            return;
        }
        if (vue.getClass().getSimpleName().equals("MockView")) {
            compte[0]++;
        }
        if (vue instanceof android.view.ViewGroup groupe) {
            for (int i = 0; i < groupe.getChildCount(); i++) {
                parcourir(groupe.getChildAt(i), compte, garde);
            }
        }
    }

    /** L'écouteur de rendu du moteur d'aperçu (posé au montage). */
    @Override
    public ApercuLayouts.EcouteurRendu ecouteurApercu() {
        return new ApercuLayouts.EcouteurRendu() {
            @Override
            public void onSucces(View rendu, long dureeMs, int vues,
                                 int largeur, int hauteur) {
                poserRendu(rendu);
                etatApercu.surSucces(vues, dureeMs, largeur, hauteur);
                etatApercu.surMockViews(compterMockViews(rendu));
                etatApercu.surRessourcesManquantes(
                        session.apercu.ressourcesManquantes(
                                session.xmlCourant));
                if (vueApercu != null) {
                    vueApercu.maj(etatApercu, Arborescence.nomAffiche(
                            session.fichierCourant));
                }
            }

            @Override
            public void onErreur(String message, Throwable cause) {
                etatApercu.surErreur(message);
                if (vueApercu != null) {
                    vueApercu.maj(etatApercu, Arborescence.nomAffiche(
                            session.fichierCourant));
                }
            }

            @Override
            public void onXmlInvalide(String message) {
                etatApercu.surXmlInvalide();
                if (vueApercu != null) {
                    vueApercu.maj(etatApercu, Arborescence.nomAffiche(
                            session.fichierCourant));
                }
            }
        };
    }

    /** Le vidage demandé de la console (confirmation honnête —
     *  carte centrée sur voile, lot A7 : le valideur part APRÈS la
     *  fermeture). */
    private void confirmerVidage() {
        popups.confirmation(ecran.getString(R.string.console_vider_titre),
                ecran.getString(R.string.console_vider_message),
                ecran.getString(R.string.action_annuler),
                ecran.getString(R.string.console_vider),
                () -> {
                    etatConsole.vider();
                    majConsole();
                });
    }

    // ------------------------------------------------------------------
    // Le menu ⋮ de la barre haute et la fermeture du projet (les
    // dialogues d'hier du contrôle → popups, lot A7, décision 12)
    // ------------------------------------------------------------------

    /** Le menu ⋮ ancré à son bouton (lot M, ADR-É8) : la case
     *  « en processus » du lot F vit désormais dans Paramètres — ici
     *  il reste demander à l'IA, les réglages, fermer le projet. */
    private void ouvrirMenu() {
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                ecran.getString(R.string.agent_demander_ia),
                () -> controleurAgent.demanderIa()));
        entrees.add(new PortePopups.EntreeMenu(
                ecran.getString(R.string.parametres_titre),
                () -> ecran.startActivity(new Intent(ecran,
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class))));
        entrees.add(new PortePopups.EntreeMenu(
                ecran.getString(R.string.atelier_fermer_projet),
                this::demanderFermeture));
        popups.menu(binding.barreHaut.binding.boutonOptions,
                racineProjet() == null ? ""
                        : String.valueOf(racineProjet().getFileName()),
                240, entrees);
    }

    /** La confirmation de fermeture du projet : le compte de fichiers
     *  modifiés de la session — jamais un texte générique qui cache
     *  (carte centrée sur voile, lot A7). */
    void demanderFermeture() {
        popups.confirmation(
                ecran.getString(R.string.atelier_fermer_titre),
                ecran.getString(R.string.atelier_fermer_message,
                        etat.nombreModifies()),
                ecran.getString(R.string.action_annuler),
                ecran.getString(R.string.atelier_fermer_projet),
                () -> ecran.finish());
    }

    // ------------------------------------------------------------------
    // Le retour système et le porte-popups (l'ordre vit dans l'état
    // pur EtatPopups, testé JVM — décision 12)
    // ------------------------------------------------------------------

    /** Ce que le retour système fait : le popup ouvert se ferme EN
     *  PREMIER (avant tiroir agent, feuille, tiroir, aperçu,
     *  confirmation et sortie), puis l'ordre d'ADR-É5. */
    EtatAtelier.ActionRetour surRetourSysteme() {
        return etatPopups.surRetour(popups.ouvert(),
                tiroirAgentOuvert(), etat);
    }

    /** Ferme tout popup ouvert (onPause/onDestroy/onConfigurationChanged
     *  de l'écran — idempotent, jamais restauré : l'état de saisie vit
     *  dans EtatPopups). */
    void fermerPopups() {
        popups.fermer();
    }

    // ------------------------------------------------------------------
    // Changement de configuration de GÉOMÉTRIE (lot A8, ADR-085
    // décision 10 — la session d'IDE n'est JAMAIS fermée)
    // ------------------------------------------------------------------

    /**
     * Le changement de configuration (la géométrie seule aujourd'hui :
     * le manifest de l'atelier ne déclare QU'elle — thème, langue et
     * police recréent toujours). La décision PURE (testée JVM,
     * {@link DecisionConfiguration}) : les popups se ferment
     * TOUJOURS (décision 12), la racine se ré-influe SEULEMENT si la
     * classe de géométrie change (économie documentée : même classe,
     * même layout), l'IDE ne se ferme JAMAIS — la session (scène,
     * LSP, aperçu, écritures) survit, {@link SessionAtelier} n'est pas
     * touché.
     *
     * @param nouvelle la configuration nouvelle (livrée par le cadre)
     */
    void surChangementConfiguration(Configuration nouvelle) {
        // la fermeture AVANT tout : jamais une carte vivante sur une
        // géométrie dépassée (décision 12 — un popup n'est pas
        // restauré, son état de saisie vit dans EtatPopups)
        popups.fermer();
        int largeurDp = nouvelle == null ? 0 : nouvelle.screenWidthDp;
        if (largeurDp <= 0) {
            // configuration muette (cas défensif) : les ressources de
            // l'écran sont déjà à jour au moment de l'appel
            largeurDp = ecran.getResources().getConfiguration()
                    .screenWidthDp;
        }
        boolean grandEcranApres = EtatAtelier.estGrandEcran(largeurDp);
        DecisionConfiguration.Reaction reaction =
                DecisionConfiguration.surGeometrie(etat.grandEcran(),
                        grandEcranApres);
        if (!reaction.reinflerRacine()) {
            // économie : même classe de géométrie, même layout — la
            // racine reste (les popups sont fermés, la seule décision
            // restante est une non-décision)
            return;
        }
        reinflerRacine(grandEcranApres);
    }

    /**
     * Ré-inflamme LA RACINE SEULE (décision 10) : le nouveau layout
     * (compact ↔ {@code layout-w720dp}) suit la configuration courante.
     * Le PUITS (la scène éditeur celeris et sa couche de popups) se
     * DÉTACHE avant et se RÉATTACHE après — jamais reconstruit ; les
     * zones montées (arborescence, console, aperçu, tiroir agent)
     * suivent : instances conservées, ré-amarrées aux conteneurs du
     * nouveau layout ; les vues neuves (barres, onglets, feuille)
     * se re-câblent et se re-posent depuis les états — un montage de
     * session SANS le rechargement.
     *
     * @param grandEcranApres la classe de disposition nouvelle
     */
    private void reinflerRacine(boolean grandEcranApres) {
        // 1) saisir ce qui survit : la scène du puits (vue + couche de
        //    popups — jamais reconstruite), la section de feuille
        List<View> scene = new ArrayList<>(
                binding.vueEditeur.getChildCount());
        for (int i = 0; i < binding.vueEditeur.getChildCount(); i++) {
            scene.add(binding.vueEditeur.getChildAt(i));
        }
        EtatFeuille.Section section = binding.feuille.section();

        // 2) détacher les zones montées de l'ANCIENNE racine (elles se
        //    ré-amorrent à la neuve dans la passe qui suit) — les
        //    fragments de la feuille meurent EXPLICITEMENT : le
        //    conteneur remplacé ne les reçoit plus, les instances
        //    neuves se posent depuis les états (lot C3)
        binding.vueEditeur.removeAllViews();
        detacher(vueArbre);
        detacher(vueApercu);
        detacher(tiroirAgent);
        if (!fragmentsFeuille.isEmpty()) {
            FragmentTransaction suppression =
                    ecran.getSupportFragmentManager().beginTransaction();
            for (FragmentSection fragment : fragmentsFeuille.values()) {
                suppression.remove(fragment);
            }
            suppression.commitNow();
            fragmentsFeuille.clear();
            sectionMontreeFeuille = null;
        }

        // 3) gonfler la racine NEUVE (le layout suit la configuration
        //    déjà commutée) et la poser en place de l'ancienne
        ActivityAtelierBinding neuve = ActivityAtelierBinding.inflate(
                ecran.getLayoutInflater());
        ecran.setContentView(neuve.getRoot());
        binding = neuve;

        // 4) ré-amarrer : le voile du porte-popups (celui du NOUVEAU
        //    layout), la scène au puits neuf, l'arbre à son conteneur
        //    (colonne amarrée en grand écran, cadre du tiroir en
        //    compact), la console dans la feuille neuve, le tiroir
        //    agent MONTÉ à la place du stub neuf (même rang, même
        //    z-plan)
        popups.poserVoile(binding.voilePopup);
        for (View enfant : scene) {
            binding.vueEditeur.addView(enfant,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
        }
        if (vueArbre != null) {
            ViewGroup conteneur = grandEcranApres
                    && binding.colonneArbre != null
                    ? binding.colonneArbre
                    : binding.tiroirFichiers.cadreArbre();
            conteneur.addView(vueArbre,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        if (tiroirAgent != null && binding.stubTiroirAgent != null) {
            ViewGroup racine = (ViewGroup) binding.getRoot();
            int rang = racine.indexOfChild(binding.stubTiroirAgent);
            racine.removeView(binding.stubTiroirAgent);
            racine.addView(tiroirAgent, rang);
        }

        // 5) re-câbler les vues neuves (auditeurs, insets, bannière
        //    calculée — décision 8) et re-pose de l'état
        binding.banniereCompat.setBackgroundColor(Graphite.melange(
                palette.alerte, 0.12f, palette.fond));
        cabler();
        etat.poserGrandEcran(grandEcranApres);
        binding.feuille.poserSection(section);
        if (texteBanniereCompat != null) {
            binding.banniereCompat.setText(texteBanniereCompat);
            binding.banniereCompat.setVisibility(View.VISIBLE);
        }
        reposerEtatSurNouvelleRacine();
    }

    /**
     * Re-pose l'état d'UI sur la racine ré-inflée : les vues sont
     * neuves, la vérité (états purs, session, contrôleurs) est intacte
     * — elle se re-déroule ici, exactement comme un montage de session
     * sans le rechargement.
     */
    private void reposerEtatSurNouvelleRacine() {
        // la barre haute : nom du projet, puce modules (le point
        // d'activité de l'agent seul ne se re-pose pas — il suivra le
        // prochain événement du service, résiduel documenté)
        if (session.racineProjet != null) {
            binding.barreHaut.poserNomProjet(String.valueOf(
                    session.racineProjet.getFileName()));
            int modules = session.modele == null ? 0
                    : session.modele.allModules().size();
            binding.barreHaut.binding.puceModules.setText(
                    ecran.getString(modules == 1
                            ? R.string.meta_module_1
                            : R.string.meta_modules, modules));
        }
        binding.etatVidePuits.setTitre(titreEtatVide);
        binding.etatVidePuits.setAide(aideEtatVide);
        // le tiroir fichiers (compact) : ouvert si l'état le dit
        if (binding.tiroir != null && etat.tiroirOuvert()) {
            binding.tiroir.ouvrir();
        }
        // la couleur du bouton rail arbre (grand écran) se recale sur
        // la visibilité de la colonne amarrée
        if (binding.boutonRailArbre != null) {
            binding.boutonRailArbre.setCouleur(
                    binding.colonneArbre.getVisibility() == View.VISIBLE
                            ? palette.accent : palette.texteSecondaire);
        }
        // les onglets et le fil d'Ariane, la pilule d'index, l'arbre,
        // les volets et le FAB, la console et la feuille (sections et
        // verdicts) — tout se re-déroule des états
        controleurEdition.majOnglets();
        controleurFichiers.majPiluleIndex();
        controleurFichiers.majArbre();
        majVolets();
        majFab();
        majConsole();
        controleurEdition.reposerFeuille();
    }

    // ------------------------------------------------------------------
    // État d'UI minimal : sauvegarde / restauration (lot A8, ADR-085
    // décision 10 — les recréations RESTANTES : thème, langue, taille
    // de police ; la géométrie, elle, ne recrée plus l'atelier)
    // ------------------------------------------------------------------

    /**
     * Sauvegarde l'état d'UI minimal dans le Bundle (la coque
     * appelle en {@code onSaveInstanceState}) : l'export de
     * l'{@link EtatAtelier} — onglets ouverts, actif, points de
     * modification, surcouches, section de feuille — et les
     * BROUILLONS de saisie des popups ({@link EtatPopups} — ils
     * survivent au même passage : la saisie vit dans l'état, jamais
     * dans le popup). Le build ne s'exporte pas (la pilule suit les
     * événements du service, documenté au rapport).
     *
     * @param sortie le Bundle de sauvegarde
     */
    void sauverEtat(Bundle sortie) {
        EtatAtelier.Export export = etat.exporter(
                binding.feuille.section());
        sortie.putStringArrayList(CLE_ONGLETS,
                new ArrayList<>(export.onglets()));
        sortie.putStringArrayList(CLE_MODIFIES,
                new ArrayList<>(export.modifies()));
        if (export.actif() != null) {
            sortie.putString(CLE_ACTIF, export.actif());
        }
        sortie.putBoolean(CLE_TIROIR, export.tiroirOuvert());
        sortie.putBoolean(CLE_CONSOLE, export.consoleDepliee());
        sortie.putBoolean(CLE_APERCU_PLEIN, export.apercuPleinEcran());
        sortie.putBoolean(CLE_VOLET, export.voletApercu());
        sortie.putBoolean(CLE_PROJET, export.projetMonte());
        sortie.putInt(CLE_SECTION, export.sectionFeuille().ordinal());
        Map<String, String> brouillons = etatPopups.exporterBrouillons();
        sortie.putStringArrayList(CLE_BROUILLONS_CLES,
                new ArrayList<>(brouillons.keySet()));
        sortie.putStringArrayList(CLE_BROUILLONS_VALEURS,
                new ArrayList<>(brouillons.values()));
    }

    /**
     * Restaure l'état d'UI minimal d'un Bundle (la coque appelle au
     * {@code onCreate}, AVANT le démarrage de session).
     *
     * @param sauvegarde le Bundle restauré (jamais nul)
     * @return l'onglet ACTIF restauré (il devient le fichier initial
     *         de la session : il se re-montre à la fin du chargement
     *         — le coût est le rebranchement, pas une reconstruction,
     *         ADR-É5), ou null si aucun
     */
    String restaurer(Bundle sauvegarde) {
        if (!sauvegarde.containsKey(CLE_ONGLETS)) {
            // première ouverture (ou Bundle d'un autre âge) : rien à
            // restaurer, l'état neuf reste
            return null;
        }
        // garde d'audit A8 : un ordinal hors bornes (Bundle écrit par
        // une AUTRE version, section réordonnée) se normalise en
        // CONSOLE — l'état dégradé vaut mieux que l'écran mort
        EtatFeuille.Section[] sections = EtatFeuille.Section.values();
        int ordinal = sauvegarde.getInt(CLE_SECTION);
        EtatFeuille.Section section = ordinal >= 0
                && ordinal < sections.length
                ? sections[ordinal] : EtatFeuille.Section.CONSOLE;
        EtatAtelier.Export export = new EtatAtelier.Export(
                sauvegarde.getStringArrayList(CLE_ONGLETS),
                sauvegarde.getString(CLE_ACTIF),
                sauvegarde.getStringArrayList(CLE_MODIFIES),
                sauvegarde.getBoolean(CLE_TIROIR),
                sauvegarde.getBoolean(CLE_CONSOLE),
                sauvegarde.getBoolean(CLE_APERCU_PLEIN),
                sauvegarde.getBoolean(CLE_VOLET),
                sauvegarde.getBoolean(CLE_PROJET), section);
        etat.importer(export);
        binding.feuille.poserSection(section);
        // garde d'audit A8 : poserSection DÉPLIE à mi-hauteur une
        // feuille repliée (le patron « le choix déplie », A6) — la
        // détente exportée se re-pose aussitôt : la feuille restaurée
        // est EXACTEMENT celle quittée (repliée reste repliée)
        binding.feuille.poserDeplie(export.consoleDepliee());
        // les brouillons des popups (deux listes parallèles)
        List<String> cles = sauvegarde
                .getStringArrayList(CLE_BROUILLONS_CLES);
        List<String> valeurs = sauvegarde
                .getStringArrayList(CLE_BROUILLONS_VALEURS);
        Map<String, String> brouillons = new LinkedHashMap<>();
        if (cles != null && valeurs != null) {
            for (int i = 0; i < cles.size() && i < valeurs.size(); i++) {
                brouillons.put(cles.get(i), valeurs.get(i));
            }
        }
        etatPopups.importerBrouillons(brouillons);
        // le tiroir fichiers (compact) se rouvre si l'état le dit
        if (binding.tiroir != null && etat.tiroirOuvert()) {
            binding.tiroir.ouvrir();
        }
        return export.actif();
    }

    /** Le contrôleur agent (le retour système et le menu y passent). */
    ControleurAgent controleurAgent() {
        return controleurAgent;
    }

    /** Le tiroir fichiers (le retour système le ferme, écran compact ;
     *  null en grand écran : l'arbre y est amarré). */
    TiroirPousse tiroir() {
        return binding.tiroir;
    }

    /** Ferme tout : popups, service agent, IDE, exécuteurs
     *  (onDestroy — les popups ne fuient jamais au détachement). */
    void fermer() {
        popups.fermer();
        controleurAgent.debrancherService();
        session.fermerIde();
        fond.shutdown();
        ecrivain.shutdown();
    }

    // ------------------------------------------------------------------
    // HoteAtelier — les services d'écran délégués à la coque
    // ------------------------------------------------------------------

    @Override
    public android.content.Context contexte() {
        return ecran;
    }

    @Override
    public void executerSurFilUi(Runnable action) {
        // lot A8 (I8, revue exigée) : TOUS les callbacks asynchrones de
        // l'atelier — exécuteur de fond, relais logcat, démon de build,
        // service de l'agent, session — passent par CETTE voie : la
        // garde de cycle de vie se pose UNE fois ici, avant le post
        // ET au moment d'exécuter (l'écran détruit n'exécute plus rien
        // : jamais une vue morte touchée, jamais un popup montré sur
        // une fenêtre disparue)
        if (ecran.isFinishing() || ecran.isDestroyed()) {
            return;
        }
        ecran.runOnUiThread(() -> {
            if (!ecran.isFinishing() && !ecran.isDestroyed()) {
                action.run();
            }
        });
    }

    @Override
    public String texte(int id) {
        return ecran.getString(id);
    }

    @Override
    public String texte(int id, Object... args) {
        return ecran.getString(id, args);
    }

    @Override
    public java.io.File dossierFichiers() {
        return ecran.getFilesDir();
    }

    @Override
    public android.content.ContentResolver resolveurContenu() {
        return ecran.getContentResolver();
    }

    @Override
    public boolean possedeCamera() {
        return ecran.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY);
    }

    @Override
    public void ouvrirEcran(Intent intention) {
        ecran.startActivity(intention);
    }

    @Override
    public androidx.activity.result.ActivityResultLauncher<String>
    choisirImageAgent() {
        // le CONTRAT TYPÉ d'Ecrans.CHOISIR_IMAGE vit dans l'écran
        // (registerForActivityResult doit précéder onCreate) — l'hôte
        // le délègue au contrôleur (décision 13 : le code 47101
        // disparaît)
        return ecran.contratChoisirImage();
    }

    @Override
    public androidx.activity.result.ActivityResultLauncher<android.net.Uri>
    prendrePhotoAgent() {
        // le CONTRAT TYPÉ d'Ecrans.PRENDRE_PHOTO — idem (le code 47102
        // disparaît)
        return ecran.contratPrendrePhoto();
    }

    @Override
    public void demarrerServiceAvantPlan(Intent intention) {
        ecran.startForegroundService(intention);
    }

    @Override
    public void lierService(Intent intention,
                           android.content.ServiceConnection connexion,
                           int drapeaux) {
        ecran.bindService(intention, connexion, drapeaux);
    }

    @Override
    public void delierService(
            android.content.ServiceConnection connexion) {
        ecran.unbindService(connexion);
    }

    // ------------------------------------------------------------------
    // HoteAtelier — les états
    // ------------------------------------------------------------------

    @Override
    public EtatAtelier etat() {
        return etat;
    }

    @Override
    public EtatConsole etatConsole() {
        return etatConsole;
    }

    @Override
    public EtatApercu etatApercu() {
        return etatApercu;
    }

    @Override
    public EtatProblemes etatProblemes() {
        return etatProblemes;
    }

    @Override
    public EtatJournal etatJournal() {
        return etatJournal;
    }

    @Override
    public EtatLogcat etatLogcat() {
        return etatLogcat;
    }

    @Override
    public EtatAgentChat etatAgent() {
        return etatAgent;
    }

    @Override
    public void poserEtatAgent(EtatAgentChat etat) {
        etatAgent = etat;
    }

    @Override
    public EtatPopups etatPopups() {
        return etatPopups;
    }

    @Override
    public PortePopups popups() {
        return popups;
    }

    @Override
    public Graphite.Palette palette() {
        return palette;
    }

    @Override
    public Reglages.Source sourceReglages() {
        return sourceReglages;
    }

    @Override
    public ExecutorService fond() {
        return fond;
    }

    // ------------------------------------------------------------------
    // HoteAtelier — la session
    // ------------------------------------------------------------------

    @Override
    public java.nio.file.Path racineProjet() {
        return session.racineProjet;
    }

    @Override
    public jo.joide.model.Workspace modele() {
        return session.modele;
    }

    @Override
    public jo.joide.ide.langages.MoteursLangage moteurs() {
        return session.moteurs;
    }

    @Override
    public jo.joide.ide.sync.LanguageModelSync sync() {
        return session.sync;
    }

    @Override
    public SceneEditeur scene() {
        return session.scene;
    }

    @Override
    public ApercuLayouts apercu() {
        return session.apercu;
    }

    @Override
    public jo.joide.ide.apercu.ConfigurationApercu configApercuBase() {
        return session.configApercuBase;
    }

    @Override
    public EcrituresFusionnees ecritures() {
        return session.ecritures;
    }

    @Override
    public String fichierCourant() {
        return session.fichierCourant;
    }

    @Override
    public void poserFichierCourant(String fichier) {
        session.fichierCourant = fichier;
    }

    @Override
    public String xmlCourant() {
        return session.xmlCourant;
    }

    @Override
    public void poserXmlCourant(String xml) {
        session.xmlCourant = xml;
    }

    @Override
    public List<String> fichiersArbre() {
        return session.fichiersArbre;
    }

    @Override
    public void poserFichiersArbre(List<String> fichiers) {
        session.fichiersArbre = fichiers;
    }

    @Override
    public boolean sortiesPresentes() {
        return session.sortiesPresentes;
    }

    @Override
    public void poserSortiesPresentes(boolean presentes) {
        session.sortiesPresentes = presentes;
    }

    @Override
    public boolean buildEnCours() {
        return buildEnCours;
    }

    @Override
    public void poserBuildEnCours(boolean enCours) {
        buildEnCours = enCours;
    }

    @Override
    public Set<String> dossiersOuverts() {
        return dossiersOuverts;
    }

    @Override
    public String filtreTiroir() {
        return filtreTiroir;
    }

    @Override
    public void poserFiltreTiroir(String filtre) {
        filtreTiroir = filtre;
    }

    @Override
    public List<PieceJointe> piecesAgent() {
        return piecesAgent;
    }

    @Override
    public List<PieceJointe> piecesEnAttenteAgent() {
        return piecesEnAttenteAgent;
    }

    @Override
    public String messageEnAttenteAgent() {
        return messageEnAttenteAgent;
    }

    @Override
    public void poserMessageEnAttenteAgent(String message) {
        messageEnAttenteAgent = message;
    }

    @Override
    public AgentService.AgentBinder agent() {
        return agent;
    }

    @Override
    public void poserAgent(AgentService.AgentBinder binder) {
        agent = binder;
    }

    // ------------------------------------------------------------------
    // HoteAtelier — les zones
    // ------------------------------------------------------------------

    @Override
    public VueArbre vueArbre() {
        return vueArbre;
    }

    @Override
    public VueAgent vueAgent() {
        return vueAgent;
    }

    @Override
    public FeuilleBasse feuille() {
        return binding.feuille;
    }

    @Override
    public BarreOnglets barreOnglets() {
        return binding.barreOnglets;
    }

    @Override
    public FilAriane filAriane() {
        return binding.filAriane;
    }

    @Override
    public View boutonApercuOnglets() {
        return binding.barreOnglets.boutonApercu();
    }

    @Override
    public TiroirPousse tiroirAgent() {
        if (tiroirAgent == null) {
            return monterTiroirAgent();
        }
        return tiroirAgent;
    }

    @Override
    public boolean tiroirAgentOuvert() {
        return tiroirAgent != null && tiroirAgent.ouvert();
    }

    @Override
    public TiroirFichiers tiroirFichiers() {
        return binding.tiroirFichiers;
    }

    @Override
    public Pilule piluleIndex() {
        return binding.barreHaut.binding.piluleIndex;
    }

    @Override
    public Puce puceModules() {
        return binding.barreHaut.binding.puceModules;
    }

    @Override
    public View pointAgent() {
        return binding.barreHaut.binding.pointAgent;
    }

    @Override
    public ControleurEdition controleurEdition() {
        return controleurEdition;
    }

    // ------------------------------------------------------------------
    // HoteAtelier — les mises à jour et le montage différé
    // (majVolets/majConsole/majFab/majVerdictConsole/majPuits/
    //  reconfigurerApercu/ecouteurApercu vivent plus haut, ce sont
    // leurs corps ; les suivants délèguent ou portent leur logique)
    // ------------------------------------------------------------------

    @Override
    public void majArbre() {
        controleurFichiers.majArbre();
    }

    @Override
    public void majPiluleIndex() {
        controleurFichiers.majPiluleIndex();
    }

    @Override
    public void rechargerArbre() {
        controleurFichiers.rechargerArbre();
    }

    @Override
    public void ecrireEnAttente() {
        session.ecrireEnAttente();
    }

    @Override
    public void montrerErreur(String message) {
        titreEtatVide = R.string.projet_illisible_sans_detail;
        aideEtatVide = message;
        binding.etatVidePuits.setTitre(
                R.string.projet_illisible_sans_detail);
        binding.etatVidePuits.setAide(message);
    }

    @Override
    public void poserEtatVide(int titre, int aide) {
        titreEtatVide = titre;
        aideEtatVide = ecran.getString(aide);
        binding.etatVidePuits.setTitre(titre);
        binding.etatVidePuits.setAide(aide);
    }

    @Override
    public void poserNomProjet(String nom) {
        binding.barreHaut.poserNomProjet(nom);
    }

    @Override
    public void poserSceneEditeur(View scene, View couchePopups) {
        binding.vueEditeur.addView(scene, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        binding.vueEditeur.addView(couchePopups,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void viderEditeur() {
        binding.vueEditeur.removeAllViews();
    }
}
