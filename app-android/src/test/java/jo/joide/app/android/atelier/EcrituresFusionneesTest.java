package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * L'écriture disque fusionnée multi-fichiers (lot J) : le dernier
 * texte de chaque fichier gagne, jamais d'entrelacement entre onglets.
 */
class EcrituresFusionneesTest {

    @Test
    void leDernierTexteDeChaqueFichierGagne() {
        EcrituresFusionnees ecritures = new EcrituresFusionnees();
        ecritures.poser("a.txt", "a v1");
        ecritures.poser("b.txt", "b v1");
        ecritures.poser("a.txt", "a v2");
        Map<String, String> drainees = ecritures.drainer();
        assertEquals(2, drainees.size());
        assertEquals("a v2", drainees.get("a.txt"),
                "le dernier texte de a gagne");
        assertEquals("b v1", drainees.get("b.txt"));
        assertEquals(0, ecritures.enAttente(),
                "le drain vide l'attente");
    }

    @Test
    void drainerDeuxFoisNeReecritPas() {
        EcrituresFusionnees ecritures = new EcrituresFusionnees();
        ecritures.poser("a.txt", "texte");
        assertEquals(1, ecritures.drainer().size());
        assertEquals(0, ecritures.drainer().size(),
                "déjà drainée : rien ne se réécrit");
    }

    @Test
    void posePendableDrainRepartAuTourSuivant() {
        EcrituresFusionnees ecritures = new EcrituresFusionnees();
        ecritures.poser("a.txt", "a");
        ecritures.poser("b.txt", "b");
        Map<String, String> premier = ecritures.drainer();
        ecritures.poser("c.txt", "c");
        assertEquals(Map.of("a.txt", "a", "b.txt", "b"), premier);
        assertEquals(Map.of("c.txt", "c"), ecritures.drainer());
    }

    @Test
    void entreesInvalidesIgnorees() {
        EcrituresFusionnees ecritures = new EcrituresFusionnees();
        ecritures.poser(null, "x");
        ecritures.poser("", "x");
        ecritures.poser("a.txt", null);
        assertEquals(0, ecritures.enAttente());
    }
}
