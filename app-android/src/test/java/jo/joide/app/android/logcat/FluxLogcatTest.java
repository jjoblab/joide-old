package jo.joide.app.android.logcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** ADR-084 — le tampon du relais : le protocole « N|Tag|Message »,
 *  les lignes mal formées sautées, le plafond annoncé, la lecture qui
 *  vide, l'auditeur notifié à chaque dépôt. */
class FluxLogcatTest {

    @Test
    void leProtocoleSeParseEtLesLignesMalFormeesSontSautees() {
        assertEquals("Tag", FluxLogcat.parser(
                "E|Tag|NullPointerException").tag());
        assertEquals("NullPointerException", FluxLogcat.parser(
                "E|Tag|NullPointerException").message());
        assertNull(FluxLogcat.parser("X|Tag|niveau inconnu"));
        assertNull(FluxLogcat.parser("E sans barres"));
        assertNull(FluxLogcat.parser("E|Tag sans message"));
        assertNull(FluxLogcat.parser(null));
        assertEquals("W", FluxLogcat.parser("W|T|m").niveau().name());
    }

    @Test
    void leTamponSeBorneEtLaLectureVide() {
        FluxLogcat flux = new FluxLogcat();
        for (int i = 0; i < FluxLogcat.PLAFOND_TAMPON + 10; i++) {
            flux.deposer(List.of("I|T|ligne " + i));
        }
        assertEquals(FluxLogcat.PLAFOND_TAMPON, flux.enAttente());
        List<FluxLogcat.LigneRelais> lues = flux.lire();
        assertEquals(FluxLogcat.PLAFOND_TAMPON, lues.size());
        assertEquals("ligne 10", lues.get(0).message());
        assertEquals(0, flux.enAttente());
        assertTrue(flux.lire().isEmpty());
    }

    @Test
    void lesLignesMalFormeesNeBloquentPasLeLot() {
        FluxLogcat flux = new FluxLogcat();
        flux.deposer(List.of("E|A|boom", "pourriture", "I|B|ok"));
        assertEquals(2, flux.enAttente());
        assertEquals("boom", flux.lire().get(0).message());
    }

    @Test
    void lAuditeurEstNotifieSurChaqueDepot() {
        FluxLogcat flux = new FluxLogcat();
        AtomicInteger coups = new AtomicInteger();
        flux.poserAuditeur(coups::incrementAndGet);
        flux.deposer(List.of("I|T|un"));
        flux.deposer(List.of("I|T|deux", "I|T|trois"));
        assertEquals(2, coups.get());
        flux.poserAuditeur(null);
        flux.deposer(List.of("I|T|quatre"));
        assertEquals(2, coups.get());
    }
}
