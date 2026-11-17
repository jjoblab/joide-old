package jo.joide.build.android.packaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Écriture déterministe d'archives zip alignées (APK et charges de
 * modules), sans passer par le rédacteur du JDK : l'en-tête local est
 * écrit à la main pour que le remplissage d'alignement vive dans le
 * champ « extra », comme le fait zipalign. Les entrées non compressées
 * sont alignées — la table de ressources et les dex sur quatre octets
 * (lecture directe en mémoire), les bibliothèques natives sur seize —
 * et les entrées compressées utilisent le niveau par défaut. Les
 * horodatages sont fixes : deux exécutions aux mêmes entrées
 * produisent des octets identiques.
 */
public final class ApkWriter {

    /** Une entrée à écrire. */
    public static final class Entry {
        /** Chemin dans l'archive (barres obliques). */
        public final String name;
        /** Contenu brut. */
        public final byte[] content;
        /** Écrit sans compression. */
        public final boolean stored;
        /** Alignement des données non compressées. */
        public final int alignment;

        Entry(String name, byte[] content, boolean stored, int alignment) {
            this.name = name;
            this.content = content;
            this.stored = stored;
            this.alignment = alignment;
        }
    }

    /** Alignement standard des données mmap. */
    public static final int ALIGN_DEFAULT = 4;
    /** Alignement page des bibliothèques natives. */
    public static final int ALIGN_NATIVE = 16;

    private static final int LOCAL_HEADER = 0x04034b50;
    private static final int CENTRAL_HEADER = 0x02014b50;
    private static final int EOCD = 0x06054b50;
    private static final int MOD_TIME = 0x0000; // 00:00:00
    private static final int MOD_DATE = 0x0021; // 1980-01-01
    private static final int VERSION = 20;

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /**
     * Ajoute une entrée compressée.
     *
     * @param name    chemin dans l'archive
     * @param content contenu
     * @return ce rédacteur
     */
    public ApkWriter deflated(String name, byte[] content) {
        entries.put(name, new Entry(name, content, false, 0));
        return this;
    }

    /**
     * Ajoute une entrée non compressée alignée sur quatre octets.
     *
     * @param name    chemin dans l'archive
     * @param content contenu
     * @return ce rédacteur
     */
    public ApkWriter stored(String name, byte[] content) {
        return stored(name, content, ALIGN_DEFAULT);
    }

    /**
     * Ajoute une entrée non compressée avec alignement explicite.
     *
     * @param name      chemin dans l'archive
     * @param content   contenu
     * @param alignment alignement en octets
     * @return ce rédacteur
     */
    public ApkWriter stored(String name, byte[] content, int alignment) {
        entries.put(name, new Entry(name, content, true, alignment));
        return this;
    }

    /**
     * Copie un fichier comme entrée compressée.
     *
     * @param name chemin dans l'archive
     * @param file fichier source
     * @return ce rédacteur
     * @throws IOException si la lecture échoue
     */
    public ApkWriter deflatedFile(String name, Path file) throws IOException {
        return deflated(name, Files.readAllBytes(file));
    }

    /**
     * Copie un fichier comme entrée non compressée alignée.
     *
     * @param name      chemin dans l'archive
     * @param file      fichier source
     * @param alignment alignement
     * @return ce rédacteur
     * @throws IOException si la lecture échoue
     */
    public ApkWriter storedFile(String name, Path file, int alignment)
            throws IOException {
        return stored(name, Files.readAllBytes(file), alignment);
    }

    /**
     * Écrit l'archive vers un fichier. Le répertoire parent est créé ;
     * l'écriture passe par une zone d'attente et un déplacement
     * atomique.
     *
     * @param output fichier de sortie
     * @throws IOException si l'écriture échoue
     */
    public void writeTo(Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Path staging = output.resolveSibling(
                output.getFileName() + ".staging-" + System.nanoTime());
        try (OutputStream out = Files.newOutputStream(staging)) {
            out.write(toBytes());
        }
        try {
            Files.move(staging, output,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(staging, output);
        }
    }

    /**
     * Écrit l'archive vers des octets (sans passer par le disque).
     *
     * @return les octets de l'archive
     */
    public byte[] toBytes() {
        List<Entry> sorted = new ArrayList<>(entries.values());
        sorted.sort((a, b) -> a.name.compareTo(b.name));
        java.io.ByteArrayOutputStream out =
                new java.io.ByteArrayOutputStream();
        List<byte[]> central = new ArrayList<>();
        for (Entry entry : sorted) {
            byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
            byte[] data = entry.stored
                    ? entry.content : deflate(entry.content);
            int extraLength = 0;
            if (entry.stored && entry.alignment > 1) {
                long dataStart = out.size() + 30 + name.length;
                long rem = dataStart % entry.alignment;
                if (rem != 0) {
                    extraLength = (int) (entry.alignment - rem);
                }
            }
            long offset = out.size();
            boolean utf8 = false;
            for (int i = 0; i < name.length; i++) {
                if (name[i] < 0) {
                    utf8 = true;
                    break;
                }
            }
            int flags = utf8 ? 0x800 : 0;
            CRC32 crc = new CRC32();
            crc.update(entry.content);
            writeInt(out, LOCAL_HEADER);
            writeShort(out, VERSION);
            writeShort(out, flags);
            writeShort(out, entry.stored ? 0 : 8);
            writeShort(out, MOD_TIME);
            writeShort(out, MOD_DATE);
            writeInt(out, (int) crc.getValue());
            writeInt(out, data.length);
            writeInt(out, entry.content.length);
            writeShort(out, name.length);
            writeShort(out, extraLength);
            out.write(name, 0, name.length);
            for (int i = 0; i < extraLength; i++) {
                out.write(0);
            }
            out.write(data, 0, data.length);

            java.io.ByteArrayOutputStream cd =
                    new java.io.ByteArrayOutputStream();
            writeInt(cd, CENTRAL_HEADER);
            writeShort(cd, 0x031E); // fait par : Unix, zip 3.0
            writeShort(cd, VERSION);
            writeShort(cd, flags);
            writeShort(cd, entry.stored ? 0 : 8);
            writeShort(cd, MOD_TIME);
            writeShort(cd, MOD_DATE);
            writeInt(cd, (int) crc.getValue());
            writeInt(cd, data.length);
            writeInt(cd, entry.content.length);
            writeShort(cd, name.length);
            writeShort(cd, 0);
            writeShort(cd, 0);
            writeShort(cd, 0);
            writeShort(cd, 0);
            writeInt(cd, 0);
            writeInt(cd, (int) offset);
            cd.write(name, 0, name.length);
            central.add(cd.toByteArray());
        }
        long centralOffset = out.size();
        long centralSize = 0;
        for (byte[] record : central) {
            out.write(record, 0, record.length);
            centralSize += record.length;
        }
        writeInt(out, EOCD);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, central.size());
        writeShort(out, central.size());
        writeInt(out, (int) centralSize);
        writeInt(out, (int) centralOffset);
        writeShort(out, 0);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] content) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(content);
        deflater.finish();
        java.io.ByteArrayOutputStream buffer =
                new java.io.ByteArrayOutputStream(content.length / 2);
        byte[] chunk = new byte[64 * 1024];
        while (!deflater.finished()) {
            int written = deflater.deflate(chunk);
            buffer.write(chunk, 0, written);
        }
        deflater.end();
        return buffer.toByteArray();
    }

    private static void writeShort(java.io.ByteArrayOutputStream out,
                                   int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private static void writeInt(java.io.ByteArrayOutputStream out,
                                 int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }
}
