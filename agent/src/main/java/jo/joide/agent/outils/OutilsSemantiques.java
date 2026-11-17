package jo.joide.agent.outils;

import java.util.List;
import java.util.Optional;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.espace.CheminsConfines;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.agent.espace.PositionEspace;

/**
 * Les outils sémantiques v1 (lot O, ADR-059) : quatre outils sur les
 * moteurs de langage — la préférence de l'ADR-É9 va aux outils
 * sémantiques plutôt qu'aux bidouilles de texte. Ce que les briques
 * ne servent pas encore (références, renommage, formatage côté
 * moteur — vides honnêtes du lot D) remonte comme NON DISPONIBLE
 * via le défaut dégradé du port, jamais comme « rien trouvé ».
 */
public final class OutilsSemantiques {

    private OutilsSemantiques() {
    }

    /** go_to_definition : la déclaration du symbole à la position. */
    public static OutilAgent allerDefinition(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("go_to_definition",
                        "Go to the declaration of the symbol at the "
                                + "given 1-based line and column.",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\"},"
                                        + "\"line\":{\"type\":"
                                        + "\"integer\",\"minimum\":1},"
                                        + "\"column\":{\"type\":"
                                        + "\"integer\",\"minimum\":1}}"),
                        false, 4_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    Optional<PositionEspace> cible = espace.definition(
                            chemin,
                            Schemas.entier(arguments, "line", 1),
                            Schemas.entier(arguments, "column", 1));
                    if (cible.isEmpty()) {
                        return "no declaration found";
                    }
                    PositionEspace position = cible.get();
                    return position.fichier + ":" + position.ligne
                            + ":" + position.colonne
                            + (position.nom == null ? ""
                            : " (" + position.nom + ")");
                });
    }

    /** find_references : les références du symbole à la position. */
    public static OutilAgent chercherReferences(
            EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("find_references",
                        "Find all references to the symbol at the "
                                + "given position.",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\"},"
                                        + "\"line\":{\"type\":"
                                        + "\"integer\",\"minimum\":1},"
                                        + "\"column\":{\"type\":"
                                        + "\"integer\",\"minimum\":1}}"),
                        false, 20_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    List<PositionEspace> references = espace.references(
                            chemin,
                            Schemas.entier(arguments, "line", 1),
                            Schemas.entier(arguments, "column", 1));
                    if (references.isEmpty()) {
                        return "no reference found";
                    }
                    StringBuilder sortie = new StringBuilder();
                    for (PositionEspace position : references) {
                        sortie.append(position.fichier).append(':')
                                .append(position.ligne).append(':')
                                .append(position.colonne)
                                .append('\n');
                    }
                    return sortie.toString();
                });
    }

    /** rename_symbol : renomme le symbole partout dans le projet. */
    public static OutilAgent renommerSymbole(
            EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("rename_symbol",
                        "Rename a symbol across the project "
                                + "(semantic rename, not text "
                                + "replace).",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\"},"
                                        + "\"line\":{\"type\":"
                                        + "\"integer\",\"minimum\":1},"
                                        + "\"column\":{\"type\":"
                                        + "\"integer\",\"minimum\":1},"
                                        + "\"new_name\":{\"type\":"
                                        + "\"string\"}}"),
                        true, 4_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    espace.renommerSymbole(chemin,
                            Schemas.entier(arguments, "line", 1),
                            Schemas.entier(arguments, "column", 1),
                            Schemas.texte(arguments, "new_name"));
                    return "symbol renamed to "
                            + Schemas.texte(arguments, "new_name");
                });
    }

    /** format_file : formate un fichier (le texte formaté revient écrit). */
    public static OutilAgent formaterFichier(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("format_file",
                        "Format a file with the language formatter.",
                        Schemas.objet("{\"path\":{\"type\":\"string\"}}"),
                        true, 4_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    String formate = espace.formaterFichier(chemin);
                    espace.ecrireFichier(chemin, formate);
                    return "formatted " + chemin;
                });
    }
}
