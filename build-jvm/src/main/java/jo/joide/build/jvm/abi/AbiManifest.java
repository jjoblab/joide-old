package jo.joide.build.jvm.abi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import jo.joide.platform.Hashing;

/**
 * Manifeste ABI d'une entrée de classpath (module ou bibliothèque) :
 * la table « classe → hachage d'ABI » d'un lot de classes, écrite en
 * texte déterministe (classes triées, fins de ligne LF).
 *
 * <p>Le manifeste est l'artefact consommé par les modules aval : deux
 * builds dont les dépendances ont le même manifeste ont la même ABI
 * de classpath, même si les octets des jar differents (corps de
 * méthode modifiés). Le nom du fichier est le condensé de son
 * contenu : un manifeste immuable par version d'ABI.</p>
 */
public final class AbiManifest {

    /** En-tête de première ligne, versionné avec le rendu d'ABI. */
    static final String HEADER = "# joide abi manifest " + ClassAbi.FORMAT;

    private final Map<String, String> entries;
    private final String digest;
    private final String text;

    private AbiManifest(Map<String, String> entries, String digest,
                        String text) {
        this.entries = entries;
        this.digest = digest;
        this.text = text;
    }

    /**
     * @param entries table classe (forme interne) → hachage d'ABI
     * @return le manifeste correspondant
     */
    public static AbiManifest of(Map<String, String> entries) {
        Map<String, String> sorted = new TreeMap<>(entries);
        StringBuilder text = new StringBuilder(
                64 + sorted.size() * 48);
        text.append(HEADER).append('\n');
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            text.append(entry.getKey()).append(' ')
                    .append(entry.getValue()).append('\n');
        }
        String body = text.toString();
        return new AbiManifest(sortedCopy(sorted),
                Hashing.toHex(Hashing.sha256(
                        body.getBytes(StandardCharsets.UTF_8)))
                        .substring(0, 32), body);
    }

    /**
     * @param text contenu exact d'un manifeste
     * @return le manifeste relu, l'en-tête est contrôlé
     * @throws IOException si l'en-tête est inconnu
     */
    public static AbiManifest parse(String text) throws IOException {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw new IOException("unknown manifest header");
        }
        Map<String, String> entries = new TreeMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            int split = line.indexOf(' ');
            if (split <= 0 || split == line.length() - 1) {
                throw new IOException("bad manifest line: " + line);
            }
            entries.put(line.substring(0, split),
                    line.substring(split + 1));
        }
        return of(entries);
    }

    /**
     * @param file fichier de manifeste
     * @return le manifeste lu
     * @throws IOException si la lecture échoue
     */
    public static AbiManifest read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Écrit ce manifeste dans un répertoire d'artefacts immuables :
     * le fichier porte le nom de son condensé ; un fichier identique
     * n'est jamais réécrit.
     *
     * @param dir répertoire des manifestes (créé si absent)
     * @return le chemin du manifeste écrit
     * @throws IOException si l'écriture échoue
     */
    public Path writeTo(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(digest + ".manifest");
        if (!Files.exists(target)) {
            Path tmp = dir.resolve(digest + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                java.nio.file.StandardCopyOption[] opts = {
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING};
                Files.move(tmp, target, opts);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                Files.deleteIfExists(tmp);
            }
        }
        return target;
    }

    /** @return les entrées classe → hachage, triées, non modifiables */
    public Map<String, String> entries() {
        return entries;
    }

    /** @return le condensé du manifeste (32 caractères hexa) */
    public String digest() {
        return digest;
    }

    /** @return le texte canonique complet */
    public String text() {
        return text;
    }

    /** @return le nombre de classes décrites */
    public int size() {
        return entries.size();
    }

    private static Map<String, String> sortedCopy(Map<String, String> src) {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(src));
    }
}
