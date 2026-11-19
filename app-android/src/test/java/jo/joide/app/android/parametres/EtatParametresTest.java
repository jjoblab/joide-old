package jo.joide.app.android.parametres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.parametres.EtatParametres.Genre;
import jo.joide.app.android.parametres.EtatParametres.Rangee;
import jo.joide.app.android.parametres.EtatParametres.Section;

/**
 * L'état d'écran des Paramètres (lot M, ADR-É8/054) : structure
 * complète (chaque rangée a un consommateur), filtrage par la
 * recherche (le nom de section compte), sections vides cachées,
 * « aucun résultat » distinct de l'écran vide.
 */
class EtatParametresTest {

    /** Les libellés du test : la table que l'écran remplit pareil. */
    private static final Function<String, String> LIBELLES =
            cle -> Map.ofEntries(
                    Map.entry(EtatParametres.SECT_APPARENCE, "Apparence"),
                    Map.entry(EtatParametres.SECT_EDITEUR, "Éditeur"),
                    Map.entry(EtatParametres.SECT_BUILD, "Build"),
                    Map.entry(EtatParametres.SECT_STOCKAGE, "Stockage"),
                    Map.entry(EtatParametres.SECT_A_PROPOS, "À propos"),
                    Map.entry(EtatParametres.R_THEME, "Thème"),
                    Map.entry(EtatParametres.R_ACCENT, "Accent"),
                    Map.entry(EtatParametres.R_ECHELLE_UI,
                            "Taille de police UI"),
                    Map.entry(EtatParametres.R_TAILLE_CODE,
                            "Taille du code"),
                    Map.entry(EtatParametres.R_NUMEROS,
                            "Numéros de ligne"),
                    Map.entry(EtatParametres.R_RETOUR_LIGNE,
                            "Retour à la ligne"),
                    Map.entry(EtatParametres.R_MOTEUR,
                            "Moteur par défaut"),
                    Map.entry(EtatParametres.R_FILS, "Fils par build"),
                    Map.entry(EtatParametres.R_PROJETS,
                            "Projets importés"),
                    Map.entry(EtatParametres.R_CIDX, "Cache CIDX"),
                    Map.entry(EtatParametres.R_M2, "Dépendances (m2)"),
                    Map.entry(EtatParametres.R_VERSION, "Version"),
                    Map.entry(EtatParametres.R_LICENCES,
                            "Licences et NOTICE"),
                    Map.entry(EtatParametres.R_JOURNAL,
                            "Journal de diagnostic"),
                    Map.entry(EtatParametres.R_DEPOT, "Dépôt"))
                    .getOrDefault(cle, "");

    @Test
    void sixSectionsDansLordreDesAdr() {
        List<Section> sections = EtatParametres.sections();
        assertEquals(List.of("apparence", "editeur", "build", "ia",
                "stockage", "a-propos"),
                sections.stream().map(Section::cle).toList());
    }

    @Test
    void lesGenresCorrespondentAuxControlesDeLaMaquette() {
        Map<String, Genre> genres = new java.util.HashMap<>();
        for (Section section : EtatParametres.sections()) {
            for (Rangee rangee : section.rangees()) {
                genres.put(rangee.cle(), rangee.genre());
            }
        }
        assertEquals(Genre.SEGMENT, genres.get(EtatParametres.R_THEME));
        assertEquals(Genre.NUANCIER, genres.get(EtatParametres.R_ACCENT));
        assertEquals(Genre.STEPPER_ENTIER,
                genres.get(EtatParametres.R_ECHELLE_UI));
        assertEquals(Genre.STEPPER_DECIMAL,
                genres.get(EtatParametres.R_TAILLE_CODE));
        assertEquals(Genre.COMMUTATEUR,
                genres.get(EtatParametres.R_NUMEROS));
        assertEquals(Genre.COMMUTATEUR,
                genres.get(EtatParametres.R_RETOUR_LIGNE));
        assertEquals(Genre.SEGMENT, genres.get(EtatParametres.R_MOTEUR));
        assertEquals(Genre.STEPPER_ENTIER,
                genres.get(EtatParametres.R_FILS));
        assertEquals(Genre.MESURE_SEULE,
                genres.get(EtatParametres.R_PROJETS));
        assertEquals(Genre.MESURE_ACTION,
                genres.get(EtatParametres.R_CIDX));
        assertEquals(Genre.MESURE_ACTION, genres.get(EtatParametres.R_M2));
        assertEquals(Genre.INFO, genres.get(EtatParametres.R_VERSION));
        assertEquals(Genre.ACTION, genres.get(EtatParametres.R_LICENCES));
        assertEquals(Genre.ACTION, genres.get(EtatParametres.R_JOURNAL));
        assertEquals(Genre.ACTION, genres.get(EtatParametres.R_DEPOT));
    }

    @Test
    void vingtDeuxRangeesEtDeuxActionsDestructricesExactement() {
        int rangees = EtatParametres.sections().stream()
                .mapToInt(s -> s.rangees().size()).sum();
        assertEquals(22, rangees,
                "le lot C2 ajoute la rangée de fournisseur IA — la "
                        + "remontée du terrain : le tiroir agent tuait "
                        + "l'app, le provider y devenait impossible "
                        + "à choisir");
        assertEquals(List.of(EtatParametres.R_CIDX, EtatParametres.R_M2),
                EtatParametres.actionsDestructrices(),
                "seules les deux vidanges sont destructrices, "
                        + "avec leur conséquence nominée");
    }

    @Test
    void laRechercheVideMontreTout() {
        assertEquals(EtatParametres.sections().size(),
                EtatParametres.filtrer("", LIBELLES).size());
        assertEquals(EtatParametres.sections().size(),
                EtatParametres.filtrer("   ", LIBELLES).size());
        assertFalse(EtatParametres.aucunResultat("", LIBELLES));
    }

    @Test
    void laRechercheTrouveUneRangeeParSonLibelle() {
        List<Section> vives = EtatParametres.filtrer("numéros", LIBELLES);
        assertEquals(1, vives.size());
        assertEquals(EtatParametres.SECT_EDITEUR, vives.get(0).cle());
        assertEquals(1, vives.get(0).rangees().size());
        assertEquals(EtatParametres.R_NUMEROS,
                vives.get(0).rangees().get(0).cle());
    }

    @Test
    void laRechercheParNomDeSectionGardeToutesLesRangeesDeLaSection() {
        List<Section> vives = EtatParametres.filtrer("stockage", LIBELLES);
        assertEquals(1, vives.size());
        assertEquals(3, vives.get(0).rangees().size());
    }

    @Test
    void laRechercheEstInsensibleAuxAccentsEtALaCasse() {
        assertEquals(1, EtatParametres.filtrer("EDITEUR", LIBELLES).size());
        assertEquals(1, EtatParametres.filtrer("éditeur", LIBELLES).size());
        // « depot » trouve « Dépôt » : les accents ne gênent pas
        List<Section> vives = EtatParametres.filtrer("depot", LIBELLES);
        assertEquals(1, vives.size());
        assertEquals(EtatParametres.SECT_A_PROPOS, vives.get(0).cle());
    }

    @Test
    void lesSectionsSansCorrespondanceDisparaissent() {
        List<Section> vives = EtatParametres.filtrer("thème", LIBELLES);
        assertEquals(1, vives.size());
        // le thème vit dans Apparence : l'accent et l'échelle
        // disparaissent avec les autres sections
        assertEquals(1, vives.get(0).rangees().size());
    }

    @Test
    void aucunResultatEstUnEtatPasUnBlanc() {
        assertTrue(EtatParametres.aucunResultat("zzz-inexistant",
                LIBELLES));
        assertFalse(EtatParametres.aucunResultat("thème", LIBELLES));
        assertFalse(EtatParametres.aucunResultat("", LIBELLES));
    }

    @Test
    void chaqueRangeeNeParaitQuuneFois() {
        List<String> vues = new java.util.ArrayList<>();
        for (Section section : EtatParametres.sections()) {
            for (Rangee rangee : section.rangees()) {
                assertFalse(vues.contains(rangee.cle()));
                vues.add(rangee.cle());
            }
        }
        assertEquals(21, vues.size(),
                "le lot U ajoute la rangée de clé OpenRouter");
    }

    @Test
    void laDerniereRangeeVivanteArreteLeSeparateurDeCarte() {
        // lot A3 : le trait de fin de rangée vit DANS chaque item — il
        // disparaît sous la dernière rangée VIVANTE du groupe (la
        // maquette : .rangée:last-child sans bord ; le filtrage
        // déplace cette dernière)
        assertEquals(EtatParametres.R_M2, EtatParametres.derniereRangee(
                EtatParametres.filtrer("stockage", LIBELLES).get(0)));
        assertEquals(EtatParametres.R_NUMEROS,
                EtatParametres.derniereRangee(
                        EtatParametres.filtrer("numéros", LIBELLES)
                                .get(0)));
        assertEquals(EtatParametres.R_TAILLE_CODE,
                EtatParametres.derniereRangee(
                        EtatParametres.filtrer("taille du code", LIBELLES)
                                .get(0)),
                "une recherche qui prend la première rangée en fait "
                        + "aussi la dernière");
    }
}
