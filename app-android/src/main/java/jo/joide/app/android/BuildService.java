package jo.joide.app.android;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import jo.joide.build.daemon.serveur.HoteDemonSocket;

/**
 * Service de premier plan du processus {@code :build} (§6.7, ADR-043) :
 * héberge le démon de build — l'hôte socket locale qui sert le
 * protocole P6 au-dessus du runner équivalent, store persistant sur la
 * base système ({@link StoreAndroid}). L'UI s'y connecte par
 * {@code DaemonClient.over} ; sa mort est un événement de flux, l'UI
 * survit et le build suivant relance le service.
 *
 * <p>Le port attribué (éphémère, rebouclage) est publié dans un petit
 * fichier du stockage interne : le client le lit après avoir demandé
 * le service — jamais de port fixe exposé.</p>
 */
public final class BuildService extends Service {

    /** Nom du canal de notification du service. */
    static final String CANAL = "joide-build";

    /** Fichier du port du démon, dans les fichiers internes. */
    static final String FICHIER_PORT = "demon-build.port";

    /**
     * Fichier de l'échec du démon, dans les fichiers internes (lot
     * U/7) : quand le démon ne part pas, la TRACE part ici — le
     * client qui attend le port lit la cause RÉELLE et la montre à
     * la console, au lieu d'un « port non publié » muet qui ne dit
     * rien du « pourquoi ».
     */
    static final String FICHIER_ERREUR = "demon-build.err";

    /**
     * Fichier de VIE du processus :build (lot VIII, ADR-070) : posé
     * à la PREMIÈRE ligne d'onCreate — sa présence prouve que le
     * processus a démarré (même si le port n'est pas encore publié),
     * son absence après trente secondes dit « le système n'a jamais
     * lancé le processus » (restriction ROM, optimiseur de batterie).
     * Le rapport du 2026-11-17 (« port non publié en 30 s » sans
     * cause) ne distinguait pas les deux — désormais le détail du
     * client tranche.
     */
    static final String FICHIER_VIE = "demon-build.vie";

    private HoteDemonSocket hote;

    @Override
    public void onCreate() {
        super.onCreate();
        // lot VIII : la VIE se publie AVANT TOUT — même un échec dans
        // le bloc gardé ci-dessous laisse la preuve que le processus
        // a démarré (le fichier d'erreur dira le reste)
        publierVie();
        // lot U/8 : les propriétés d'outillage vivent AUSSI dans le
        // processus :build — une propriété système ne TRAVERSE PAS
        // les processus, et le démon (moteur par défaut) localise
        // l'outillage dans SON propre processus (BuildRunner →
        // ToolchainLocator.defaultCacheDir / aapt2Embarque). Sans ces
        // deux lignes, le démon cherchait « user.home » (absent sur
        // ART : Path.of(null,…) tue le démarrage) et ne voyait PAS
        // l'aapt2 embarqué — tout build Android via le service
        // échouait (rapporté le 2026-11-17 : « le build ne
        // fonctionne pas »).
        //
        // lot VI : le corps ENTIER est gardé — une faute AVANT le
        // onStartCommand (canaux de notification, propriétés) tuait
        // le processus :build en SILENCE : ni port ni fichier
        // d'erreur, et le client comptait jusqu'à trente secondes
        // avant son « port non publié » (rapporté le 2026-11-17).
        // Désormais TOUT échec de démarrage écrit SA cause dans le
        // canal d'erreur — le client la montre au lieu d'attendre.
        try {
            // lot XX : les propriétés d'outillage vivent dans
            // ProprietesConstruction — UNE vérité pour les deux
            // processus (le service, l'écran atelier) ; le lot XX y
            // ajoute l'APK du fork (registrar Compose dexé) et le jar
            // du plugin extrait des assets
            ProprietesConstruction.poser(this);
            NotificationManager gestionnaire =
                    getSystemService(NotificationManager.class);
            if (gestionnaire != null
                    && gestionnaire.getNotificationChannel(CANAL) == null) {
                gestionnaire.createNotificationChannel(
                        new NotificationChannel(CANAL,
                                getString(R.string.canal_build),
                                NotificationManager.IMPORTANCE_LOW));
            }
        } catch (Throwable t) {
            android.util.Log.e("joide-build", "service ne part pas", t);
            publierErreur(Paths.get(getFilesDir().getAbsolutePath()), t);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // lot VI : même garde qu'onCreate — une faute ici (politique
        // de service de premier plan, notification refusée, ressource
        // absente) tuait le processus AVANT le catch de demarrer() :
        // le fichier d'erreur ne paraissait jamais et le client
        // bouclait trente secondes sur « port non publié » (rapporté
        // le 2026-11-17). L'échec s'écrit, le service s'arrête, le
        // client replie en processus — la cause est MONTRÉE.
        try {
            Notification notification = new Notification.Builder(this,
                    CANAL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(getString(R.string.service_build_actif))
                    .setOngoing(true)
                    .build();
            startForeground(startId, notification);
            if (hote == null) {
                demarrer();
            } else {
                // lot U/6 : le démon VIT déjà — le port se republie quand
                // même : le client qui a effacé un fichier périmé (refus
                // de connexion pendant un build) sinon ne reverrait JAMAIS
                // le port, et chaque build bouclerait sur « injoignable »
                // tant que le service resterait debout (rapporté le
                // 2026-11-17)
                republierPort();
            }
            return START_NOT_STICKY;
        } catch (Throwable t) {
            android.util.Log.e("joide-build", "service ne part pas", t);
            publierErreur(Paths.get(getFilesDir().getAbsolutePath()), t);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    /** Republie le port du démon vivant (au mieux : la boucle du
     *  client ressonde, le service suivant repartira). */
    private void republierPort() {
        try {
            Path stockage = Paths.get(getFilesDir().getAbsolutePath());
            publierPort(stockage, hote.port());
            // lot IX : la VIE suit le port. Le service VIVANT d'un
            // build précédent ne repasse JAMAIS par onCreate — le
            // client du build suivant (qui efface demon-build.vie
            // avant de redemander) concluait « le processus :build
            // n'a JAMAIS démarré » alors qu'il tournait depuis le
            // build d'avant (rapport du 2026-11-17, build #2 :
            // diagnostic faux sur un service en parfaite santé)
            publierVie();
        } catch (IOException e) {
            android.util.Log.w("joide-build",
                    "port non republie : " + e.getMessage());
        }
    }

    private void demarrer() {
        Path stockage = Paths.get(getFilesDir().getAbsolutePath());
        try {
            // lot M (ADR-É8) : les fils par build viennent des réglages,
            // lus au démarrage du service — jamais codés deux fois.
            // La lecture vit DANS le try : un réglage illisible ne tue
            // JAMAIS le processus sans nettoyage — un crash ici laisserait
            // le vieux fichier de port, et chaque build bouclerait sur
            // « service de build injoignable » (rapporté le 2026-11-17).
            int fils = jo.joide.app.android.parametres.Reglages.lire(
                    jo.joide.app.android.parametres.PreferencesAndroid
                            .source(this)).filsParBuild;
            hote = new HoteDemonSocket(
                    outDir -> StoreAndroid.open(outDir), fils);
            int port = hote.demarrer();
            publierPort(stockage, port);
            effacerErreur(stockage.toFile());
            android.util.Log.i("joide-build",
                    "démon prêt, port " + port);
        } catch (Throwable t) {
            // lot U/6 : IOException|RuntimeException ne voyait pas les
            // ERREURS (classe manquante, initialisation avortée) — le
            // processus :build mourait en SILENCE, le port ne paraissait
            // jamais et chaque build répétait « injoignable ». La trace
            // part au journal (Paramètres → Journal), le service
            // s'arrête propre : le build suivant repartira en repli
            // en processus (lot U/6).
            android.util.Log.e("joide-build", "démon ne part pas", t);
            // lot U/7 : la trace part AUSSI dans le canal d'erreur que
            // le client lit pendant son attente — la console montre la
            // cause RÉELLE (« démon : ClassNotFoundException ... ») au
            // lieu d'un « port non publié » muet
            publierErreur(stockage, t);
            stopSelf();
            return;
        }
        new Thread(() -> {
            try {
                hote.attendreArret();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // lot IX : mort anormale du fil d'acceptation — la
                // CAUSE part au canal d'erreur AVANT l'arrêt : le
                // client qui attend le port la MONTRERA (un Error
                // tuait le fil en silence : « connexion refusée »
                // sans jamais dire pourquoi, rapport 2026-11-17)
                Throwable panique = hote.panique();
                if (panique != null) {
                    publierErreur(Paths.get(
                            getFilesDir().getAbsolutePath()), panique);
                }
                stopSelf();
            }
        }, "joide-build-service").start();
    }

    private static void publierPort(Path stockage, int port)
            throws IOException {
        Path cible = stockage.resolve(FICHIER_PORT);
        Path temporaire = stockage.resolve(FICHIER_PORT + ".tmp");
        Files.write(temporaire,
                (String.valueOf(port) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporaire, cible,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temporaire, cible,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Publie l'échec du démon (lot U/7) : une ligne courte, la cause
     *  réelle — le client l'affiche à la console pendant son attente. */
    private static void publierErreur(Path stockage, Throwable t) {
        try {
            Files.write(stockage.resolve(FICHIER_ERREUR),
                    ("démon de build : " + t).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // au mieux : le journal système garde la trace complète
        }
    }

    /**
     * @return le port publié par le service, ou -1 s'il n'a pas écrit
     *         (public depuis le lot H : l'écran IDE vit dans son propre
     *         paquet et attend le démarrage du service)
     */
    public static int lirePort(File fichiers) {
        try {
            byte[] contenu = Files.readAllBytes(Paths.get(
                    fichiers.getAbsolutePath(), FICHIER_PORT));
            return Integer.parseInt(
                    new String(contenu, StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /**
     * Efface le fichier de port (le client, après un refus de connexion :
     * un port lu n'est pas un port servi — un fichier PÉRIMÉ laissé par
     * un service mort sans onDestroy bouclerait l'échec, la republication
     * du service reparti doit pouvoir le remplacer).
     *
     * @param fichiers le dossier des fichiers internes
     */
    public static void effacerPort(File fichiers) {
        try {
            Files.deleteIfExists(Paths.get(
                    fichiers.getAbsolutePath(), FICHIER_PORT));
        } catch (IOException e) {
            // au mieux : la boucle du client réessaie dans son budget
        }
    }

    /**
     * @return la cause RÉELLE de l'échec du démon (lot U/7), ou null
     *         s'il n'y a pas d'échec connu — le client la montre à la
     *         console au lieu d'un « port non publié » muet
     */
    public static String lireErreur(File fichiers) {
        try {
            byte[] contenu = Files.readAllBytes(Paths.get(
                    fichiers.getAbsolutePath(), FICHIER_ERREUR));
            String texte = new String(contenu, StandardCharsets.UTF_8)
                    .trim();
            return texte.isEmpty() ? null : texte;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Efface le canal d'erreur (le client, avant de redemander le
     *  service : un échec d'hier ne doit pas censurer un essai neuf). */
    public static void effacerErreur(File fichiers) {
        try {
            Files.deleteIfExists(Paths.get(
                    fichiers.getAbsolutePath(), FICHIER_ERREUR));
        } catch (IOException e) {
            // au mieux : le canal est indicatif
        }
    }

    /** Publie la vie du processus : pid et instant — au mieux (la
     *  preuve manquante vaut moins qu'un service qui démarre). */
    private void publierVie() {
        try {
            Files.write(Paths.get(getFilesDir().getAbsolutePath(),
                    FICHIER_VIE), ("pid=" + android.os.Process.myPid()
                    + " démarré=" + System.currentTimeMillis()
                    + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // au mieux : le canal d'erreur reste la preuve principale
        }
    }

    /**
     * @return la preuve de vie du processus :build (pid et instant),
     *         ou null s'il n'a jamais démarré — le client s'en sert
     *         pour distinguer « jamais lancé » de « lancé mais pas
     *         prêt » dans son message d'injoignabilité (lot VIII)
     */
    public static String lireVie(File fichiers) {
        try {
            byte[] contenu = Files.readAllBytes(Paths.get(
                    fichiers.getAbsolutePath(), FICHIER_VIE));
            String texte = new String(contenu, StandardCharsets.UTF_8)
                    .trim();
            return texte.isEmpty() ? null : texte;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Efface la preuve de vie (le client, avant de redemander le
     *  service : une vie d'hier ne doit pas fausser le diagnostic
     *  d'un essai neuf). */
    public static void effacerVie(File fichiers) {
        try {
            Files.deleteIfExists(Paths.get(
                    fichiers.getAbsolutePath(), FICHIER_VIE));
        } catch (IOException e) {
            // au mieux : la preuve est indicative
        }
    }

    @Override
    public void onDestroy() {
        if (hote != null) {
            hote.close();
            hote = null;
        }
        try {
            Files.deleteIfExists(Paths.get(getFilesDir().getAbsolutePath(),
                    FICHIER_PORT));
            // la vie part avec le processus : une preuve de vie sans
            // port ni erreur relancerait le diagnostic à vide
            Files.deleteIfExists(Paths.get(getFilesDir().getAbsolutePath(),
                    FICHIER_VIE));
        } catch (IOException e) {
            // le fichier de port part au mieux : le service meurt, le
            // port éphémère n'est plus servi
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
