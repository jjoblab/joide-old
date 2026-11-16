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

    private HoteDemonSocket hote;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager gestionnaire =
                getSystemService(NotificationManager.class);
        if (gestionnaire != null
                && gestionnaire.getNotificationChannel(CANAL) == null) {
            gestionnaire.createNotificationChannel(
                    new NotificationChannel(CANAL,
                            getString(R.string.canal_build),
                            NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CANAL)
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
    }

    /** Republie le port du démon vivant (au mieux : la boucle du
     *  client ressonde, le service suivant repartira). */
    private void republierPort() {
        try {
            publierPort(
                    Paths.get(getFilesDir().getAbsolutePath()),
                    hote.port());
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
            stopSelf();
            return;
        }
        new Thread(() -> {
            try {
                hote.attendreArret();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
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

    @Override
    public void onDestroy() {
        if (hote != null) {
            hote.close();
            hote = null;
        }
        try {
            Files.deleteIfExists(Paths.get(getFilesDir().getAbsolutePath(),
                    FICHIER_PORT));
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
