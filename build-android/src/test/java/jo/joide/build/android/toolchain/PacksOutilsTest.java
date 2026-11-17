package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;
import jo.joide.platform.Hashing;

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

    /** Lot XIII — le fichier retourné par {@code fichierAssure} est
     *  TOUJOURS en LECTURE SEULE (la politique W^X d'ART : un dex/jar
     *  MODIFIABLE n'entre PAS dans un chargeur de classes — la
     *  manière CodeAssist) : un pack posé modifiable sur le disque
     *  par une version antérieure (le terrain : « SecurityException:
     *  Writable dex file … is not allowed », abort 134 au démarrage
     *  de la VM ouvrière) se répare AU CHARGEMENT, sans
     *  re-téléchargement, et le contenu n'a pas bougé. */
    @Test
    void lePackAssureEstEnLectureSeule(@TempDir Path dossier)
            throws Exception {
        Path cache = dossier.resolve("toolchain");
        String ancienDir = System.getProperty("joide.toolchain.dir");
        System.setProperty("joide.toolchain.dir", cache.toString());
        try {
            // le pack d'essai : épinglage SAIN par le SHA réel du
            // contenu (aucun téléchargement, jamais de réseau ici)
            byte[] contenu = "dex de test joide"
                    .getBytes(StandardCharsets.UTF_8);
            PacksOutils.Pack faux = new PacksOutils.Pack("essai", "1.0",
                    "essai-1.0.dex.zip", contenu.length,
                    Hashing.toHex(Hashing.sha256(contenu)),
                    "https://exemple.invalid/essai-1.0.dex.zip");
            Path local = PacksOutils.fichierLocal(faux);
            Files.createDirectories(local.getParent());
            Files.write(local, contenu);
            assertTrue(local.toFile().canWrite(),
                    "le pack doit être posé MODIFIABLE, comme une "
                            + "version antérieure le faisait");
            assertTrue(PacksOutils.present(faux),
                    "le pack d'essai doit être sain");

            Path assure = PacksOutils.fichierAssure(faux);
            assertEquals(local, assure);
            assertFalse(assure.toFile().canWrite(),
                    "un pack posé modifiable doit passer en lecture "
                            + "seule au chargement (W^X d'ART)");

            // idempotent : un second passage ne lève ni ne le rend
            // modifiable
            PacksOutils.fichierAssure(faux);
            assertFalse(assure.toFile().canWrite());
            assertArrayEquals(contenu, Files.readAllBytes(assure),
                    "le contenu du pack n'a pas à bouger");
        } finally {
            if (ancienDir != null) {
                System.setProperty("joide.toolchain.dir", ancienDir);
            } else {
                System.clearProperty("joide.toolchain.dir");
            }
        }
    }

    /** Lot XIII — l'imposition de lecture seule est « au mieux » :
     *  un fichier absent ne lève rien (le chargeur en processus
     *  s'en accommode — même tolérance que ChargeurDexAndroid), un
     *  fichier modifiable passe en lecture seule. */
    @Test
    void imposerLectureSeuleEstAuMieux(@TempDir Path dossier)
            throws Exception {
        PacksOutils.imposerLectureSeule(
                dossier.resolve("absent.dex.zip"));
        Path fichier = dossier.resolve("fichier.dex.zip");
        Files.writeString(fichier, "x");
        PacksOutils.imposerLectureSeule(fichier);
        assertFalse(fichier.toFile().canWrite());
    }
}
