package jo.joide.app.android.parametres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Le stockage chiffré des clés éprouvé JVM avec une clé forgée (lot
 * P, ADR-058) : aller-retour chiffré, IV aléatoire par écriture
 * (deux poses du même texte diffèrent), l'en-tête se lit, un fichier
 * d'une autre clé est un absent honnête — jamais de clés de secours
 * en clair, jamais de crash de démarrage.
 */
class ClesChiffreesTest {

    @TempDir
    Path bac;

    private SecretKey cle;
    private ClesChiffrees stockage;

    @BeforeEach
    void preparer() throws Exception {
        KeyGenerator generateur = KeyGenerator.getInstance("AES");
        generateur.init(256);
        cle = generateur.generateKey();
        stockage = new ClesChiffrees(cle, bac,
                new SecureRandom());
    }

    @Test
    void allerRetourChiffre() throws Exception {
        stockage.poser("anthropic", "sk-ant-secret-123");
        assertEquals("sk-ant-secret-123", stockage.lire("anthropic"));
        assertTrue(stockage.estPosee("anthropic"));
    }

    @Test
    void deuxFournisseursDeuxFichiers() throws Exception {
        stockage.poser("anthropic", "cleA");
        stockage.poser("openai", "cleO");
        assertEquals("cleA", stockage.lire("anthropic"));
        assertEquals("cleO", stockage.lire("openai"));
        assertFalse(stockage.estPosee("gemini"));
    }

    @Test
    void laCleNeVitJamaisEnClairDansLeFichier() throws Exception {
        stockage.poser("anthropic", "sk-ant-secret-123");
        byte[] contenu = Files.readAllBytes(
                bac.resolve("anthropic.cle"));
        String texte = new String(contenu, StandardCharsets.ISO_8859_1);
        assertFalse(texte.contains("sk-ant-secret-123"),
                "la cle d'API ne doit jamais apparaître en clair");
        assertTrue(texte.startsWith("joide.cles/1"),
                "l'en-tête du format se lit (audit)");
    }

    @Test
    void livEstAleatoireParEcriture() throws Exception {
        stockage.poser("openai", "la-meme-cle");
        byte[] premiere = Files.readAllBytes(bac.resolve("openai.cle"));
        stockage.poser("openai", "la-meme-cle");
        byte[] seconde = Files.readAllBytes(bac.resolve("openai.cle"));
        assertFalse(java.util.Arrays.equals(premiere, seconde),
                "deux ecritures du meme texte doivent différer "
                        + "(IV aléatoire)");
        // ... et pourtant la reliture donne la meme cle
        assertEquals("la-meme-cle", stockage.lire("openai"));
    }

    @Test
    void uneAutreCleEstUnAbsentHonneste() throws Exception {
        stockage.poser("anthropic", "cleA");
        KeyGenerator generateur = KeyGenerator.getInstance("AES");
        generateur.init(256);
        ClesChiffrees autreCle = new ClesChiffrees(
                generateur.generateKey(), bac, new SecureRandom());
        // réinstallation / clé perdue : null, pas d'exception
        assertNull(autreCle.lire("anthropic"));
        assertFalse(autreCle.estPosee("anthropic"));
    }

    @Test
    void unFichierCorrompuEstUnAbsentHonneste() throws Exception {
        stockage.poser("anthropic", "cleA");
        Files.write(bac.resolve("anthropic.cle"),
                "n'importe quoi".getBytes(StandardCharsets.UTF_8));
        assertNull(stockage.lire("anthropic"));
    }

    @Test
    void effacerSupprimeVraiment() throws Exception {
        stockage.poser("anthropic", "cleA");
        stockage.effacer("anthropic");
        assertNull(stockage.lire("anthropic"));
        assertFalse(Files.exists(bac.resolve("anthropic.cle")));
        // effacer l'absent ne casse rien
        stockage.effacer("anthropic");
    }

    @Test
    void remplacerChangeLaCle() throws Exception {
        stockage.poser("openai", "ancienne");
        stockage.poser("openai", "nouvelle");
        assertEquals("nouvelle", stockage.lire("openai"));
    }

    @Test
    void leNomDeFichierNeutraliseLesIdentifiantsEtranges()
            throws Exception {
        stockage.poser("un/fournisseur..etrange", "cle");
        assertTrue(Files.exists(
                bac.resolve("un-fournisseur--etrange.cle")));
        assertEquals("cle",
                stockage.lire("un/fournisseur..etrange"));
    }
}
