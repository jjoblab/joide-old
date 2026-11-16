package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.NouveauNom.Raison;

/**
 * La validation des nouveaux noms du tiroir (lot J) : un nom refusé
 * ne crée jamais rien sur le disque.
 */
class NouveauNomTest {

    @Test
    void nomsRecevables() {
        assertNull(NouveauNom.valider("MainActivity.kt"));
        assertNull(NouveauNom.valider("layout"));
        assertNull(NouveauNom.valider(".gitignore"));
        assertNull(NouveauNom.valider("notes v2 (brouillon).md"));
        assertNull(NouveauNom.valider("a".repeat(100)));
    }

    @Test
    void refusMotives() {
        assertEquals(Raison.VIDE, NouveauNom.valider(null));
        assertEquals(Raison.VIDE, NouveauNom.valider(""));
        assertEquals(Raison.TROP_LONG,
                NouveauNom.valider("a".repeat(101)));
        assertEquals(Raison.SEPARATEUR,
                NouveauNom.valider("app/../cible.txt"),
                "un chemin déguisé est refusé");
        assertEquals(Raison.SEPARATEUR,
                NouveauNom.valider("sous\\dossier"));
        assertEquals(Raison.POINT, NouveauNom.valider("."));
        assertEquals(Raison.POINT, NouveauNom.valider(".."));
        assertEquals(Raison.CONTROLE,
                NouveauNom.valider("a\nb"));
        assertEquals(Raison.POINT_FINAL,
                NouveauNom.valider("brouillon."));
    }
}
