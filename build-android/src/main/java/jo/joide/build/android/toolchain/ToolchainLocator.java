package jo.joide.build.android.toolchain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import jo.joide.deps.repo.HttpTransport;
import jo.joide.deps.repo.Transport;
import jo.joide.deps.repo.TransportResponse;
import jo.joide.platform.Hashing;

/**
 * Localisation de l'outillage Android : variables d'environnement
 * explicites d'abord ({@code JOIDE_AAPT2}, {@code JOIDE_ANDROID_JAR},
 * {@code JOIDE_R8_JAR}), puis jar de D8/R8 présent sur le classpath
 * d'exécution, puis téléchargement épinglé (URL, taille et SHA-256
 * fixes) dans un répertoire de cache partagé entre projets.
 *
 * <p>Le téléchargement passe par le transport HTTPS du résolveur de
 * dépendances : https seul, TLS vérifié, plafonds de temps et de
 * taille. Une empreinte divergente est un échec explicite — jamais un
 * binaire non vérifié sur le disque.</p>
 */
public final class ToolchainLocator {

    /** Version d'aapt2 (binaire natif, classifieur linux). */
    public static final String AAPT2_VERSION = "9.5.1-16666295";
    /** SHA-256 du jar d'aapt2 linux. */
    public static final String AAPT2_SHA256
            = "fa1014cb1431622d292ef55ba870e75882ea19987bec541698b912f446a2d08f";
    /** Taille attendue du jar d'aapt2 linux. */
    public static final long AAPT2_SIZE = 2_451_278L;

    /**
     * Nom du jar des stubs d'invokedynamic posé au cache (le contenu
     * d'AGP core-lambda-stubs, embarqué en ressource du module — la
     * doctrine du lot XX : tout embarqué, zéro téléchargement
     * d'outillage à l'exécution).
     */
    public static final String LAMBDA_STUBS_JAR_NAME
            = "core-lambda-stubs.jar";
    /** La ressource embarquée portant les stubs. */
    private static final String LAMBDA_STUBS_RESOURCE
            = "/jo/joide/build/android/toolchain/core-lambda-stubs.jar";

    // lot XIX : les binaires aapt2 ANDROID (ReVanced v1.1.0, la même
    // source que les jniLibs de l'APK — ELF statique bionic, segments
    // alignés 16 Ko, empreintes vérifiées par téléchargement dans le
    // sandbox le 2026-11-17) : le repli quand l'aapt2 embarqué manque
    private static final String AAPT2_ANDROID_BASE_URL
            = "https://github.com/ReVanced/aapt2/releases/download/v1.1.0";
    /** SHA-256 du binaire aapt2 Android ARM64 (binaire nu). */
    public static final String AAPT2_ANDROID_ARM64_SHA256
            = "7e5ae2e1f62fc24cab14072555ffd0a1a7e1ce27e82cc1008967b835b6d8df5b";
    /** Taille attendue du binaire aapt2 Android ARM64. */
    public static final long AAPT2_ANDROID_ARM64_SIZE = 4_693_752L;
    /** SHA-256 du binaire aapt2 Android x86_64 (émulateurs). */
    public static final String AAPT2_ANDROID_X86_64_SHA256
            = "46d2244c3c574979ecae452c8a32e0a25ac0184bfe29a3d805821e36401ecdcb";
    /** Taille attendue du binaire aapt2 Android x86_64. */
    public static final long AAPT2_ANDROID_X86_64_SIZE = 4_993_552L;

    /** Révision de plateforme téléchargée (android.jar + AIDL). */
    public static final String PLATFORM_REVISION = "35_r02";
    /** SHA-256 de l'archive de plateforme (contrôle du téléchargement). */
    public static final String PLATFORM_SHA256
            = "0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0";

    /** Version de D8/R8. */
    public static final String R8_VERSION = "9.5.32";
    /** SHA-256 du jar de D8/R8. */
    public static final String R8_SHA256
            = "7c7b67be3069d395cdfbdea4799ffd04d75d81ccecf3d01a78df37b1f5843298";

    private static final String GOOGLE_MAVEN
            = "https://dl.google.com/android/maven2";
    private static final String GOOGLE_REPOSITORY
            = "https://dl.google.com/android/repository";

    private final Path cacheDir;
    private final Transport transport;

    /**
     * @return le répertoire de cache de l'outillage : la propriété
     *         système {@code joide.toolchain.dir} d'abord (sur
     *         appareil, l'application ne peut pas poser d'env), la
     *         variable d'environnement {@code JOIDE_TOOLCHAIN_DIR}
     *         ensuite, sinon {@code ~/.cache/joide/toolchain}
     */
    public static Path defaultCacheDir() {
        String propriete = System.getProperty("joide.toolchain.dir");
        if (propriete != null && !propriete.isBlank()) {
            return Path.of(propriete.trim());
        }
        String override = System.getenv("JOIDE_TOOLCHAIN_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".cache",
                "joide", "toolchain");
    }

    /**
     * Le binaire aapt2 EMBARQUÉ de l'application (lot U) : jniLibs
     * porte libaapt2.so par ABI (ReVanced/aapt2, segments 16 Ko
     * alignés), ART n'exécute les binaires que depuis
     * nativeLibraryDir — la propriété système {@code joide.native.dir}
     * le nomme. Absent (bureau) : {@code null}.
     */
    public static Path aapt2Embarque() {
        String natif = System.getProperty("joide.native.dir");
        if (natif == null || natif.isBlank()) {
            return null;
        }
        Path exe = Path.of(natif.trim(), "libaapt2.so");
        return Files.isRegularFile(exe) && exe.toFile().canExecute()
                ? exe : null;
    }

    /**
     * @param cacheDir répertoire de cache de l'outillage (partagé
     *                 entre projets)
     */
    public ToolchainLocator(Path cacheDir) {
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir")
                .toAbsolutePath().normalize();
        this.transport = new HttpTransport();
    }

    /**
     * Localise l'outillage complet sans rien télécharger : tous les
     * chemins doivent exister.
     *
     * @return l'outillage, ou {@code null} si une pièce manque
     */
    public Toolchain findLocal() {
        Path aapt2 = envPath("JOIDE_AAPT2");
        Path platform = envPath("JOIDE_ANDROID_JAR");
        Path r8 = envPath("JOIDE_R8_JAR");
        if (r8 == null) {
            r8 = r8FromClasspath();
        }
        if (aapt2 == null) {
            // lot U : sur appareil, aapt2 est EMBARQUÉ (jniLibs) ;
            // lot XIX : sinon la VARIANTE d'android (ReVanced épinglé,
            // téléchargée par ensureAapt2 si absente du cache) — le
            // jar linux de Google Maven ne sert plus qu'au bureau
            aapt2 = aapt2Embarque();
        }
        if (aapt2 == null) {
            aapt2 = cachedAapt2();
        }
        if (platform == null) {
            platform = cachedPlatform();
        }
        if (r8 == null) {
            r8 = cachedR8();
        }
        if (aapt2 == null || platform == null || r8 == null) {
            return null;
        }
        Path aidl = platform.getParent().resolve("framework.aidl");
        return new Toolchain(aapt2, platform,
                Files.isRegularFile(aidl) ? aidl : null, r8,
                lambdaStubs());
    }

    /**
     * Localise l'outillage, téléchargeant les pièces manquantes dans
     * le cache. Les téléchargements sont épinglés par URL et SHA-256.
     *
     * @return l'outillage complet
     * @throws IOException si un téléchargement échoue ou qu'une
     *                     empreinte diverge
     */
    public Toolchain locate() throws IOException {
        Toolchain local = findLocal();
        if (local != null) {
            return local;
        }
        Files.createDirectories(cacheDir);
        Path aapt2 = ensureAapt2();
        Path platform = ensurePlatform();
        Path r8 = ensureR8();
        Path aidl = platform.getParent().resolve("framework.aidl");
        return new Toolchain(aapt2, platform,
                Files.isRegularFile(aidl) ? aidl : null, r8,
                lambdaStubs());
    }

    /**
     * Les stubs d'invokedynamic du compile-time (StringConcatFactory
     * pour la concaténation, LambdaMetafactory pour les lambdas) :
     * android.jar les OMET et, à compliance ≥ 9, la moindre
     * concaténation ou lambda émet un invokedynamic dont la bootstrap
     * doit se résoudre au compile-time — le terrain du 2026-11-18 :
     * « The type java.lang.invoke.StringConcatFactory cannot be
     * resolved » sur compileJava après generateRJar (le plateau 35 ne
     * porte que LambdaMetafactory dans son jar system-modules ; AGP
     * pose core-lambda-stubs.jar du build-tools, la même doctrine
     * l'IDE de référence ; jamais dexé, D8 désugare l'indy au dexage).
     *
     * <p>Sources, par priorité : le fichier DÉJÀ AU CACHE (l'APK
     * embarque la pièce en asset et l'extrait une fois dans le MÊME
     * cache via ProprietesConstruction — l'appareil n'a rien à
     * faire), puis la variable d'environnement
     * {@code JOIDE_LAMBDA_STUBS}, puis la ressource EMBARQUÉE du
     * module, extraite une fois au cache (le bureau, les tests).
     * Retourne nul si la pièce manque (contractuellement possible —
     * les appelants acceptent le nul, comme le fait l'IDE de référence sur
     * les vieux build-tools).</p>
     */
    private Path lambdaStubs() {
        Path cached = cacheDir.resolve(LAMBDA_STUBS_JAR_NAME);
        if (Files.isRegularFile(cached)) {
            return cached;
        }
        Path env = envPath("JOIDE_LAMBDA_STUBS");
        if (env != null) {
            return env;
        }
        try (InputStream resource = ToolchainLocator.class
                .getResourceAsStream(LAMBDA_STUBS_RESOURCE)) {
            if (resource == null) {
                return null;
            }
            byte[] bytes = resource.readAllBytes();
            if (!Files.isRegularFile(cached)
                    || !Arrays.equals(bytes, Files.readAllBytes(cached))) {
                Files.createDirectories(cached.getParent());
                Files.write(cached, bytes);
            }
            return cached;
        } catch (IOException e) {
            return null;
        }
    }

    private Path ensureAapt2() throws IOException {
        Path cached = cachedAapt2();
        if (cached != null) {
            return cached;
        }
        // lot XIX : sur APPAREIL, le binaire ANDROID (ReVanced, la
        // même source que les jniLibs de l'APK, épinglé) — JAMAIS le
        // jar linux : son ELF x86_64 est inexecutable sur un
        // téléphone ARM64, et bionic (execvp) retombe sur le shell
        // qui le parse : « syntax error: unexpected '(' » (terrain
        // du 2026-11-17, APK reconstruit sans libaapt2.so embarqué)
        if (!"linux".equals(varianteAapt2(System.getProperty("os.arch"),
                surAppareil()))) {
            return assureAapt2Android();
        }
        Path jar = cacheDir.resolve("aapt2-" + AAPT2_VERSION + "-linux.jar");
        String coordinate = "com/android/tools/build/aapt2/" + AAPT2_VERSION
                + "/aapt2-" + AAPT2_VERSION + "-linux.jar";
        downloadPinned(GOOGLE_MAVEN + "/" + coordinate, jar, AAPT2_SHA256);
        Path binary = cacheDir.resolve("aapt2-" + AAPT2_VERSION);
        SafeZip.extract(jar, AAPT2_SHA256, binary);
        Path exe = binary.resolve("aapt2");
        if (!Files.isRegularFile(exe)) {
            throw new IOException("aapt2 binary missing from archive");
        }
        if (!exe.toFile().setExecutable(true)) {
            throw new IOException("cannot make aapt2 executable: " + exe);
        }
        return exe;
    }

    /**
     * Le binaire aapt2 ANDROID pour l'ABI de cette machine (ReVanced
     * v1.1.0, épinglé) : ELF statique bionic, segments alignés 16 Ko.
     * Le binaire nu se pose directement au cache — pas d'archive à
     * extraire.
     */
    private Path assureAapt2Android() throws IOException {
        String variante = varianteAapt2(System.getProperty("os.arch"),
                true);
        boolean arm64 = variante.endsWith("arm64-v8a");
        String abi = arm64 ? "arm64-v8a" : "x86_64";
        Path exe = cacheDir.resolve("aapt2-" + variante);
        if (!Files.isRegularFile(exe)) {
            downloadPinned(AAPT2_ANDROID_BASE_URL + "/aapt2-" + abi, exe,
                    arm64 ? AAPT2_ANDROID_ARM64_SHA256
                            : AAPT2_ANDROID_X86_64_SHA256);
        }
        if (!exe.toFile().setExecutable(true)) {
            throw new IOException("cannot make aapt2 executable: " + exe);
        }
        return exe;
    }

    private Path ensurePlatform() throws IOException {
        Path cached = cachedPlatform();
        if (cached != null) {
            return cached;
        }
        Path zip = cacheDir.resolve("platform-" + PLATFORM_REVISION + ".zip");
        downloadPinned(
                GOOGLE_REPOSITORY + "/platform-" + PLATFORM_REVISION + ".zip",
                zip, PLATFORM_SHA256);
        Path dir = cacheDir.resolve("platform-" + PLATFORM_REVISION);
        SafeZip.extract(zip, PLATFORM_SHA256, dir);
        Path jar = findAndroidJar(dir);
        if (jar == null) {
            throw new IOException("android.jar missing from platform zip");
        }
        return jar;
    }

    private Path ensureR8() throws IOException {
        Path fromCp = r8FromClasspath();
        if (fromCp != null) {
            return fromCp;
        }
        Path cached = cachedR8();
        if (cached != null) {
            return cached;
        }
        Path jar = cacheDir.resolve("r8-" + R8_VERSION + ".jar");
        String coordinate = "com/android/tools/r8/" + R8_VERSION
                + "/r8-" + R8_VERSION + ".jar";
        downloadPinned(GOOGLE_MAVEN + "/" + coordinate, jar, R8_SHA256);
        return jar;
    }

    private void downloadPinned(String url, Path target, String sha256)
            throws IOException {
        if (Files.isRegularFile(target)
                && Hashing.toHex(Hashing.sha256(target))
                        .equalsIgnoreCase(sha256)) {
            return;
        }
        Path staging = target.resolveSibling(
                target.getFileName() + ".part-" + System.nanoTime());
        Files.createDirectories(staging.getParent());
        TransportResponse response = transport.get(url, null);
        if (!response.isHit()) {
            Files.deleteIfExists(staging);
            throw new IOException("download failed for " + url + " : "
                    + response.message);
        }
        String got = Hashing.toHex(Hashing.sha256(response.bytes));
        if (!got.equalsIgnoreCase(sha256)) {
            throw new IOException("checksum mismatch for " + url + " : "
                    + got + " (expected " + sha256 + ")");
        }
        try (OutputStream out = Files.newOutputStream(staging)) {
            out.write(response.bytes);
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(staging, target);
        }
    }

    private Path cachedAapt2() {
        // lot XIX : la variante d'abord (android-arm64-v8a,
        // android-x86_64), puis le binaire linux extrait du jar — le
        // bureau garde le chemin historique, l'appareil ne retombe
        // JAMAIS dessus (varianteAapt2)
        Path android = cacheDir.resolve("aapt2-" + varianteAapt2(
                System.getProperty("os.arch"), true));
        if (Files.isRegularFile(android) && android.toFile().canExecute()) {
            return android;
        }
        if (!"linux".equals(varianteAapt2(System.getProperty("os.arch"),
                surAppareil()))) {
            return null;
        }
        Path exe = cacheDir.resolve("aapt2-" + AAPT2_VERSION).resolve("aapt2");
        return Files.isRegularFile(exe) && exe.toFile().canExecute()
                ? exe : null;
    }

    /**
     * La variante d'aapt2 pour cette machine : {@code android-arm64-v8a}
     * (téléphone ARM64), {@code android-x86_64} (émulateur) ou
     * {@code linux} (bureau, ou ABI Android inconnue — aucun binaire
     * Android publié pour elle). Le jeu d'instructions décide, pas le
     * système : la détection d'ART d'abord (BuildRunner.surAppareil),
     * l'architecture ensuite.
     */
    static String varianteAapt2(String osArch, boolean appareil) {
        if (!appareil) {
            return "linux";
        }
        String arch = osArch == null ? "" : osArch.toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "android-arm64-v8a";
        }
        if (arch.contains("x86_64") || arch.contains("amd64")) {
            return "android-x86_64";
        }
        return "linux";
    }

    /** Vrai si la VM hôte est ART (appareil) : le vendeur « Android »
     *  ou un java.home sous /apex — la même sonde que BuildRunner. */
    static boolean surAppareil() {
        String vendeur = System.getProperty("java.vendor", "");
        String home = System.getProperty("java.home", "");
        return vendeur.contains("Android") || home.startsWith("/apex/");
    }

    private Path cachedPlatform() {
        return findAndroidJar(
                cacheDir.resolve("platform-" + PLATFORM_REVISION));
    }

    private Path cachedR8() {
        Path jar = cacheDir.resolve("r8-" + R8_VERSION + ".jar");
        return Files.isRegularFile(jar) ? jar : null;
    }

    private static Path findAndroidJar(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName() != null
                            && p.getFileName().toString().equals("android.jar"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path envPath(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value.trim());
        return Files.isRegularFile(path) ? path : null;
    }

    /**
     * Cherche un jar de D8/R8 sur le classpath d'exécution : la
     * dépendance résolue par le système de build sert alors d'outil,
     * sans téléchargement.
     *
     * @return le chemin du jar, ou {@code null}
     */
    public static Path r8FromClasspath() {
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path path = Path.of(entry.trim());
            String name = path.getFileName() == null ? ""
                    : path.getFileName().toString();
            if (Files.isRegularFile(path)
                    && (name.equals("r8.jar")
                        || (name.startsWith("r8-") && name.endsWith(".jar")))) {
                return path;
            }
        }
        return null;
    }
}
