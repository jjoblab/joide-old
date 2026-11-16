package jo.joide.app.android.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.ui.choix.ChoixSegment;
import jo.joide.app.android.ui.Graphite;

/**
 * La « capture » du lot I contre la maquette
 * {@code docs/ecrans/design/creation-projet.html} : le test lit la
 * maquette et vérifie que l'assistant livré en reproduit la
 * structure — trois étapes avec progression, quatre modèles (la
 * franchise Android sur la carte), les puces de catégorie et leurs
 * comptes exacts, le formulaire piloté par le modèle (paquet et
 * minSdk sur le seul modèle Android, segment 28 par défaut), la
 * fiche du récapitulatif, l'arborescence en code compact. La
 * validation visuelle sur appareil revient au propriétaire via
 * l'APK du CI (ADR-048, écart P8 n° 4).
 */
class MaquetteCreationTest {

    @Test
    void laMaquettePorteLesTroisEtapesEtLeursComptes() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Étape 1 · Modèles"));
        assertTrue(maquette.contains("Étape 2 · Identité"));
        assertTrue(maquette.contains("Étape 3 · Récapitulatif"));
        assertTrue(maquette.contains(">1/3<"));
        assertTrue(maquette.contains(">2/3<"));
        assertTrue(maquette.contains(">3/3<"));
    }

    @Test
    void lesQuatreModelesEtLaFranchiseSurLaCarte() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Application Android"));
        assertTrue(maquette.contains("Console JVM"));
        assertTrue(maquette.contains("Bibliothèque JVM"));
        assertTrue(maquette.contains(">Vide</span>"));
        // la franchise de la carte Android — jamais une promesse
        assertTrue(maquette.contains(
                "JVM sur l'appareil ; APK au bureau (P8)"));
        // les modèles livrés portent la même franchise, mot pour mot
        // sur le fond (la limite nommée : la chaîne de bureau)
        String franchise = ModeleProjetTest.modelesAssets()
                .get("application-android").franchise();
        assertTrue(franchise.contains("JVM sur l'appareil"));
        assertTrue(franchise.contains("chaîne de bureau"),
                "la carte livrée nomme la limite de bureau : "
                        + franchise);
        // les comptes des puces de catégorie (Tous 4, Applications 1,
        // Bibliothèques 2, Vide 1) — l'état pur les reproduit
        assertTrue(maquette.contains(
                "Tous <span class=\"n\">4</span>"));
        assertTrue(maquette.contains(
                "Applications <span class=\"n\">1</span>"));
        assertTrue(maquette.contains(
                "Bibliothèques <span class=\"n\">2</span>"));
        assertTrue(maquette.contains(
                "Vide <span class=\"n\">1</span>"));
        EtatCreation etat = new EtatCreation(ModeleProjetTest
                .listeModeles(), List.of());
        assertEquals(4, etat.modelesVisibles().size());
        etat.changerCategorie(ModeleProjet.Categorie.APPLICATIONS);
        assertEquals(1, etat.modelesVisibles().size());
        etat.changerCategorie(ModeleProjet.Categorie.BIBLIOTHEQUES);
        assertEquals(2, etat.modelesVisibles().size());
        etat.changerCategorie(ModeleProjet.Categorie.VIDE);
        assertEquals(1, etat.modelesVisibles().size());
    }

    @Test
    void formulairePiloteParLeModeleCommeLaMaquette() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Nom du projet"));
        assertTrue(maquette.contains("Modules"));
        assertTrue(maquette.contains("proposés par le modèle, modifiables"));
        assertTrue(maquette.contains("Paquet Android"));
        assertTrue(maquette.contains("minSdk"));
        assertTrue(maquette.contains(
                "targetSdk reste 28 (D4)"));
        // le segment de la maquette : 28 sélectionné par défaut —
        // les niveaux proposés excluent 24 (ADR-009 : Java 17 exige
        // minSdk 28, écart consigné ADR-050)
        assertTrue(maquette.contains(
                "<span class=\"seg\">24</span><span class=\"seg on\">28</span>"));
        assertEquals(List.of(28, 30), GenerateurProjet.MIN_SDK);
        // les couleurs du segment : accent 18 % sur surface haute,
        // texte accent — ChoixSegment (maquette : rgba(130,170,255,.18))
        Graphite.Palette sombre = Graphite.sombre();
        ChoixSegment.Apparence active =
                ChoixSegment.apparence(sombre, true);
        assertEquals(Graphite.melange(sombre.accent, 0.18f,
                sombre.surfaceHaute), active.fond());
        assertEquals(sombre.accent, active.texte());
        assertTrue(active.gras());
        ChoixSegment.Apparence inactive =
                ChoixSegment.apparence(sombre, false);
        assertEquals(sombre.texteSecondaire, inactive.texte());
    }

    @Test
    void recapitulatifEnLignesEtArborescenceEnCode() throws IOException {
        String maquette = maquette();
        for (String cle : new String[]{"Modèle", "Nom", "Modules",
                "Paquet Android", "minSdk"}) {
            assertTrue(maquette.contains(cle),
                    "la fiche porte la ligne " + cle);
        }
        assertTrue(maquette.contains("Arborescence générée"));
        assertTrue(maquette.contains("mon-application/"));
        assertTrue(maquette.contains("· module :app"));
        assertTrue(maquette.contains("· module :core"));
        // l'état pur produit les mêmes lignes sur le modèle Android
        EtatCreation etat = new EtatCreation(ModeleProjetTest
                .listeModeles(), List.of());
        assertTrue(etat.selectionnerModele("application-android"));
        assertEquals("mon-application", etat.nom(),
                "le défaut unique démarre à mon-application");
        etat.suivante();
        etat.suivante();
        List<EtatCreation.LigneRecap> lignes = etat.lignesRecap();
        assertEquals(5, lignes.size());
        assertEquals(":app, :core", lignes.get(2).valeur());
        List<EtatCreation.LigneArbo> arbo = etat.arborescence();
        assertTrue(arbo.stream().anyMatch(l ->
                l.texte().equals("mon-application/")));
        assertTrue(arbo.stream().anyMatch(l -> l.annotation()
                && l.texte().contains(":app")));
        assertTrue(arbo.stream().anyMatch(l -> l.annotation()
                && l.texte().contains(":core")));
        assertTrue(arbo.stream().anyMatch(l ->
                l.texte().endsWith("activity_main.xml")));
    }

    @Test
    void rienNEstCreeAvantLeBouton() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Créer le projet"));
        assertTrue(maquette.contains("tout ou rien"));
        // la bannière d'information de l'étape 2 (stockage privé,
        // création atomique) est reprise telle quelle
        assertTrue(maquette.contains(
                "Le projet sera créé dans le stockage privé de JoIDE"));
    }

    private static String maquette() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(
                    "docs/ecrans/design/creation-projet.html");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException("creation-projet.html not found from "
                + Paths.get("").toAbsolutePath());
    }
}
