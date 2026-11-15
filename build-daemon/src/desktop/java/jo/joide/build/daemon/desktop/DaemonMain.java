package jo.joide.build.daemon.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.protocol.Frames;
import jo.joide.build.daemon.protocol.Json;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;
import jo.joide.store.BuildStore;
import jo.joide.store.SqliteStores;
import jo.joide.vfs.DirtySet;

/**
 * Point d'entrée du démon de bureau : lit les cadres sur l'entrée
 * standard, exécute les builds sur l'exécuteur équivalent (store
 * SQLite persistant à la racine des sorties du build — le contexte se
 * reconstruit depuis le disque après une mort brutale), écrit les
 * événements sur la sortie standard. Les erreurs standard vont sur
 * la sortie d'erreur du parent : un OOM de la fille y est visible
 * sans corrompre le protocole.
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
        AtomicReference<CancelToken.Cancellation> cancellation =
                new AtomicReference<>();
        Runtime.getRuntime().addShutdownHook(new Thread(runner::close,
                "joide-daemon-shutdown"));
        frames.write(Json.object(
                "type", "hello",
                "daemon", "joide",
                "version", 1,
                "maxHeapBytes", Runtime.getRuntime().maxMemory(),
                "hotCompilers", 1));
        while (true) {
            String frame = frames.read();
            JsonValue request = jo.joide.deps.json.Json.parse(frame).root;
            String type = request.member("type").asString();
            if ("shutdown".equals(type)) {
                runner.close();
                return;
            }
            long id = request.member("id").asLong();
            switch (type == null ? "" : type) {
                case "ping" -> frames.write(Json.object(
                        "type", "pong", "id", id));
                case "warmup" -> warmup(frames, id,
                        request.member("bytes").asLong());
                case "cancel" -> {
                    CancelToken.Cancellation handle = cancellation.get();
                    if (handle != null) {
                        handle.cancel();
                    }
                }
                case "crash" -> crash(id, request);
                case "build" -> {
                    JsonValue specValue = request.member("spec");
                    BuildSpec spec;
                    try {
                        spec = BuildSpec.fromJson(specValue);
                    } catch (RuntimeException invalid) {
                        frames.write(Json.object(
                                "type", "build-refused", "id", id,
                                "reason", "spec",
                                "message", String.valueOf(
                                        invalid.getMessage())));
                        continue;
                    }
                    build(frames, runner, cancellation, id, spec);
                }
                default -> frames.write(Json.object(
                        "type", "error", "id", id < 0 ? -1 : id,
                        "message", "unknown request type: " + type));
            }
        }
    }

    /**
     * Injection de panne (preuve « OOM forcé ») : réservée aux
     * machines virtuelles lancées avec {@code -Djoide.daemon.faults=1}
     * — le démon de production refuse toujours. Le mode « oom »
     * épuise le tas sans rattrapage : l'erreur remonte hors de la
     * boucle et tue la machine virtuelle, comme un vrai OOM.
     *
     * @param id      identifiant de requête
     * @param request requête « crash »
     * @throws IOException si l'acquittement échoue
     */
    private static void crash(long id, JsonValue request)
            throws IOException {
        if (!"1".equals(System.getProperty("joide.daemon.faults"))) {
            Frames frames = new Frames(System.in, System.out);
            frames.write(Json.object(
                    "type", "crash-refused", "id", id,
                    "message", "fault injection disabled"));
            return;
        }
        String mode = String.valueOf(request.member("mode").asString());
        if ("oom".equals(mode)) {
            // épuisement réel du tas, sans spirale de GC : la pique
            // demande plus que le tas ne peut jamais contenir (une
            // allocation impossible échoue après un ramassage au
            // plus), l'erreur relancée s'échappe de la boucle comme
            // un OOM naturel — les rattrapages du protocole attrapent
            // Exception, jamais Error
            java.util.List<byte[]> sink = new java.util.ArrayList<>();
            try {
                while (true) {
                    sink.add(new byte[(int) Math.min(
                            Integer.MAX_VALUE - 2,
                            Runtime.getRuntime().maxMemory())]);
                }
            } catch (OutOfMemoryError saturated) {
                sink.clear();
                System.out.flush();
                throw new OutOfMemoryError(
                        "forced oom (fault injection)");
            }
        }
        throw new IllegalArgumentException("unknown crash mode: " + mode);
    }

    private static void warmup(Frames frames, long id, long bytes)
            throws IOException {
        if (bytes < 0 || bytes > Runtime.getRuntime().maxMemory()) {
            frames.write(Json.object(
                    "type", "build-refused", "id", id,
                    "reason", "memory",
                    "message", "warmup above daemon heap"));
            return;
        }
        try {
            byte[] touch = new byte[(int) bytes];
            // toucher chaque page : le tas est rendu au système
            for (int i = 0; i < touch.length; i += 4096) {
                touch[i] = 1;
            }
            frames.write(Json.object(
                    "type", "warmed", "id", id, "bytes", bytes));
        } catch (OutOfMemoryError oom) {
            // l'allocation échoue : le démon le dit et survit
            frames.write(Json.object(
                    "type", "build-refused", "id", id,
                    "reason", "memory",
                    "message", "warmup allocation failed"));
        }
    }

    private static void build(Frames frames, BuildRunner runner,
                              AtomicReference<CancelToken.Cancellation>
                                      cancellation,
                              long id, BuildSpec spec) throws IOException {
        Map<jo.joide.platform.RootName, DirtySet> dirty = new LinkedHashMap<>();
        CancelToken.Cancellation handle = CancelToken.create();
        cancellation.set(handle);
        frames.write(Json.object("type", "build-started", "id", id));
        try {
            BuildRunner.Outcome outcome = runner.run(spec, dirty,
                    (name, status) -> {
                        try {
                            frames.write(Json.object(
                                    "type", "task", "id", id,
                                    "name", name, "status", status));
                        } catch (IOException e) {
                            // le client est parti : les événements
                            // suivants sont muets, le build continue
                        }
                    }, handle.token());
            frames.write(Json.object(
                    "type", "build-finished", "id", id,
                    "success", outcome.success,
                    "executed", outcome.executed,
                    "fromCache", outcome.fromCache,
                    "upToDate", outcome.upToDate,
                    "peakHeapBytes", outcome.peakHeapBytes,
                    "wallMillis", outcome.wallMillis,
                    "cancelled", outcome.cancelled,
                    "problems", outcome.problems,
                    "hotCompilers", BuildRunner.compilerInstances(),
                    "hotDexers", BuildRunner.dexerInstances()));
        } catch (Exception e) {
            // la pile va sur l'erreur standard (relayée au parent) :
            // le cadre ne porte que la nature et le message
            e.printStackTrace();
            frames.write(Json.object(
                    "type", "build-finished", "id", id,
                    "success", false,
                    "problems", java.util.List.of(
                            e.getClass().getSimpleName() + ": "
                                    + String.valueOf(e.getMessage())),
                    "cancelled", false));
        } finally {
            cancellation.set(null);
        }
    }

    private static BuildStore openStore(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        return SqliteStores.open(outDir.resolve("build.db"));
    }
}
