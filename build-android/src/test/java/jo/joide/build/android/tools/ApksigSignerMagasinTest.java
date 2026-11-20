package jo.joide.build.android.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lot C11 — le magasin du signataire s'ouvre par TYPE EXPLICITE : le
 * terrain du 2026-11-20 rapportait « :app:sign[debug] : signing failed
 * — Unrecognized keystore format, please load with a specific type ».
 * Le lecteur de l'appareil n'installe que PKCS12 et BKS et ne
 * déchiffre pas un PKCS12 moderne (PBES2, ce qu'écrit keytool depuis
 * Java 12) : la sonde du JDK n'y reconnaissait rien et l'erreur ne
 * nommait ni la cause ni le remède. Ces tests tiennent les terrains
 * de la machine virtuelle : le PKCS12 moderne s'ouvre par le type
 * installé, le JKS s'ouvre par le sien, un fichier aliéné rend un
 * diagnostic qui liste les types essayés ET le remède (regénérer en
 * PKCS12 compatible), un fichier absent se dit absent.
 */
class ApksigSignerMagasinTest {

    /** Le mot de passe du terrain (celui du BuildRunner). */
    private static final char[] MOT_DE_PASSE = "joidepass".toCharArray();

    /** keytool invoqué comme le harnais du pipeline d'acceptation. */
    private static void generer(Path keystore, String... options)
            throws IOException, InterruptedException {
        List<String> commande = new ArrayList<>();
        commande.add(Path.of(System.getProperty("java.home"), "bin",
                "keytool").toString());
        commande.add("-genkeypair");
        commande.add("-keystore");
        commande.add(keystore.toString());
        for (String option : options) {
            commande.add(option);
        }
        commande.addAll(List.of(
                "-storepass", "joidepass",
                "-keypass", "joidepass",
                "-alias", "joide",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "100",
                "-dname", "CN=JoIDE Test,O=JoIDE,C=FR"));
        Process processus = new ProcessBuilder(commande)
                .inheritIO().start();
        assertEquals(0, processus.waitFor(), "keytool doit réussir");
    }

    /**
     * Un PKCS12 MODERNE (keytool sans option, la manière d'écriture
     * actuelle) s'ouvre par le type PKCS12 installé — sur la machine
     * virtuelle le lecteur déchiffre PBES2 ; sur l'appareil c'est le
     * fournisseur réflexif qui le prend si le lecteur installé ne
     * peut pas. La chaîne de types garantit l'ouverture des deux
     * côtés.
     */
    @Test
    void pkcs12ModerneSouvreParLeType(@TempDir Path temp) throws Exception {
        Path magasin = temp.resolve("moderne.keystore");
        generer(magasin);
        KeyStore ouvert = ApksigSigner.ouvrirMagasin(magasin,
                MOT_DE_PASSE);
        assertTrue(ouvert.containsAlias("joide"));
    }

    /** Un JKS s'ouvre par son type sur la machine virtuelle. */
    @Test
    void jksSouvreParLeType(@TempDir Path temp) throws Exception {
        Path magasin = temp.resolve("ancien.jks");
        generer(magasin, "-storetype", "JKS");
        KeyStore ouvert = ApksigSigner.ouvrirMagasin(magasin,
                MOT_DE_PASSE);
        assertTrue(ouvert.containsAlias("joide"));
    }

    /**
     * Un fichier aliéné rend un diagnostic honnête : les types essayés
     * se listent, le remède du terrain se dit — jamais le message muet
     * du terrain (« Unrecognized keystore format », sans cause ni
     * remède).
     */
    @Test
    void fichierAlienDiagnostiqueEtRemedie(@TempDir Path temp)
            throws Exception {
        Path magasin = temp.resolve("aliene.keystore");
        Files.write(magasin, new byte[] {'P', 'A', 'S', 0, 1, 2, 3, 4,
                5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        IOException erreur = assertThrows(IOException.class,
                () -> ApksigSigner.ouvrirMagasin(magasin, MOT_DE_PASSE));
        assertTrue(erreur.getMessage().contains("PKCS12"),
                () -> erreur.getMessage());
        assertTrue(erreur.getMessage().contains("legacy"),
                () -> erreur.getMessage());
    }

    /** Un fichier absent se dit absent, avec son chemin. */
    @Test
    void fichierAbsentSeDitAbsent(@TempDir Path temp) {
        Path magasin = temp.resolve("nul.part");
        IOException erreur = assertThrows(IOException.class,
                () -> ApksigSigner.ouvrirMagasin(magasin, MOT_DE_PASSE));
        assertTrue(erreur.getMessage().contains("not found"),
                () -> erreur.getMessage());
        assertTrue(erreur.getMessage().contains("nul.part"),
                () -> erreur.getMessage());
    }
}
