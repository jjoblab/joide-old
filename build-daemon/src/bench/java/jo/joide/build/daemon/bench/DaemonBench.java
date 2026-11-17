package jo.joide.build.daemon.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.build.daemon.bench.BenchProjectGenerator.GeneratedModule;
import jo.joide.build.daemon.client.DaemonClient;
import jo.joide.build.daemon.protocol.BuildSpec;

/**
 * Banc du démon : preuves de mémoire et de latence. Génère un projet
 * au gabarit du tier demandé (mêmes comptes que tools-bench), le
 * construit dans un démon au tas plafonné, mesure — salutation,
 * aller-retour de ping, build à froid (temps mur et pic de tas),
 * build sans changement à chaud — et échoue si le pic de tas dépasse
 * le plafond ou si un build échoue.
 *
 * <p>Exécution : {@code ./gradlew :build-daemon:daemonBench -Ptier=L}
 * (S par défaut). Le tas du démon suit la propriété
 * {@code daemonHeapMb} (700 par défaut — le critère du tier L).</p>
 */
public final class DaemonBench {

    private DaemonBench() {
    }

    /**
     * @param args tier (S/M/L/XL), répertoire de travail, tas du
     *             démon en Mo (700 par défaut)
     * @throws Exception si le banc échoue
     */
    public static void main(String[] args) throws Exception {
        DaemonTier tier = DaemonTier.parse(
                args.length > 0 ? args[0] : "S");
        Path work = Path.of(args.length > 1 ? args[1]
                : "build/daemon-bench/" + tier.name().toLowerCase());
        int daemonHeapMb = args.length > 2
                ? Integer.parseInt(args[2]) : 700;
        int buildHeapMb = Math.max(320, daemonHeapMb - 20);

        Path project = work.resolve("projet");
        Path out = work.resolve("out");
        Path m2 = Files.createDirectories(work.resolve("m2"));
        Files.createDirectories(work);

        System.out.println("=== banc du démon — tier "
                + tier.name() + " ===");
        System.out.println("modules=" + tier.modules
                + " fichiers=" + tier.totalFiles
                + " couches=" + tier.layers
                + " hubs=" + tier.hubs
                + " tas du démon=" + daemonHeapMb + " Mo");

        long generationStart = System.nanoTime();
        List<GeneratedModule> modules =
                new BenchProjectGenerator(tier).generate(project);
        long generationMillis = (System.nanoTime() - generationStart)
                / 1_000_000;
        System.out.println("génération : " + generationMillis
                + " ms (" + tier.totalFiles + " fichiers)");

        BuildSpec spec = specOf(tier, project, out, m2, modules,
                buildHeapMb);

        long spawnStart = System.nanoTime();
        try (DaemonClient daemon = DaemonClient.spawn(
                Path.of(DaemonClient.currentClasspath()),
                "jo.joide.build.daemon.desktop.DaemonMain",
                daemonHeapMb)) {
            DaemonClient.Event hello = daemon.await("hello", 120);
            long helloMillis = (System.nanoTime() - spawnStart)
                    / 1_000_000;
            System.out.println("salutation : " + helloMillis
                    + " ms (lancement machine virtuelle + poignée de "
                    + "main), tas max "
                    + hello.body.member("maxHeapBytes").asLong()
                            / (1024 * 1024) + " Mo");

            // aller-retours de ping : la latence du protocole seul
            List<Long> rtts = new ArrayList<>();
            for (int ping = 0; ping < 50; ping++) {
                long start = System.nanoTime();
                daemon.ping(1000 + ping);
                daemon.await("pong", 30);
                rtts.add((System.nanoTime() - start) / 1_000_000);
            }
            System.out.println("ping : médiane " + median(rtts)
                    + " ms, max " + rtts.stream().max(Long::compare)
                            .orElse(0L) + " ms (50 aller-retours)");

            // build à froid : temps mur, pic de tas
            System.out.println("build à froid…");
            long coldStart = System.nanoTime();
            DaemonClient.Event cold = buildAndWait(daemon, 1, spec);
            long coldMillis = (System.nanoTime() - coldStart)
                    / 1_000_000;
            require(cold.body.member("success").asBoolean(),
                    "build à froid échoué : " + cold);
            long peakHeapBytes = cold.body.member("peakHeapBytes")
                    .asLong();
            System.out.println("build à froid : " + coldMillis
                    + " ms, " + cold.body.member("executed").asLong()
                    + " tâches, pic de tas "
                    + peakHeapBytes / (1024 * 1024) + " Mo ("
                    + peakHeapBytes + " octets), compilateurs chauds "
                    + cold.body.member("hotCompilers").asLong());

            // build sans changement à chaud : la latence du « rien à
            // faire » — celle que l'IDE paie à chaque sauvegarde
            long hotStart = System.nanoTime();
            DaemonClient.Event hot = buildAndWait(daemon, 2, spec);
            long hotMillis = (System.nanoTime() - hotStart)
                    / 1_000_000;
            require(hot.body.member("success").asBoolean(),
                    "build à chaud échoué : " + hot);
            require(hot.body.member("executed").asLong() == 0,
                    "le build sans changement doit être sans travail : "
                            + hot);
            System.out.println("build sans changement : " + hotMillis
                    + " ms, zéro tâche exécutée, "
                    + hot.body.member("upToDate").asLong()
                    + " à jour, compilateurs chauds "
                    + hot.body.member("hotCompilers").asLong());
            require(hot.body.member("hotCompilers").asLong() == 1,
                    "la fabrique doit être réutilisée (compilateurs "
                            + "chauds)");

            // LA preuve de mémoire : le pic reste sous le plafond du
            // démon (le critère du tier L : 700 Mo)
            long limitBytes = daemonHeapMb * 1024L * 1024L;
            require(peakHeapBytes <= limitBytes,
                    "pic de tas " + peakHeapBytes + " > plafond "
                            + limitBytes);
            System.out.println("PREUVE : pic de tas "
                    + peakHeapBytes / (1024 * 1024) + " Mo ≤ "
                    + daemonHeapMb + " Mo au tier " + tier.name());

            daemon.shutdown();
            System.out.println("=== banc du démon : OK ===");
        }
    }

    private static BuildSpec specOf(DaemonTier tier, Path project,
                                    Path out, Path m2,
                                    List<GeneratedModule> modules,
                                    int heapMb) {
        List<BuildSpec.ModuleSpec> specs = new ArrayList<>();
        for (GeneratedModule module : modules) {
            specs.add(new BuildSpec.ModuleSpec(module.name, false,
                    List.of(module.name + "/src/main/java"),
                    List.of(), null, null, module.dependencies,
                    List.of(), List.of()));
        }
        return new BuildSpec(BuildSpec.Kind.JVM,
                project.toString(), out.toString(), m2.toString(),
                "17", 4, heapMb, specs, List.of());
    }

    private static DaemonClient.Event buildAndWait(DaemonClient daemon,
                                                   long id,
                                                   BuildSpec spec)
            throws IOException {
        daemon.build(id, spec);
        while (true) {
            DaemonClient.Event event = daemon.next(1800);
            if ("build-finished".equals(event.type)) {
                return event;
            }
        }
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        return sorted.get(sorted.size() / 2);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
