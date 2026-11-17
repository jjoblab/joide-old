package jo.joide.build.daemon.serveur;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

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
    /** Cause d'une mort anormale du fil d'acceptation (un Error
     *  échappé de la boucle de protocole) — le service la publie
     *  dans son canal d'erreur au lieu d'un silence. */
    private final AtomicReference<Throwable> panique =
            new AtomicReference<>();

    /**
     * @param storeOpener fabrique de store par build
     * @param threads     fils de construction
     * @throws IOException si l'écoute locale échoue
     */
    public HoteDemonSocket(BuildRunner.StoreOpener storeOpener, int threads)
            throws IOException {
        this.runner = new BuildRunner(storeOpener, threads);
        // port éphémère sur le rebouclage IPv4 EXPLICITE : jamais
        // exposé au réseau. JAMAIS getLoopbackAddress() : sur Android
        // (libcore) il rend ::1, l'IPv6 — le démon écoutait sur
        // [::1]:port pendant que le client composait 127.0.0.1:port,
        // connexion REFUSÉE à chaque build, démon pourtant vivant
        // (« port publié mais connexion refusée 6 fois », rapport du
        // 2026-11-17 : la JVM de bureau rend 127.0.0.1, les tests
        // passaient, l'appareil échouait). L'IPv4 127.0.0.1 existe
        // sur tout appareil Android : les deux bouts s'y accordent.
        this.server = new ServerSocket(0, 1,
                InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
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

    /** @return l'adresse d'écoute (le rebouclage IPv4 127.0.0.1 —
     *         le test l'ancre : jamais ::1) */
    public InetAddress adresseEcoute() {
        return server.getInetAddress();
    }

    /** @return la cause d'une mort anormale du fil d'acceptation,
     *         ou {@code null} s'il vit ou s'est arrêté proprement */
    public Throwable panique() {
        return panique.get();
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
            } catch (BoucleDemon.PaniqueFatale fatale) {
                // l'injection de panne DOIT tuer le démon : elle
                // traverse les gardes (le fil meurt, le chien de
                // garde du service arrête tout — sémantique des
                // preuves de robustesse)
                throw fatale;
            } catch (Throwable t) {
                // un Error (classe manquante, lien natif cassé) ne
                // tue PLUS le démon : la connexion tombe, la cause
                // s'ENREGISTRE (le chien de garde du service la
                // publiera dans son canal d'erreur), la connexion
                // suivante reste servie — l'ancien fil mort en
                // silence ne laissait au client qu'un « connexion
                // refusée » sans cause (rapport du 2026-11-17)
                panique.compareAndSet(null, t);
                t.printStackTrace();
                if (ferme) {
                    return;
                }
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
