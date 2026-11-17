package jo.joide.build.android.dex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lecture de l'en-tête d'un fichier dex : magie, comptages de la table
 * des symboles. Les comptages servent à la répartition des dex en
 * buckets (plafond de 65 536 méthodes référencées par fichier dex).
 */
public final class DexInfo {

    /** Magie attendue d'un fichier dex. */
    public static final String MAGIC = "dex\n";

    /** Nombre de méthodes référencées. */
    public final int methodIds;
    /** Nombre de champs référencés. */
    public final int fieldIds;
    /** Nombre de chaînes référencées. */
    public final int stringIds;
    /** Nombre de types référencés. */
    public final int typeIds;
    /** Taille du fichier en octets. */
    public final long size;

    private DexInfo(int methodIds, int fieldIds, int stringIds,
                    int typeIds, long size) {
        this.methodIds = methodIds;
        this.fieldIds = fieldIds;
        this.stringIds = stringIds;
        this.typeIds = typeIds;
        this.size = size;
    }

    /**
     * Comptages explicites (outils de test et planification).
     *
     * @param methodIds méthodes référencées
     * @param fieldIds  champs référencés
     * @param stringIds chaînes référencées
     * @param typeIds   types référencés
     * @param size      taille du fichier
     * @return les comptages
     */
    public static DexInfo ofCounts(int methodIds, int fieldIds,
                                   int stringIds, int typeIds, long size) {
        return new DexInfo(methodIds, fieldIds, stringIds, typeIds, size);
    }

    /**
     * Lit l'en-tête d'un fichier dex.
     *
     * @param dex fichier dex
     * @return les comptages
     * @throws IOException si le fichier est trop court ou que la
     *                     magie est invalide
     */
    public static DexInfo read(Path dex) throws IOException {
        byte[] bytes = Files.readAllBytes(dex);
        return parse(bytes);
    }

    /**
     * Analyse l'en-tête depuis les octets d'un fichier dex.
     *
     * @param bytes octets du fichier dex
     * @return les comptages
     * @throws IOException si la magie est invalide
     */
    public static DexInfo parse(byte[] bytes) throws IOException {
        if (bytes.length < 112) {
            throw new IOException("dex file too short: " + bytes.length);
        }
        ByteBuffer header = ByteBuffer.wrap(bytes, 0, 112)
                .order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        header.get(magic);
        if (!MAGIC.equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("bad dex magic: "
                    + new String(magic, java.nio.charset.StandardCharsets.US_ASCII));
        }
        header.position(56);
        int stringIds = header.getInt();
        header.getInt(); // string_ids_off
        int typeIds = header.getInt();
        header.getInt(); // type_ids_off
        header.getInt(); // proto_ids_size
        header.getInt(); // proto_ids_off
        int fieldIds = header.getInt();
        header.getInt(); // field_ids_off
        int methodIds = header.getInt();
        return new DexInfo(methodIds, fieldIds, stringIds, typeIds,
                bytes.length);
    }

    @Override
    public String toString() {
        return "DexInfo[methods=" + methodIds + " fields=" + fieldIds
                + " strings=" + stringIds + " size=" + size + "]";
    }
}
