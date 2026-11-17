package jo.joide.build.jvm.graph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jo.joide.platform.Hashing;

/**
 * État incrémental d'une tâche de compilation : pour chaque source,
 * paquet, imports, classes produites (et leurs super-types), classes
 * référencées ; pour chaque classe produite, le hachage d'ABI ; la
 * spécification compilée et les manifestes consommés.
 *
 * <p>L'état vit dans le répertoire de sortie de la tâche : il suit
 * donc le cache de sorties (restauration comprise) et disparaît avec
 * un nettoyage. C'est un cache, jamais une source de vérité : tout
 * échec de lecture ou d'intégrité se traduit par une recompilation
 * complète, pas par un échec du build.</p>
 */
public final class CompileState {

    /** Magicie + version du format. */
    private static final int MAGIC = 0x4A424353; // "JBCS"
    private static final int VERSION = 1;

    /** Dépendances d'une source. */
    public static final class SourceDeps {
        /** Chemin relatif au module, séparateurs « / ». */
        public final String relativePath;
        /** Paquet en forme pointée, vide si paquet par défaut. */
        public final String packageName;
        /** Imports simples (forme pointée). */
        public final List<String> singleImports;
        /** Paquets des imports à la demande. */
        public final List<String> starImports;
        /** Classes produites → super-types directs (forme interne). */
        public final Map<String, List<String>> produced;
        /** Classes référencées (sur-ensemble sûr). */
        public final List<String> references;

        /** @param relativePath chemin relatif */
        public SourceDeps(String relativePath) {
            this(relativePath, "");
        }

        /**
         * @param relativePath chemin relatif
         * @param packageName   paquet en forme pointée
         */
        public SourceDeps(String relativePath, String packageName) {
            this.relativePath = relativePath;
            this.packageName = packageName;
            this.singleImports = new ArrayList<>();
            this.starImports = new ArrayList<>();
            this.produced = new LinkedHashMap<>();
            this.references = new ArrayList<>();
        }

        SourceDeps(String relativePath, String packageName,
                   List<String> singleImports, List<String> starImports,
                   Map<String, List<String>> produced,
                   List<String> references) {
            this.relativePath = relativePath;
            this.packageName = packageName;
            this.singleImports = singleImports;
            this.starImports = starImports;
            this.produced = produced;
            this.references = references;
        }
    }

    /** Spécification compilée (chaîne canonique de la tâche). */
    public String spec = "";
    /** Condensés des manifestes consommés, par entrée de classpath. */
    public final Map<String, String> consumedManifests = new LinkedHashMap<>();
    /** Dépendances par source, chemin relatif → dépendances. */
    public final Map<String, SourceDeps> sources = new LinkedHashMap<>();
    /** ABI par classe produite, forme interne → hachage. */
    public final Map<String, String> classAbis = new LinkedHashMap<>();

    /**
     * @param bytes octets d'un état sérialisé
     * @return l'état décodé
     * @throws IOException si le format ou l'intégrité est en défaut
     */
    public static CompileState decode(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes));
        if (in.readInt() != MAGIC) {
            throw new IOException("bad state magic");
        }
        if (in.readUnsignedShort() != VERSION) {
            throw new IOException("unsupported state version");
        }
        int bodyLength = in.readInt();
        byte[] body = new byte[bodyLength];
        in.readFully(body);
        byte[] digest = new byte[32];
        in.readFully(digest);
        if (!java.util.Arrays.equals(digest, Hashing.sha256(body))) {
            throw new IOException("state checksum mismatch");
        }
        CompileState state = new CompileState();
        DataInputStream data = new DataInputStream(
                new ByteArrayInputStream(body));
        state.spec = readString(data);
        int entries = data.readUnsignedShort();
        for (int i = 0; i < entries; i++) {
            state.consumedManifests.put(readString(data),
                    readString(data));
        }
        int sourceCount = data.readInt();
        for (int i = 0; i < sourceCount; i++) {
            String path = readString(data);
            String packageName = readString(data);
            List<String> singles = readStrings(data);
            List<String> stars = readStrings(data);
            int producedCount = data.readUnsignedShort();
            Map<String, List<String>> produced = new LinkedHashMap<>();
            for (int p = 0; p < producedCount; p++) {
                produced.put(readString(data), readStrings(data));
            }
            List<String> references = readStrings(data);
            state.sources.put(path, new SourceDeps(path, packageName,
                    singles, stars, produced, references));
        }
        int abiCount = data.readInt();
        for (int i = 0; i < abiCount; i++) {
            state.classAbis.put(readString(data), readString(data));
        }
        return state;
    }

    /**
     * @return les octets sérialisés, déterministes (ordres stables,
     * condensé de contrôle en fin de flux)
     */
    public byte[] encode() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
        try {
            DataOutputStream data = new DataOutputStream(buffer);
            writeString(data, spec);
            TreeMap<String, String> sortedManifests =
                    new TreeMap<>(consumedManifests);
            data.writeShort(sortedManifests.size());
            for (Map.Entry<String, String> entry : sortedManifests.entrySet()) {
                writeString(data, entry.getKey());
                writeString(data, entry.getValue());
            }
            data.writeInt(sources.size());
            TreeMap<String, SourceDeps> sortedSources =
                    new TreeMap<>(sources);
            for (SourceDeps deps : sortedSources.values()) {
                writeString(data, deps.relativePath);
                writeString(data, deps.packageName);
                writeSortedStrings(data, deps.singleImports);
                writeSortedStrings(data, deps.starImports);
                TreeMap<String, List<String>> sortedProduced =
                        new TreeMap<>(deps.produced);
                data.writeShort(sortedProduced.size());
                for (Map.Entry<String, List<String>> entry
                        : sortedProduced.entrySet()) {
                    writeString(data, entry.getKey());
                    writeSortedStrings(data, entry.getValue());
                }
                writeSortedStrings(data, deps.references);
            }
            data.writeInt(classAbis.size());
            TreeMap<String, String> sortedAbis = new TreeMap<>(classAbis);
            for (Map.Entry<String, String> entry : sortedAbis.entrySet()) {
                writeString(data, entry.getKey());
                writeString(data, entry.getValue());
            }
            data.flush();
        } catch (IOException e) {
            throw new IllegalStateException("unreachable", e);
        }
        byte[] body = buffer.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                body.length + 42);
        try {
            DataOutputStream header = new DataOutputStream(out);
            header.writeInt(MAGIC);
            header.writeShort(VERSION);
            header.writeInt(body.length);
            header.write(body);
            header.write(Hashing.sha256(body));
            header.flush();
        } catch (IOException e) {
            throw new IllegalStateException("unreachable", e);
        }
        return out.toByteArray();
    }

    private static void writeSortedStrings(DataOutputStream out,
                                          List<String> values)
            throws IOException {
        List<String> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        out.writeShort(sorted.size());
        for (String value : sorted) {
            writeString(out, value);
        }
    }

    private static void writeString(DataOutputStream out, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> readStrings(DataInputStream in)
            throws IOException {
        int count = in.readUnsignedShort();
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readString(in));
        }
        return values;
    }

    /** @return le chemin d'état conventionnel d'un répertoire de sortie */
    public static Path stateFile(Path outputDir) {
        return outputDir.resolve("compile-state.bin");
    }
}
