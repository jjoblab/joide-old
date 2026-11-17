package jo.joide.app.android.accueil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * La « capture » du lot H contre la maquette
 * {@code docs/ecrans/design/accueil-projets.html} : le test lit la
 * maquette (fichier autonome, jetons et textes contractuels) et
 * vérifie que l'état d'écran livré reproduit sa structure — états
 * vide et peuplé, fusion de la reprise avec la première carte,
 * compteur exact, une seule ligne de méta, types de tuiles. La
 * validation visuelle sur appareil revient au propriétaire via
 * l'APK du CI (ADR-048, écart P8 n° 4 : pas d'émulateur au bac à
 * sable).
 */
class MaquetteAccueilTest {

    private static final long BASE = 1_700_000_000_000L;

    @Test
    void laMaquettePorteLesDeuxEtats() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("État peuplé"),
                "la maquette montre l'état peuplé");
        assertTrue(maquette.contains("Première ouverture"),
                "la maquette montre l'état vide");
    }

    @Test
    void letatPeupleDeLaMaquetteSeReproduitParLetatPur() throws IOException {
        String maquette = maquette();
        // la reprise : mon-application, la plus récente (il y a 2 h)
        assertTrue(maquette.contains(">mon-application</span>"));
        assertTrue(maquette.contains("Reprendre"));
        assertTrue(maquette.contains(
                "Application Android · 3 modules · il y a 2 h"));
        // la section : 3 cartes, compteur 3, tri par dernière ouverture
        assertTrue(maquette.contains(
                "<span class=\"nom-sect\">Projets</span>"
                        + "<span class=\"compte\">3"));
        assertTrue(maquette.contains("protocole-p8"));
        assertTrue(maquette.contains("socle-deps"));
        assertTrue(maquette.contains("exemple-gradle"));
        // la reprise ne se répète pas dans la liste : la section
        // commence après son en-tête « Projets » (jamais avant)
        int debutSection = maquette.indexOf(
                "<span class=\"nom-sect\">Projets</span>");
        assertTrue(debutSection > 0);
        assertFalse(Pattern.compile("nom\">mon-application<")
                .matcher(maquette.subSequence(
                        debutSection, maquette.length()))
                .find(),
                "la reprise ne se répète pas dans la liste (pas de doublon)");

        // le même état par la classe pure : 4 projets, la reprise
        // fusionnée, la section en compte 3
        List<ProjetsRecents.Entree> entrees = List.of(
                entree("mon-application", TypeProjet.ANDROID, BASE
                        - 2 * 3_600_000L, 3, "mon-application"),
                entree("protocole-p8", TypeProjet.CONSOLE,
                        BASE - 86_400_000L, 1, "protocole-p8"),
                entree("socle-deps", TypeProjet.BIBLIOTHEQUE,
                        BASE - 8L * 86_400_000L, 2, "socle-deps"),
                entree("exemple-gradle", TypeProjet.GRADLE,
                        BASE - 14L * 86_400_000L, 0, "exemple-gradle"));
        EtatAccueil.Vue vue = EtatAccueil.composer(entrees, null);
        assertEquals("mon-application", vue.reprise().id());
        assertEquals(3, vue.compteSection());
        assertEquals(4, vue.total());
        assertEquals("3", vue.texteCompteur());
        assertEquals(List.of("protocole-p8", "socle-deps", "exemple-gradle"),
                vue.section().stream().map(ProjetsRecents.Entree::id)
                        .toList());
    }

    @Test
    void letatVideDeLaMaquetteSeReproduitParLetatPur() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Aucun projet pour l'instant"));
        assertTrue(maquette.contains("Créer un projet"));
        assertTrue(maquette.contains("Importer un dossier (SAF)"));
        assertTrue(maquette.contains("vide-zone"),
                "l'état vide est l'encadré pointillé de première classe");
        EtatAccueil.Vue vue = EtatAccueil.composer(List.of(), null);
        assertTrue(vue.premiereFois());
        assertFalse(vue.sectionVisible());
    }

    @Test
    void laMaquettePorteLesQuatreTuilesEtLeGrisGradle() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("tuile android"));
        assertTrue(maquette.contains("tuile console"));
        assertTrue(maquette.contains("tuile biblio"));
        assertTrue(maquette.contains("tuile vide"));
        // le projet Gradle prend la tuile grise, sa méta porte la
        // franchise de compatibilité, jamais un déguisement
        assertTrue(maquette.contains(
                "Projet Gradle · compatibilité — import statique, échappatoire export"));
        assertEquals(jo.joide.app.android.ui.choix.ChoixCarte.TypeTuile.VIDE,
                TypeProjet.GRADLE.tuile());
    }

    @Test
    void uneSeuleLigneDeMetaParCarte() throws IOException {
        String maquette = maquette();
        // chaque carte : .lég (reprise) ou .nom + UNE .méta — jamais
        // deux actions visibles en concurrence (ADR-É1, leçon 2)
        int cartes = maquette.split("class=\"carte", -1).length - 1;
        int metas = maquette.split("class=\"méta\"", -1).length - 1;
        assertEquals(cartes, metas,
                "une carte = exactement une ligne de méta");
    }

    private static ProjetsRecents.Entree entree(String id, TypeProjet type,
                                                long ouvert, int modules,
                                                String nom) {
        return new ProjetsRecents.Entree(id, nom, type, id, ouvert, modules);
    }

    private static String maquette() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(
                    "docs/ecrans/design/accueil-projets.html");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException("accueil-projets.html not found from "
                + Paths.get("").toAbsolutePath());
    }
}
