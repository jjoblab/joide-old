package jo.joide.app.android.atelier.agent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;

import jo.joide.agent.ReductionImages;

/**
 * Le réducteur d'images de l'hôte Android (lot S, ADR-064 posant le
 * réducteur promis au lot Q) : décodage BitmapFactory, mise à
 * l'échelle au plus long côté, ré-encodage JPEG — une photo de 5 Mo
 * ne coûte pas plus de jetons que sa version réduite.
 */
public final class ReducteurImagesAndroid
        implements ReductionImages.Reducteur {

    /** Le plus long côté visé (une image plus petite passe telle quelle). */
    static final int COTE_MAX = 1_568;

    /** La qualité JPEG du ré-encodage. */
    static final int QUALITE_JPEG = 85;

    @Override
    public ImageReduite reduire(String mediaType, byte[] octets,
                                int budget) {
        if (octets == null || octets.length == 0) {
            return null;
        }
        Bitmap source = BitmapFactory.decodeByteArray(
                octets, 0, octets.length);
        if (source == null) {
            // pas une image décodable : le refus monte à l'appelant
            return null;
        }
        try {
            Bitmap cible = source;
            int longCote = Math.max(source.getWidth(),
                    source.getHeight());
            if (longCote > COTE_MAX) {
                float echelle = (float) COTE_MAX / longCote;
                cible = Bitmap.createScaledBitmap(source,
                        Math.max(1, Math.round(
                                source.getWidth() * echelle)),
                        Math.max(1, Math.round(
                                source.getHeight() * echelle)),
                        true);
            }
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            if (!cible.compress(Bitmap.CompressFormat.JPEG,
                    QUALITE_JPEG, sortie)) {
                return null;
            }
            byte[] reduits = sortie.toByteArray();
            if (reduits.length == 0
                    || reduits.length > budget) {
                // encore au-dessus du budget après réduction : refus
                // honnête, jamais une image muette en silence
                return null;
            }
            return new ImageReduite("image/jpeg", reduits);
        } finally {
            source.recycle();
        }
    }

    /**
     * La chaîne base64 d'une image réduite (l'attache appelle ceci
     * après {@link ReductionImages#reduire}).
     *
     * @param octets les octets réduits
     * @return le base64
     */
    public static String base64(byte[] octets) {
        return Base64.getEncoder().encodeToString(octets);
    }

    /** Le type de média d'une extension courante (défaut png). */
    public static String mediaTypeDe(String nom) {
        String bas = nom == null ? "" : nom.toLowerCase(Locale.ROOT);
        if (bas.endsWith(".jpg") || bas.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (bas.endsWith(".webp")) {
            return "image/webp";
        }
        if (bas.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }
}
