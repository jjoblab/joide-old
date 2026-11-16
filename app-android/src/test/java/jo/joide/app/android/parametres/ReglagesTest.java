package jo.joide.app.android.parametres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.parametres.Reglages.Moteur;
import jo.joide.app.android.parametres.Reglages.Theme;

/**
 * Les réglages du lot M (ADR-É8/054) : défauts, tolérance aux
 * valeurs corrompues (le défaut, jamais une exception), bornes des
 * écritures, aller-retour complet par la source.
 */
class ReglagesTest {

    /** Une source en mémoire (les préférences réelles font pareil). */
    private static final class SourceMemoire implements Reglages.Source {
        final Map<String, String> valeurs = new HashMap<>();

        @Override
        public String lire(String cle) {
            return valeurs.get(cle);
        }

        @Override
        public void ecrire(String cle, String valeur) {
            valeurs.put(cle, valeur);
        }
    }

    @Test
    void lesDefautsSontCeQueLAdrExige() {
        Reglages defauts = Reglages.defauts();
        assertEquals(Theme.SOMBRE, defauts.theme, "sombre par défaut");
        assertEquals(0, defauts.accent, "accent de la palette par défaut");
        assertEquals(100, defauts.echelleUi, "texte UI à 100 % par défaut");
        assertEquals(13.5d, defauts.tailleCode, "13,5 sp, Graphite.CODE");
        assertTrue(defauts.numerosLignes, "numéros visibles par défaut");
        assertEquals(false, defauts.retourLigne,
                "retour à la ligne coupé par défaut");
        assertEquals(Moteur.SERVICE, defauts.moteur,
                "le service :build par défaut");
        assertEquals(2, defauts.filsParBuild, "deux fils, comme le service P8");
    }

    @Test
    void uneSourceVideDonneLesDefauts() {
        Reglages lus = Reglages.lire(new SourceMemoire());
        assertEquals(Reglages.defauts().theme, lus.theme);
        assertEquals(Reglages.defauts().tailleCode, lus.tailleCode);
        assertEquals(Reglages.defauts().filsParBuild, lus.filsParBuild);
    }

    @Test
    void lesValeursCorrompuesRetombentSurLesDefauts() {
        SourceMemoire source = new SourceMemoire();
        source.valeurs.put("theme", "pas-un-theme");
        source.valeurs.put("accent", "bleu");
        source.valeurs.put("echelle.ui", "12x");
        source.valeurs.put("editeur.taille", "gigantesque");
        source.valeurs.put("build.moteur", "");
        source.valeurs.put("build.fils", "-3");
        Reglages lus = Reglages.lire(source);
        assertEquals(Theme.SOMBRE, lus.theme);
        assertEquals(0, lus.accent);
        assertEquals(100, lus.echelleUi);
        assertEquals(13.5d, lus.tailleCode);
        assertEquals(Moteur.SERVICE, lus.moteur);
        assertEquals(2, lus.filsParBuild);
        // un booléen illisible vaut false (Boolean.parseBoolean) :
        // c'est le comportement du cadre, le défaut couvre l'ABSENT
        source.valeurs.put("editeur.numeros", "peut-etre");
        assertEquals(false, Reglages.lire(source).numerosLignes);
    }

    @Test
    void lesValeursHorsBornesSontRefuseesALaLecture() {
        SourceMemoire source = new SourceMemoire();
        source.valeurs.put("accent", "5");
        source.valeurs.put("echelle.ui", "87");
        source.valeurs.put("editeur.taille", "99");
        source.valeurs.put("build.fils", "9");
        Reglages lus = Reglages.lire(source);
        assertEquals(0, lus.accent, "nuancier à cinq pastilles");
        assertEquals(100, lus.echelleUi, "hors pas de 5 : le défaut");
        assertEquals(13.5d, lus.tailleCode);
        assertEquals(2, lus.filsParBuild);
    }

    @Test
    void lesEcrituresSontBorneesEtReluesTelles() {
        SourceMemoire source = new SourceMemoire();
        Reglages.ecrireTheme(source, Theme.CLAIR);
        Reglages.ecrireAccent(source, 4);
        Reglages.ecrireEchelleUi(source, 400);
        Reglages.ecrireTailleCode(source, 50);
        Reglages.ecrireNumerosLignes(source, false);
        Reglages.ecrireRetourLigne(source, true);
        Reglages.ecrireMoteur(source, Moteur.PROCESSUS);
        Reglages.ecrireFils(source, 99);
        Reglages lus = Reglages.lire(source);
        assertEquals(Theme.CLAIR, lus.theme);
        assertEquals(4, lus.accent);
        assertEquals(130, lus.echelleUi, "bornée au plafond");
        assertEquals(20d, lus.tailleCode, "bornée au plafond");
        assertEquals(false, lus.numerosLignes);
        assertTrue(lus.retourLigne);
        assertEquals(Moteur.PROCESSUS, lus.moteur);
        assertEquals(8, lus.filsParBuild, "bornée au plafond");
    }

    @Test
    void lePlancherDesBornesSappliqueAussi() {
        SourceMemoire source = new SourceMemoire();
        Reglages.ecrireEchelleUi(source, 10);
        Reglages.ecrireTailleCode(source, 1);
        Reglages.ecrireFils(source, 0);
        Reglages lus = Reglages.lire(source);
        assertEquals(85, lus.echelleUi);
        assertEquals(10d, lus.tailleCode);
        assertEquals(1, lus.filsParBuild);
    }

    @Test
    void laccentHorsNuancierEstRefuseALecriture() {
        SourceMemoire source = new SourceMemoire();
        assertThrows(IllegalArgumentException.class,
                () -> Reglages.ecrireAccent(source, 5));
        assertThrows(IllegalArgumentException.class,
                () -> Reglages.ecrireAccent(source, -1));
    }

    @Test
    void uneSourceNulleEstRefusee() {
        assertThrows(NullPointerException.class,
                () -> Reglages.lire(null));
    }

    @Test
    void lesTroisThemesExistentEtSontDistincts() {
        assertEquals(3, Theme.values().length);
        assertEquals(2, Moteur.values().length);
    }

    @Test
    void reglagesIaParDefautAnthropicEtModeleStandard() {
        Reglages defauts = Reglages.defauts();
        assertEquals(Reglages.FournisseurIa.ANTHROPIC,
                defauts.fournisseurIa);
        assertEquals(Reglages.MODELE_IA_DEFAUT, defauts.modeleIa);
        assertEquals("", defauts.urlIa);
    }

    @Test
    void leFournisseurIaInvalideRetombeSurLeDefaut() {
        SourceMemoire source = new SourceMemoire();
        source.valeurs.put("ia.fournisseur", "GEMINI_FUTUR");
        assertEquals(Reglages.FournisseurIa.ANTHROPIC,
                Reglages.lire(source).fournisseurIa);
    }

    @Test
    void leModeleVideOuTropLongRetombeSurLeDefaut() {
        SourceMemoire source = new SourceMemoire();
        source.valeurs.put("ia.modele", "   ");
        assertEquals(Reglages.MODELE_IA_DEFAUT,
                Reglages.lire(source).modeleIa);
        source.valeurs.put("ia.modele", "x".repeat(101));
        assertEquals(Reglages.MODELE_IA_DEFAUT,
                Reglages.lire(source).modeleIa);
    }

    @Test
    void lUrlNonHttpEstRefuseeEnLectureEtEnEcriture() {
        SourceMemoire source = new SourceMemoire();
        source.valeurs.put("ia.url", "ftp://pirate.example");
        assertEquals("", Reglages.lire(source).urlIa);
        assertThrows(IllegalArgumentException.class,
                () -> Reglages.ecrireUrlIa(source, "javascript:alert(1)"));
    }

    @Test
    void leModeleSePoseEtSeRelitMaisJamaisVide() {
        SourceMemoire source = new SourceMemoire();
        Reglages.ecrireModeleIa(source, "gpt-5.2-mini");
        assertEquals("gpt-5.2-mini", Reglages.lire(source).modeleIa);
        assertThrows(IllegalArgumentException.class,
                () -> Reglages.ecrireModeleIa(source, ""));
    }

    @Test
    void lUrlHttpSePoseEtLaVideResteAutorisee() {
        SourceMemoire source = new SourceMemoire();
        Reglages.ecrireUrlIa(source, "https://passerelle.example/v1");
        assertEquals("https://passerelle.example/v1",
                Reglages.lire(source).urlIa);
        Reglages.ecrireUrlIa(source, "");
        assertEquals("", Reglages.lire(source).urlIa);
    }
}
