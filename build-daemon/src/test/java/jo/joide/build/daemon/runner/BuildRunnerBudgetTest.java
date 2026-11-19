package jo.joide.build.daemon.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le budget d'admission s'AJUSTE au tas, jamais un refus (lot U/7) :
 * le rapport du 2026-11-17 — « build refused: memory budget 448 MiB
 * exceeds daemon heap 256 MiB » — fermait le build entier sur les
 * appareils dont le plafond de tas du manifeste est 256 Mio. La
 * manière éprouvée : le matériel borne, l'outil s'adapte.
 */
class BuildRunnerBudgetTest {

    @Test
    void leBudgetSousLeTasPasseTelQuel() {
        // 16 Mio : bien sous le tas de n'importe quelle JVM de test
        assertEquals(16, BuildRunner.budgetEffectif(16));
    }

    @Test
    void leBudgetAuDessusDuTasSePlafonneAuTas() {
        // demande astronomique : le plafond EST le tas réel de la JVM
        int effectif = BuildRunner.budgetEffectif(
                Integer.MAX_VALUE / (1024 * 1024));
        long tasMio = Runtime.getRuntime().maxMemory() / 1048576L;
        assertEquals((int) Math.max(1, tasMio), effectif);
        assertTrue(effectif >= 1);
    }

    @Test
    void leBudgetEffectifNeDepasseJamaisLeTas() {
        for (int demande : new int[]{16, 64, 256, 448, 512, 1024}) {
            long budgetBytes = BuildRunner.budgetEffectif(demande)
                    * 1024L * 1024L;
            assertTrue(budgetBytes <= Runtime.getRuntime().maxMemory(),
                    "demande " + demande + " Mio doit s'ajuster au tas");
        }
    }
}
