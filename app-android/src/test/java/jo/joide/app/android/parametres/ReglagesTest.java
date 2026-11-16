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
    void leModeleParFournisseurSePoseEtSeRelitIsolement() {
        SourceMemoire source = new SourceMemoire();
        Reglages.ecrireModelePour(source,
                Reglages.FournisseurIa.OPENROUTER,
                "anthropic/claude-sonnet-4.5");
        Reglages.ecrireModelePour(source,
                Reglages.FournisseurIa.ANTHROPIC, "claude-opus-4-1");
        assertEquals("anthropic/claude-sonnet-4.5", Reglages
                .lireModelePour(source, Reglages.FournisseurIa.OPENROUTER));
        assertEquals("claude-opus-4-1", Reglages.lireModelePour(source,
                Reglages.FournisseurIa.ANTHROPIC));
        assertEquals("", Reglages.lireModelePour(source,
                Reglages.FournisseurIa.GEMINI), "absent : vide, pas "
                + "de retombée silencieuse sur le modèle partagé");
        assertThrows(IllegalArgumentException.class,
                () -> Reglages.ecrireModelePour(source,
                        Reglages.FournisseurIa.OPENROUTER, ""));
    }

    @Test
    void leGardeDAppartenanceReconnaitLesFamilles() {
        Reglages.FournisseurIa[] tous = Reglages.FournisseurIa.values();
        // anthropic : les claude
        assertTrue(Reglages.modeleAppartient("claude-sonnet-4-5",
                Reglages.FournisseurIa.ANTHROPIC));
        // gemini : gemini, gemma, models/
        assertTrue(Reglages.modeleAppartient("gemini-2.5-flash",
                Reglages.FournisseurIa.GEMINI));
        assertTrue(Reglages.modeleAppartient("models/gemini-pro",
                Reglages.FournisseurIa.GEMINI));
        // openrouter : un id porteur de barre
        assertTrue(Reglages.modeleAppartient("openai/gpt-5.1",
                Reglages.FournisseurIa.OPENROUTER));
        assertTrue(Reglages.modeleAppartient("z-ai/glm-4.6",
                Reglages.FournisseurIa.OPENROUTER));
        // openai : ni claude, ni gemini, ni barre
        assertTrue(Reglages.modeleAppartient("gpt-5.1",
                Reglages.FournisseurIa.OPENAI));
        // les travers interdits — un modèle hors famille ne part
        // jamais chez le fournisseur qui ne l'a pas (le rapport du
        // 2026-11-17 : « claude-sonnet-4-5 » envoyé à OpenRouter)
        for (Reglages.FournisseurIa f : tous) {
            assertTrue(Reglages.modeleAppartient("claude-sonnet-4-5", f)
                    == (f == Reglages.FournisseurIa.ANTHROPIC),
                    () -> "claude chez " + f);
            assertTrue(Reglages.modeleAppartient("gemini-2.5-pro", f)
                    == (f == Reglages.FournisseurIa.GEMINI),
                    () -> "gemini chez " + f);
            assertTrue(Reglages.modeleAppartient("z-ai/glm-4.6", f)
                    == (f == Reglages.FournisseurIa.OPENROUTER),
                    () -> "barre chez " + f);
        }
        // vide ou blanc : rien n'appartient
        assertTrue(!Reglages.modeleAppartient("",
                Reglages.FournisseurIa.ANTHROPIC));
        assertTrue(!Reglages.modeleAppartient("   ",
                Reglages.FournisseurIa.ANTHROPIC));
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
