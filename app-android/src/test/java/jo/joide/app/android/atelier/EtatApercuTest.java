package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.EtatApercu.Banniere;
import jo.joide.app.android.atelier.EtatApercu.Genre;
import jo.joide.app.android.atelier.EtatApercu.Gravite;

/**
 * Les bannieres de franchise de l'apercu (lot L, ADR-É7) : pendant la
 * frappe le dernier rendu valide est conserve et DIT, une ressource
 * manquante passe au repli natif et le DIT, une classe inconnue rend
 * une MockView sans échec, l'échec de rendu garde la surface — le
 * volet ne devient jamais blanc ni faux. Les stats sont mesurees,
 * jamais inventees.
 */
class EtatApercuTest {

    @Test
    void renduNominalStatsMesureesAucuneBanniere() {
        EtatApercu etat = new EtatApercu();
        assertTrue(etat.bannieres().isEmpty(),
                "rendu nominal : pas de bannière");
        assertFalse(etat.renduValide());
        etat.surSucces(42, 38L, 720, 1280);
        assertTrue(etat.renduValide());
        assertEquals(42, etat.stats().vues());
        assertEquals(38L, etat.stats().dureeMs());
        assertEquals(720, etat.stats().largeur());
        assertEquals(1280, etat.stats().hauteur());
    }

    @Test
    void pendantLaFrappeLeDernierRenduValideEstConserveEtDit() {
        EtatApercu etat = new EtatApercu();
        etat.surSucces(42, 38L, 720, 1280);
        etat.surXmlInvalide();
        assertTrue(etat.renduValide(),
                "le rendu valide RESTE : le volet ne blanchit jamais");
        List<Banniere> bannieres = etat.bannieres();
        assertEquals(1, bannieres.size());
        assertEquals(Gravite.INFO, bannieres.get(0).gravite());
        assertEquals(Genre.FRAPPE, bannieres.get(0).genre());
        assertEquals("42", bannieres.get(0).detail(),
                "la bannière cite les vues du rendu conservé");
        // un rendu reussi sort de la frappe
        etat.surSucces(43, 41L, 720, 1280);
        assertTrue(etat.bannieres().isEmpty());
    }

    @Test
    void ressourceManquanteResteTantQuElleManque() {
        EtatApercu etat = new EtatApercu();
        etat.surSucces(10, 5L, 720, 1280);
        etat.surRessourcesManquantes(
                List.of("@color/accent", "@string/bienvenue"));
        List<Banniere> bannieres = etat.bannieres();
        assertEquals(1, bannieres.size());
        assertEquals(Gravite.ALERTE, bannieres.get(0).gravite());
        assertEquals(Genre.RESSOURCES, bannieres.get(0).genre());
        assertEquals("@color/accent, @string/bienvenue",
                bannieres.get(0).detail());
        assertTrue(etat.renduValide(),
                "le rendu continue avec le repli — pas d'échec");
        etat.surRessourcesManquantes(List.of());
        assertTrue(etat.bannieres().isEmpty(),
                "la bannière reste seulement tant que ça manque");
    }

    @Test
    void mockViewEstUnEchecNominalPasBloquant() {
        EtatApercu etat = new EtatApercu();
        etat.surSucces(7, 12L, 720, 1280);
        etat.surMockViews(1);
        List<Banniere> bannieres = etat.bannieres();
        assertEquals(1, bannieres.size());
        assertEquals(Genre.MOCKVIEW, bannieres.get(0).genre());
        assertEquals("1", bannieres.get(0).detail());
        assertTrue(etat.renduValide());
    }

    @Test
    void erreurDeRenduLaPlusGraveEnPremier() {
        EtatApercu etat = new EtatApercu();
        etat.surSucces(42, 38L, 720, 1280);
        etat.surRessourcesManquantes(List.of("@color/accent"));
        etat.surErreur("échec sémantique du rendu");
        etat.surXmlInvalide();
        List<Banniere> bannieres = etat.bannieres();
        assertEquals(3, bannieres.size(),
                "erreur + repli + frappe coexistent, rien n'est masqué");
        assertEquals(Gravite.ERREUR, bannieres.get(0).gravite());
        assertEquals(Genre.ERREUR, bannieres.get(0).genre());
        assertEquals("échec sémantique du rendu",
                bannieres.get(0).detail());
        assertEquals(Gravite.ALERTE, bannieres.get(1).gravite());
        assertEquals(Gravite.INFO, bannieres.get(2).gravite());
    }

    @Test
    void avertissementsDeConstitutionSontPortes() {
        EtatApercu etat = new EtatApercu();
        etat.surAvertissementsConfig(
                List.of("res directory not found: /p/res"));
        assertEquals(1, etat.bannieres().size());
        assertEquals(Genre.CONFIG, etat.bannieres().get(0).genre());
        assertTrue(etat.bannieres().get(0).detail()
                .contains("res directory not found"));
    }

    @Test
    void themeJamaisDevineRepliHote() {
        EtatApercu etat = new EtatApercu();
        assertNull(etat.nomTheme(), "null = repli hote, et il se dit");
        etat.setTheme("Theme.MonApp");
        assertEquals("Theme.MonApp", etat.nomTheme());
    }

    @Test
    void controlesReelsDensiteNuitDimensions() {
        EtatApercu etat = new EtatApercu();
        assertEquals(2, etat.densite(), "densite 2× par defaut");
        assertEquals(360, etat.largeurDp());
        assertEquals(640, etat.hauteurDp());
        assertEquals(720, etat.largeurPx(),
                "dp × densite : les pixels de la facade");
        assertEquals(1280, etat.hauteurPx());
        etat.setDensite(3);
        assertEquals(1080, etat.largeurPx());
        etat.setDensite(0);
        assertEquals(1, etat.densite(), "bornee au segment 1..3");
        etat.setDensite(9);
        assertEquals(3, etat.densite());
        etat.setDimensions(400, 800);
        assertEquals(400, etat.largeurDp());
        assertEquals(800, etat.hauteurDp());
        etat.setDimensions(10, 4000);
        assertEquals(120, etat.largeurDp(), "bornee au raisonnable");
        assertEquals(960, etat.hauteurDp());
        assertFalse(etat.nuit());
        etat.setNuit(true);
        assertTrue(etat.nuit());
    }
}
