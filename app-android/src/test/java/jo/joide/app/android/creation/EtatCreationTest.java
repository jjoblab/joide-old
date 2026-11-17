package jo.joide.app.android.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.accueil.ProjetsRecents;
import jo.joide.app.android.accueil.TypeProjet;

/**
 * La machine à états de l'assistant (ADR-É4) : progression des trois
 * étapes, validation au fil de la saisie (le bouton Continuer
 * reflète la validité), formulaire piloté par le modèle (paquet et
 * minSdk seulement pour Android), bascule des modules avec refus
 * motivé, défauts uniques, récapitulatif exact (l'arbre EST la
 * liste des fichiers qui seront écrits).
 */
class EtatCreationTest {

    private static List<ModeleProjet.Modele> modeles() throws IOException {
        return ModeleProjetTest.listeModeles();
    }

    private static ModeleProjet.Modele modeleAndroid() throws IOException {
        return ModeleProjetTest.modelesAssets().get("application-android");
    }

    @Test
    void progressionDesEtapesEtRetour() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertEquals(EtatCreation.Etape.MODELE, etat.etape());
        assertFalse(etat.continuerPossible(),
                "aucun modèle choisi : Continuer inactif");
        // la sélection ouvre l'étape 2
        assertTrue(etat.selectionnerModele("console-jvm"));
        assertTrue(etat.continuerPossible());
        etat.suivante();
        assertEquals(EtatCreation.Etape.IDENTITE, etat.etape());
        etat.suivante();
        assertEquals(EtatCreation.Etape.RECAPITULATIF, etat.etape());
        // le retour remonte d'une étape, jamais au-delà du Modèle
        etat.precedente();
        assertEquals(EtatCreation.Etape.IDENTITE, etat.etape());
        etat.precedente();
        assertEquals(EtatCreation.Etape.MODELE, etat.etape());
        assertTrue(etat.surPremiereEtape());
    }

    @Test
    void selectionInvalideIgnoree() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertFalse(etat.selectionnerModele("inconnu"));
        assertNull(etat.modele());
    }

    @Test
    void defautsUniquesEtDerivesDuModele() throws IOException {
        ProjetsRecents.Entree pris = new ProjetsRecents.Entree("x",
                "mon-application", TypeProjet.ANDROID, "x",
                1_700_000_000_000L, 2);
        EtatCreation etat = new EtatCreation(modeles(), List.of(pris));
        assertTrue(etat.selectionnerModele("application-android"));
        // le nom par défaut évite les projets existants
        assertEquals("mon-application-2", etat.nom());
        assertEquals("jo.exemple.monapplication2", etat.paquet());
        assertEquals(28, etat.minSdk());
        assertEquals(List.of("app", "core"), etat.modulesChoisis());
        // le paquet suit le nom tant qu'il n'est pas touché
        etat.changerNom("Essai 2");
        assertEquals("jo.exemple.essai2", etat.paquet());
        // touché, il ne suit plus
        etat.changerPaquet("com.exemple.essai");
        etat.changerNom("autre");
        assertEquals("com.exemple.essai", etat.paquet());
    }

    @Test
    void formulairePiloteParLeModele() throws IOException {
        EtatCreation console = new EtatCreation(modeles(), List.of());
        assertTrue(console.selectionnerModele("console-jvm"));
        assertFalse(console.champsPaquet(),
                "la console n'a pas de champ paquet");
        assertFalse(console.champsMinSdk());
        // sans champ paquet, l'identité reste valide
        assertTrue(console.identiteValide());

        EtatCreation android = new EtatCreation(modeles(), List.of());
        assertTrue(android.selectionnerModele("application-android"));
        assertTrue(android.champsPaquet());
        assertTrue(android.champsMinSdk());
        // le bouton se juge à l'étape Identité (étape courante)
        android.suivante();
        assertEquals(EtatCreation.Etape.IDENTITE, android.etape());
        // paquet cassé : Continuer reflète l'invalidité, pas d'alerte
        android.changerPaquet("Majuscule");
        assertFalse(android.paquetValide());
        assertFalse(android.continuerPossible());
        android.changerPaquet("jo.exemple.ok");
        assertTrue(android.continuerPossible());
        // nom vide : inactif aussi
        android.changerNom("   ");
        assertEquals(EtatCreation.RaisonNom.VIDE, android.raisonNom());
        assertFalse(android.continuerPossible());
    }

    @Test
    void basculeDesModulesAvecRefusMotive() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertTrue(etat.selectionnerModele("application-android"));
        // :core est requis par :app : le retrait est refusé et nommé
        EtatCreation.Bascule refus = etat.basculerModule("core");
        assertFalse(refus.faite());
        assertEquals("app", refus.requisPar());
        assertEquals("app", etat.moduleRefusePar());
        assertEquals(List.of("app", "core"), etat.modulesChoisis());
        // :app se retire : :core reste, seul
        EtatCreation.Bascule retraitApp = etat.basculerModule("app");
        assertTrue(retraitApp.faite());
        assertEquals(List.of("core"), etat.modulesChoisis());
        // :app revient — l'ordre du modèle est conservé
        assertTrue(etat.basculerModule("app").faite());
        assertEquals(List.of("app", "core"), etat.modulesChoisis());
    }

    @Test
    void filtreEtCategoriesDeLaGalerie() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertEquals(4, etat.modelesVisibles().size());
        etat.changerCategorie(ModeleProjet.Categorie.APPLICATIONS);
        assertEquals(1, etat.modelesVisibles().size());
        etat.changerCategorie(ModeleProjet.Categorie.BIBLIOTHEQUES);
        assertEquals(2, etat.modelesVisibles().size());
        etat.changerCategorie(ModeleProjet.Categorie.VIDE);
        assertEquals(1, etat.modelesVisibles().size());
        etat.changerCategorie(null);
        etat.filtrer("CONSOLE");
        assertEquals(1, etat.modelesVisibles().size());
        assertEquals("Console JVM",
                etat.modelesVisibles().get(0).nom());
        etat.filtrer("zzz");
        assertTrue(etat.modelesVisibles().isEmpty());
    }

    @Test
    void recapitulatifExactEtArborescence() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertTrue(etat.selectionnerModele("application-android"));
        etat.changerNom("mon-application");
        etat.suivante();
        etat.suivante();
        assertEquals(EtatCreation.Etape.RECAPITULATIF, etat.etape());
        // la fiche porte les cinq lignes de la maquette
        List<EtatCreation.LigneRecap> lignes = etat.lignesRecap();
        assertEquals(5, lignes.size());
        assertEquals("modele", lignes.get(0).cle());
        assertEquals("Application Android", lignes.get(0).valeur());
        assertEquals("mon-application", lignes.get(1).valeur());
        assertEquals(":app, :core", lignes.get(2).valeur());
        assertEquals("jo.exemple.monapplication", lignes.get(3).valeur());
        assertEquals("28", lignes.get(4).valeur());
        // l'arbre correspond EXACTEMENT aux fichiers de la génération
        GenerateurProjet.Generation generation = etat.generation();
        assertNotNull(generation);
        assertTrue(generation.exploitable());
        List<EtatCreation.LigneArbo> arbo = etat.arborescence();
        assertEquals("mon-application/", arbo.get(0).texte());
        long fichiersArbo = arbo.stream()
                .filter(l -> !l.annotation())
                .filter(l -> !l.texte().endsWith("/"))
                .count();
        assertEquals(generation.fichiers().size(), fichiersArbo,
                "une ligne de fichier par fichier généré, ni plus ni"
                        + " moins");
        assertTrue(arbo.stream().anyMatch(
                l -> l.annotation() && l.texte().contains(":app")));
        assertTrue(arbo.stream().anyMatch(
                l -> l.annotation() && l.texte().contains(":core")));
    }

    @Test
    void generationRefuseeSansModele() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertNull(etat.generation());
        assertTrue(etat.lignesRecap().isEmpty());
        assertTrue(etat.arborescence().isEmpty());
    }

    @Test
    void minSdkHorsBornesIgnore() throws IOException {
        EtatCreation etat = new EtatCreation(modeles(), List.of());
        assertTrue(etat.selectionnerModele("application-android"));
        etat.changerMinSdk(24);
        assertEquals(28, etat.minSdk(), "24 écarté (ADR-009)");
        etat.changerMinSdk(30);
        assertEquals(30, etat.minSdk());
    }
}
