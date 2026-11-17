package jo.joide.agent;

import java.util.Base64;
import java.util.Locale;

/**
 * La réduction des images CÔTÉ CLIENT (lot Q, ADR-062) : une image
 * qui dépasse le budget d'octets coûte cher à chaque requête
 * (l'historique est relu en entier à chaque itération) — elle se
 * réduit AVANT d'entrer dans la conversation, jamais après (une
 * partie IMAGE insérée est à sa taille finale, l'historique ne se
 * réécrit pas).
 *
 * <p>Le ré-encodage réel (mise à l'échelle) appartient à la
 * plateforme de l'hôte — BitmapFactory côté Android — : il passe par
 * le port {@link Reducteur} injecté. Sans réducteur (ou sur échec),
 * le comportement est HONNÊTE et borné : l'image sous budget part
 * telle quelle ; l'image hors budget n'entre jamais dans la
 * conversation, remplacée par une note qui dit ce qui s'est passé —
 * jamais une omission muette.</p>
 */
public final class ReductionImages {

    /**
     * Le budget d'octets DÉCODÉS par image (le base64 compte ~4/3) :
     * au-delà, l'image doit passer par le réducteur ou ne pas entrer.
     */
    public static final int BUDGET_OCTETS_DEFAUT = 1_500_000;

    /**
     * Le port de ré-encodage de l'hôte : reçoit le type de média et
     * les octets décodés, rend les octets réduits et leur type —
     * implémenté côté application, jamais ici (aucun Android).
     */
    public interface Reducteur {

        /** Le résultat d'un ré-encodage : octets + type de média. */
        final class ImageReduite {

            /** Le type de média RÉSULTANT (ex. jpeg si ré-encodé). */
            public final String mediaType;
            /** Les octets réduits. */
            public final byte[] octets;

            /** @param mediaType le type résultant @param octets les octets */
            public ImageReduite(String mediaType, byte[] octets) {
                this.mediaType = mediaType;
                this.octets = octets;
            }
        }

        /**
         * @param mediaType le type de média reçu
         * @param octets    les octets décodés
         * @param budget    le budget d'octets à viser
         * @return l'image réduite, ou null si impossible
         */
        ImageReduite reduire(String mediaType, byte[] octets,
                             int budget);
    }

    /** Le résultat d'une réduction : l'image réduite, ou une note. */
    public static final class ResultatImage {

        /** L'image réduite (null si la note remplace). */
        public final PartieContenu image;
        /** La note honnête qui remplace (null si l'image passe). */
        public final PartieContenu note;

        private ResultatImage(PartieContenu image,
                              PartieContenu note) {
            this.image = image;
            this.note = note;
        }
    }

    private ReductionImages() {
    }

    /**
     * Réduit une image au budget, par le réducteur de l'hôte.
     *
     * @param mediaType le type de média reçu
     * @param base64    les octets encodés base64
     * @param reducteur le ré-encodeur de l'hôte (null : aucun)
     * @param budget    le budget d'octets décodés
     * @return l'image réduite, ou la note qui la remplace
     */
    public static ResultatImage reduire(String mediaType, String base64,
                                        Reducteur reducteur, int budget) {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return note(mediaType, "invalid base64 image data");
        }
        if (octets.length <= budget) {
            return new ResultatImage(
                    PartieContenu.image(mediaType, base64), null);
        }
        if (reducteur == null) {
            return note(mediaType, octets.length + " bytes exceeds the "
                    + budget + " byte budget and no image reducer is "
                    + "installed");
        }
        Reducteur.ImageReduite reduite;
        try {
            reduite = reducteur.reduire(mediaType, octets, budget);
        } catch (Exception e) {
            return note(mediaType, "image reduction failed: "
                    + String.valueOf(e.getMessage()));
        }
        if (reduite == null || reduite.octets == null
                || reduite.octets.length == 0) {
            return note(mediaType, "image could not be reduced");
        }
        return new ResultatImage(PartieContenu.image(
                String.valueOf(reduite.mediaType).toLowerCase(Locale.ROOT),
                Base64.getEncoder().encodeToString(reduite.octets)),
                null);
    }

    /** La note honnête qui remplace une image non servable. */
    private static ResultatImage note(String mediaType, String motif) {
        return new ResultatImage(null, PartieContenu.texte("[image of "
                + "type " + mediaType + " omitted: " + motif + "]"));
    }

    /**
     * @param base64 les octets encodés
     * @return la taille décodée approximative (sans décoder)
     */
    public static long tailleDecodeeApprox(String base64) {
        int longueur = base64 == null ? 0 : base64.length();
        int padding = 0;
        if (longueur > 0 && base64.endsWith("==")) {
            padding = 2;
        } else if (longueur > 0 && base64.endsWith("=")) {
            padding = 1;
        }
        return Math.max(0, longueur / 4 * 3 - padding);
    }
}
