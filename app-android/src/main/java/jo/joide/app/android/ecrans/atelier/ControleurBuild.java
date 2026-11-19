package jo.joide.app.android.ecrans.atelier;

import android.content.Intent;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jo.joide.app.android.BuildService;
import jo.joide.app.android.SpecsBuild;
import jo.joide.app.android.StoreAndroid;
import jo.joide.app.android.console.EtatConsole;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.build.daemon.client.DaemonClient;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.ide.sync.ExternalResolution;

/**
 * L6 — le contrôleur du build : la chaîne construire → constitution
 * → service ou en processus → événements → fin. Le moteur de vérité
 * reste EtatConsole/EtatAtelier (portés par l'hôte) ; le contrôleur
 * orchestre et met à jour les volets via l'hôte. Les corps viennent
 * de l'écran d'hier sans changement de comportement (extraction
 * mécanique, mêmes commentaires).
 *
 * <p>Lot A6 (ADR-085 décision 3) : le contrôleur dépend de
 * {@link HoteAtelier}, plus des champs de l'écran — le compteur de
 * builds et le client du démon sont à LUI (personne d'autre ne les
 * touche), les constantes d'attente du port suivent.</p>
 */
final class ControleurBuild {

    /**
     * L'attente de la publication du port (lot U/7 : 30 s — le
     * démarrage À FROID du processus :build paie le fork du zygote
     * et le chargement ART de tout le moteur de build ; sur un
     * appareil occupé, 10 s sortaient avant la publication et
     * chaque premier build répétait « injoignable », rapport du
     * 2026-11-17).
     */
    static final long ATTENTE_PORT_MS = 30_000L;

    /**
     * Le pas de re-demande du service pendant l'attente (lot U/7) :
     * un service tué entre-temps repart avec cette demande — jamais
     * une attente muette jusqu'au délai complet.
     */
    static final long REDEMANDE_SERVICE_MS = 5_000L;

    /** Le compteur de builds de la session (la pilule porte le numéro). */
    private final AtomicLong numerosBuild = new AtomicLong();

    /** Le client du démon de build (null hors connexion). */
    private final AtomicReference<DaemonClient> clientDemon =
            new AtomicReference<>();

    private final HoteAtelier hote;

    ControleurBuild(HoteAtelier hote) {
        this.hote = hote;
    }

    void construire() {
        if (hote.racineProjet() == null || hote.modele() == null
                || hote.buildEnCours()) {
            return;
        }
        long numero = numerosBuild.incrementAndGet();
        hote.etat().commencerBuild(numero);
        hote.etatConsole().surDemarrage(numero,
                System.currentTimeMillis());
        hote.etat().deplierConsole();
        hote.majVolets();
        hote.majConsole();
        hote.majFab();
        hote.majArbre();
        // lot U : la constitution peut EXTRAIRE des AAR au disque et
        // LOCALISER l'outillage — elle vit sur le fil de fond, ses
        // écarts remontent à la console dès qu'ils sont connus
        ExternalResolution externals = hote.sync().derniereResolution()
                .orElse(ExternalResolution.empty());
        hote.fond().execute(() -> {
            // lot U/8 : le keystore de débogage se prépare AVANT la
            // constitution — l'asset de l'APK se copie UNE fois vers
            // filesDir/toolchain, la variante debug signée suit
            // (avant : APK produit non signé, ininstallable)
            java.nio.file.Path keystoreDebug =
                    jo.joide.app.android.PreparationChaine
                            .assurerKeystoreDebug(hote.contexte());
            SpecsBuild.Constitution constitution = SpecsBuild
                    .constituer(hote.modele(), externals,
                            hote.racineProjet(),
                            hote.racineProjet().resolve("build-joide"),
                            Paths.get(hote.dossierFichiers()
                                    .getAbsolutePath(), "m2"),
                            keystoreDebug);
            hote.executerSurFilUi(() -> {
                hote.etatConsole().surEcarts(constitution.ecarts);
                if (!constitution.constructible()) {
                    hote.etatConsole().surRienAConstruire();
                    hote.etat().interrompreBuild();
                    hote.majConsole();
                    finirBuild();
                    return;
                }
                // lot M : le moteur par défaut vit dans Paramètres
                boolean enProcessus = Reglages.lire(
                        hote.sourceReglages()).moteur
                        == Reglages.Moteur.PROCESSUS;
                if (enProcessus) {
                    hote.etatConsole().surAnnonceEnProcessus();
                }
                hote.majConsole();
                if (enProcessus) {
                    hote.fond().execute(() -> construireEnProcessus(
                            constitution.spec, numero));
                } else {
                    hote.fond().execute(() -> construireParService(
                            constitution.spec, numero));
                }
            });
        });
    }

    private void construireParService(BuildSpec spec, long numero) {
        // lot U/7 : le canal d'erreur part AVANT de demander le service
        // — un échec d'hier ne doit jamais censurer un essai neuf ;
        // lot VIII : la preuve de VIE part aussi — une vie d'hier ne
        // doit pas faire croire que le processus démarre
        BuildService.effacerErreur(hote.dossierFichiers());
        BuildService.effacerVie(hote.dossierFichiers());
        hote.demarrerServiceAvantPlan(new Intent(hote.contexte(),
                BuildService.class));
        Socket socket = null;
        DaemonClient client = null;
        long limite = System.currentTimeMillis() + ATTENTE_PORT_MS;
        long prochaineRedemande =
                System.currentTimeMillis() + REDEMANDE_SERVICE_MS;
        // un port lu n'est pas un port servi : le fichier peut être
        // PÉRIMÉ (un service mort sans onDestroy l'a laissé — force
        // stop, crash du processus :build) — chaque tentative refusée
        // efface le fichier et la boucle attend la republication du
        // service reparti, jamais une boucle d'échec sur un vieux
        // port (« service de build injoignable » rapporté le
        // 2026-11-17)
        String causeDemon = null;
        // lot VIII (ADR-070) : la preuve de vie du processus :build et
        // le compte des connexions REFUSÉES distingueront « jamais
        // lancé », « lancé mais pas prêt » et « mort juste après
        // publication » dans le détail final
        boolean vieVue = false;
        int refus = 0;
        while (System.currentTimeMillis() < limite) {
            // lot U/7 : le démon a-t-il écrit SA cause d'échec ? la
            // boucle sort tôt avec la cause RÉELLE au lieu de courir
            // jusqu'au délai (« démon de build : ClassNotFoundException
            // ... » vaut mieux que « port non publié »)
            String cause = BuildService.lireErreur(
                    hote.dossierFichiers());
            if (cause != null) {
                causeDemon = cause;
                break;
            }
            if (!vieVue && BuildService.lireVie(
                    hote.dossierFichiers()) != null) {
                vieVue = true;
            }
            int port = BuildService.lirePort(hote.dossierFichiers());
            if (port >= 0) {
                try {
                    socket = new Socket("127.0.0.1", port);
                    socket.setTcpNoDelay(true);
                    client = DaemonClient.over(
                            socket.getInputStream(),
                            socket.getOutputStream());
                    break;
                } catch (IOException refuse) {
                    refus++;
                    BuildService.effacerPort(hote.dossierFichiers());
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException ignore) {
                            // le socket est déjà mort
                        }
                        socket = null;
                    }
                }
            }
            // lot U/7 : sans nouvelle du service, on le REDEMANDE —
            // un service tué entre-temps (pression système, crash
            // avant publication) repart avec cette demande, jamais
            // une attente muette jusqu'au délai
            if (System.currentTimeMillis() >= prochaineRedemande) {
                hote.demarrerServiceAvantPlan(new Intent(
                        hote.contexte(), BuildService.class));
                prochaineRedemande =
                        System.currentTimeMillis()
                                + REDEMANDE_SERVICE_MS;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                hote.executerSurFilUi(this::finirBuild);
                return;
            }
        }
        if (client == null) {
            // lot U/6 : le budget est écoulé SANS connexion — le build
            // ne meurt plus : il continue EN PROCESSUS, annoncé dans la
            // console (jamais un échec muet — rapport « injoignable »
            // du 2026-11-17 ; le mode en processus est le même moteur,
            // le service n'apporte que l'isolation du tas). La cause
            // RÉELLE du démon (lot U/7) sort en premier s'il l'a
            // écrite : « injoignable » seul n'apprend rien.
            // lot VIII (ADR-070) : le détail DISTINGUE les silences du
            // service — la cause écrite par le démon d'abord, puis
            // « jamais lancé » (aucune preuve de vie : restriction
            // ROM/batterie) contre « lancé mais pas prêt », et le port
            // publié mais refusé (démon mort juste après publication).
            // « port non publié » seul n'apprenait rien (rapport du
            // 2026-11-17).
            String detail = causeDemon;
            if (detail == null) {
                if (!vieVue) {
                    detail = "port non publié en "
                            + (ATTENTE_PORT_MS / 1000) + " s et le"
                            + " processus :build n'a JAMAIS démarré"
                            + " (restriction ROM ou batterie ?)";
                } else if (refus > 0) {
                    detail = "port publié mais connexion refusée "
                            + refus + " fois — rien n'écoute sur"
                            + " 127.0.0.1 (démon :build arrêté ou"
                            + " injoignable)";
                } else {
                    detail = "port non publié en "
                            + (ATTENTE_PORT_MS / 1000) + " s"
                            + " (processus :build démarré, démon pas"
                            + " prêt)";
                }
            }
            final String detailFinal = detail;
            hote.executerSurFilUi(() -> {
                hote.etatConsole().surInjoignable(detailFinal);
                hote.etatConsole().surRepliEnProcessus();
                hote.majConsole();
            });
            construireEnProcessus(spec, numero);
            return;
        }
        try (DaemonClient branche = client) {
            clientDemon.set(branche);
            DaemonClient.Event salutation =
                    branche.await("hello", 30);
            evenement(salutation.type, salutation.id,
                    salutation.body);
            construireAvecClient(branche, spec, numero);
        } catch (DaemonClient.DaemonDiedException e) {
            // §6.7 : la mort du démon est un événement, l'UI survit —
            // le build suivant relance le service
            hote.executerSurFilUi(() -> {
                hote.etatConsole().surInterrompu();
                hote.etat().interrompreBuild();
                hote.majConsole();
                hote.majFab();
            });
        } catch (IOException e) {
            hote.executerSurFilUi(() -> {
                hote.etatConsole().surInjoignable(
                        String.valueOf(e.getMessage()));
                hote.etat().interrompreBuild();
                hote.majConsole();
                hote.majFab();
            });
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                    // le socket est déjà mort
                }
            }
            clientDemon.set(null);
            hote.executerSurFilUi(this::finirBuild);
        }
    }

    private void construireAvecClient(DaemonClient client, BuildSpec spec,
                                      long numero) throws IOException {
        client.build(numero, spec);
        while (true) {
            DaemonClient.Event event = client.next(900);
            evenement(event.type, event.id, event.body);
            if ("build-finished".equals(event.type)) {
                return;
            }
        }
    }

    private void evenement(String type, long id,
                           jo.joide.deps.json.JsonValue corps) {
        long maintenant = System.currentTimeMillis();
        hote.executerSurFilUi(() -> {
            hote.etatConsole().surEvenement(type, id, corps, maintenant);
            jo.joide.app.android.ConsoleBuild.Phase phase =
                    hote.etatConsole().phaseCourante();
            if (phase != null) {
                hote.etat().phaseBuild(phase.name().toLowerCase(
                        java.util.Locale.ROOT));
            }
            if ("build-finished".equals(type)) {
                hote.etat().terminerBuild(
                        corps.member("success").asBoolean());
                hote.majFab();
            }
            hote.majConsole();
        });
    }

    private void construireEnProcessus(BuildSpec spec, long numero) {
        int fils = Reglages.lire(hote.sourceReglages()).filsParBuild;
        try (BuildRunner runner = new BuildRunner(
                outDir -> StoreAndroid.open(outDir), fils)) {
            BuildRunner.Outcome resultat = runner.run(spec, null,
                    (nom, statut) -> hote.executerSurFilUi(() -> {
                        hote.etatConsole().surTache(nom, statut,
                                System.currentTimeMillis());
                        jo.joide.app.android.ConsoleBuild.Phase phase =
                                hote.etatConsole().phaseCourante();
                        if (phase != null) {
                            hote.etat().phaseBuild(phase.name()
                                    .toLowerCase(java.util.Locale.ROOT));
                        }
                        hote.majConsole();
                    }), jo.joide.platform.CancelToken.NEVER);
            hote.executerSurFilUi(() -> {
                hote.etatConsole().surBilanEnProcessus(numero,
                        resultat.success, resultat.executed,
                        resultat.fromCache, resultat.upToDate,
                        resultat.problems, System.currentTimeMillis());
                hote.etat().terminerBuild(resultat.success);
                hote.majConsole();
            });
        } catch (Exception e) {
            hote.executerSurFilUi(() -> {
                hote.etatConsole().surTermine(numero, false, 0, 0, 0, -1,
                        java.util.List.of(String.valueOf(e.getMessage())),
                        System.currentTimeMillis());
                hote.etat().terminerBuild(false);
                hote.majConsole();
            });
        } finally {
            hote.executerSurFilUi(this::finirBuild);
        }
    }

    /** Arrête le build en cours : la croix de la pilule (critère J). */
    void arreterBuild() {
        DaemonClient client = clientDemon.get();
        if (client == null || !hote.buildEnCours()) {
            return;
        }
        long numero = hote.etat().build() == null ? -1
                : hote.etat().build().numero();
        if (numero < 0) {
            return;
        }
        try {
            client.cancel(numero);
        } catch (IOException e) {
            // le canal vient de mourir : les événements suivants
            // raconteront l'interruption, jamais un état zombie
        }
    }

    private void finirBuild() {
        hote.poserBuildEnCours(false);
        hote.vueArbre().setSortiesEnCours(false);
        hote.majFab();
        hote.majVerdictConsole();
        hote.rechargerArbre();
    }
}
