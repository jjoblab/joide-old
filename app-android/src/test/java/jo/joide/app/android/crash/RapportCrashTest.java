package jo.joide.app.android.crash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lot C1 — le test du RAPPORT DE CRASH (cœur pur du garde-crash) : le
 * « détecter tout le type crash » se vérifie SANS appareil — la
 * classification des genres (fracture avec sa classe exacte, gel de
 * l'interface), le format {@code joide.crash/1} (en-tête, identité,
 * piles, métadonnées), la cause première des enveloppes, le nom de
 * fichier classé par moment.
 */
class RapportCrashTest {

    @Test
    void uneFracturePorteSaClasseExacteEtSaCausePremiere() {
        RuntimeException cause = new RuntimeException("la racine");
        IllegalStateException enveloppe =
                new IllegalStateException("l'enveloppe", cause);
        RapportCrash rapport = RapportCrash.de(1000L, "joide-atelier",
                enveloppe, "");
        assertEquals(RapportCrash.Genre.FRACTURE, rapport.genre);
        // la CLASSE EXACTE du type (pas « erreur inconnue ») — la
        // moitié de la demande « tout le type crash »
        assertEquals("java.lang.RuntimeException", rapport.classe);
        assertEquals("la racine", rapport.message);
        assertEquals("joide-atelier", rapport.fil);
    }

    @Test
    void uneFractureSansPanneSeDitThrowableJamaisMuet() {
        RapportCrash rapport = RapportCrash.de(2L, "fil", null, "");
        assertEquals(RapportCrash.Genre.FRACTURE, rapport.genre);
        assertEquals("Throwable", rapport.classe);
    }

    @Test
    void unGelDeInterfacePorteSonGenrePropreEtSaPile() {
        RapportCrash rapport = RapportCrash.gel(3L, "  at bloquee()\n",
                8);
        assertEquals(RapportCrash.Genre.GEL_INTERFACE, rapport.genre);
        assertTrue(rapport.libelleType().contains("ANR"),
                "le libellé du type de gel dit sa classe (ANR) : "
                        + rapport.libelleType());
        assertTrue(rapport.message.contains("8 s"),
                "le seuil du gel se dit honnêtement : " + rapport.message);
        // la pile DU FIL PRINCIPAL est le diagnostic du gel
        assertTrue(rapport.pile.contains("bloquee()"));
    }

    @Test
    void leTextePorteEnteteIdentitePilesEtMetadonnees() {
        NullPointerException panne =
                new NullPointerException("absent");
        RapportCrash rapport = RapportCrash.de(0L, "main", panne,
                "  at android.app.Activity.onCreate()\n");
        String texte = rapport.texte("1.2.3 (abc)", "15 (API 35)",
                "Fabriquant Modèle");
        assertTrue(texte.startsWith(RapportCrash.ENTETE + "\n"));
        assertTrue(texte.contains("genre: exception non interceptée"));
        assertTrue(texte.contains("classe: java.lang.NullPointerException"));
        assertTrue(texte.contains("message: absent"));
        assertTrue(texte.contains("version: 1.2.3 (abc)"));
        assertTrue(texte.contains("android: 15 (API 35)"));
        assertTrue(texte.contains("appareil: Fabriquant Modèle"));
        assertTrue(texte.contains("--- pile du plantage ---"));
        // un plantage du fil PRINCIPAL ne répète pas sa pile (elle
        // EST la pile du plantage) ; un plantage d'un AUTRE fil
        // emporte la pile du fil principal — vérifiée ici à part
        RapportCrash fond = RapportCrash.de(0L, "joide-import", panne,
                "  at android.app.Activity.onCreate()\n");
        assertTrue(fond.texte("v", "a", "m")
                .contains("--- pile du fil principal ---"));
    }

    @Test
    void leNomDeFichierClasseParMomentDeFaconDeterministe() {
        RapportCrash rapport = RapportCrash.de(0L, "main", null, "");
        assertTrue(rapport.nomFichier().startsWith("crash-"));
        assertTrue(rapport.nomFichier().endsWith(".txt"));
        assertEquals(rapport.nomFichier(),
                RapportCrash.de(0L, "main", null, "").nomFichier(),
                "le même moment porte le même nom : la purge par nom "
                        + "fonctionne");
    }

    @Test
    void leRapportSecritEtSeRelitTelQuel(@TempDir Path bac)
            throws IOException {
        RapportCrash rapport = RapportCrash.de(0L, "main",
                new ArithmeticException("/ par zéro"), "");
        Path fichier = bac.resolve(rapport.nomFichier());
        Files.writeString(fichier, rapport.texte("v", "a", "m"));
        assertEquals(rapport.texte("v", "a", "m"),
                Files.readString(fichier));
        assertFalse(Files.readString(fichier).isEmpty());
    }
}
