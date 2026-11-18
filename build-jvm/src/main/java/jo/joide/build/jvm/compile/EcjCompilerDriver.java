package jo.joide.build.jvm.compile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.batch.FileSystem;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import jo.joide.platform.Problem;
import jo.joide.platform.Severity;

/**
 * Pilote du compilateur Java ecj (même artefact que le patcheur
 * :ecj-art de code-lsp, EPL-2.0) : compilation en processus, capture
 * exacte des classes écrites et des diagnostics structurés (code
 * numérique du problème, ligne et colonne).
 *
 * <p>L'API pilotée est celle du compilateur par lots, sans
 * javax.tools (absent du niveau 28) : le pilote reste donc le même
 * code sur JVM de bureau et sur ART (où l'artefact patché par
 * :ecj-art remplace le jar d'origine).</p>
 */
public final class EcjCompilerDriver implements CompilerDriver {

    private final String toolVersion;

    /** Construit le pilote et fige la version de l'outil. */
    public EcjCompilerDriver() {
        this.toolVersion = "ecj-" + detectVersion();
    }

    /**
     * Version lue dans le manifeste du jar réellement chargé (le
     * classpath peut contenir plusieurs manifestes : celui du jar
     * portant cette classe fait foi). Repli documenté si les classes
     * sont exposées hors jar.
     */
    private static String detectVersion() {
        try {
            java.net.URL location = EcjCompilerDriver.class
                    .getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                java.io.File file = new java.io.File(location.toURI());
                if (file.isFile()) {
                    try (java.util.jar.JarFile jar =
                            new java.util.jar.JarFile(file)) {
                        String version = jar.getManifest()
                                .getMainAttributes()
                                .getValue("Bundle-Version");
                        if (version != null && !version.isEmpty()) {
                            return version;
                        }
                    }
                }
            }
        } catch (RuntimeException | java.io.IOException
                | java.net.URISyntaxException e) {
            // repli ci-dessous
        }
        return "3.40.0";
    }

    @Override
    public String toolVersion() {
        return toolVersion;
    }

    @Override
    public CompileResult compile(CompileRequest request) {
        List<Path> classpath = request.classpath;
        List<FileSystem.Classpath> entries = new ArrayList<>(
                classpath.size() + 1);
        // Plateforme Android (android.jar dans le classpath, posé par
        // le modèle) : ECJ reste NON-modulaire et lie java.lang.* aux
        // OCTETS du jar de plateforme — jamais à l'image jrt de la VM
        // hôte (la manière CodeAssist, ImageFreeJavaCompiler : à
        // compliance ≥ 9 le front-end batch refuse -bootclasspath
        // android.jar, un jar non modulaire n'est pas un --system
        // valide, et ART n'a PAS de jimage — le sondage jrt sur
        // java.home=/apex/com.android.art levait
        // « IllegalStateException: Failed to init packages for
        // /apex/com.android.art », le terrain du 2026-11-17 sur
        // app:generateRJar).
        boolean plateformeAndroid = classpath.stream().anyMatch(
                p -> p.getFileName().toString().equals("android.jar"));
        if (!plateformeAndroid) {
            // Module JVM pur (bureau) : le jrt du JDK courant fournit
            // java.lang.Object. Le sondage est encadré : sur ART sans
            // jar de plateforme, son échec laisse le classpath seul
            // répondre (diagnostics propres — Object non résolu — pas
            // un crash de tâche).
            try {
                String javaHome = System.getProperty("java.home");
                if (javaHome != null) {
                    FileSystem.Classpath jrt = FileSystem.getJrtClasspath(
                            javaHome, request.encoding, null,
                            new java.util.HashMap<>());
                    if (jrt != null) {
                        entries.add(jrt);
                    }
                }
            } catch (RuntimeException e) {
                // pas de jrt ici (ART sans android.jar) : voir le
                // commentaire ci-dessus
            }
        }
        for (Path entry : classpath) {
            FileSystem.Classpath resolved = FileSystem.getClasspath(
                    entry.toString(), request.encoding, null);
            if (resolved != null) {
                entries.add(resolved);
            } else {
                return new CompileResult(false, List.of(Problem.error(
                        "classpath-entry-error",
                        "classpath entry does not exist: " + entry)),
                        List.of());
            }
        }
        List<CompilationUnit> units = new ArrayList<>(
                request.sources.size());
        Map<String, String> sourceTexts =
                new java.util.HashMap<>();
        for (Path source : request.sources) {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(source);
            } catch (IOException e) {
                return new CompileResult(false, List.of(Problem.error(
                        "source-read-error",
                        "cannot read source file " + source
                                + ": " + e.getMessage())),
                        List.of());
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            sourceTexts.put(source.toString(), text);
            units.add(new CompilationUnit(text.toCharArray(),
                    source.toString(), request.encoding));
        }
        List<Problem> diagnostics = new ArrayList<>();
        TreeSet<String> written = new TreeSet<>();
        IErrorHandlingPolicy policy = new IErrorHandlingPolicy() {
            @Override
            public boolean proceedOnErrors() {
                return true; // collecter tous les diagnostics
            }

            @Override
            public boolean stopOnFirstError() {
                return false;
            }

            @Override
            public boolean ignoreAllErrors() {
                return false;
            }
        };
        Map<String, String> options = new java.util.HashMap<>();
        options.put(CompilerOptions.OPTION_Source, request.sourceLevel);
        options.put(CompilerOptions.OPTION_TargetPlatform,
                request.sourceLevel);
        options.put(CompilerOptions.OPTION_Compliance,
                request.sourceLevel);
        options.put(CompilerOptions.OPTION_Encoding, request.encoding);
        CompilerOptions compilerOptions = new CompilerOptions(options);
        compilerOptions.docCommentSupport = false;

        ICompilerRequestor requestor = result -> {
            CategorizedProblem[] problems = result.getProblems();
            if (problems != null) {
                for (CategorizedProblem problem : problems) {
                    diagnostics.add(toProblem(problem, sourceTexts));
                }
            }
            ClassFile[] classFiles = result.getClassFiles();
            if (classFiles == null) {
                return;
            }
            for (ClassFile classFile : classFiles) {
                String relative = fileNameOf(classFile);
                if (relative == null) {
                    continue;
                }
                try {
                    Path target = request.outputDir.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.write(target, classFile.getBytes());
                    written.add(relative);
                } catch (IOException e) {
                    diagnostics.add(Problem.error("class-write-error",
                            "cannot write class " + relative + ": "
                                    + e.getMessage()));
                }
            }
        };

        try {
            FileSystem nameEnvironment = new AccessibleFileSystem(
                    entries.toArray(new FileSystem.Classpath[0]));
            try {
                Compiler compiler = new Compiler(nameEnvironment, policy,
                        compilerOptions, requestor,
                        new DefaultProblemFactory(Locale.ROOT));
                compiler.compile(units.toArray(
                        new CompilationUnit[0]));
            } finally {
                nameEnvironment.cleanup();
            }
        } catch (RuntimeException e) {
            return new CompileResult(false, List.of(Problem.error(
                    "compiler-crash",
                    "java compiler crashed: " + e)),
                    List.of());
        }
        boolean ok = diagnostics.stream().noneMatch(
                p -> p.severity() == Severity.ERROR);
        return new CompileResult(ok, diagnostics,
                new ArrayList<>(written));
    }

    /** Ouvre le constructeur protégé (entrées déjà résolues). */
    private static final class AccessibleFileSystem extends FileSystem {
        AccessibleFileSystem(FileSystem.Classpath[] entries) {
            super(entries, new String[0], false);
        }
    }
    private static Problem toProblem(CategorizedProblem problem,
                                     Map<String, String> sources) {
        String file = String.valueOf(problem.getOriginatingFileName());
        int line = problem.getSourceLineNumber();
        int column = columnOf(problem, sources.get(file));
        String where = file + ":" + line + ":" + column;
        Problem diagnostic;
        if (problem.isError()) {
            diagnostic = Problem.error("ecj." + problem.getID(),
                    problem.getMessage());
        } else if (problem.isWarning()) {
            diagnostic = Problem.warn("ecj." + problem.getID(),
                    problem.getMessage());
        } else {
            diagnostic = Problem.info("ecj." + problem.getID(),
                    problem.getMessage());
        }
        return diagnostic.at(where);
    }

    /**
     * Colonne calculée depuis le décalage de début du problème : ecj
     * expose la position, pas la colonne.
     */
    private static int columnOf(CategorizedProblem problem, String text) {
        if (text == null) {
            return 1;
        }
        int start = problem.getSourceStart();
        if (start < 0 || start >= text.length()) {
            return 1;
        }
        int lineStart = text.lastIndexOf('\n', start) + 1;
        return start - lineStart + 1;
    }

    private static String fileNameOf(ClassFile classFile) {
        String name = new String(classFile.fileName());
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (!name.endsWith(".class")) {
            name = name + ".class";
        }
        return name;
    }
}
