package jo.joide.build.daemon.serveur;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import jo.joide.build.daemon.protocol.Frames;
import jo.joide.build.daemon.runner.BuildRunner;

/**
 * Hôte du démon sur socket locale (ADR-043) : un serveur TCP sur
 * l'adresse de rebouclage qui sert le protocole P6 au-dessus d'un
 * {@link BuildRunner} possédé — le transport de l'application Android
 * (processus {@code :build}), réutilisable côté bureau.
 *
 * <p>Une seule connexion à la fois : l'UI est un client unique ; la
 * connexion suivante est servie après la fin de la précédente (fin de
 * flux ou « shutdown »). Le {@link BuildRunner} — donc les compilateurs
 * chauds — survit aux reconnexions du client.</p>
 *
 * <p>La mort du démon est un événement de flux : la fermeture de la
 * socket côté client se lit comme une fin de canal, jamais comme un
 * état zombie. Une erreur non rattrapée (dont l'injection de panne)
 * tue le fil d'acceptation : {@link #attendreArret()} rend alors la
 * main à l'hôte, qui arrête son service.</p>
 */
public final class HoteDemonSocket implements AutoCloseable {

    private final BuildRunner runner;
    private final ServerSocket server;
    private final Thread boucle;
    /** Connexion couramment servie ; fermée par {@link #close()} pour
     *  que la mort de l'hôte se lise comme une fin de canal chez le
     *  client (jamais un état zombie). */
    private volatile Socket courant;
    private volatile boolean ferme;

    /**
     * @param storeOpener fabrique de store par build
     * @param threads     fils de construction
     * @throws IOException si l'écoute locale échoue
     */
    public HoteDemonSocket(BuildRunner.StoreOpener storeOpener, int threads)
            throws IOException {
        this.runner = new BuildRunner(storeOpener, threads);
        // port éphémère sur le rebouclage : jamais exposé au réseau
        this.server = new ServerSocket(0, 1,
                InetAddress.getLoopbackAddress());
        this.boucle = new Thread(this::accepter, "joide-hote-demon");
        this.boucle.setDaemon(true);
    }

    /**
     * Démarre le service des connexions.
     *
     * @return le port local attribué
     */
    public int demarrer() {
        boucle.start();
        return server.getLocalPort();
    }

    /** @return le port local d'écoute */
    public int port() {
        return server.getLocalPort();
    }

    /**
     * Attend la fin du fil d'acceptation : fermeture volontaire
     * ({@link #close()}) ou erreur fatale du démon.
     *
     * @throws InterruptedException si l'attente est interrompue
     */
    public void attendreArret() throws InterruptedException {
        boucle.join();
    }

    private void accepter() {
        while (!ferme) {
            try (Socket client = server.accept()) {
                client.setTcpNoDelay(true);
                courant = client;
                Frames frames = new Frames(client.getInputStream(),
                        client.getOutputStream());
                new BoucleDemon(runner).servir(frames);
                // fin de canal (fin de flux ou « shutdown ») : la
                // connexion suivante est servie avec le même runner
            } catch (IOException e) {
                if (ferme) {
                    return;
                }
                // client déconnecté en plein échange : la connexion
                // suivante reste servie, le démon survit au client
            } finally {
                courant = null;
            }
        }
    }

    @Override
    public void close() {
        ferme = true;
        // la connexion courante tombe la première : le client lit une
        // fin de canal, pas un silence
        Socket servie = courant;
        if (servie != null) {
            try {
                servie.close();
            } catch (IOException e) {
                // déjà tombée : rien à dire
            }
        }
        try {
            server.close();
        } catch (IOException e) {
            // la socket d'écoute se ferme : le fil d'acceptation rend
            // la main, l'erreur de fermeture n'a rien à dire
        }
        runner.close();
        try {
            boucle.join(2000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
