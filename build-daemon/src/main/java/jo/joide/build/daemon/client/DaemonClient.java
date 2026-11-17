package jo.joide.build.daemon.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.protocol.Frames;
import jo.joide.build.daemon.protocol.Json;
import jo.joide.deps.json.JsonValue;

/**
 * Client du démon de build : lance la machine virtuelle fille, échange
 * les cadres du protocole et expose les événements en file bloquante.
 * La mort du démon (OOM, kill) est un événement comme les autres : le
 * client survit toujours.
 */
public final class DaemonClient implements AutoCloseable {

    /** Un événement reçu du démon. */
    public static final class Event {
        /** Type d'événement (« hello », « build-finished »…). */
        public final String type;
        /** Identifiant de requête lié, ou -1. */
        public final long id;
        /** Champs de l'événement. */
        public final JsonValue body;

        Event(String type, long id, JsonValue body) {
            this.type = type;
            this.id = id;
            this.body = body;
        }

        /** @return le champ texte « message » ou vide */
        public String message() {
            String value = body.member("message").asString();
            return value == null ? "" : value;
        }

        @Override
        public String toString() {
            return "Event[" + type + " #" + id + " " + body + "]";
        }
    }

    /** Le démon est mort : le client survit. */
    public static final class DaemonDiedException extends IOException {
        private static final long serialVersionUID = 1L;

        DaemonDiedException(String message) {
            super(message);
        }
    }

    /** Délai d'attente par défaut des événements. */
    public static final long DEFAULT_TIMEOUT_SECONDS = 900;

    private final Process process;
    private final Frames frames;
    /** Flux du transport par socket (ADR-043) ; null au lancement
     *  d'une machine virtuelle fille. */
    private final InputStream entree;
    private final OutputStream sortie;
    private final BlockingQueue<Event> events =
            new ArrayBlockingQueue<>(1024);
    private final Thread reader;
    private volatile boolean closed;

    private DaemonClient(Process process, Frames frames,
                         InputStream entree, OutputStream sortie) {
        this.process = process;
        this.frames = frames;
        this.entree = entree;
        this.sortie = sortie;
        this.reader = new Thread(this::readLoop, "joide-daemon-client");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    /**
     * Lance un démon.
     *
     * @param classpath classpath de la machine virtuelle fille
     * @param mainClass point d'entrée du démon
     * @param heapMb    plafond de tas de la fille (« -Xmx »)
     * @param args      arguments supplémentaires de la machine virtuelle
     * @return le client connecté (handshake « hello » à attendre)
     * @throws IOException si le lancement échoue
     */
    public static DaemonClient spawn(Path classpath, String mainClass,
                                     int heapMb, String... args)
            throws IOException {
        // java.home plutôt que ProcessHandle (absent du niveau 28) :
        // le lancement d'une machine virtuelle fille est un geste de
        // bureau, mais le client voyage dans l'application Android
        String java = Path.of(System.getProperty("java.home"),
                "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-Xmx" + heapMb + "m");
        for (String arg : args) {
            command.add(arg);
        }
        command.add("-cp");
        command.add(classpath.toString());
        command.add(mainClass);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Process process = builder.start();
        // les erreurs standard de la fille sont relayées sur les
        // nôtres : un OOM y sera visible, le protocole ne les porte pas
        Thread stderrRelay = new Thread(() -> {
            try (var in = process.getErrorStream()) {
                in.transferTo(System.err);
            } catch (IOException e) {
                // la fille est morte : fin du relais
            }
        }, "joide-daemon-stderr");
        stderrRelay.setDaemon(true);
        stderrRelay.start();
        Frames frames = new Frames(process.getInputStream(),
                process.getOutputStream());
        return new DaemonClient(process, frames, null, null);
    }

    /**
     * Connecte un démon déjà hébergé sur un canal de flux (ADR-043 :
     * le service de build de l'application Android, socket locale).
     * La mort du démon est la fin du canal : EOF →
     * {@link DaemonDiedException}, le client survit.
     *
     * @param entree flux des cadres du démon
     * @param sortie flux des cadres vers le démon
     * @return le client connecté (handshake « hello » à attendre)
     */
    public static DaemonClient over(InputStream entree,
                                    OutputStream sortie) {
        Frames frames = new Frames(entree, sortie);
        return new DaemonClient(null, frames, entree, sortie);
    }

    /**
     * Envoie une requête de build.
     *
     * @param id   identifiant de requête
     * @param spec description du build
     * @throws IOException si l'écriture échoue ou que le démon est mort
     */
    public void build(long id, BuildSpec spec) throws IOException {
        send(Json.object(
                "type", "build",
                "id", id,
                "spec", spec.toJson()));
    }

    /**
     * Envoie une annulation de build.
     *
     * @param id identifiant de requête à annuler
     * @throws IOException si l'écriture échoue
     */
    public void cancel(long id) throws IOException {
        send(Json.object("type", "cancel", "id", id));
    }

    /**
     * Envoie un préchauffage (allocation de tas, toucher les pages).
     *
     * @param id    identifiant de requête
     * @param bytes octets à allouer et toucher
     * @throws IOException si l'écriture échoue
     */
    public void warmup(long id, long bytes) throws IOException {
        send(Json.object("type", "warmup", "id", id, "bytes", bytes));
    }

    /**
     * Envoie une salutation.
     *
     * @param id identifiant de requête
     * @throws IOException si l'écriture échoue
     */
    public void ping(long id) throws IOException {
        send(Json.object("type", "ping", "id", id));
    }

    /**
     * Demande une injection de panne (réservée aux machines virtuelles
     * lancées avec {@code -Djoide.daemon.faults=1} ; le démon de
     * production répond {@code crash-refused}).
     *
     * @param id   identifiant de requête
     * @param mode « oom »
     * @throws IOException si l'écriture échoue
     */
    public void crash(long id, String mode) throws IOException {
        send(Json.object("type", "crash", "id", id, "mode", mode));
    }

    /**
     * Demande l'arrêt propre du démon.
     *
     * @throws IOException si l'écriture échoue
     */
    public void shutdown() throws IOException {
        send(Json.object("type", "shutdown"));
    }

    private void send(String frame) throws IOException {
        if (closed) {
            throw new DaemonDiedException("daemon already closed");
        }
        try {
            frames.write(frame);
        } catch (IOException e) {
            throw new DaemonDiedException("daemon write failed: "
                    + e.getMessage());
        }
    }

    /**
     * Attend le prochain événement.
     *
     * @param timeoutSeconds délai d'attente
     * @return l'événement, jamais {@code null}
     * @throws IOException          si le délai expire
     * @throws DaemonDiedException  si le démon meurt en attendant
     */
    public Event next(long timeoutSeconds) throws IOException {
        Event event;
        try {
            event = events.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting", e);
        }
        if (event == null) {
            throw new IOException("timeout waiting for daemon event");
        }
        if ("daemon-dead".equals(event.type)) {
            throw new DaemonDiedException(event.message());
        }
        return event;
    }

    /**
     * Attend le prochain événement d'un type donné ; les autres sont
     * conservés.
     *
     * @param type           type attendu
     * @param timeoutSeconds délai
     * @return l'événement
     * @throws IOException si le délai expire ou que le démon meurt
     */
    public Event await(String type, long timeoutSeconds) throws IOException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        List<Event> skipped = new ArrayList<>();
        while (true) {
            long left = TimeUnit.NANOSECONDS.toSeconds(
                    deadline - System.nanoTime());
            if (left <= 0) {
                throw new IOException("timeout waiting for " + type);
            }
            Event event = next(left);
            if (type.equals(event.type)) {
                for (Event pending : skipped) {
                    events.add(pending);
                }
                return event;
            }
            skipped.add(event);
        }
    }

    /**
     * Attend la disparition complète du démon (collecte du code
     * de sortie) — la fermeture des flux peut précéder de quelques
     * instants la récolte du statut. Sur un transport par flux, la
     * disparition est la mort du fil de lecture (EOF).
     *
     * @param timeoutSeconds délai d'attente
     * @return le code de sortie du démon (0 sur transport par flux)
     * @throws IOException si le délai expire
     */
    public int awaitExit(long timeoutSeconds) throws IOException {
        try {
            if (process != null) {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw new IOException(
                            "daemon did not exit in time");
                }
                return process.exitValue();
            }
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (reader.isAlive()) {
                long restant = deadline - System.nanoTime();
                if (restant <= 0) {
                    throw new IOException(
                            "daemon did not exit in time");
                }
                reader.join(TimeUnit.NANOSECONDS.toMillis(restant) + 1L);
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting", e);
        }
    }

    /**
     * Tue le démon sans politesse (kill -9, ou fermeture du canal sur
     * transport par flux).
     */
    public void killNow() {
        if (process != null) {
            process.destroyForcibly();
            return;
        }
        fermerCanal();
    }

    /** @return vrai si le processus du démon est vivant (sur transport
     *         par flux : le canal est ouvert et le fil de lecture
     *         vivant) */
    public boolean alive() {
        if (process != null) {
            return process.isAlive();
        }
        return !closed && reader.isAlive();
    }

    @Override
    public void close() {
        closed = true;
        if (process != null) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        fermerCanal();
    }

    private void fermerCanal() {
        try {
            entree.close();
        } catch (IOException e) {
            // le canal se ferme : l'erreur de fermeture est muette
        }
        try {
            sortie.close();
        } catch (IOException e) {
            // idem côté écriture
        }
    }

    /**
     * @return le classpath d'exécution courant, transmis aux machines
     *         virtuelles filles : la propriété
     *         {@code joide.test.classpath} posée par la chaîne de
     *         build, sinon le classpath de la machine virtuelle
     * @throws IllegalStateException si aucun classpath exploitable
     */
    public static String currentClasspath() {
        String fromProperty = System.getProperty("joide.test.classpath");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromJvm = System.getProperty("java.class.path");
        if (fromJvm != null && !fromJvm.isBlank()) {
            return fromJvm;
        }
        throw new IllegalStateException(
                "no classpath available for child JVM");
    }

    private void readLoop() {
        try {
            while (!closed) {
                String frame = frames.read();
                jo.joide.deps.json.Json.Document document =
                        jo.joide.deps.json.Json.parse(frame);
                JsonValue root = document.root;
                String type = root.member("type").asString();
                long id = root.member("id").asLong();
                Event event = new Event(type == null ? "unknown" : type,
                        id < 0 ? -1 : id, root);
                if (!events.offer(event)) {
                    // file pleine : le client ne consomme pas
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!closed) {
                String message = "daemon died";
                if (process != null && !process.isAlive()) {
                    message = "daemon died (exit "
                            + process.exitValue() + ")";
                } else if (process == null) {
                    message = "daemon died (channel closed)";
                }
                events.offer(new Event("daemon-dead", -1,
                        jo.joide.deps.json.Json.parse(
                                Json.object("message", message)).root));
            }
        }
    }
}
