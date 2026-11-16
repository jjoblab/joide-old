package jo.joide.app.android.parametres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.parametres.EtatParametres.Rangee;
import jo.joide.app.android.parametres.EtatParametres.Section;
import jo.joide.app.android.parametres.Reglages.Theme;

/**
 * La « capture » du lot M contre la maquette
 * {@code docs/ecrans/design/parametres.html} et l'ADR-É8 : le test
 * lit la maquette (fichier autonome, textes contractuels) et
 * vérifie que l'état livré reproduit sa structure — sections,
 * contrôles (segment trois options, stepper « 13,5 », interrupteur
 * posé), mesures à droite avec « Vider », et les phrases de
 * conséquence EXACTES de l'ADR sur les deux actions destructrices.
 * La validation visuelle sur appareil revient au propriétaire via
 * l'APK du CI (ADR-048, écart P8 n° 4).
 */
class MaquetteParametresTest {

    @Test
    void laMaquettePorteLesSectionsDeLetat() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("nom-sect\">Apparence"));
        assertTrue(maquette.contains("nom-sect\">Éditeur"));
        assertTrue(maquette.contains("nom-sect\">Build"));
        assertTrue(maquette.contains("nom-sect\">Stockage"));
        // l'état porte les cinq sections, dans cet ordre (l'À propos
        // vit dans l'ADR-É8 §5, l'extrait lisible s'arrête au stockage)
        List<Section> sections = EtatParametres.sections();
        assertEquals("apparence", sections.get(0).cle());
        assertEquals("editeur", sections.get(1).cle());
        assertEquals("build", sections.get(2).cle());
        assertEquals("stockage", sections.get(3).cle());
        assertEquals("a-propos", sections.get(4).cle());
    }

    @Test
    void leSegmentThemeTroisOptionsEtLeStepperTailleDeLaMaquette()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("<span class=\"on\">Sombre</span>"
                + "<span class=\"grise\">Système</span>"
                + "<span class=\"grise\">Clair</span>"));
        assertTrue(maquette.contains("<span class=\"val\">13,5</span>"));
        // l'état porte le même segment et le stepper décimal 10–20
        assertTrue(maquette.contains("Sombre par défaut"));
        assertEquals(Theme.SOMBRE, Reglages.defauts().theme);
        assertEquals(10d, Reglages.TAILLE_CODE_MIN);
        assertEquals(20d, Reglages.TAILLE_CODE_MAX);
        assertEquals(13.5d, Reglages.TAILLE_CODE_DEFAUT);
        Rangee taille = rangee(EtatParametres.R_TAILLE_CODE);
        assertEquals(EtatParametres.Genre.STEPPER_DECIMAL, taille.genre());
    }

    @Test
    void interrupteurNumerosPoseEtMesuresADroiteAvecVider()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Numéros de ligne"));
        assertTrue(maquette.contains("<span class=\"interrupteur on\">"));
        assertTrue(Reglages.defauts().numerosLignes,
                "posé par défaut, comme la maquette");
        assertTrue(maquette.contains(
                "<span class=\"valeur\">42 Mo <span class=\"action-texte"
                        + "\">Vider</span></span>"));
        assertTrue(maquette.contains(
                "<span class=\"valeur\">240 Mo <span class=\"action-texte"
                        + " rouge\">Vider</span></span>"),
                "le vidage m2 est rouge, le cidx accent — deux actions "
                        + "nommées, pas une genericité");
        // les trois mesures vivent dans l'état, CIDX et m2 avec action
        assertEquals(EtatParametres.Genre.MESURE_SEULE,
                rangee(EtatParametres.R_PROJETS).genre());
        assertEquals(EtatParametres.Genre.MESURE_ACTION,
                rangee(EtatParametres.R_CIDX).genre());
        assertEquals(EtatParametres.Genre.MESURE_ACTION,
                rangee(EtatParametres.R_M2).genre());
    }

    @Test
    void leMoteurParDefautEstLaPromotionDeLaCaseDuLotF()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "<span class=\"on\">Service :build</span><span>"
                        + "En processus</span>"));
        assertTrue(maquette.contains(
                "La mort du service interrompt le build, jamais l'IDE."));
        assertEquals(EtatParametres.Genre.SEGMENT,
                rangee(EtatParametres.R_MOTEUR).genre());
        assertTrue(rangee(EtatParametres.R_MOTEUR).legende(),
                "la franchise du service est la légende de la rangée");
    }

    @Test
    void laRechercheDeLaMaquetteFiltreSansJamaisInventer()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "Rechercher dans les paramètres…"),
                "le champ de recherche de la maquette");
        // la règle de franchise vit dans l'ADR-É8 §Franchise
        String adr = lireDocs("docs/ecrans/008-parametres.md");
        assertTrue(adr.contains(
                "La recherche ne trouve que ce qui existe"));
        assertTrue(adr.contains(
                "état affiché, pas un blanc"),
                "le passage à la ligne du Markdown ne casse pas la phrase");
        Function<String, String> libelles = cle -> Map.of(
                EtatParametres.R_CIDX, "Cache CIDX",
                EtatParametres.SECT_STOCKAGE, "Stockage").get(cle);
        assertFalse(EtatParametres.aucunResultat("cidx", libelles));
        assertTrue(EtatParametres.aucunResultat("impossible", libelles));
    }

    @Test
    void lesConsequencesReellesDeLAdrSontDansLesChainesFrancaises()
            throws IOException {
        // ADR-É8 : « Vider le cache CIDX ? L'index se reconstruit à la
        // prochaine synchronisation. » — la phrase exacte vit dans
        // values-fr, la conséquence nominée n'est pas paraphrasée
        String adr = lireDocs("docs/ecrans/008-parametres.md");
        assertTrue(adr.contains(
                "l'index se reconstruit à la prochaine sync"),
                "la conséquence CIDX de l'ADR, telle quelle");
        assertTrue(adr.contains(
                "dépendances seront re-téléchargées"),
                "la conséquence m2 de l'ADR, telle quelle");
        String chaines = lireRes("app-android/src/main/res/values-fr/"
                + "strings.xml");
        assertTrue(chaines.contains(
                "L\\'index se reconstruit à la prochaine "
                        + "synchronisation."));
        assertTrue(chaines.contains(
                "Les dépendances seront re-téléchargées"));
        String chainesEn = lireRes("app-android/src/main/res/values/"
                + "strings.xml");
        assertTrue(chainesEn.contains("rebuilt at the next "
                        + "synchronization"),
                "la conséquence existe aussi en anglais — parité");
    }

    @Test
    void lesReglagesReelsSeulementAucuneRangeeOrpheline() {
        // ADR-É8 : « un réglage = une rangée + un consommateur » —
        // l'état n'expose ni police de code ni thème de syntaxe :
        // sans alternative modifiable ni consommateur, elles ne sont
        // pas des réglages (écart consigné ADR-054)
        List<String> cles = EtatParametres.sections().stream()
                .flatMap(s -> s.rangees().stream())
                .map(Rangee::cle).toList();
        assertFalse(cles.contains("police-code"));
        assertFalse(cles.contains("syntaxe"));
        assertEquals(15, cles.size());
    }

    private static Rangee rangee(String cle) {
        for (Section section : EtatParametres.sections()) {
            for (Rangee rangee : section.rangees()) {
                if (rangee.cle().equals(cle)) {
                    return rangee;
                }
            }
        }
        throw new IllegalStateException("row not found: " + cle);
    }

    private static String maquette() throws IOException {
        return lireDepuisRacine("docs/ecrans/design/parametres.html");
    }

    private static String lireDocs(String relatif) throws IOException {
        return lireDepuisRacine(relatif);
    }

    private static String lireRes(String relatif) throws IOException {
        return lireDepuisRacine(relatif);
    }

    private static String lireDepuisRacine(String relatif)
            throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(relatif);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException(relatif + " not found from "
                + Paths.get("").toAbsolutePath());
    }
}
