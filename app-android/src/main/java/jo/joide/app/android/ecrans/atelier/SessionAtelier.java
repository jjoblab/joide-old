package jo.joide.app.android.ecrans.atelier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EcrituresFusionnees;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.ui.Graphite;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.HttpTransport;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.ide.apercu.ApercuLayouts;
import jo.joide.ide.apercu.ConfigurationApercu;
import jo.joide.ide.apercu.ConfigurationsApercu;
import jo.joide.ide.editeur.SceneEditeur;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.ide.sync.AndroidPlatform;
import jo.joide.ide.sync.LanguageModelSnapshot;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.ide.sync.ModuleDirectories;
import jo.joide.ide.sync.ProjetLspAdapter;
import jo.joide.ide.sync.Resolutions;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoad;
import jo.joide.model.WorkspaceLoader;

/**
 * Lot A6 (ADR-085 décisions 2 et 3) — LE MONTAGE DE SESSION DE
 * L'ATELIER : {@code charger}, {@code monterIde}, {@code monterVues}
 * et {@code fermerIde} (~250 lignes d'hier, extraits d'{@code
 * EcranAtelier} sans changement de comportement, mêmes commentaires).
 * La session — moteurs de langage, sync, scène éditeur, aperçu, le
 * modèle et les fichiers — vit ICI, plus dans les champs de l'écran :
 * la coque reste mince, la classe dédiée se teste et se relit seule.
 *
 * <p>La scène éditeur (celeris) vit dans le {@code puits} du layout
 * racine ({@code vue_editeur}) : elle s'y ATTACHE au montage et s'en
 * détache à la fermeture — le conteneur, lui, n'est JAMAIS
 * reconstruit (la ré-inflation de la racine au lot A8 le
 * réattachera). Le puits reçoit ses dimensions du LAYOUT : plus de
 * cadre posé en code.</p>
 */
final class SessionAtelier {

    private final EcranAtelier ecran;
    private final HoteAtelier hote;

    /** IDE monté (null tant qu'aucun projet natif n'est chargé). */
    MoteursLangage moteurs;
    LanguageModelSync sync;
    SceneEditeur scene;
    ApercuLayouts apercu;
    ConfigurationApercu configApercuBase;
    Workspace modele;
    Path racineProjet;
    String fichierCourant;
    String fichierInitial;
    String xmlCourant;
    List<String> fichiersArbre = List.of();
    boolean sortiesPresentes;
    final EcrituresFusionnees ecritures = new EcrituresFusionnees();

    SessionAtelier(EcranAtelier ecran, HoteAtelier hote) {
        this.ecran = ecran;
        this.hote = hote;
    }

    /** Démarre le chargement du projet sur le fil de fond. */
    void demarrer(Path racine, String fichierInitial) {
        this.fichierInitial = fichierInitial;
        hote.fond().execute(() -> charger(racine));
    }

    private void charger(Path racine) {
        WorkspaceLoad charge = WorkspaceLoader.load(racine);
        if (!charge.isUsable()) {
            String detail = charge.errors().isEmpty()
                    ? "?" : charge.errors().get(0).message();
            hote.executerSurFilUi(() -> hote.montrerErreur(
                    hote.texte(R.string.projet_illisible, detail)));
            return;
        }
        monterIde(racine, charge);
    }

    private void monterIde(Path racine, WorkspaceLoad charge) {
        Workspace workspace = charge.workspace().orElse(null);
        if (workspace == null) {
            hote.executerSurFilUi(() -> hote.montrerErreur(
                    hote.texte(R.string.projet_illisible_sans_detail)));
            return;
        }
        DependencyCache cache;
        try {
            cache = new DependencyCache(
                    Paths.get(hote.dossierFichiers().getAbsolutePath(),
                            "m2"));
        } catch (IOException e) {
            hote.executerSurFilUi(() -> hote.montrerErreur(
                    hote.texte(R.string.import_echoue,
                            String.valueOf(e.getMessage()))));
            return;
        }
        ResolverConfig config = new ResolverConfig.Builder()
                .repository(new Repository.Remote("central",
                        "https://repo.maven.apache.org/maven2",
                        new HttpTransport()))
                // fix du 2026-11-17 : Google Maven manquait — toute
                // dépendance AndroidX (com.android.*, androidx.*)
                // restait « non résolue, absente du classpath de
                // build » et la compilation échouait ; CodeAssist
                // sert central + google par défaut
                .repository(new Repository.Remote("google",
                        "https://dl.google.com/android/maven2",
                        new HttpTransport()))
                .cache(cache)
                .build();
        LanguageModelSync syncLocal = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                Resolutions.byResolver(config));
        MoteursLangage moteursLocal = new MoteursLangage(syncLocal,
                Paths.get(hote.dossierFichiers().getAbsolutePath(),
                        "cidx"));
        LanguageModelSnapshot instantane =
                moteursLocal.synchroniser(workspace);
        hote.executerSurFilUi(() -> monterVues(racine, workspace,
                syncLocal, moteursLocal, instantane));
    }

    private void monterVues(Path racine, Workspace workspace,
                            LanguageModelSync syncLocal,
                            MoteursLangage moteursLocal,
                            LanguageModelSnapshot instantane) {
        if (ecran.isFinishing() || ecran.isDestroyed()) {
            moteursLocal.close();
            return;
        }
        fermerIde();
        racineProjet = racine;
        modele = workspace;
        sync = syncLocal;
        moteurs = moteursLocal;
        hote.etat().setProjetMonte(true);
        hote.controleurEdition().journaliser(
                EtatJournal.Source.INDEX,
                EtatJournal.Niveau.INFO,
                workspace.allModules().size() + " modules · "
                        + fichiersArbre.size() + " fichiers");
        hote.puceModules().setText(
                hote.texte(workspace.allModules().size() == 1
                        ? R.string.meta_module_1 : R.string.meta_modules,
                        workspace.allModules().size()));
        hote.poserNomProjet(String.valueOf(racine.getFileName()));

        apercu = new ApercuLayouts(ecran);
        configApercuBase = ConfigurationsApercu.depuisSnapshot(instantane);
        hote.etatApercu().surAvertissementsConfig(
                configApercuBase.avertissements());
        hote.reconfigurerApercu();
        apercu.poserEcouteur(hote.ecouteurApercu());

        scene = new SceneEditeur(ecran, moteurs);
        // lot L3 (ADR-083) : les diagnostics BRUTS alimentent la
        // section Problèmes (la source LSP de la feuille)
        scene.poserAuditeurDiagnosticsEcran(
                hote.controleurEdition()::surDiagnosticsLangage);
        // lot M (ADR-É8) : les réglages d'éditeur s'appliquent à
        // l'OUVERTURE suivante — la session se monte avec eux
        Reglages reglagesEditeur =
                Reglages.lire(
                        hote.sourceReglages());
        scene.taillePolice((float) reglagesEditeur.tailleCode);
        scene.numerosLignes(reglagesEditeur.numerosLignes);
        scene.retourLigne(reglagesEditeur.retourLigne);
        scene.theme(Graphite.estSombre(hote.palette()));
        scene.poserAuditeurEdition(texte -> {
            if (fichierCourant == null) {
                return;
            }
            ecritures.poser(fichierCourant, texte);
            hote.etat().marquerModifie(fichierCourant);
            hote.controleurEdition().majOnglets();
            if (FichiersProjet.estMiseEnPage(fichierCourant)) {
                xmlCourant = texte;
                apercu.rendre(texte);
            }
        });
        // la scène vit dans le PUITS du layout racine (le conteneur
        // déclaré, jamais reconstruit) : la vue de l'éditeur et sa
        // couche de popups s'y attachent au montage
        hote.poserSceneEditeur(scene.vue(), scene.couchePopups());
        hote.poserEtatVide(R.string.atelier_etat_vide_titre,
                R.string.atelier_etat_vide_aide);
        hote.majPiluleIndex();
        hote.rechargerArbre();
        if (fichierInitial != null && !fichierInitial.isEmpty()) {
            // lot I : l'atelier s'ouvre sur le fichier d'entrée du
            // modèle, jamais un atelier vide (ADR-É4)
            hote.controleurEdition().ouvrirFichier(fichierInitial, null);
            fichierInitial = null;
        }
    }

    /** Écrit les modifications en attente sur le disque (le dernier
     *  gagne, lot F) — les échecs se disent. */
    void ecrireEnAttente() {
        java.util.Map<String, String> aEcrire = ecritures.drainer();
        for (java.util.Map.Entry<String, String> entree
                : aEcrire.entrySet()) {
            try {
                Files.write(racineProjet.resolve(entree.getKey()),
                        entree.getValue().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                hote.executerSurFilUi(() -> hote.popups().information(
                        hote.texte(R.string.import_echoue_titre),
                        hote.texte(R.string.ecriture_echouee),
                        hote.texte(R.string.action_ok)));
                return;
            }
        }
    }

    /** Ferme l'IDE monté (scène, aperçu, moteurs) et détache la scène
     *  du puits — le conteneur lui reste pour le montage suivant. */
    void fermerIde() {
        if (scene != null) {
            scene.close();
            scene = null;
        }
        if (apercu != null) {
            apercu.close();
            apercu = null;
        }
        if (moteurs != null) {
            moteurs.close();
            moteurs = null;
        }
        sync = null;
        modele = null;
        fichierCourant = null;
        hote.viderEditeur();
    }
}
