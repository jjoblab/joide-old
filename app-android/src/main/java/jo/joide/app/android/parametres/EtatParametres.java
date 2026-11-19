package jo.joide.app.android.parametres;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * L'état d'écran des Paramètres (lot M, ADR-É8/054) : la structure
 * des sections et rangées, le filtrage par la recherche, la franchise
 * des actions destructrices. Classe PURE testée JVM — l'écran monte,
 * traduit et déroule.
 *
 * <p>Règles portées par l'état : une section dont aucune rangée ne
 * correspond à la recherche ne s'affiche pas ; « aucun résultat » est
 * un état affiché, distinct d'un écran vide ; chaque action
 * destructrice porte SA conséquence réelle en une phrase (identifiée
 * par son identifiant de chaîne, jamais un texte générique qui
 * cache).</p>
 */
public final class EtatParametres {

    /** Le genre de contrôle d'une rangée (ce que l'écran y monte). */
    public enum Genre {
        /** Options exclusives (segment) avec légende facultative. */
        SEGMENT,
        /** Stepper entier (valeur affichée sans décimale). */
        STEPPER_ENTIER,
        /** Stepper décimal (une décimale affichée, « 13,5 »). */
        STEPPER_DECIMAL,
        /** Commutateur (interrupteur). */
        COMMUTATEUR,
        /** Nuancier restreint (pastilles). */
        NUANCIER,
        /** Mesure disque + action destructrice (« 42 Mo · Vider »). */
        MESURE_ACTION,
        /** Mesure disque seule (aucune action possible). */
        MESURE_SEULE,
        /** Action simple (texte accentué « Consulter »). */
        ACTION,
        /** Information en lecture seule (valeur à droite). */
        INFO
    }

    /** Une rangée : identifiant stable, genre, légende facultative. */
    public record Rangee(String cle, Genre genre, boolean legende) {
    }

    /** Une section : nom d'affichage et rangées dans l'ordre. */
    public record Section(String cle, List<Rangee> rangees) {
    }

    /** Identifiants des sections (ordre de l'écran). */
    public static final String SECT_APPARENCE = "apparence";
    public static final String SECT_EDITEUR = "editeur";
    public static final String SECT_BUILD = "build";
    public static final String SECT_IA = "ia";
    public static final String SECT_STOCKAGE = "stockage";
    public static final String SECT_A_PROPOS = "a-propos";

    /** Identifiants des rangées (stables : recherche, tests, écran). */
    public static final String R_THEME = "theme";
    public static final String R_ACCENT = "accent";
    public static final String R_ECHELLE_UI = "echelle-ui";
    public static final String R_TAILLE_CODE = "taille-code";
    public static final String R_NUMEROS = "numeros";
    public static final String R_RETOUR_LIGNE = "retour-ligne";
    public static final String R_MOTEUR = "moteur";
    public static final String R_FILS = "fils";
    public static final String R_PROJETS = "projets-importes";
    public static final String R_CIDX = "cache-cidx";
    public static final String R_M2 = "m2";
    public static final String R_VERSION = "version";
    public static final String R_LICENCES = "licences";
    public static final String R_JOURNAL = "journal";
    public static final String R_DEPOT = "depot";
    public static final String R_CLE_ANTHROPIC = "cle-anthropic";
    public static final String R_CLE_OPENAI = "cle-openai";
    public static final String R_CLE_GEMINI = "cle-gemini";
    /** La clé OpenRouter (lot U) : la passerelle multi-modèles. */
    public static final String R_CLE_OPENROUTER = "cle-openrouter";
    public static final String R_MODELE_IA = "modele-ia";
    public static final String R_URL_IA = "url-ia";

    /**
     * La structure complète de l'écran : cinq sections, chaque rangée
     * a un consommateur réel (ADR-É8 : jamais une rangée orpheline —
     * police de code et thème de syntaxe, sans alternative ni
     * consommateur modifiable, n'en font pas partie).
     */
    public static List<Section> sections() {
        return List.of(
                new Section(SECT_APPARENCE, List.of(
                        new Rangee(R_THEME, Genre.SEGMENT, true),
                        new Rangee(R_ACCENT, Genre.NUANCIER, false),
                        new Rangee(R_ECHELLE_UI, Genre.STEPPER_ENTIER,
                                true))),
                new Section(SECT_EDITEUR, List.of(
                        new Rangee(R_TAILLE_CODE, Genre.STEPPER_DECIMAL,
                                false),
                        new Rangee(R_NUMEROS, Genre.COMMUTATEUR, false),
                        new Rangee(R_RETOUR_LIGNE, Genre.COMMUTATEUR,
                                false))),
                new Section(SECT_BUILD, List.of(
                        new Rangee(R_MOTEUR, Genre.SEGMENT, true),
                        new Rangee(R_FILS, Genre.STEPPER_ENTIER, true))),
                new Section(SECT_IA, List.of(
                        new Rangee(R_CLE_ANTHROPIC, Genre.ACTION, true),
                        new Rangee(R_CLE_OPENAI, Genre.ACTION, true),
                        new Rangee(R_CLE_GEMINI, Genre.ACTION, true),
                        new Rangee(R_CLE_OPENROUTER, Genre.ACTION, true),
                        new Rangee(R_MODELE_IA, Genre.ACTION, true),
                        new Rangee(R_URL_IA, Genre.ACTION, true))),
                new Section(SECT_STOCKAGE, List.of(
                        new Rangee(R_PROJETS, Genre.MESURE_SEULE, false),
                        new Rangee(R_CIDX, Genre.MESURE_ACTION, false),
                        new Rangee(R_M2, Genre.MESURE_ACTION, false))),
                new Section(SECT_A_PROPOS, List.of(
                        new Rangee(R_VERSION, Genre.INFO, false),
                        new Rangee(R_LICENCES, Genre.ACTION, false),
                        new Rangee(R_JOURNAL, Genre.ACTION, false),
                        new Rangee(R_DEPOT, Genre.ACTION, false))));
    }

    /**
     * Les actions destructrices et leur conséquence réelle (identifiant
     * de chaîne : la phrase exacte vit dans les ressources, en deux
     * langues — le test de maquette prouve qu'elle dit ce que l'ADR
     * exige).
     *
     * @return l'association rangée vers chaîne de conséquence
     */
    public static List<String> actionsDestructrices() {
        return List.of(R_CIDX, R_M2);
    }

    /**
     * Filtre les sections par la recherche : une rangée correspond si
     * son libellé affiché OU le nom de sa section contient la requête
     * (insensible à la casse et aux accents) ; les sections sans
     * rangée correspondante disparaissent.
     *
     * @param requete  le texte cherché (vide ou blanc : tout)
     * @param libelles la fonction rangée/section vers libellé affiché
     * @return les sections vivantes, rangées filtrées dedans
     */
    public static List<Section> filtrer(String requete,
                                        Function<String, String> libelles) {
        Objects.requireNonNull(libelles, "libelles");
        String cible = normaliser(requete);
        if (cible.isEmpty()) {
            return sections();
        }
        List<Section> vivantes = new ArrayList<>();
        for (Section section : sections()) {
            String nomSection = normaliser(
                    Objects.requireNonNullElse(libelles.apply(section.cle()),
                            ""));
            List<Rangee> gardees = new ArrayList<>();
            for (Rangee rangee : section.rangees()) {
                String libelle = normaliser(
                        Objects.requireNonNullElse(libelles.apply(rangee.cle()),
                                ""));
                if (libelle.contains(cible) || nomSection.contains(cible)) {
                    gardees.add(rangee);
                }
            }
            if (!gardees.isEmpty()) {
                vivantes.add(new Section(section.cle(), gardees));
            }
        }
        return vivantes;
    }

    /**
     * @param requete  le texte cherché
     * @param libelles la fonction rangée/section vers libellé affiché
     * @return vrai si la recherche est ouverte et ne trouve RIEN —
     *         un état à afficher, jamais un blanc
     */
    public static boolean aucunResultat(String requete,
                                         Function<String, String> libelles) {
        return !normaliser(requete).isEmpty()
                && filtrer(requete, libelles).isEmpty();
    }

    /**
     * La clé de la dernière rangée VIVANTE d'une section filtrée
     * (lot A3 : le séparateur de carte s'arrête avant elle — la
     * maquette ne trace jamais de trait sous la dernière rangée
     * d'un groupe, et le filtrage déplace cette dernière).
     *
     * @param section la section FILTRÉE (rangées vivantes dedans)
     * @return la clé de sa dernière rangée, ou {@code null} si vide
     */
    public static String derniereRangee(Section section) {
        List<Rangee> rangees = section.rangees();
        return rangees.isEmpty() ? null
                : rangees.get(rangees.size() - 1).cle();
    }

    /** Normalisation de recherche : minuscules, sans accent, sans blancs parasites. */
    static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        String plat = java.text.Normalizer.normalize(texte.trim(),
                java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plat.toLowerCase(Locale.ROOT);
    }

    private EtatParametres() {
    }
}
