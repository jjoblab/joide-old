package jo.joide.build.android.toolchain;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
                Files.isRegularFile(aidl) ? aidl : null, r8);
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
                Files.isRegularFile(aidl) ? aidl : null, r8);
    }

    private Path ensureAapt2() throws IOException {
        Path cached = cachedAapt2();
        if (cached != null) {
            return cached;
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
        Path exe = cacheDir.resolve("aapt2-" + AAPT2_VERSION).resolve("aapt2");
        return Files.isRegularFile(exe) && exe.toFile().canExecute()
                ? exe : null;
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
