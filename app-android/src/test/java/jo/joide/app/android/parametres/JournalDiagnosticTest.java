package jo.joide.app.android.parametres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Le journal de diagnostic du lot M : lecture par commande injectée
 * (logcat au bac à sable des tests), bornage aux 400 dernières
 * lignes, lignes vides retirées, longues lignes tronquées avec
 * annonce — jamais un faux journal silencieux.
 */
class JournalDiagnosticTest {

    @Test
    void laLecturePasseParLaCommandeInjectee() throws IOException {
        String brut = JournalDiagnostic.lire(() -> new ByteArrayInputStream(
                "première\ndeuxième".getBytes(StandardCharsets.UTF_8)));
        assertEquals("première\ndeuxième", brut);
    }

    @Test
    void leBornageGardeLesDernieresLignes() {
        StringBuilder lignes = new StringBuilder();
        for (int i = 1; i <= 500; i++) {
            lignes.append("ligne ").append(i).append('\n');
        }
        List<String> gardees = JournalDiagnostic.borner(
                lignes.toString());
        assertEquals(JournalDiagnostic.PLAFOND_LIGNES, gardees.size());
        assertEquals("ligne 101", gardees.get(0),
                "les plus anciennes partent, jamais les récentes");
        assertEquals("ligne 500", gardees.get(gardees.size() - 1));
    }

    @Test
    void lesLignesVidesEtBlanchesDisparaissent() {
        List<String> gardees = JournalDiagnostic.borner("a\n \n\t\nb\n\n");
        assertEquals(List.of("a", "b"), gardees);
    }

    @Test
    void unJournalVideResteVide() {
        assertTrue(JournalDiagnostic.borner("").isEmpty());
        assertTrue(JournalDiagnostic.borner(null).isEmpty());
    }

    @Test
    void uneLigneTropLongueEstTronqueeEtAnnoncee() {
        StringBuilder longue = new StringBuilder();
        for (int i = 0; i < JournalDiagnostic.PLAFOND_LIGNE + 10; i++) {
            longue.append('x');
        }
        List<String> gardees = JournalDiagnostic.borner(
                longue.toString());
        assertEquals(1, gardees.size());
        assertEquals(JournalDiagnostic.PLAFOND_LIGNE + 1,
                gardees.get(0).length(),
                "tronquée à 2000 caractères + l'ellipse annoncée");
        assertTrue(gardees.get(0).endsWith("…"));
    }

    @Test
    void uneCommandeQuiEchoueEstUneIOExceptionNommee() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> JournalDiagnostic.lire(
                        () -> {
                            throw new IOException("logcat unavailable");
                        }));
    }
}
