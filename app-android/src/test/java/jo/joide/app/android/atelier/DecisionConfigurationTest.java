package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.DecisionConfiguration.Reaction;

/**
 * La décision de configuration de l'atelier éprouvée en JVM pur (lot
 * A8, ADR-085 décision 10 — critère §6.10 du chantier : « un
 * changement de géométrie n'appelle pas {@code fermerIde()} et ferme
 * les popups »). Aucune vue, aucun Bundle, aucun Manifest : la logique
 * seule — l'écran déroule.
 */
class DecisionConfigurationTest {

    /** Même classe de géométrie : la racine ne se ré-influe pas. */
    @Test
    void memeClasseDeGeometrieLaRacineNeSeReinfluePas() {
        Reaction meme = DecisionConfiguration.surGeometrie(false, false);
        assertFalse(meme.reinflerRacine(),
                "économie : compact → compact, même layout — la racine "
                        + "reste (un câblage entier ne dit rien de plus)");
        assertTrue(meme.fermerPopups(),
                "décision 12 : les popups se ferment à TOUT changement "
                        + "de configuration, même sans ré-inflation");
        assertFalse(meme.fermerIde());
        Reaction memeGrand = DecisionConfiguration.surGeometrie(true, true);
        assertFalse(memeGrand.reinflerRacine(),
                "économie : grand écran → grand écran, même layout");
        assertTrue(memeGrand.fermerPopups());
        assertFalse(memeGrand.fermerIde());
    }

    /** Compact vers grand écran : la racine se ré-influe. */
    @Test
    void compactVersGrandEcranLaRacineSeReinflue() {
        Reaction reaction = DecisionConfiguration.surGeometrie(false, true);
        assertTrue(reaction.reinflerRacine(),
                "la classe change : le nouveau layout (layout-w720dp) "
                        + "porte une autre composition — la racine se "
                        + "ré-influe (le puits se détache et se rattache)");
        assertTrue(reaction.fermerPopups(),
                "décision 12 : popups fermés, jamais restaurés");
        assertFalse(reaction.fermerIde(),
                "décision 10 : fermerIde() n'est JAMAIS appelé sur la "
                        + "géométrie — la session (scène celeris, LSP, "
                        + "aperçu, écritures) survit");
    }

    /** Grand écran vers compact : la racine se ré-influe aussi. */
    @Test
    void grandEcranVersCompactLaRacineSeReinflue() {
        Reaction reaction = DecisionConfiguration.surGeometrie(true, false);
        assertTrue(reaction.reinflerRacine(),
                "la classe change en sens inverse : même décision");
        assertTrue(reaction.fermerPopups());
        assertFalse(reaction.fermerIde());
    }

    /** Le critère §6.10, dans TOUTE la grille des classes. */
    @Test
    void laGeometrieNeFermeJamaisLIdeQuelleQueSoitLaClasse() {
        for (boolean avant : new boolean[]{false, true}) {
            for (boolean apres : new boolean[]{false, true}) {
                Reaction reaction =
                        DecisionConfiguration.surGeometrie(avant, apres);
                assertFalse(reaction.fermerIde(),
                        "fermerIde() interdit sur la géométrie (" + avant
                                + " → " + apres + ") : la session d'IDE "
                                + "survit à un changement de géométrie");
                assertTrue(reaction.fermerPopups(),
                        "les popups se ferment dans toute la grille ("
                                + avant + " → " + apres + ")");
            }
        }
    }
}
