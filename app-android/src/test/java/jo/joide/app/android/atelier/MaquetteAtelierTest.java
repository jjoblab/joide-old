package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.Arborescence.Noeud;
import jo.joide.app.android.atelier.EtatAtelier.ActionRetour;

/**
 * La « capture » du lot J contre la maquette
 * {@code docs/ecrans/design/atelier.html} : le test lit la maquette
 * (fichier autonome, textes contractuels) et vérifie que l'état
 * d'atelier livré reproduit sa structure — les trois vues (édition,
 * tiroir, grand écran), le seuil 720 dp, les onglets avec point de
 * modification, les sorties estompées, l'ordre de fermeture du
 * retour. La validation visuelle sur appareil revient au propriétaire
 * via l'APK du CI (ADR-048, écart P8 n° 4 : pas d'émulateur au bac à
 * sable).
 */
class MaquetteAtelierTest {

    private static final List<String> PROJET = List.of(
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/jo/exemple/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml",
            "app/src/main/res/values/strings.xml",
            "core/src/main/java/Outil.kt",
            "joide.toml");

    @Test
    void laMaquettePorteLesTroisVues() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Téléphone · édition"));
        assertTrue(maquette.contains("Téléphone · tiroir arborescence"));
        assertTrue(maquette.contains(
                "Grand écran (≥ 720 dp) — tout amarré"));
        assertTrue(maquette.contains("seuil adaptatif 720 dp"));
        assertEquals(720, EtatAtelier.SEUIL_GRAND_ECRAN_DP,
                "le seuil de la maquette est celui de l'état");
    }

    @Test
    void barreDongletsDeLaMaquetteSeReproduitParLetatPur() throws IOException {
        String maquette = maquette();
        // trois onglets : l'actif, un modifié (point), un simple
        assertTrue(maquette.contains(
                "<span class=\"onglet actif\">MainActivity.kt"));
        assertTrue(maquette.contains(
                "activity_main.xml <span class=\"sale\"></span>"));
        assertTrue(maquette.contains("<span class=\"onglet\">joide.toml"));
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("app/src/main/java/jo/exemple/MainActivity.kt");
        etat.ouvrir("app/src/main/res/layout/activity_main.xml");
        etat.ouvrir("joide.toml");
        etat.marquerModifie("app/src/main/res/layout/activity_main.xml");
        assertEquals(List.of(
                "app/src/main/java/jo/exemple/MainActivity.kt",
                "app/src/main/res/layout/activity_main.xml",
                "joide.toml"), etat.onglets());
        assertTrue(etat.estModifie(
                "app/src/main/res/layout/activity_main.xml"));
        assertFalse(etat.estModifie("joide.toml"));
    }

    @Test
    void tiroirDeLaMaquetteSortiesEstompeesJamaisSources() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("class=\"niveau sorties\""));
        assertTrue(maquette.contains(
                "Sorties de build — estompées pendant qu'elles se"
                        + " construisent"));
        assertTrue(maquette.contains("jamais présentées comme sources"));
        // le même arbre par la classe pure : l'entrée active
        // surlignée, build-joide en fin de racine, marqué sorties
        List<Noeud> noeuds = Arborescence.composer(PROJET,
                "mon-application",
                Arborescence.parentsDe(
                        "app/src/main/res/layout/activity_main.xml"),
                "app/src/main/res/layout/activity_main.xml", true);
        Noeud actif = noeuds.stream().filter(Noeud::actif).findFirst()
                .orElseThrow();
        assertEquals("activity_main.xml", actif.nom(),
                "l'entrée active de la maquette est surlignée par"
                        + " l'état");
        Noeud dernier = noeuds.get(noeuds.size() - 1);
        assertEquals(Arborescence.NOM_SORTIES, dernier.nom());
        assertTrue(dernier.sorties());
        assertFalse(dernier.ouvert());
    }

    @Test
    void grandEcranDeLaMaquetteRailArbreAmarreApercuCoteACote() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("class=\"rail\""));
        assertTrue(maquette.contains("col-arbre"));
        assertTrue(maquette.contains("volet-apercu"));
        assertTrue(maquette.contains("console-amarree"));
        // l'état suit : grand écran, le tiroir amarré n'est pas une
        // surcouche — le retour passe à l'aperçu puis la fermeture
        EtatAtelier etat = new EtatAtelier(true);
        etat.setProjetMonte(true);
        etat.ouvrirTiroir();
        assertEquals(ActionRetour.DEMANDER_FERMETURE, etat.surRetour());
        etat.surActifEstMiseEnPage(true);
        assertTrue(etat.voletApercu(),
                "la mise en page active obtient son volet droit");
    }

    @Test
    void retourSystemeFermeLesSurcouchesUneParUneJamaisZombie() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "Le retour ferme d'abord les surcouches — jamais un état"
                        + " zombie"));
        // la figcaption annonce l'ordre ; l'état pur le prouve
        EtatAtelier etat = new EtatAtelier(false);
        etat.setProjetMonte(true);
        etat.deplierConsole();
        etat.ouvrirTiroir();
        assertEquals(ActionRetour.FERMER_CONSOLE, etat.surRetour());
        etat.replierConsole();
        assertEquals(ActionRetour.FERMER_TIROIR, etat.surRetour());
        etat.fermerTiroir();
        assertEquals(ActionRetour.DEMANDER_FERMETURE, etat.surRetour());
    }

    @Test
    void fabEtPiluleDeBuild() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("class=\"fab\""));
        assertTrue(maquette.contains("aria-label=\"Construire\""));
        // la pilule d'état : numéro et phase pendant le build, la
        // croix arrête (critère du lot J)
        EtatAtelier etat = new EtatAtelier(false);
        assertFalse(etat.buildEnCours());
        etat.commencerBuild(4);
        etat.phaseBuild("compilation");
        assertTrue(etat.buildEnCours());
        assertEquals(4, etat.build().numero());
        assertEquals("compilation", etat.build().phase());
        etat.terminerBuild(true);
        assertFalse(etat.buildEnCours());
    }

    private static String maquette() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(
                    "docs/ecrans/design/atelier.html");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException("atelier.html not found from "
                + Paths.get("").toAbsolutePath());
    }
}
