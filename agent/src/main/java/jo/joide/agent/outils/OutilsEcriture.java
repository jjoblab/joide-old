package jo.joide.agent.outils;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.espace.CheminsConfines;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.platform.CancelToken;

/**
 * Les outils d'écriture v1 (lot O, ADR-059) : six outils MUTANTS —
 * le marqueur porte la permission, le séquencement et les
 * checkpoints. L'édition exige l'occurrence EXACTE unique ; les
 * chemins sont confinés avant tout accès.
 */
public final class OutilsEcriture {

    private OutilsEcriture() {
    }

    /** create_file : crée, refuse si le fichier existe. */
    public static OutilAgent creerFichier(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("create_file",
                        "Create a new file with the given content. "
                                + "Fails if the file already exists.",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\"},"
                                        + "\"content\":{\"type\":"
                                        + "\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    espace.creerFichier(chemin,
                            Schemas.texte(arguments, "content", ""));
                    return "created " + chemin;
                });
    }

    /** write_file : écrit le contenu complet (création ou remplacement). */
    public static OutilAgent ecrireFichier(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("write_file",
                        "Write the complete content of a file "
                                + "(creates or replaces).",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\"},"
                                        + "\"content\":{\"type\":"
                                        + "\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    espace.ecrireFichier(chemin,
                            Schemas.texte(arguments, "content", ""));
                    return "wrote " + chemin;
                });
    }

    /** edit_file : remplace l'occurrence EXACTE unique. */
    public static OutilAgent editerFichier(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("edit_file",
                        "Replace an EXACT text occurrence in a file. "
                                + "The old text must appear exactly "
                                + "once — include surrounding lines "
                                + "to disambiguate.",
                        Schemas.objet(
                                "{\"path\":{\"type\":\"string\"},"
                                        + "\"old_text\":{\"type\":"
                                        + "\"string\"},"
                                        + "\"new_text\":{\"type\":"
                                        + "\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    espace.editerFichier(chemin,
                            Schemas.texte(arguments, "old_text"),
                            Schemas.texte(arguments, "new_text", ""));
                    return "edited " + chemin;
                });
    }

    /** rename_path : renomme ou déplace un chemin. */
    public static OutilAgent renommerChemin(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("rename_path",
                        "Rename or move a file or directory to a "
                                + "new relative path.",
                        Schemas.objet(
                                "{\"from\":{\"type\":\"string\"},"
                                        + "\"to\":{\"type\":"
                                        + "\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    String de = CheminsConfines.confiner(
                            Schemas.texte(arguments, "from"));
                    String vers = CheminsConfines.confiner(
                            Schemas.texte(arguments, "to"));
                    espace.renommerChemin(de, vers);
                    return "renamed " + de + " to " + vers;
                });
    }

    /** delete_path : supprime un fichier ou dossier. */
    public static OutilAgent supprimerChemin(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("delete_path",
                        "Delete a file or directory (recursively). "
                                + "Irreversible outside checkpoints.",
                        Schemas.objet("{\"path\":{\"type\":\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    String chemin = CheminsConfines.confiner(
                            Schemas.texte(arguments, "path"));
                    espace.supprimerChemin(chemin);
                    return "deleted " + chemin;
                });
    }

    /** add_dependency : résolution P4, lockfile, modèle déclaratif. */
    public static OutilAgent ajouterDependance(
            EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("add_dependency",
                        "Add an external dependency to a module "
                                + "(coordinates group:artifact:"
                                + "version). Resolves against the "
                                + "repositories and writes the "
                                + "lockfile.",
                        Schemas.objet(
                                "{\"module\":{\"type\":\"string\","
                                        + "\"description\":\"module "
                                        + "path, e.g. :app\"},"
                                        + "\"coordinates\":{\"type\":"
                                        + "\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    String module = Schemas.texte(arguments, "module");
                    String coordonnees = Schemas.texte(arguments,
                            "coordinates");
                    boolean ajoute = espace.ajouterDependance(module,
                            coordonnees);
                    return ajoute
                            ? "added " + coordonnees + " to " + module
                            : "already present, nothing to do";
                });
    }
}
