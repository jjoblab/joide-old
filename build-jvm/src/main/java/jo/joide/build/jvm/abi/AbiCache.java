package jo.joide.build.jvm.abi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import jo.joide.platform.Hashing;

/**
 * Cache des manifestes ABI des entrées de classpath externes (jar ou
 * répertoires hors des sorties du build en cours).
 *
 * <p>Clé de cache : le condensé de contenu de l'entrée, retrouvé sans
 * relecture quand la taille, la date et la clé de fichier n'ont pas
 * changé (index persistant dans le répertoire de sortie). Les
 * manifestes sont immuables et adressés par leur propre condensé ;
 * un cache corrompu est reconstruit silencieusement — il n'est jamais
 * une source de vérité.</p>
 */
public final class AbiCache {

    /** Plafond d'entrées de classes par entrée de classpath. */
    public static final int DEFAULT_MAX_CLASSES = 200_000;

    private final Path cacheDir;
    private final Map<String, String> signals = new HashMap<>();
    private boolean indexDirty;

    /**
     * @param cacheDir répertoire du cache (créé à l'usage)
     */
    public AbiCache(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * @param entry entrée de classpath (jar ou répertoire)
     * @return le manifeste ABI de l'entrée, calculé ou en cache
     * @throws IOException si la lecture ou le calcul échoue
     */
    public synchronized AbiManifest manifestOf(Path entry) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(entry,
                BasicFileAttributes.class);
        String key = entry + "|" + attrs.size() + "|" + attrs.lastModifiedTime()
                .toMillis();
        String cachedDigest = signals.get(key);
        if (cachedDigest != null) {
            Path cached = cacheDir.resolve(cachedDigest + ".manifest");
            if (Files.exists(cached)) {
                return AbiManifest.read(cached);
            }
        }
        AbiManifest manifest = EntryAbi.compute(entry, DEFAULT_MAX_CLASSES);
        Path written = manifest.writeTo(cacheDir);
        signals.put(key, manifest.digest());
        markIndexDirty();
        // l'index est réécrit en une passe : un crash laisse au pire
        // une entrée non découverte (recalcul au prochain build)
        writeIndex();
        return manifest;
    }

    private void markIndexDirty() {
        indexDirty = true;
    }

    private void writeIndex() throws IOException {
        if (!indexDirty) {
            return;
        }
        Files.createDirectories(cacheDir);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : signals.entrySet()) {
            text.append(entry.getKey()).append(' ')
                    .append(entry.getValue()).append('\n');
        }
        Path index = cacheDir.resolve("index.txt");
        Path tmp = cacheDir.resolve("index.tmp");
        Files.writeString(tmp, text.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, index,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        indexDirty = false;
    }

    /** Recharge l'index persistant ; un index absent démarre vide. */
    public synchronized void load() {
        signals.clear();
        Path index = cacheDir.resolve("index.txt");
        if (!Files.exists(index)) {
            return;
        }
        try {
            String text = Files.readString(index, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                int split = line.lastIndexOf(' ');
                if (split > 0 && split < line.length() - 1) {
                    signals.put(line.substring(0, split),
                            line.substring(split + 1));
                }
            }
        } catch (IOException e) {
            // index illisible : reconstruit à l'usage, le cache n'est
            // jamais une source de vérité
            signals.clear();
        }
    }

    /** @return le condensé de contenu d'un fichier, hexadécimal */
    public static String contentDigest(Path file) throws IOException {
        return Hashing.toHex(Hashing.sha256(file)).substring(0, 32);
    }
}
