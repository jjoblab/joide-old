package jo.joide.build.android.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.origin.Origin;

import jo.joide.build.android.dex.Dexer;

/**
 * Dexeur en processus : D8 et R8 chargés par le classloader du build,
 * sans fork — le coût de démarrage n'est payé qu'une fois et les
 * compilateurs restent chauds entre les dexages. Réservé aux petites
 * et moyennes entrées (le pic de tas reste dans la machine virtuelle
 * du build) ; les grosses fusions passent par le fork à gros tas.
 */
public final class InProcessDexer implements Dexer {

    private final String version;

    /** Charge le dexeur et lit sa version une fois pour toutes. */
    public InProcessDexer() {
        this.version = "r8-in-process:"
                + com.android.tools.r8.Version.getVersionString();
    }

    @Override
    public void dex(DexRequest request) throws IOException {
        CollectingDiagnostics diagnostics = new CollectingDiagnostics();
        try {
            D8Command.Builder builder = D8Command.builder(diagnostics)
                    .setMode(CompilationMode.RELEASE)
                    .setMinApiLevel(request.minApi)
                    .addProgramResourceProvider(
                            programProvider(request.program))
                    .setOutput(request.outputDir, OutputMode.DexIndexed);
            for (Path lib : request.classpath) {
                if (lib.getFileName() != null && lib.getFileName().toString()
                        .endsWith(".dex")) {
                    continue;
                }
                builder.addLibraryFiles(lib);
            }
            D8.run(builder.build());
        } catch (com.android.tools.r8.CompilationFailedException e) {
            throw new IOException("D8 failed: " + diagnostics.messages(),
                    e);
        }
        requireDex(request.outputDir);
    }

    @Override
    public void merge(MergeRequest request) throws IOException {
        CollectingDiagnostics diagnostics = new CollectingDiagnostics();
        try {
            D8Command.Builder builder = D8Command.builder(diagnostics)
                    .setMode(CompilationMode.RELEASE)
                    .setMinApiLevel(request.minApi)
                    .addProgramResourceProvider(
                            programProvider(request.dexFiles))
                    .setOutput(request.outputDir, OutputMode.DexIndexed);
            for (Path lib : request.classpath) {
                if (lib.getFileName() != null && lib.getFileName().toString()
                        .endsWith(".dex")) {
                    continue;
                }
                builder.addLibraryFiles(lib);
            }
            D8.run(builder.build());
        } catch (com.android.tools.r8.CompilationFailedException e) {
            throw new IOException("D8 merge failed: "
                    + diagnostics.messages(), e);
        }
        requireDex(request.outputDir);
    }

    @Override
    public void shrink(ShrinkRequest request) throws IOException {
        CollectingDiagnostics diagnostics = new CollectingDiagnostics();
        try {
            R8Command.Builder builder = R8Command.builder(diagnostics)
                    .setMode(CompilationMode.RELEASE)
                    .setMinApiLevel(request.minApi)
                    .addProgramResourceProvider(
                            programProvider(request.program))
                    .addLibraryFiles(request.classpath)
                    .addProguardConfiguration(request.keepRules, Origin.root())
                    .setOutput(request.outputDir, OutputMode.DexIndexed);
            if (request.mappingOut != null) {
                builder.setProguardMapOutputPath(request.mappingOut);
            }
            R8.run(builder.build());
        } catch (com.android.tools.r8.CompilationFailedException e) {
            throw new IOException("R8 failed: " + diagnostics.messages(), e);
        }
        requireDex(request.outputDir);
    }

    @Override
    public String toolVersion() {
        return version;
    }

    /**
     * Fournisseur de ressources programme : les répertoires de
     * classes sont balayés en fichiers .class, les archives passent
     * par le fournisseur dédié du jar.
     */
    private static com.android.tools.r8.ProgramResourceProvider programProvider(
            List<Path> entries) throws IOException {
        List<com.android.tools.r8.ProgramResource> resources =
                new ArrayList<>();
        List<Path> archives = new ArrayList<>();
        for (Path entry : entries) {
            if (java.nio.file.Files.isDirectory(entry)) {
                try (var walk = java.nio.file.Files.walk(entry)) {
                    walk.filter(java.nio.file.Files::isRegularFile)
                            .filter(f -> f.toString().endsWith(".class"))
                            .sorted()
                            .forEach(f -> resources.add(
                                    com.android.tools.r8.ProgramResource
                                            .fromFile(
                                                    com.android.tools.r8.ProgramResource.Kind.CF,
                                                    f)));
                }
            } else if (entry.toString().endsWith(".dex")) {
                resources.add(com.android.tools.r8.ProgramResource.fromFile(
                        com.android.tools.r8.ProgramResource.Kind.DEX, entry));
            } else if (entry.toString().endsWith(".class")) {
                resources.add(com.android.tools.r8.ProgramResource.fromFile(
                        com.android.tools.r8.ProgramResource.Kind.CF, entry));
            } else {
                archives.add(entry);
            }
        }
        List<com.android.tools.r8.ProgramResourceProvider> providers =
                new ArrayList<>();
        for (Path archive : archives) {
            providers.add(com.android.tools.r8.ArchiveProgramResourceProvider
                    .fromArchive(archive));
        }
        return new com.android.tools.r8.ProgramResourceProvider() {
            @Override
            @SuppressWarnings("deprecation")
            public java.util.Collection<com.android.tools.r8.ProgramResource>
                    getProgramResources() throws com.android.tools.r8.ResourceException {
                List<com.android.tools.r8.ProgramResource> all =
                        new ArrayList<>(resources);
                for (com.android.tools.r8.ProgramResourceProvider provider
                        : providers) {
                    provider.getProgramResources(all::add);
                }
                return all;
            }

            @Override
            public void getProgramResources(
                    java.util.function.Consumer<
                            com.android.tools.r8.ProgramResource> consumer)
                    throws com.android.tools.r8.ResourceException {
                resources.forEach(consumer);
                for (com.android.tools.r8.ProgramResourceProvider provider
                        : providers) {
                    provider.getProgramResources(consumer);
                }
            }
        };
    }

    private static void requireDex(Path outputDir) throws IOException {
        if (!Files.isDirectory(outputDir)) {
            throw new IOException("dex output missing: " + outputDir);
        }
        try (var stream = Files.list(outputDir)) {
            if (stream.noneMatch(p -> p.getFileName() != null
                    && p.getFileName().toString().endsWith(".dex"))) {
                throw new IOException("no dex produced in " + outputDir);
            }
        }
    }

    /** Collecte des diagnostics de compilation pour les messages. */
    private static final class CollectingDiagnostics
            implements DiagnosticsHandler {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void warning(Diagnostic warning) {
            synchronized (lines) {
                lines.add("warning: " + warning.getDiagnosticMessage());
            }
        }

        @Override
        public void error(Diagnostic error) {
            synchronized (lines) {
                lines.add("error: " + error.getDiagnosticMessage());
            }
        }

        @Override
        public void info(Diagnostic info) {
            synchronized (lines) {
                lines.add("info: " + info.getDiagnosticMessage());
            }
        }

        String messages() {
            synchronized (lines) {
                return String.join("\n", lines);
            }
        }
    }
}
