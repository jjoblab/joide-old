package jo.joide.agent.outils;

import java.io.IOException;
import java.util.List;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.espace.CheminsConfines;
import jo.joide.agent.espace.DiagnosticEspace;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.agent.espace.OccurrenceEspace;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * Les outils de lecture v1 (lot O, ADR-059) : six outils, lectures
 * OVERLAY-preferred, fenêtres et plafonds PORTÉS PAR L'OUTIL (un
 * résultat déjà borné part une fois dans le cache — leçon 10 de
 * l'ADR-É9). Le confinement passe par {@link CheminsConfines} avant
 * tout accès au port.
 */
public final class OutilsLecture {

    /** Le plafond de lignes d'une fenêtre de lecture. */
    public static final int FENETRE_LIGNES = 400;

    /** Le plafond de caractères d'un résultat de lecture. */
    public static final int PLAFOND_CARACTERES = 50_000;

    /** Le plafond d'occurrences d'une recherche. */
    public static final int PLAFOND_OCCURRENCES = 50;

    private OutilsLecture() {
    }

    /** read_file : lit une fenêtre de lignes d'un fichier. */
    public static OutilAgent lireFichier(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("read_file",
                        "Read a UTF-8 text file from the project. "
                                + "Returns a window of lines with "
                                + "line numbers; use offset and "
                                + "limit for large files.",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\","
                                        + "\"description\":\"relative"
                                        + " file path\"},"
                                        + "\"offset\":{\"type\":"
                                        + "\"integer\",\"minimum\":1,"
                                        + "\"description\":\"first "
                                        + "line to show (1-based)\"},"
                                        + "\"limit\":{\"type\":"
                                        + "\"integer\",\"minimum\":1,"
                                        + "\"description\":\"max "
                                        + "lines to show\"}}"),
                        false, PLAFOND_CARACTERES),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    int debut = Math.max(1,
                            Schemas.entier(arguments, "offset", 1));
                    int limite = Math.min(FENETRE_LIGNES,
                            Schemas.entier(arguments, "limit",
                                    FENETRE_LIGNES));
                    String contenu = espace.lireFichier(chemin);
                    // un saut de ligne final ne crée pas de ligne
                    // fantôme : la dernière ligne vide ne compte pas
                    if (contenu.endsWith("\n")) {
                        contenu = contenu.substring(0,
                                contenu.length() - 1);
                    }
                    String[] lignes = contenu.split("\n", -1);
                    int premier = Math.min(debut, lignes.length);
                    int dernier = Math.min(lignes.length,
                            premier + limite - 1);
                    StringBuilder sortie = new StringBuilder();
                    for (int i = premier; i <= dernier; i++) {
                        if (sortie.length() > PLAFOND_CARACTERES) {
                            sortie.append("\n[output truncated at ")
                                    .append(PLAFOND_CARACTERES)
                                    .append(" characters]");
                            break;
                        }
                        sortie.append(i).append(": ")
                                .append(lignes[i - 1]).append('\n');
                    }
                    sortie.append("(").append(lignes.length)
                            .append(" lines total, showing ")
                            .append(premier).append('-')
                            .append(dernier).append(')');
                    return sortie.toString();
                });
    }

    /** list_dir : liste un dossier. */
    public static OutilAgent listerDossier(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("list_dir",
                        "List the entries of a directory (folders "
                                + "end with '/'). Use \"\" for the "
                                + "project root.",
                        Schemas.objet("{\"path\":{\"type\":\"string\","
                                + "\"description\":\"relative "
                                + "directory path, empty for root\"}}"),
                        false, PLAFOND_CARACTERES),
                (arguments, jeton) -> {
                    String brut = Schemas.texte(arguments, "path", "");
                    String chemin = brut.isEmpty() ? ""
                            : CheminsConfines.confiner(brut);
                    List<String> entrees = espace.listerDossier(chemin);
                    return String.join("\n", entrees) + "\n("
                            + entrees.size() + " entries)";
                });
    }

    /** search_text : cherche un motif, texte ou regex. */
    public static OutilAgent chercherTexte(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("search_text",
                        "Search a pattern across the project files. "
                                + "Plain text by default, regular "
                                + "expression if regex is true.",
                        Schemas.objet(
                                "{\"pattern\":{\"type\":\"string\"},"
                                        + "\"regex\":{\"type\":"
                                        + "\"boolean\"}}"),
                        false, PLAFOND_CARACTERES),
                (arguments, jeton) -> {
                    String motif = Schemas.texte(arguments, "pattern");
                    boolean regex = Schemas.booleen(arguments, "regex",
                            false);
                    List<OccurrenceEspace> occurrences =
                            espace.chercherTexte(motif, regex,
                                    PLAFOND_OCCURRENCES);
                    if (occurrences.size() == PLAFOND_OCCURRENCES) {
                        return formater(occurrences)
                                + "\n(result capped at "
                                + PLAFOND_OCCURRENCES
                                + " matches)";
                    }
                    return formater(occurrences);
                });
    }

    /** find_symbol : cherche où un symbole est déclaré. */
    public static OutilAgent trouverSymbole(
            EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("find_symbol",
                        "Find where a symbol (class, function, "
                                + "variable) is declared in the "
                                + "project.",
                        Schemas.objet("{\"name\":{\"type\":\"string\"}}"),
                        false, PLAFOND_CARACTERES),
                (arguments, jeton) -> {
                    String nom = Schemas.texte(arguments, "name");
                    return formater(espace.trouverSymbole(nom));
                });
    }

    /** get_diagnostics : les problèmes d'un fichier. */
    public static OutilAgent diagnostics(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("get_diagnostics",
                        "Get the language diagnostics of a file "
                                + "(errors, warnings).",
                        Schemas.objet("{\"path\":{\"type\":\"string\"}}"),
                        false, PLAFOND_CARACTERES),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    List<DiagnosticEspace> diagnostiques =
                            espace.diagnostics(chemin);
                    if (diagnostiques.isEmpty()) {
                        return "no diagnostics";
                    }
                    StringBuilder sortie = new StringBuilder();
                    for (DiagnosticEspace diagnostic : diagnostiques) {
                        if (sortie.length() > PLAFOND_CARACTERES) {
                            sortie.append("\n[truncated]");
                            break;
                        }
                        sortie.append(diagnostic.gravite).append(' ')
                                .append(diagnostic.fichier)
                                .append(':').append(diagnostic.ligne)
                                .append(": ")
                                .append(diagnostic.message)
                                .append('\n');
                    }
                    return sortie.toString();
                });
    }

    /** project_overview : l'aperçu honnête du projet. */
    public static OutilAgent apercuProjet(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("project_overview",
                        "Get an overview of the project: modules, "
                                + "structure, build state.",
                        Schemas.aucun(), false, PLAFOND_CARACTERES),
                (arguments, jeton) -> espace.apercuProjet());
    }

    private static String formater(List<OccurrenceEspace> occurrences) {
        if (occurrences.isEmpty()) {
            return "no match";
        }
        StringBuilder sortie = new StringBuilder();
        for (OccurrenceEspace occurrence : occurrences) {
            sortie.append(occurrence.fichier).append(':')
                    .append(occurrence.ligne).append(": ")
                    .append(occurrence.extrait).append('\n');
        }
        return sortie.toString();
    }

}
