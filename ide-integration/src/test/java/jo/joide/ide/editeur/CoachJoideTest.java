package jo.joide.ide.editeur;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Le coach de repli : mots réservés des langages servis, badges des
 * mots du document (réservé → mot-clé, autre → variable), et les
 * silences honnêtes (documentation et signature locales absentes —
 * le serveur passe premier).
 */
final class CoachJoideTest {

    private final CoachJoide coach = new CoachJoide();

    @Test
    void lesMotsReservesCouvrentLesTroisLangages() {
        List<String> mots = coach.motsReserves();
        assertTrue(mots.contains("public"), "Java");
        assertTrue(mots.contains("record"), "Java moderne");
        assertTrue(mots.contains("fun"), "Kotlin");
        assertTrue(mots.contains("val"), "Kotlin");
        assertTrue(mots.contains("TextView"), "XML");
    }

    @Test
    void lesMotsReservesSontUniques() {
        List<String> mots = coach.motsReserves();
        assertEquals(mots.size(),
                mots.stream().distinct().count(),
                "aucun doublon dans la table du coach");
    }

    @Test
    void leBadgeDistingueReserveEtMotDuDocument() {
        var candidats = coach.candidats(List.of("public", "maVariable"));
        assertEquals(2, candidats.size());
        assertEquals(jo.celeris.android.widgets.BadgeGenre.Genre.MOT_CLE,
                candidats.get(0).genre());
        assertEquals(
                jo.celeris.android.widgets.BadgeGenre.Genre.VARIABLE,
                candidats.get(1).genre());
        assertEquals("maVariable", candidats.get(1).libelle());
    }

    @Test
    void documentationEtSignatureLocalesAbsentes() {
        assertNull(coach.docDe("nimporte"));
        assertNull(coach.signatureDe(
                jo.celeris.core.text.Document.of("class A {}"), 3));
    }
}
