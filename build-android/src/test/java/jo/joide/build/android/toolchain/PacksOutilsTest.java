package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;

/**
 * Les packs d'outils différés (2026-11-17) : le mappage service →
 * pack (D8/R8 d'un côté, bundletool de l'autre, apksig embarqué) et
 * l'HONNÊTETÉ des constantes épinglées — une taille nulle ou une
 * empreinte qui n'est pas 64 chiffres hexa veut dire « pack jamais
 * régénéré après un changement de version » : le refus est ici, pas
 * sur l'appareil d'un utilisateur.
 */
class PacksOutilsTest {

    @Test
    void leDexerVientDuPackR8LeBundleDuPackBundletool() {
        assertEquals(PacksOutils.R8, PacksOutils.packDe(Dexer.class));
        assertEquals(PacksOutils.BUNDLETOOL,
                PacksOutils.packDe(BundleAssembler.class));
        // apksig reste EMBARQUÉ : trop léger pour un pack — aucun
        // pack ne le sert, jamais de téléchargement inutile
        assertNull(PacksOutils.packDe(
                jo.joide.build.android.sign.ApkSignerService.class));
    }

    @Test
    void lesPacksPortentLeNomEtLurlDeLeurVersion() {
        for (PacksOutils.Pack pack : new PacksOutils.Pack[]{
                PacksOutils.R8, PacksOutils.BUNDLETOOL}) {
            assertTrue(pack.fichier.endsWith(".dex.zip"), pack.fichier);
            assertTrue(pack.fichier.contains(pack.version), pack.fichier);
            assertTrue(pack.url.endsWith(pack.fichier), pack.url);
        }
        assertEquals("r8-" + ToolchainLocator.R8_VERSION + ".dex.zip",
                PacksOutils.R8.fichier);
    }

    @Test
    void lesEmpreintesEpingleesSontPleines() {
        // 64 chiffres hexa, jamais un marqueur de génération oublié :
        // la tâche :app-android:dexPacksOutils échoue déjà sur la
        // divergence, ce test protège le dépôt entre deux générations
        assertNotNull(PacksOutils.R8.sha256);
        assertTrue(PacksOutils.R8.sha256.matches("[0-9a-f]{64}"),
                "SHA_R8 non renseigné : lancez "
                        + ":app-android:dexPacksOutils et reportez "
                        + "les valeurs");
        assertTrue(PacksOutils.BUNDLETOOL.sha256
                .matches("[0-9a-f]{64}"),
                "SHA_BUNDLETOOL non renseigné : lancez "
                        + ":app-android:dexPacksOutils et reportez "
                        + "les valeurs");
        assertTrue(PacksOutils.R8.taille > 0, "TAILLE_R8 non renseignée");
        assertTrue(PacksOutils.BUNDLETOOL.taille > 0,
                "TAILLE_BUNDLETOOL non renseignée");
    }
}
