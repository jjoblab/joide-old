package jo.joide.build.daemon.serveur;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.protocol.Frames;
import jo.joide.build.daemon.protocol.Json;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;
import jo.joide.vfs.DirtySet;

/**
 * Boucle de protocole du démon : la salutation puis le service des
 * requêtes (ping, warmup, cancel, crash, build, shutdown) sur un canal
 * de cadres donné. Extraite du point d'entrée de bureau (P6) pour le
 * câblage Android (ADR-043) : le même service tourne sur l'entrée
 * standard du démon de bureau et sur une socket locale du service de
 * build de l'application — jamais deux protocoles.
 *
 * <p>Une instance sert UN canal à la fois ; le {@link BuildRunner}
 * (compilateurs chauds) appartient à l'appelant et peut être partagé
 * entre canaux successifs.</p>
 */
public final class BoucleDemon {

    /**
     * Panne FATALE par conception (injection « oom ») : elle DOIT
     * traverser toutes les gardes et tuer le démon — la machine
     * virtuelle meurt sur son fil principal, la preuve de robustesse
     * l'exige (DaemonDeathResumeTest). Les AUTRES jeters (classe
     * manquante, handler cassé) se répondent : le service survit.
     */
    static final class PaniqueFatale extends Error {
        private static final long serialVersionUID = 1L;

        PaniqueFatale(Throwable cause) {
            super("injection de panne fatale", cause);
        }
    }

    private final BuildRunner runner;
    private final AtomicReference<CancelToken.Cancellation> cancellation =
            new AtomicReference<>();

    /**
     * @param runner exécuteur des builds (possédé par l'appelant, jamais
     *               fermé par la boucle)
     */
    public BoucleDemon(BuildRunner runner) {
        this.runner = runner;
    }

    /**
     * Écrit la salutation puis sert les requêtes jusqu'à « shutdown »
     * ou la fin du canal. Une erreur non rattrapée (dont l'injection
     * de panne « oom ») s'échappe : le canal meurt, l'appelant décide
     * du sort du processus.
     *
     * @param frames canal de cadres du pair
     * @throws IOException si le canal échoue
     */
    public void servir(Frames frames) throws IOException {
        frames.write(Json.object(
                "type", "hello",
                "daemon", "joide",
                "version", 1,
                "maxHeapBytes", Runtime.getRuntime().maxMemory(),
                "hotCompilers", 1));
        while (true) {
            String frame = frames.read();
            // lot IX : chaque requête vit dans SA garde — un cadre
            // illisible (ou un handler cassé) se RÉPOND, le canal
            // reste debout ; l'ancien fil mourait au premier jeter
            // et le client ne voyait qu'un canal tombé sans cause
            long id = -1;
            try {
                JsonValue request = jo.joide.deps.json.Json.parse(
                        frame).root;
                String type = request.member("type").asString();
                if ("shutdown".equals(type)) {
                    return;
                }
                id = request.member("id").asLong();
                switch (type == null ? "" : type) {
                    case "ping" -> frames.write(Json.object(
                            "type", "pong", "id", id));
                    case "warmup" -> warmup(frames, id,
                            request.member("bytes").asLong());
                    case "cancel" -> {
                        CancelToken.Cancellation handle =
                                cancellation.get();
                        if (handle != null) {
                            handle.cancel();
                        }
                    }
                    case "crash" -> crash(frames, id, request);
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
                        build(frames, id, spec);
                    }
                    default -> frames.write(Json.object(
                            "type", "error", "id", id < 0 ? -1 : id,
                            "message", "unknown request type: " + type));
                }
            } catch (BoucleDemon.PaniqueFatale fatale) {
                // l'injection DOIT tuer : elle traverse les gardes
                throw fatale;
            } catch (IOException e) {
                // le canal lui-même est mort : rien à répondre,
                // la boucle s'arrête (la socket dira pourquoi)
                throw e;
            } catch (Throwable t) {
                t.printStackTrace();
                frames.write(Json.object(
                        "type", "error", "id", id,
                        "message", t.getClass().getSimpleName() + ": "
                                + String.valueOf(t.getMessage())));
            }
        }
    }

    /**
     * Injection de panne (preuve « OOM forcé ») : réservée aux machines
     * virtuelles lancées avec {@code -Djoide.daemon.faults=1} — le
     * démon de production refuse toujours. Le mode « oom » épuise le
     * tas sans rattrapage : l'erreur s'échappe de la boucle et tue la
     * machine virtuelle, comme un vrai OOM.
     */
    private static void crash(Frames frames, long id, JsonValue request)
            throws IOException {
        if (!"1".equals(System.getProperty("joide.daemon.faults"))) {
            frames.write(Json.object(
                    "type", "crash-refused", "id", id,
                    "message", "fault injection disabled"));
            return;
        }
        String mode = String.valueOf(request.member("mode").asString());
        if ("oom".equals(mode)) {
            java.util.List<byte[]> sink = new java.util.ArrayList<>();
            try {
                while (true) {
                    sink.add(new byte[(int) Math.min(
                            Integer.MAX_VALUE - 2,
                            Runtime.getRuntime().maxMemory())]);
                }
            } catch (OutOfMemoryError saturated) {
                sink.clear();
                // la panne FATALE traverse les gardes de la boucle :
                // le démon meurt (preuve DaemonDeathResumeTest), un
                // simple OutOfMemoryError serait RÉPONDU comme erreur
                // depuis que chaque requête vit dans sa garde (lot IX)
                throw new PaniqueFatale(saturated);
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

    private void build(Frames frames, long id, BuildSpec spec)
            throws IOException {
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
        } catch (Throwable e) {
            // la pile va sur l'erreur standard (logcat côté Android) :
            // le cadre ne porte que la nature et le message — et le
            // démon SURVIT : un Error (classe manquante, lien natif
            // cassé, injection de panne) doit coûter LE build, pas le
            // service. L'ancien « catch (Exception) » laissait filer
            // les Error : le fil d'acceptation mourait, la socket
            // tombait, et le client ne voyait qu'un « connexion
            // refusée » sans cause (rapport du 2026-11-17)
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
}
