package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * La réduction des images côté client (lot Q) : sous budget l'image
 * part telle quelle, hors budget elle passe par le réducteur de
 * l'hôte, et sans réducteur (ou sur échec) elle est remplacée par
 * une note honnête — jamais une omission muette.
 */
class ReductionImagesTest {

    private static String base64De(int octets) {
        byte[] donnees = new byte[octets];
        java.util.Arrays.fill(donnees, (byte) 'A');
        return Base64.getEncoder().encodeToString(donnees);
    }

    @Test
    void uneImageSousBudgetPartTelleQuelle() {
        String base64 = base64De(100);
        ReductionImages.ResultatImage resultat = ReductionImages
                .reduire("image/png", base64, null, 1_000);
        assertNotNull(resultat.image);
        assertNull(resultat.note);
        assertEquals("image/png", resultat.image.mediaType);
        assertEquals(base64, resultat.image.donnees);
    }

    @Test
    void uneImageHorsBudgetSansReducteurEstRemplaceeParUneNote() {
        ReductionImages.ResultatImage resultat = ReductionImages
                .reduire("image/png", base64De(5_000), null, 1_000);
        assertNull(resultat.image);
        assertNotNull(resultat.note);
        assertTrue(resultat.note.texte.contains("omitted"),
                "la note dit ce qui s'est passé : "
                        + resultat.note.texte);
        assertTrue(resultat.note.texte.contains("5000 bytes"),
                "la note porte la taille réelle");
    }

    @Test
    void leReducteurDeLhoteReduitEtRenommeLeType() {
        ReductionImages.Reducteur reducteur = (mediaType, octets,
                                               budget) -> {
            byte[] reduits = new byte[budget / 2];
            return new ReductionImages.Reducteur.ImageReduite(
                    "image/jpeg", reduits);
        };
        ReductionImages.ResultatImage resultat = ReductionImages
                .reduire("image/png", base64De(10_000), reducteur,
                        1_000);
        assertNotNull(resultat.image);
        assertNull(resultat.note);
        assertEquals("image/jpeg", resultat.image.mediaType,
                "le type RÉSULTANT du réducteur fait foi");
        assertTrue(resultat.image.donnees.length() < 1_000);
    }

    @Test
    void unReducteurQuiEchoueProduitUneNoteHonnete() {
        ReductionImages.Reducteur echec = (mediaType, octets,
                                           budget) -> {
            throw new IllegalStateException("decoder missing");
        };
        ReductionImages.ResultatImage resultat = ReductionImages
                .reduire("image/png", base64De(10_000), echec, 1_000);
        assertNull(resultat.image);
        assertTrue(resultat.note.texte.contains("reduction failed"));
        // et un réducteur qui rend null
        ReductionImages.ResultatImage nul = ReductionImages.reduire(
                "image/png", base64De(10_000),
                (mediaType, octets, budget) -> null, 1_000);
        assertTrue(nul.note.texte.contains("could not be reduced"));
    }

    @Test
    void unBase64CasseNeLeveJamais() {
        ReductionImages.ResultatImage resultat = ReductionImages
                .reduire("image/png", "@@@pas-du-base64@@@", null,
                        1_000);
        assertNull(resultat.image);
        assertTrue(resultat.note.texte.contains("invalid base64"));
    }

    @Test
    void laTailleDecodeeApproxSeCalculeSansDecoder() {
        String base64 = base64De(3_000);
        assertEquals(3_000,
                ReductionImages.tailleDecodeeApprox(base64));
    }
}
