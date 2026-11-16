package jo.joide.app.android.accueil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * L'état d'écran de l'accueil (ADR-É3) : tri, fusion de la reprise
 * avec la première carte, compteur exact, état vide de première
 * classe, filtre de recherche, âges relatifs et salutation.
 */
class EtatAccueilTest {

    private static final long BASE = 1_700_000_000_000L;

    private static ProjetsRecents.Entree entree(String id, long ouvert) {
        return new ProjetsRecents.Entree(id, id, TypeProjet.CONSOLE, id,
                ouvert, 1);
    }

    @Test
    void aucunProjetEtatVideDePremiereClasse() {
        EtatAccueil.Vue vue = EtatAccueil.composer(List.of(), null);
        assertTrue(vue.premiereFois());
        assertNull(vue.reprise());
        assertTrue(vue.section().isEmpty());
        assertEquals(0, vue.total());
        assertFalse(vue.aucunResultat());
    }

    @Test
    void unProjetLaRepriseFusionneAvecLaPremiereCarte() {
        EtatAccueil.Vue vue = EtatAccueil.composer(
                List.of(entree("seul", BASE)), "");
        assertFalse(vue.premiereFois());
        assertEquals("seul", vue.reprise().id());
        assertTrue(vue.section().isEmpty());
        assertEquals(1, vue.total());
        assertEquals("0", vue.texteCompteur());
        // une section vide ne s'affiche pas : rien à compter
        assertFalse(vue.sectionVisible());
    }

    @Test
    void plusieursProjetsTriDecroissantRepriseExclue() {
        EtatAccueil.Vue vue = EtatAccueil.composer(List.of(
                entree("vieux", BASE - 10L * 86_400_000L),
                entree("recent", BASE),
                entree("moyen", BASE - 86_400_000L)), null);
        assertEquals("recent", vue.reprise().id());
        assertEquals(List.of("moyen", "vieux"),
                vue.section().stream().map(ProjetsRecents.Entree::id)
                        .toList());
        assertEquals(3, vue.total());
        assertEquals(2, vue.compteSection());
        assertEquals("2", vue.texteCompteur());
    }

    @Test
    void egaliteDeMomentTriParIdentifiantDeterministe() {
        EtatAccueil.Vue vue = EtatAccueil.composer(
                List.of(entree("b", BASE), entree("a", BASE)), "");
        assertEquals("a", vue.reprise().id());
        assertEquals(List.of("b"),
                vue.section().stream().map(ProjetsRecents.Entree::id)
                        .toList());
    }

    @Test
    void rechercheFiltreSurLeNomEtCacheLaReprise() {
        ProjetsRecents.Entree proto = new ProjetsRecents.Entree("proto",
                "protocole-p8", TypeProjet.CONSOLE, "proto",
                BASE, 1);
        ProjetsRecents.Entree socle = new ProjetsRecents.Entree("socle",
                "socle-deps", TypeProjet.BIBLIOTHEQUE, "socle",
                BASE - 1000L, 2);
        EtatAccueil.Vue vue = EtatAccueil.composer(List.of(proto, socle),
                "PROTO");
        assertNull(vue.reprise());
        assertEquals(List.of("proto"),
                vue.section().stream().map(ProjetsRecents.Entree::id)
                        .toList());
        assertEquals(2, vue.total());
        assertEquals("1/2", vue.texteCompteur());
        assertFalse(vue.aucunResultat());
    }

    @Test
    void rechercheSansResultatLigneAucunResultatPasEtatVide() {
        EtatAccueil.Vue vue = EtatAccueil.composer(
                List.of(entree("a", BASE)), "zzz");
        assertFalse(vue.premiereFois());
        assertNull(vue.reprise());
        assertTrue(vue.section().isEmpty());
        assertTrue(vue.aucunResultat());
        assertTrue(vue.sectionVisible());
        assertEquals("0/1", vue.texteCompteur());
    }

    @Test
    void nomDefautUniquePourLAssistant() {
        ProjetsRecents.Entree pris = new ProjetsRecents.Entree("x",
                "mon-application", TypeProjet.ANDROID, "x", BASE, 2);
        assertEquals("mon-application-2",
                EtatAccueil.nomDefautUnique(List.of(pris),
                        "mon-application"));
        assertEquals("autre",
                EtatAccueil.nomDefautUnique(List.of(pris), "autre"));
    }

    @Test
    void agesRelatifsParSeuils() {
        long maintenant = BASE;
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.INSTANT, 1),
                AgeRelatif.de(maintenant, maintenant - 59_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.MINUTES, 30),
                AgeRelatif.de(maintenant, maintenant - 30 * 60_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.HEURES, 2),
                AgeRelatif.de(maintenant, maintenant - 2 * 3_600_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.HIER, 1),
                AgeRelatif.de(maintenant, maintenant - 30 * 3_600_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.JOURS, 3),
                AgeRelatif.de(maintenant, maintenant - 3 * 86_400_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.SEMAINE_DERNIERE, 1),
                AgeRelatif.de(maintenant, maintenant - 8 * 86_400_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.SEMAINES, 3),
                AgeRelatif.de(maintenant, maintenant - 22 * 86_400_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.MOIS, 6),
                AgeRelatif.de(maintenant, maintenant - 180L * 86_400_000L));
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.ANNEE, 1),
                AgeRelatif.de(maintenant, maintenant - 400L * 86_400_000L));
    }

    @Test
    void ageDansLeFuturJamaisNegatif() {
        assertEquals(new AgeRelatif.Age(AgeRelatif.Unite.INSTANT, 1),
                AgeRelatif.de(BASE, BASE + 3_600_000L));
    }

    @Test
    void salutationJourEtSoir() {
        assertEquals(Salutation.Moment.BONJOUR, Salutation.de(5));
        assertEquals(Salutation.Moment.BONJOUR, Salutation.de(12));
        assertEquals(Salutation.Moment.BONJOUR, Salutation.de(17));
        assertEquals(Salutation.Moment.BONSOIR, Salutation.de(18));
        assertEquals(Salutation.Moment.BONSOIR, Salutation.de(23));
        assertEquals(Salutation.Moment.BONSOIR, Salutation.de(0));
        assertEquals(Salutation.Moment.BONSOIR, Salutation.de(4));
    }

    @Test
    void tuileTonnaleParType() {
        assertEquals(jo.joide.app.android.ui.choix.ChoixCarte.TypeTuile.ANDROID,
                TypeProjet.ANDROID.tuile());
        assertEquals(jo.joide.app.android.ui.choix.ChoixCarte.TypeTuile.VIDE,
                TypeProjet.GRADLE.tuile());
        assertEquals(java.util.Optional.of(TypeProjet.CONSOLE),
                TypeProjet.deJson("console"));
        assertTrue(TypeProjet.deJson("exotique").isEmpty());
    }
}
