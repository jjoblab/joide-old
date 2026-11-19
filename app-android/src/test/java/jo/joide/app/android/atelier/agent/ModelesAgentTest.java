package jo.joide.app.android.atelier.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.agent.ModelesAgent.Filtre;
import jo.joide.app.android.atelier.agent.ModelesAgent.Sections;
import jo.joide.app.android.atelier.agent.ModelesAgent.Tri;

/**
 * Les tests JVM du lot C4 — le modèle pur du SÉLECTEUR de modèles
 * (la manière du sélecteur éprouvé : recherche multi-terme,
 * fournisseur d'identifiant, gratuits seulement, tri naturel,
 * sections une fois chaque modèle).
 */
class ModelesAgentTest {

    private static final List<String> LISTE = List.of(
            "anthropic/claude-sonnet-4.5",
            "anthropic/claude-haiku-4.5",
            "openai/gpt-5.1",
            "openai/gpt-4.1",
            "z-ai/glm-4.6:free",
            "gpt-5.1-nu");

    // ---- le fournisseur d'identifiant ---------------------------------

    @Test
    void leFournisseurDIdentifiant() {
        assertEquals("anthropic",
                ModelesAgent.fournisseurDe("anthropic/claude-sonnet"));
        assertEquals("z-ai", ModelesAgent.fournisseurDe("z-ai/glm-4.6"));
        assertNull(ModelesAgent.fournisseurDe("gpt-5.1"));
        assertNull(ModelesAgent.fournisseurDe("/sans-tete"));
        assertNull(ModelesAgent.fournisseurDe(null));
    }

    @Test
    void lesGratuits() {
        assertTrue(ModelesAgent.estGratuit("z-ai/glm-4.6:free"));
        assertTrue(ModelesAgent.estGratuit("z-ai/glm-4.6:FREE"));
        assertFalse(ModelesAgent.estGratuit("z-ai/glm-4.6"));
        assertFalse(ModelesAgent.estGratuit(null));
    }

    @Test
    void leNomNuRetireLePrefixeFournisseur() {
        assertEquals("Claude Sonnet 4.5",
                ModelesAgent.nomNu("anthropic/claude-sonnet-4.5",
                        "Anthropic: Claude Sonnet 4.5"));
        // sans fournisseur d'identifiant, le nom reste entier
        assertEquals("GPT-5.1",
                ModelesAgent.nomNu("gpt-5.1", "GPT-5.1"));
        // un deux-points sans fournisseur d'identifiant reste
        assertEquals("Rapport : version 2",
                ModelesAgent.nomNu("gpt-5.1", "Rapport : version 2"));
    }

    // ---- la recherche multi-terme ------------------------------------

    @Test
    void laRechercheExigeChaqueTerme() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("anthropic sonnet", null, false, Tri.NOM),
                List.of(), List.of());
        assertEquals(
                List.of("anthropic/claude-sonnet-4.5"), sections.reste);
    }

    @Test
    void laRechercheInsensibleALaCasseEtAuxBlancs() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("  OPENAI   gpt ", null, false, Tri.NOM),
                List.of(), List.of());
        assertEquals(List.of("openai/gpt-4.1", "openai/gpt-5.1"),
                sections.reste);
    }

    @Test
    void leFiltreFournisseurRetrAcit() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("", "z-ai", false, Tri.NOM),
                List.of(), List.of());
        assertEquals(List.of("z-ai/glm-4.6:free"), sections.reste);
    }

    @Test
    void leFiltreGratuitsSeuls() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("", null, true, Tri.NOM),
                List.of(), List.of());
        assertEquals(List.of("z-ai/glm-4.6:free"), sections.reste);
    }

    // ---- les sections --------------------------------------------------

    @Test
    void unModeleSeMontreUneSeuleFois() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("", null, false, Tri.NOM),
                List.of("openai/gpt-5.1"),
                List.of("openai/gpt-5.1", "z-ai/glm-4.6:free"));
        assertEquals(List.of("openai/gpt-5.1"), sections.favoris);
        assertEquals(List.of("z-ai/glm-4.6:free"), sections.recents);
        assertFalse(sections.reste.contains("openai/gpt-5.1"));
        assertFalse(sections.reste.contains("z-ai/glm-4.6:free"));
        assertEquals(sections.total() - 2, sections.reste.size());
    }

    @Test
    void lesRecentsGardentLOrdreDuPlusRecent() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("", null, false, Tri.NOM),
                List.of(),
                List.of("gpt-5.1-nu", "openai/gpt-4.1"));
        assertEquals(List.of("gpt-5.1-nu", "openai/gpt-4.1"),
                sections.recents);
    }

    @Test
    void lesFavorisHorsListeNeComptentPas() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("", null, false, Tri.NOM),
                List.of("inconnu/fantome"), List.of());
        assertTrue(sections.favoris.isEmpty());
    }

    @Test
    void lesDoublonsDeLaListeSeGardentUneFois() {
        Sections sections = ModelesAgent.sections(
                List.of("openai/gpt-5.1", "openai/gpt-5.1"),
                new Filtre("", null, false, Tri.NOM), List.of(),
                List.of());
        assertEquals(List.of("openai/gpt-5.1"), sections.reste);
    }

    // ---- le tri naturel -------------------------------------------------

    @Test
    void leTriNaturelLitLesNombresEnNombres() {
        assertTrue(ModelesAgent.comparaisonNaturelle(
                "Modèle 2", "Modèle 10") < 0);
        assertTrue(ModelesAgent.comparaisonNaturelle(
                "GPT-4.1", "GPT-5") < 0);
        assertTrue(ModelesAgent.comparaisonNaturelle(
                "GPT-5.1", "GPT-5.10") < 0);
        assertEquals(0, ModelesAgent.comparaisonNaturelle(
                "GPT-5.1", "GPT-5.1"));
        // les zéros de tête ne pèsent pas
        assertEquals(0, ModelesAgent.comparaisonNaturelle(
                "gpt-007", "gpt-7"));
    }

    @Test
    void leTriNaturelInsensibleALaCasse() {
        assertTrue(ModelesAgent.comparaisonNaturelle(
                "alpha", "Beta") < 0);
        assertEquals(0, ModelesAgent.comparaisonNaturelle(
                "Alpha", "alpha"));
    }

    @Test
    void leTriParFournisseurPuisNom() {
        Sections sections = ModelesAgent.sections(LISTE,
                new Filtre("", null, false, Tri.FOURNISSEUR),
                List.of(), List.of());
        // l'identifiant SANS fournisseur porte en TÊTE (la chaîne
        // vide d'abord — le compareBy vendor ?: "" de l'IDE de référence),
        // puis anthropic < openai < z-ai, le nom dans chacun (4.1
        // avant 5.1 — le nombre se lit)
        assertEquals(List.of("gpt-5.1-nu",
                        "anthropic/claude-haiku-4.5",
                        "anthropic/claude-sonnet-4.5",
                        "openai/gpt-4.1", "openai/gpt-5.1",
                        "z-ai/glm-4.6:free"),
                sections.reste);
    }

    // ---- les fournisseurs ----------------------------------------------

    @Test
    void lesFournisseursDistinctsEtComptes() {
        List<String> fournisseurs = ModelesAgent.fournisseurs(LISTE);
        assertEquals(
                List.of("anthropic", "openai", "z-ai"), fournisseurs);
        assertEquals(2, ModelesAgent.nombreDuFournisseur(
                LISTE, "anthropic"));
        assertEquals(0, ModelesAgent.nombreDuFournisseur(
                LISTE, "microsoft"));
    }

    @Test
    void unGratuitDansLaListe() {
        assertTrue(ModelesAgent.unGratuit(LISTE));
        assertFalse(ModelesAgent.unGratuit(
                List.of("openai/gpt-5.1")));
        assertFalse(ModelesAgent.unGratuit(null));
    }
}
