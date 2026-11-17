package jo.joide.build.daemon.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;
import jo.joide.build.android.sign.ApkSignerService;
import jo.joide.build.android.toolchain.Toolchain;
import jo.joide.build.android.toolchain.ToolchainLocator;
import jo.joide.build.android.tasks.AndroidModuleSpec;
import jo.joide.build.android.tasks.AndroidTaskFactory;
import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.build.jvm.compile.KotlinCompilerDriver;
import jo.joide.build.jvm.tasks.JvmTaskFactory;
import jo.joide.platform.CancelToken;
import jo.joide.platform.Clock;
import jo.joide.platform.Problem;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.store.BuildStore;
import jo.joide.tasks.BuildEngine;
import jo.joide.tasks.BuildEvents;
import jo.joide.tasks.BuildReport;
import jo.joide.tasks.ResourceBudget;
import jo.joide.tasks.SchedulingMode;
import jo.joide.tasks.TaskGraph;
import jo.joide.tasks.TaskResult;
import jo.joide.tasks.TaskStatus;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.TreeScanner;
import jo.joide.vfs.VfsSnapshot;

/**
 * Exécuteur de builds du démon, équivalent en processus : construit
 * le graphe (pipeline JVM ou Android selon la description), l'exécute
 * sur le moteur complet et publie les événements de tâches au fur et
 * à mesure. Le même exécuteur tourne dans le démon (compilateurs
 * chauds : une seule instance par démon, les fabriques et le dexeur
 * en processus sont créés une fois et réutilisés) et directement dans
 * la machine virtuelle du client pour le mode équivalent sans
 * processus.
 */
public final class BuildRunner implements AutoCloseable {

    /** Sortie d'un build terminé. */
    public static final class Outcome {
        /** Le build a réussi. */
        public final boolean success;
        /** Tâches réellement exécutées. */
        public final int executed;
        /** Tâches restaurées du cache. */
        public final int fromCache;
        /** Tâches à jour. */
        public final int upToDate;
        /** Pic de tas du build en octets. */
        public final long peakHeapBytes;
        /** Durée du build en millisecondes. */
        public final long wallMillis;
        /** Problèmes des tâches échouées. */
        public final List<String> problems;
        /** Le build a été annulé. */
        public final boolean cancelled;

        Outcome(boolean success, int executed, int fromCache, int upToDate,
                long peakHeapBytes, long wallMillis, List<String> problems,
                boolean cancelled) {
            this.success = success;
            this.executed = executed;
            this.fromCache = fromCache;
            this.upToDate = upToDate;
            this.peakHeapBytes = peakHeapBytes;
            this.wallMillis = wallMillis;
            this.problems = List.copyOf(problems);
            this.cancelled = cancelled;
        }
    }

    /** Récepteur d'événements de tâches, appelé en direct pendant
     *  l'exécution : chaque démarrage (statut « STARTED ») et chaque
     *  fin (statut final) est publié dès qu'il se produit. */
    public interface EventSink {
        /**
         * @param name    nom de la tâche
         * @param status  « STARTED » ou statut final
         */
        void task(String name, String status);
    }

    /** Fabrique de store : le démon de bureau ouvre SQLite, le mode
     *  en processus peut fournir ce qu'il veut. */
    public interface StoreOpener {
        /**
         * @param outDir répertoire des sorties du build
         * @return le store ouvert pour ce build
         * @throws IOException si l'ouverture échoue
         */
        BuildStore open(Path outDir) throws IOException;
    }

    private static final AtomicLong FACTORY_INSTANCES = new AtomicLong();
    private static final AtomicLong DEXER_INSTANCES = new AtomicLong();

    private final StoreOpener storeOpener;
    private final ExecutorService pool;
    private final AtomicReference<CancelToken> currentCancellation =
            new AtomicReference<>();
    private final JvmHolder jvmHolder = new JvmHolder();
    private final AndroidHolder androidHolder = new AndroidHolder();
    private volatile CompilerDriver kotlinDriverResolu;

    /**
     * Les compilateurs restent chauds : la fabrique du pipeline JVM
     * (et son pilote ecj) est créée une fois par espace de travail et
     * réutilisée — reconstruire le même projet ne paie aucune
     * initialisation.
     */
    private static final class JvmHolder {
        private String key;
        private JvmTaskFactory factory;

        JvmTaskFactory factoryFor(BuildSpec spec) {
            String next = spec.out + "|" + spec.m2 + "|"
                    + spec.sourceLevel;
            if (factory == null || !next.equals(key)) {
                FACTORY_INSTANCES.incrementAndGet();
                factory = new JvmTaskFactory(Path.of(spec.out),
                        Path.of(spec.m2), spec.sourceLevel);
                key = next;
            }
            return factory;
        }
    }

    private static final class AndroidHolder {
        Dexer scopeDexer;
        Dexer mergeDexer;
        ApkSignerService signer;
        BundleAssembler assembler;

        void ensure() {
            if (scopeDexer == null) {
                DEXER_INSTANCES.incrementAndGet();
                scopeDexer = outil(Dexer.class,
                        "jo.joide.build.android.tools.InProcessDexer");
                // lot U : le fork à gros tas n'existe que là où une
                // JVM fille existe (bureau) — sur APPAREIL, la fusion
                // passe par le dexeur en processus embarqué
                Path r8 = ToolchainLocator.r8FromClasspath();
                mergeDexer = r8 != null && javaExecutablePresente()
                        ? new jo.joide.build.android.dex.ForkDexer(
                                r8, "1g", 900) : scopeDexer;
                signer = outil(ApkSignerService.class,
                        "jo.joide.build.android.tools.ApksigSigner");
                assembler = outil(BundleAssembler.class,
                        "jo.joide.build.android.tools"
                                + ".BundletoolAssembler");
            }
        }

        /**
         * L'outil ServiceLoader d'abord (bureau : les adaptateurs et
         * leurs jars vivent sur le classpath) ; sinon le constructeur
         * sans argument se charge par réflexion, enfin le pack DIFFÉRÉ
         * en dernier recours : un APK allégé charge l'adaptateur depuis
         * un pack de dex téléchargé une fois (PacksOutils).
         *
         * <p>Lot VIII (ADR-070) : les causes VOYAGENT dans le message —
         * le rapport du 2026-11-17 montrait l'InvocationTargetException
         * de l'embarqué pendant que la cause réelle du pack se perdait
         * dans stderr (logcat sur l'appareil). Un
         * InvocationTargetException s'aplatit vers sa cible : « java.lang.
         * reflect.InvocationTargetException » ne dit rien, « NoClassDefFound
         * Error: com/android/tools/r8/Version » dit tout.</p>
         */
        private static <T> T outil(Class<T> type, String nomClasse) {
            try {
                T charge = ServiceLoader.load(type).findFirst()
                        .orElse(null);
                if (charge != null) {
                    return charge;
                }
            } catch (Throwable ignore) {
                // la lecture des services peut manquer : la
                // réflexion reste la route de l'appareil
            }
            Throwable rupture = null;
            try {
                Class<?> classe = Class.forName(nomClasse);
                return type.cast(
                        classe.getDeclaredConstructor().newInstance());
            } catch (Throwable e) {
                // APK allégé : l'adaptateur n'y vit plus (lot VIII) —
                // le pack différé est LA voie de l'appareil
                rupture = e;
            }
            try {
                T duPack = outilDuPack(type, nomClasse);
                if (duPack != null) {
                    return duPack;
                }
                throw new IllegalStateException(
                        "embedded build tool unavailable: " + nomClasse
                                + " — embarqué : " + causeApplatie(rupture)
                                + " ; aucun pack ne porte ce service :"
                                + " l'adaptateur doit vivre dans l'APK",
                        rupture);
            } catch (IOException echecPack) {
                if (rupture != null) {
                    echecPack.addSuppressed(rupture);
                }
                throw new IllegalStateException(
                        "embedded build tool unavailable: " + nomClasse
                                + " — embarqué : "
                                + causeApplatie(rupture)
                                + " ; " + echecPack.getMessage(),
                        echecPack);
            }
        }

        /**
         * La cause MONTRÉE : un InvocationTargetException ne dit rien
         * de sa cible — le message porte la cible aplatie, la chaîne
         * d'origine reste entière dans la cause de l'exception.
         */
        private static Throwable causeApplatie(Throwable t) {
            Throwable courante = t;
            for (int i = 0; i < 5 && courante != null; i++) {
                if (courante instanceof
                        java.lang.reflect.InvocationTargetException) {
                    Throwable cible = courante.getCause();
                    if (cible != null && cible != courante) {
                        courante = cible;
                        continue;
                    }
                }
                break;
            }
            return courante;
        }

        /**
         * L'adaptateur d'un pack différé : téléchargé une fois puis
         * chargé PAR NOM dans le chargeur du pack — jamais
         * ServiceLoader ici : le chargeur PARENT porterait aussi les
         * fichiers de services. Le mur de chargeurs reste étanche :
         * l'adaptateur n'expose que l'interface SPI du parent.
         *
         * <p>Lot VIII (ADR-070) : plus AUCUNE copie embarquée des
         * adaptateurs packés dans l'APK — la délégation parent-d'abord
         * du DexClassLoader servait la copie de l'APK (sans ses
         * classes d'outil) et le pack ne chargeait JAMAIS (rapport du
         * 2026-11-17 : « pack différé r8 9.5.32 indisponible
         * (java.lang.reflect.InvocationTargetException) »). L'échec
         * du pack se LÈVE avec sa cause — plus de retour null muet.</p>
         *
         * @return l'adaptateur, ou {@code null} si AUCUN pack ne
         *         porte ce service (apksig : l'embarqué est requis)
         * @throws IOException le pack existe mais échoue :
         *                     téléchargement, empreinte, pont ou
         *                     chargement — la cause voyage
         */
        private static <T> T outilDuPack(Class<T> type,
                                          String nomClasse)
                throws IOException {
            jo.joide.build.android.toolchain.PacksOutils.Pack pack =
                    jo.joide.build.android.toolchain.PacksOutils
                            .packDe(type);
            if (pack == null) {
                return null;
            }
            try {
                ClassLoader chargeur =
                        jo.joide.build.android.toolchain.PacksOutils
                                .chargeurDe(pack);
                Class<?> classe = Class.forName(nomClasse, true,
                        chargeur);
                return type.cast(
                        classe.getDeclaredConstructor().newInstance());
            } catch (IOException e) {
                throw new IOException("pack " + pack + " : "
                        + e.getMessage(), e);
            } catch (ReflectiveOperationException e) {
                throw new IOException("pack " + pack + " : adaptateur "
                        + nomClasse + " : " + causeApplatie(e), e);
            }
        }

        /** @return vrai si un exécutable java est à portée de fork */
        private static boolean javaExecutablePresente() {
            String home = System.getProperty("java.home", "");
            return !home.isBlank()
                    && java.nio.file.Files.isRegularFile(
                    java.nio.file.Path.of(home, "bin", "java"));
        }
    }

    /**
     * @param storeOpener fabrique de store par build
     * @param threads     fils de construction
     */
    public BuildRunner(StoreOpener storeOpener, int threads) {
        this.storeOpener = storeOpener;
        // fils démons : la boucle du démon est le seul fil de vie —
        // une erreur non rattrapée (OOM) tue la boucle, la machine
        // virtuelle doit mourir avec elle, pas zombifier derrière des
        // fils de travail au repos
        this.pool = Executors.newFixedThreadPool(Math.max(1, threads),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "joide-daemon-build");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** @return le nombre de fabriques de pipeline créées depuis le
     *         démarrage (preuve : constant entre les builds du même
     *         espace — compilateurs chauds) */
    public static long compilerInstances() {
        return FACTORY_INSTANCES.get();
    }

    /** @return le nombre de dexeurs en processus créés depuis le
     *         démarrage (preuve : constant entre les builds Android) */
    public static long dexerInstances() {
        return DEXER_INSTANCES.get();
    }

    /**
     * Le budget d'admission EFFECTIF d'un tas demandé : plafonné au
     * tas réel de la machine virtuelle qui exécute le build — le
     * budget est un plafond d'usage, jamais un motif de refus (lot
     * U/7 : sur appareil le tas vaut souvent 256 Mio, un budget
     * demandé au-dessus s'ajuste au lieu de fermer la porte).
     *
     * @param demandeMb le budget demandé par la spec (Mio)
     * @return le budget effectif (Mio), jamais supérieur au tas
     */
    public static int budgetEffectif(int demandeMb) {
        long maxHeap = Runtime.getRuntime().maxMemory();
        long budgetBytes = demandeMb * 1024L * 1024L;
        if (budgetBytes <= maxHeap) {
            return demandeMb;
        }
        return (int) Math.max(1, maxHeap / 1048576L);
    }

    /**
     * Exécute le build décrit.
     *
     * @param spec        description du build
     * @param dirty       ensembles sales par racine (peut être vide :
     *                    le démon rescannera)
     * @param sink        récepteur d'événements, peut être {@code null}
     * @param cancellation jeton d'annulation du build
     * @return le résultat synthétique
     * @throws IOException si la préparation échoue
     */
    public Outcome run(BuildSpec spec,
                       Map<RootName, DirtySet> dirty,
                       EventSink sink,
                       CancelToken cancellation) throws IOException {
        long started = System.currentTimeMillis();
        RootSet roots = rootsOf(spec);
        TaskGraph graph = graphOf(spec, roots);
        long maxHeap = Runtime.getRuntime().maxMemory();
        // lot U/7 : le budget demandé DÉPASSE le tas ? il s'AJUSTE au
        // tas — jamais un refus. Sur appareil le plafond est celui du
        // manifeste (256 Mio sans largeHeap sur beaucoup de
        // téléphones, même récents) : refuser signifiait « aucun
        // build » pour ces appareils — le rapport du 2026-11-17
        // (« build refused: memory budget 448 MiB exceeds daemon heap
        // 256 MiB ») suivait exactement ce mur. La manière CodeAssist
        // (AndroidMemory) : le matériel borne, l'outil s'adapte — le
        // budget reste un plafond d'usage, pas un droit d'entrée.
        int budgetMb = budgetEffectif(spec.heapMb);
        if (budgetMb != spec.heapMb && sink != null) {
            sink.task("budget mémoire",
                    "AJUSTÉ " + budgetMb + " MIO (demandé "
                            + spec.heapMb + " MIO, tas "
                            + (maxHeap / 1048576L) + " MIO)");
        }
        try (BuildStore store = storeOpener.open(Path.of(spec.out))) {
            BuildEngine engine = BuildEngine.builder()
                    .roots(roots)
                    .store(store)
                    .clock(Clock.system())
                    .executor(pool)
                    .mode(SchedulingMode.FAIL_FAST)
                    .events(liveEvents(sink))
                    .budget(new ResourceBudget(Math.max(1, spec.threads),
                            budgetMb))
                    .outputCacheDir(Path.of(spec.out).resolveSibling("cache"))
                    .build();
            currentCancellation.set(cancellation);
            Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
            Map<RootName, DirtySet> dirtyMap = new LinkedHashMap<>();
            for (Map.Entry<RootName, Path> anchor
                    : roots.asMap().entrySet()) {
                if (Files.isDirectory(anchor.getValue())) {
                    VfsSnapshot current = VfsSnapshot.of(
                            TreeScanner.scan(anchor.getValue(), pool,
                                    CancelToken.NEVER));
                    snapshots.put(anchor.getKey(), current);
                    dirtyMap.put(anchor.getKey(), changeSetOf(
                            anchor.getKey(), current, dirty, store));
                }
            }
            try {
                BuildReport report = engine.build(graph, dirtyMap,
                        snapshots, cancellation);
                return outcomeOf(report, started, sink);
            } finally {
                currentCancellation.set(null);
            }
        }
    }

    /** Écouteur du moteur qui publie les tâches au fur et à mesure :
     *  un démon qui ne parle qu'à la fin n'est pas un démon. */
    private static BuildEvents liveEvents(EventSink sink) {
        if (sink == null) {
            return BuildEvents.NOOP;
        }
        return new BuildEvents() {
            @Override
            public void taskStarted(String taskName) {
                sink.task(taskName, "STARTED");
            }

            @Override
            public void taskFinished(TaskResult result) {
                sink.task(result.name(), result.status().name());
            }
        };
    }

    /**
     * Ensemble sale d'une racine : l'appelant qui sait déjà (un
     * observateur branché au protocole) prime ; sinon la racine des
     * sources est diffusée contre l'instantané persisté du store — le
     * démon meurt et renaît, le diff disque remplace l'observateur.
     * Les racines de sorties et de bibliothèques restent vides (les
     * sorties du graphe relisent fraîches, les bibliothèques sont
     * immuables).
     *
     * @param root    racine à évaluer
     * @param current instantané frais de la racine
     * @param hints   ensembles sales explicites de l'appelant, ou
     *                {@code null}
     * @param store   store du build (instantanés persistés)
     * @return l'ensemble sale de la racine
     */
    private static DirtySet changeSetOf(RootName root,
                                        VfsSnapshot current,
                                        Map<RootName, DirtySet> hints,
                                        BuildStore store) {
        if (hints != null) {
            DirtySet explicit = hints.get(root);
            if (explicit != null) {
                return explicit;
            }
        }
        if (root != RootName.PROJECT) {
            return DirtySet.empty();
        }
        // première construction : aucun instantané persisté, tout est
        // ajouté (le diff contre un instantané vide le dit)
        VfsSnapshot previous = VfsSnapshot.of(
                store.loadSnapshot(root.id()));
        return current.diffAgainst(previous);
    }

    private RootSet rootsOf(BuildSpec spec) {
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(RootName.PROJECT, Path.of(spec.project).toAbsolutePath()
                .normalize());
        anchors.put(RootName.OUT, Path.of(spec.out).toAbsolutePath()
                .normalize());
        anchors.put(RootName.M2, Path.of(spec.m2).toAbsolutePath()
                .normalize());
        if (spec.kind == BuildSpec.Kind.ANDROID) {
            anchors.put(RootName.SDK, ToolchainLocator.defaultCacheDir());
        }
        return RootSet.of(anchors);
    }

    /**
     * Le pilote Kotlin EFFECTIF : l'embarqué d'abord (démon bureau —
     * jar sur le classpath), la VM FORKÉE en repli (lot VII : l'APK
     * allégé ne porte plus le compilateur — le pack différé le sert
     * à une VM non-zygote au tas libre). Mémorisé : les compilateurs
     * restent chauds, l'ouvrière survit aux builds.
     *
     * <p>La sonde regarde la classe du COMPILATEUR (pas la stdlib —
     * elle reste embarquée pour l'ABI et d'autres briques : le
     * constructeur du pilote embarqué ne leverait PAS sur l'APK
     * allégé, seul compile() échouerait, trop tard).</p>
     */
    private CompilerDriver kotlinDriver() {
        CompilerDriver resolu = kotlinDriverResolu;
        if (resolu != null) {
            return resolu;
        }
        try {
            Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    false, BuildRunner.class.getClassLoader());
            resolu = new KotlinCompilerDriver();
        } catch (Throwable rupture) {
            // ClassNotFoundException attendu sur l'APK allégé : le jar
            // du compilateur n'y vit plus — la voie forkée prend le
            // relais (pack épinglé, PacksOutils.KOTLIN)
            resolu = new jo.joide.build.android.toolchain
                    .KotlinForkedDriver();
        }
        kotlinDriverResolu = resolu;
        return resolu;
    }

    private TaskGraph graphOf(BuildSpec spec, RootSet roots)
            throws IOException {
        if (spec.kind == BuildSpec.Kind.ANDROID) {
            androidHolder.ensure();
            Toolchain toolchain = new ToolchainLocator(
                    ToolchainLocator.defaultCacheDir()).locate();
            AndroidTaskFactory factory = new AndroidTaskFactory(
                    Path.of(spec.out), Path.of(spec.m2),
                    ToolchainLocator.defaultCacheDir(), toolchain,
                    androidHolder.scopeDexer, androidHolder.mergeDexer,
                    androidHolder.signer, androidHolder.assembler,
                    spec.sourceLevel)
                    .withKotlinDriver(kotlinDriver())
                    .withSourceAnchors(roots)
                    .signingPassword("joidepass".toCharArray());
            for (BuildSpec.ModuleSpec module : spec.modules) {
                AndroidModuleSpec android = toAndroidSpec(module);
                if (module.application) {
                    // les variantes appartiennent aux applications ;
                    // les bibliothèques sont liées dans chaque variante
                    for (Map<String, Object> variant : spec.variants) {
                        android.variants.add(variantOf(variant));
                    }
                }
                factory.module(android);
            }
            return factory.buildGraph();
        }
        JvmTaskFactory factory = jvmHolder.factoryFor(spec)
                .withKotlinDriver(kotlinDriver())
                .withSourceAnchors(roots);
        for (BuildSpec.ModuleSpec module : spec.modules) {
            JvmTaskFactory.ModuleSpec jvm = new JvmTaskFactory.ModuleSpec(
                    module.name);
            for (String source : module.sources) {
                jvm.sourceRoots.add(jo.joide.platform.RootedPath.of(
                        RootName.PROJECT, source));
            }
            for (String dependency : module.moduleDependencies) {
                jvm.moduleDependencies.add(dependency);
            }
            for (String jar : module.externalJars) {
                jvm.externalJars.add(Path.of(jar));
            }
            factory.module(jvm);
        }
        return factory.buildGraph();
    }

    private static AndroidModuleSpec toAndroidSpec(BuildSpec.ModuleSpec m) {
        AndroidModuleSpec spec = new AndroidModuleSpec(m.name, m.application);
        if (m.manifest != null) {
            spec.manifest = Path.of(m.manifest);
        }
        for (String resource : m.resources) {
            spec.resourceDirs.add(Path.of(resource));
        }
        if (m.assets != null) {
            spec.assetsDir = Path.of(m.assets);
        }
        for (String source : m.sources) {
            spec.sourceRoots.add(Path.of(source));
        }
        for (String dependency : m.moduleDependencies) {
            spec.moduleDependencies.add(dependency);
        }
        for (String jar : m.externalJars) {
            spec.externalJars.add(Path.of(jar));
        }
        for (String aar : m.externalAarDirs) {
            spec.externalAarDirs.add(Path.of(aar));
        }
        return spec;
    }

    private static AndroidVariant variantOf(Map<String, Object> variant) {
        String buildType = String.valueOf(
                variant.getOrDefault("buildType", "debug"));
        AndroidVariant.Builder builder = AndroidVariant.builder(
                String.valueOf(variant.getOrDefault("applicationId",
                        "com.example.app")), buildType);
        Object versionCode = variant.get("versionCode");
        if (versionCode instanceof Number number) {
            builder.versionCode(number.intValue());
        }
        Object minSdk = variant.get("minSdk");
        if (minSdk instanceof Number number) {
            builder.minSdk(number.intValue());
        }
        if (Boolean.TRUE.equals(variant.get("minifyEnabled"))) {
            builder.minifyEnabled(true);
        }
        if (Boolean.TRUE.equals(variant.get("v1Signing"))) {
            builder.v1Signing(true);
        }
        if (Boolean.TRUE.equals(variant.get("v2Signing"))) {
            builder.v2Signing(true);
        }
        if (Boolean.TRUE.equals(variant.get("v3Signing"))) {
            builder.v3Signing(true);
        }
        Object keystore = variant.get("keystoreFile");
        if (keystore instanceof String file) {
            builder.keystoreFile(file);
            builder.keystoreAlias(String.valueOf(
                    variant.getOrDefault("keystoreAlias", "joide")));
        }
        return builder.build();
    }

    private static Outcome outcomeOf(BuildReport report, long started,
                                     EventSink sink) {
        // les événements de tâches partent en direct (liveEvents) :
        // ici, seul le bilan est calculé
        int executed = 0;
        int fromCache = 0;
        int upToDate = 0;
        List<String> problems = new ArrayList<>();
        boolean cancelled = false;
        for (TaskResult result : report.results()) {
            switch (result.status()) {
                case SUCCEEDED -> executed += result.didWork() ? 1 : 0;
                case FROM_CACHE -> fromCache++;
                case UP_TO_DATE -> upToDate++;
                case CANCELLED -> cancelled = true;
                default -> {
                }
            }
            if (result.status() == TaskStatus.FAILED) {
                for (Problem problem : result.diagnostics()) {
                    problems.add(result.name() + " : " + problem.message());
                }
            }
        }
        return new Outcome(report.successful() && !cancelled, executed,
                fromCache, upToDate, report.peakHeapBytes(),
                System.currentTimeMillis() - started, problems, cancelled);
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
