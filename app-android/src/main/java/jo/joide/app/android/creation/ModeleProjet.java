package jo.joide.app.android.creation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jo.joide.app.android.ui.choix.ChoixCarte;
import jo.joide.model.toml.Toml;
import jo.joide.model.toml.TomlDocument;
import jo.joide.model.toml.TomlValue;

/**
 * Un modèle de projet au format déclaratif {@code joide.template/1}
 * (ADR-É4, ADR-050) : dérivé du format joide.toml — des déclarations,
 * jamais un script. Un modèle apporte ses modules, ses fichiers, ses
 * dépendances et ses champs (paquet et minSdk dérivés de ses modules
 * Android) sans changer l'assistant : un modèle v3 est un fichier
 * TOML de plus dans {@code assets/modeles/}.
 *
 * <p>Classe PURE : l'analyse du TOML (parseur maison de core-model,
 * ADR-018) est tolérante et signale — un champ inconnu est un
 * problème, jamais une réparation silencieuse. Les contenus de
 * fichiers sont des tableaux de lignes littérales (chaînes TOML
 * sans échappement) ; les substitutions ({@code {nom}},
 * {@code {paquet}}, {@code {paquet-chemin}}, {@code {min-sdk}})
 * se font à la génération, pas à l'analyse.</p>
 */
public final class ModeleProjet {

    private ModeleProjet() {
    }

    /** Schéma versionné des modèles. */
    public static final String SCHEMA = "joide.template/1";

    /** Les catégories de la galerie (maquette création-projet.html). */
    public enum Categorie {
        /** Applications (Android). */
        APPLICATIONS,
        /** Bibliothèques et consoles JVM. */
        BIBLIOTHEQUES,
        /** Point de départ vide. */
        VIDE
    }

    /**
     * Un module proposé par le modèle : le miroir d'un
     * joide-module.toml aplati — racines par rôle, ensemble « test »,
     * manifeste Android, classe principale, modules requis.
     */
    public record ModuleModele(String id, String type, boolean defaut,
                               Map<String, List<String>> racines,
                               String manifeste, String classePrincipale,
                               List<String> requiert) {
    }

    /** Un fichier généré par le modèle, lié à un module ou non. */
    public record FichierModele(String chemin, String module,
                                List<String> lignes) {
    }

    /** Une dépendance externe posée sur un module du modèle. */
    public record DepExterne(String module, String coordonnees,
                             String portee) {
    }

    /**
     * Le modèle complet. {@code franchise} (couleur alerte) et
     * {@code note} (gris) sont la franchise de la carte — au moins
     * l'une des deux est attendue ; {@code fichierEntree} est le
     * fichier ouvert dans l'atelier après création.
     */
    public record Modele(String id, String nom, String description,
                         Categorie categorie, ChoixCarte.TypeTuile tuile,
                         String franchise, String note,
                         String fichierEntree,
                         List<ModuleModele> modules,
                         List<FichierModele> fichiers,
                         List<DepExterne> dependances) {
    }

    /** Résultat d'analyse : le modèle ou ses problèmes, jamais null. */
    public record Analyse(Modele modele, List<String> problemes) {
        /** @return vrai si le modèle est exploitable */
        public boolean exploitable() {
            return modele != null;
        }
    }

    /**
     * Analyse un document de modèle.
     *
     * @param texte le contenu TOML
     * @return le modèle et ses problèmes (le modèle est null si le
     *         document est inutilisable)
     */
    public static Analyse analyser(String texte) {
        List<String> problemes = new ArrayList<>();
        TomlDocument document = Toml.parse(texte == null ? "" : texte);
        document.problems().forEach(p -> problemes.add(
                "modele : ligne " + p.line() + " : " + p.message()));
        TomlValue racine = document.root();
        Map<String, TomlValue> table = racine.asTable();
        if (table == null) {
            problemes.add("modele : document vide");
            return new Analyse(null, problemes);
        }
        String schema = texte(table.get("schema"));
        if (!SCHEMA.equals(schema)) {
            problemes.add("modele : schema attendu " + SCHEMA
                    + ", trouve " + schema);
            return new Analyse(null, problemes);
        }
        String id = texte(table.get("id"));
        String nom = texte(table.get("nom"));
        String description = texte(table.get("description"));
        String categorieBrute = texte(table.get("categorie"));
        String tuileBrute = texte(table.get("tuile"));
        if (id == null || nom == null || description == null
                || categorieBrute == null || tuileBrute == null) {
            problemes.add("modele : id, nom, description, categorie et"
                    + " tuile sont obligatoires");
            return new Analyse(null, problemes);
        }
        Categorie categorie = switch (categorieBrute) {
            case "applications" -> Categorie.APPLICATIONS;
            case "bibliotheques" -> Categorie.BIBLIOTHEQUES;
            case "vide" -> Categorie.VIDE;
            default -> null;
        };
        ChoixCarte.TypeTuile tuile;
        try {
            tuile = ChoixCarte.TypeTuile.valueOf(
                    tuileBrute.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            tuile = null;
        }
        if (categorie == null || tuile == null) {
            problemes.add("modele " + id + " : categorie ou tuile"
                    + " inconnue (" + categorieBrute + " / " + tuileBrute
                    + ")");
            return new Analyse(null, problemes);
        }
        // un modèle sans module est légal : le modèle Vide est un
        // joide.toml minimal (ADR-É4) — le générateur n'écrit que lui
        List<ModuleModele> modules = lireModules(table.get("modules"),
                problemes);
        List<FichierModele> fichiers = lireFichiers(
                table.get("fichiers"), problemes);
        List<DepExterne> dependances = lireDependances(
                table.get("dependances"), problemes);
        if (texte(table.get("franchise")) == null
                && texte(table.get("note")) == null) {
            problemes.add("modele " + id + " : franchise ou note"
                    + " attendue (jamais une promesse muette)");
        }
        for (String cle : table.keySet()) {
            if (!List.of("schema", "id", "nom", "description", "categorie",
                    "tuile", "franchise", "note", "fichier-entree",
                    "modules", "fichiers", "dependances").contains(cle)) {
                problemes.add("modele " + id + " : cle inconnue " + cle);
            }
        }
        if (!problemes.isEmpty()) {
            return new Analyse(null, problemes);
        }
        return new Analyse(new Modele(id, nom, description, categorie,
                tuile, texte(table.get("franchise")),
                texte(table.get("note")),
                texte(table.get("fichier-entree")),
                modules, fichiers, dependances), problemes);
    }

    private static List<ModuleModele> lireModules(TomlValue serie,
                                                  List<String> problemes) {
        List<ModuleModele> modules = new ArrayList<>();
        if (serie == null) {
            return modules;
        }
        List<TomlValue> elements = serie.asArray();
        if (elements == null) {
            problemes.add("modele : modules doit etre une serie");
            return modules;
        }
        for (TomlValue element : elements) {
            Map<String, TomlValue> bloc = element.asTable();
            if (bloc == null) {
                problemes.add("modele : module mal forme");
                continue;
            }
            String id = texte(bloc.get("id"));
            String type = texte(bloc.get("type"));
            if (id == null || type == null) {
                problemes.add("modele : module sans id ou type");
                continue;
            }
            Map<String, List<String>> racines = new LinkedHashMap<>();
            for (String role : List.of("source", "test", "resources",
                    "android-res", "assets", "aidl")) {
                List<String> chemins = textes(bloc.get(role));
                if (!chemins.isEmpty()) {
                    racines.put(role, chemins);
                }
            }
            ModuleModele module = new ModuleModele(id, type,
                    booleen(bloc.get("defaut")), racines,
                    texte(bloc.get("manifeste")),
                    texte(bloc.get("main-class")),
                    textes(bloc.get("requiert")));
            if (modules.stream().anyMatch(m -> m.id().equals(id))) {
                problemes.add("modele : module en doublon " + id);
                continue;
            }
            modules.add(module);
        }
        return modules;
    }

    private static List<FichierModele> lireFichiers(TomlValue serie,
                                                    List<String> problemes) {
        List<FichierModele> fichiers = new ArrayList<>();
        if (serie == null) {
            return fichiers;
        }
        List<TomlValue> elements = serie.asArray();
        if (elements == null) {
            problemes.add("modele : fichiers doit etre une serie");
            return fichiers;
        }
        for (TomlValue element : elements) {
            Map<String, TomlValue> bloc = element.asTable();
            if (bloc == null) {
                problemes.add("modele : fichier mal forme");
                continue;
            }
            String chemin = texte(bloc.get("chemin"));
            List<String> lignes = textes(bloc.get("lignes"));
            if (chemin == null || lignes.isEmpty()) {
                problemes.add("modele : fichier sans chemin ou lignes");
                continue;
            }
            fichiers.add(new FichierModele(chemin,
                    texte(bloc.get("module")), lignes));
        }
        return fichiers;
    }

    private static List<DepExterne> lireDependances(TomlValue serie,
                                                    List<String> problemes) {
        List<DepExterne> dependances = new ArrayList<>();
        if (serie == null) {
            return dependances;
        }
        List<TomlValue> elements = serie.asArray();
        if (elements == null) {
            problemes.add("modele : dependances doit etre une serie");
            return dependances;
        }
        for (TomlValue element : elements) {
            Map<String, TomlValue> bloc = element.asTable();
            if (bloc == null) {
                problemes.add("modele : dependance mal formee");
                continue;
            }
            String module = texte(bloc.get("module"));
            String coordonnees = texte(bloc.get("coordonnees"));
            if (module == null || coordonnees == null) {
                problemes.add("modele : dependance sans module ni"
                        + " coordonnees");
                continue;
            }
            dependances.add(new DepExterne(module, coordonnees,
                    Optional.ofNullable(texte(bloc.get("portee")))
                            .orElse("implementation")));
        }
        return dependances;
    }

    private static String texte(TomlValue valeur) {
        return valeur == null ? null : valeur.asString();
    }

    private static List<String> textes(TomlValue valeur) {
        List<String> out = new ArrayList<>();
        if (valeur != null && valeur.asArray() != null) {
            for (TomlValue item : valeur.asArray()) {
                String s = item.asString();
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static boolean booleen(TomlValue valeur) {
        Boolean valeurBrute = valeur == null ? null : valeur.asBoolean();
        return valeurBrute != null && valeurBrute;
    }
}
