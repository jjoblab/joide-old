package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.EtatApercu.Genre;
import jo.joide.app.android.atelier.EtatApercu.Gravite;

/**
 * La « capture » du lot L contre la maquette
 * {@code docs/ecrans/design/apercu.html} : le test lit la maquette et
 * vérifie que l'état d'aperçu livré reproduit sa structure — rendu
 * nominal avec stats mesurées, contrôles réels (densité segmentée,
 * mode nuit, dimensions cibles), bannières de franchise (frappe,
 * ressource manquante, MockView, échec) et le rendu « tel quel,
 * jamais retouché ». La validation sur appareil revient au
 * propriétaire via l'APK du CI (le rendu LayoutLib exige un Context
 * Android : prouvé par l'APK du lot F, pas sur JVM).
 */
class MaquetteApercuTest {

    @Test
    void laMaquettePorteLesDeuxEtats() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Rendu nominal"));
        assertTrue(maquette.contains("Pendant la frappe — franchise"));
        assertTrue(maquette.contains("ADR-É7"));
    }

    @Test
    void renduNominalStatsMesureesEtControlesReels() throws IOException {
        String maquette = maquette();
        // stats de l'écouteur, jamais inventées ; contrôles réels du
        // ConfigurationApercu, rien d'inventé
        assertTrue(maquette.contains(
                "42 vues · 38 ms · 360 × 640"));
        assertTrue(maquette.contains("1×</span><span class=\"on\">2×"
                + "</span><span>3×"));
        assertTrue(maquette.contains("360 × 640"));
        assertTrue(maquette.contains("debouncé 400 ms (lot E)"));
        EtatApercu etat = new EtatApercu();
        etat.surSucces(42, 38L, 720, 1280);
        assertEquals(42, etat.stats().vues());
        assertEquals(2, etat.densite(), "densité 2× par défaut");
        assertEquals(360, etat.largeurDp());
        assertEquals(640, etat.hauteurDp());
        assertEquals(720, etat.largeurPx(), "dp × densité en pixels");
        assertTrue(etat.bannieres().isEmpty(),
                "rendu nominal : pas de bannière");
    }

    @Test
    void pendantLaFrappeLeDernierRenduValideEstConserveEtDit() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "XML en frappe — dernier rendu valide conservé"));
        assertTrue(maquette.contains(
                "Le volet ne devient jamais blanc ni faux"));
        EtatApercu etat = new EtatApercu();
        etat.surSucces(42, 38L, 720, 1280);
        etat.surXmlInvalide();
        assertEquals(1, etat.bannieres().size());
        assertEquals(Genre.FRAPPE, etat.bannieres().get(0).genre());
        assertEquals(Gravite.INFO, etat.bannieres().get(0).gravite());
        assertTrue(etat.renduValide());
    }

    @Test
    void ressourceManquanteRepliNatifAnnonce() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "@color/accent manquante — repli aux ressources"
                        + " natives"));
        assertTrue(maquette.contains(
                "la bannière reste tant que la ressource manque"));
        EtatApercu etat = new EtatApercu();
        etat.surSucces(42, 38L, 720, 1280);
        etat.surRessourcesManquantes(List.of("@color/accent"));
        assertEquals(Genre.RESSOURCES, etat.bannieres().get(0).genre());
        assertEquals(Gravite.ALERTE, etat.bannieres().get(0).gravite());
        assertEquals("@color/accent", etat.bannieres().get(0).detail());
        assertTrue(etat.renduValide(),
                "le rendu continue avec le repli, pas un échec");
    }

    @Test
    void mockViewSansEchecEtThemeJamaisDevine() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("rend une MockView sans échec"));
        // « repli hôte » : la franchise du thème vit dans la SPEC
        // (ADR-É7) et dans l'état pur — nomTheme() null se dit
        EtatApercu etat = new EtatApercu();
        etat.surSucces(7, 12L, 720, 1280);
        etat.surMockViews(1);
        assertEquals(Genre.MOCKVIEW, etat.bannieres().get(0).genre());
        assertTrue(etat.renduValide(),
                "MockView est un échec nominal, pas bloquant");
        assertEquals(null, etat.nomTheme(),
                "null = repli hôte (jamais deviné), et la vue le dit");
    }

    @Test
    void modeNuitEtDensiteSontDesReglagesReels() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Nuit"));
        assertTrue(maquette.contains("mode nuit, qualificateur -night"));
        EtatApercu etat = new EtatApercu();
        assertFalseNuit(etat);
        etat.setNuit(true);
        assertTrue(etat.nuit());
        etat.setDensite(3);
        assertEquals(3, etat.densite());
        assertEquals(1080, etat.largeurPx());
    }

    private static void assertFalseNuit(EtatApercu etat) {
        assertTrue(!etat.nuit());
    }

    private static String maquette() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(
                    "docs/ecrans/design/apercu.html");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException("apercu.html not found from "
                + Paths.get("").toAbsolutePath());
    }
}
