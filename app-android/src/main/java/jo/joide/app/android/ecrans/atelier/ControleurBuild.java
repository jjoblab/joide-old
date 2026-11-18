package jo.joide.app.android.ecrans.atelier;

import android.content.Intent;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Paths;

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
 * reste EtatConsole/EtatAtelier (portés par l'écran) ; le contrôleur
 * orchestre et met à jour les volets via l'écran. Les corps viennent
 * d'EcranAtelier sans changement de comportement (extraction
 * mécanique, mêmes commentaires).
 */
final class ControleurBuild {

    private final EcranAtelier atelier;

    ControleurBuild(EcranAtelier atelier) {
        this.atelier = atelier;
    }

    void construire() {
        if (atelier.racineProjet == null || atelier.modele == null || atelier.buildEnCours) {
            return;
        }
        long numero = atelier.numerosBuild.incrementAndGet();
        atelier.etat.commencerBuild(numero);
        atelier.etatConsole.surDemarrage(numero, System.currentTimeMillis());
        atelier.etat.deplierConsole();
        atelier.majVolets();
        atelier.majConsole();
        atelier.majFab();
        atelier.majArbre();
        // lot U : la constitution peut EXTRAIRE des AAR au disque et
        // LOCALISER l'outillage — elle vit sur le fil de fond, ses
        // écarts remontent à la console dès qu'ils sont connus
        ExternalResolution externals = atelier.sync.derniereResolution()
                .orElse(ExternalResolution.empty());
        atelier.fond.execute(() -> {
            // lot U/8 : le keystore de débogage se prépare AVANT la
            // constitution — l'asset de l'APK se copie UNE fois vers
            // filesDir/toolchain, la variante debug signée suit
            // (avant : APK produit non signé, ininstallable)
            java.nio.file.Path keystoreDebug =
                    jo.joide.app.android.PreparationChaine
                            .assurerKeystoreDebug(atelier);
            SpecsBuild.Constitution constitution = SpecsBuild
                    .constituer(atelier.modele, externals, atelier.racineProjet,
                            atelier.racineProjet.resolve("build-joide"),
                            Paths.get(atelier.getFilesDir().getAbsolutePath(),
                                    "m2"), keystoreDebug);
            atelier.runOnUiThread(() -> {
                atelier.etatConsole.surEcarts(constitution.ecarts);
                if (!constitution.constructible()) {
                    atelier.etatConsole.surRienAConstruire();
                    atelier.etat.interrompreBuild();
                    atelier.majConsole();
                    finirBuild();
                    return;
                }
                // lot M : le moteur par défaut vit dans Paramètres
                boolean enProcessus = Reglages.lire(atelier.sourceReglages)
                        .moteur == Reglages.Moteur.PROCESSUS;
                if (enProcessus) {
                    atelier.etatConsole.surAnnonceEnProcessus();
                }
                atelier.majConsole();
                if (enProcessus) {
                    atelier.fond.execute(() -> construireEnProcessus(
                            constitution.spec, numero));
                } else {
                    atelier.fond.execute(() -> construireParService(
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
        BuildService.effacerErreur(atelier.getFilesDir());
        BuildService.effacerVie(atelier.getFilesDir());
        atelier.startForegroundService(new Intent(atelier, BuildService.class));
        Socket socket = null;
        DaemonClient client = null;
        long limite = System.currentTimeMillis() + atelier.ATTENTE_PORT_MS;
        long prochaineRedemande =
                System.currentTimeMillis() + atelier.REDEMANDE_SERVICE_MS;
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
            String cause = BuildService.lireErreur(atelier.getFilesDir());
            if (cause != null) {
                causeDemon = cause;
                break;
            }
            if (!vieVue
                    && BuildService.lireVie(atelier.getFilesDir()) != null) {
                vieVue = true;
            }
            int port = BuildService.lirePort(atelier.getFilesDir());
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
                    BuildService.effacerPort(atelier.getFilesDir());
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
                atelier.startForegroundService(new Intent(atelier,
                        BuildService.class));
                prochaineRedemande =
                        System.currentTimeMillis() + atelier.REDEMANDE_SERVICE_MS;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                atelier.runOnUiThread(this::finirBuild);
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
                            + (atelier.ATTENTE_PORT_MS / 1000) + " s et le"
                            + " processus :build n'a JAMAIS démarré"
                            + " (restriction ROM ou batterie ?)";
                } else if (refus > 0) {
                    detail = "port publié mais connexion refusée "
                            + refus + " fois — rien n'écoute sur"
                            + " 127.0.0.1 (démon :build arrêté ou"
                            + " injoignable)";
                } else {
                    detail = "port non publié en "
                            + (atelier.ATTENTE_PORT_MS / 1000) + " s"
                            + " (processus :build démarré, démon pas"
                            + " prêt)";
                }
            }
            final String detailFinal = detail;
            atelier.runOnUiThread(() -> {
                atelier.etatConsole.surInjoignable(detailFinal);
                atelier.etatConsole.surRepliEnProcessus();
                atelier.majConsole();
            });
            construireEnProcessus(spec, numero);
            return;
        }
        try (DaemonClient branche = client) {
            atelier.clientDemon.set(branche);
            DaemonClient.Event salutation =
                    branche.await("hello", 30);
            evenement(salutation.type, salutation.id,
                    salutation.body);
            construireAvecClient(branche, spec, numero);
        } catch (DaemonClient.DaemonDiedException e) {
            // §6.7 : la mort du démon est un événement, l'UI survit —
            // le build suivant relance le service
            atelier.runOnUiThread(() -> {
                atelier.etatConsole.surInterrompu();
                atelier.etat.interrompreBuild();
                atelier.majConsole();
                atelier.majFab();
            });
        } catch (IOException e) {
            atelier.runOnUiThread(() -> {
                atelier.etatConsole.surInjoignable(String.valueOf(e.getMessage()));
                atelier.etat.interrompreBuild();
                atelier.majConsole();
                atelier.majFab();
            });
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                    // le socket est déjà mort
                }
            }
            atelier.clientDemon.set(null);
            atelier.runOnUiThread(this::finirBuild);
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
        atelier.runOnUiThread(() -> {
            atelier.etatConsole.surEvenement(type, id, corps, maintenant);
            jo.joide.app.android.ConsoleBuild.Phase phase =
                    atelier.etatConsole.phaseCourante();
            if (phase != null) {
                atelier.etat.phaseBuild(phase.name().toLowerCase(
                        java.util.Locale.ROOT));
            }
            if ("build-finished".equals(type)) {
                atelier.etat.terminerBuild(
                        corps.member("success").asBoolean());
                atelier.majFab();
            }
            atelier.majConsole();
        });
    }

    private void construireEnProcessus(BuildSpec spec, long numero) {
        int fils = Reglages.lire(atelier.sourceReglages).filsParBuild;
        try (BuildRunner runner = new BuildRunner(
                outDir -> StoreAndroid.open(outDir), fils)) {
            BuildRunner.Outcome resultat = runner.run(spec, null,
                    (nom, statut) -> atelier.runOnUiThread(() -> {
                        atelier.etatConsole.surTache(nom, statut,
                                System.currentTimeMillis());
                        jo.joide.app.android.ConsoleBuild.Phase phase =
                                atelier.etatConsole.phaseCourante();
                        if (phase != null) {
                            atelier.etat.phaseBuild(phase.name().toLowerCase(
                                    java.util.Locale.ROOT));
                        }
                        atelier.majConsole();
                    }), jo.joide.platform.CancelToken.NEVER);
            atelier.runOnUiThread(() -> {
                atelier.etatConsole.surBilanEnProcessus(numero,
                        resultat.success, resultat.executed,
                        resultat.fromCache, resultat.upToDate,
                        resultat.problems, System.currentTimeMillis());
                atelier.etat.terminerBuild(resultat.success);
                atelier.majConsole();
            });
        } catch (Exception e) {
            atelier.runOnUiThread(() -> {
                atelier.etatConsole.surTermine(numero, false, 0, 0, 0, -1,
                        java.util.List.of(String.valueOf(e.getMessage())),
                        System.currentTimeMillis());
                atelier.etat.terminerBuild(false);
                atelier.majConsole();
            });
        } finally {
            atelier.runOnUiThread(this::finirBuild);
        }
    }

    /** Arrête le build en cours : la croix de la pilule (critère J). */
    void arreterBuild() {
        DaemonClient client = atelier.clientDemon.get();
        if (client == null || !atelier.buildEnCours) {
            return;
        }
        long numero = atelier.etat.build() == null ? -1 : atelier.etat.build().numero();
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
        atelier.buildEnCours = false;
        atelier.vueArbre.setSortiesEnCours(false);
        atelier.majFab();
        atelier.majVerdictConsole();
        atelier.rechargerArbre();
    }
}
