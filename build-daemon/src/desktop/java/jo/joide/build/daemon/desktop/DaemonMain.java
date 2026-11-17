package jo.joide.build.daemon.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jo.joide.build.daemon.protocol.Frames;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.build.daemon.serveur.BoucleDemon;
import jo.joide.store.BuildStore;
import jo.joide.store.SqliteStores;

/**
 * Point d'entrée du démon de bureau : lit les cadres sur l'entrée
 * standard, exécute les builds sur l'exécuteur équivalent (store
 * SQLite persistant à la racine des sorties du build — le contexte se
 * reconstruit depuis le disque après une mort brutale), écrit les
 * événements sur la sortie standard. Les erreurs standard vont sur
 * la sortie d'erreur du parent : un OOM de la fille y est visible
 * sans corrompre le protocole.
 *
 * <p>La boucle de protocole vit dans {@link BoucleDemon} (partagée
 * avec l'hôte socket du câblage Android, ADR-043) : ce point d'entrée
 * ne fait que monter le démon et le servir sur l'entrée standard.</p>
 */
public final class DaemonMain {

    private DaemonMain() {
    }

    /**
     * @param args inutilisés
     * @throws Exception si le protocole échoue
     */
    public static void main(String[] args) throws Exception {
        Frames frames = new Frames(System.in, System.out);
        // fils de construction : propriété joide.daemon.threads
        // (l'IDE de P8 la réglera par appareil ; les preuves à cœur
        // unique la posent pour ne pas affamer la machine de test)
        int threads = Math.max(1, Integer.getInteger(
                "joide.daemon.threads", 4));
        BuildRunner runner = new BuildRunner(DaemonMain::openStore,
                threads);
        Runtime.getRuntime().addShutdownHook(new Thread(runner::close,
                "joide-daemon-shutdown"));
        new BoucleDemon(runner).servir(frames);
    }

    private static BuildStore openStore(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        return SqliteStores.open(outDir.resolve("build.db"));
    }
}
