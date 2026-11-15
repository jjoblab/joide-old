package jo.joide.build.daemon.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Cadres de longueur : chaque message du protocole est un texte JSON
 * UTF-8 précédé de sa longueur en octets (entier signé sur quatre
 * octets, petit boutien). Les deux sens du flux utilisent le même
format. Un plafond de cadre protège le lecteur des flux corrompus.
 */
public final class Frames {

    /** Plafond d'un cadre : dix Mio. */
    public static final int MAX_FRAME_BYTES = 10 * 1024 * 1024;

    private final DataInputStream in;
    private final DataOutputStream out;
    private final Object writeLock = new Object();

    /**
     * @param in  flux entrant (cadres du pair)
     * @param out flux sortant (cadres vers le pair)
     */
    public Frames(InputStream in, OutputStream out) {
        this.in = new DataInputStream(in);
        this.out = new DataOutputStream(out);
    }

    /**
     * Lit le prochain cadre ; bloque jusqu'à disponibilité.
     *
     * @return le texte JSON du cadre
     * @throws IOException si le flux se ferme ou qu'un cadre dépasse
     *                     le plafond
     */
    public String read() throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("frame out of bounds: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Écrit un cadre.
     *
     * @param message texte JSON du cadre
     * @throws IOException si l'écriture échoue
     */
    public void write(String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME_BYTES) {
            throw new IOException("frame too large: " + bytes.length);
        }
        synchronized (writeLock) {
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

    /** @return vrai si des octets sont disponibles sans bloquer */
    public boolean ready() throws IOException {
        return in.available() >= 4;
    }
}
