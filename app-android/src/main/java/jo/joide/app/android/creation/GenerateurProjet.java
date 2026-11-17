package jo.joide.app.android.creation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jo.joide.app.android.accueil.ClassifieurProjet;
import jo.joide.app.android.accueil.ProjetsRecents;
import jo.joide.app.android.accueil.TypeProjet;
import jo.joide.model.ContentRole;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.ModuleWriter;
import jo.joide.model.Project;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;

/**
 * Le générateur de projet (ADR-É4) : un modèle déclaratif plus une
 * identité produisent l'arborescence COMPLÈTE en mémoire — le
 * joide.toml et les joide-module.toml par les écrivains canoniques de
 * core-model (deterministes, testés depuis P1), les fichiers du
 * modèle par substitution. Rien n'est écrit ici : l'écriture
 * atomique tout ou rien vit dans {@link EcrireProjet}.
 *
 * <p>Classe PURE, testée JVM : dérivation du paquet par défaut,
 * valeurs par défaut du modèle, validations (identifiants légaux,
 * nom non vide, paquet Android), échec sans écriture. Les
 * substitutions reconnues : {@code {nom}}, {@code {paquet}},
 * {@code {paquet-chemin}}, {@code {min-sdk}}.</p>
 */
public final class GenerateurProjet {

    private GenerateurProjet() {
    }

    /** Le nom par défaut des projets créés (unique par l'état). */
    public static final String NOM_DEFAUT = "mon-application";

    /** Le préfixe des paquets dérivés par défaut. */
    public static final String PREFIXE_PAQUET = "jo.exemple.";

    /** Les niveaux minSdk proposés (28 par défaut, ADR-009 : Java 17). */
    public static final List<Integer> MIN_SDK = List.of(28, 30);

    /** L'identité du projet saisie à l'étape 2. */
    public record Identite(String nom, List<String> modules,
                           String paquet, int minSdk) {
    }

    /** Un fichier généré : chemin relatif et contenu complet. */
    public record FichierGenere(String chemin, String contenu) {
    }

    /** Le résultat de la génération. */
    public record Generation(List<FichierGenere> fichiers,
                             TypeProjet type, int nbModules,
                             String fichierEntree,
                             List<String> problemes,
                             List<String> dossiers) {

        /** @return vrai si la génération est exploitable */
        public boolean exploitable() {
            return problemes.isEmpty();
        }
    }

    /**
     * Génère l'arborescence complète du projet.
     *
     * @param modele le modèle choisi
     * @param identite l'identité saisie (étape 2)
     * @return la génération : fichiers prêts à écrire, type du
     *         projet créé, fichier d'entrée, problèmes (vide si
     *         exploitable)
     */
    public static Generation generer(ModeleProjet.Modele modele,
                                     Identite identite) {
        Objects.requireNonNull(modele, "modele");
        Objects.requireNonNull(identite, "identite");
        List<String> problemes = new ArrayList<>();
        String nom = identite.nom() == null ? "" : identite.nom().trim();
        if (nom.isEmpty()) {
            problemes.add("creation : nom requis");
        } else if (nom.length() > 64) {
            problemes.add("creation : nom de 64 caracteres au plus");
        }
        List<String> choisis = identite.modules();
        if (choisis.isEmpty() && !modele.modules().isEmpty()) {
            problemes.add("creation : au moins un module");
        }
        boolean avecAndroid = modele.modules().stream()
                .filter(m -> choisis.contains(m.id()))
                .anyMatch(m -> m.type().startsWith("android-"));
        String paquet = identite.paquet();
        if (avecAndroid) {
            if (paquet == null || !paquetValide(paquet)) {
                problemes.add("creation : paquet Android invalide");
            }
        }
        if (!MIN_SDK.contains(identite.minSdk())) {
            problemes.add("creation : minSdk hors bornes ("
                    + identite.minSdk() + ")");
        }
        for (ModeleProjet.ModuleModele m : modele.modules()) {
            if (!choisis.contains(m.id())) {
                // un module écarté n'exige rien : ses fichiers
                // disparaissent avec lui, rien ne pend
                continue;
            }
            for (String requis : m.requiert()) {
                if (!choisis.contains(requis)) {
                    problemes.add("creation : " + m.id()
                            + " requiert " + requis
                            + " qui n'est pas conserve");
                }
            }
        }
        if (!problemes.isEmpty()) {
            return new Generation(List.of(), TypeProjet.VIDE, 0, null,
                    problemes, List.of());
        }

        List<Module> modules = new ArrayList<>();
        for (ModeleProjet.ModuleModele m : modele.modules()) {
            if (!choisis.contains(m.id())) {
                continue;
            }
            modules.add(construireModule(m, modele, choisis, identite,
                    problemes));
        }
        // lot IX : les RACINES DÉCLARÉES de chaque module retenu
        // existent dès la création — le modèle « application-android
        // » déclare core/src/main/kotlin sans jamais y mettre de
        // fichier : le dossier manquait, et le premier build
        // échouait sur « cannot scan input directory » (rapport du
        // 2026-11-17) pendant que le moteur apprenait la tolérance.
        // Le dossier VIDE se voit dans l'arborescence de l'atelier,
        // l'utilisateur peut y créer ses fichiers.
        List<String> dossiers = new ArrayList<>();
        for (ModeleProjet.ModuleModele m : modele.modules()) {
            if (!choisis.contains(m.id())) {
                continue;
            }
            for (List<String> racines : m.racines().values()) {
                for (String chemin : racines) {
                    String dossier = m.id() + "/" + chemin;
                    if (!dossiers.contains(dossier)) {
                        dossiers.add(dossier);
                    }
                }
            }
        }
        java.util.Collections.sort(dossiers);
        Map<String, String> reglages = new LinkedHashMap<>();
        if (avecAndroid) {
            reglages.put("android.min-sdk",
                    String.valueOf(identite.minSdk()));
        }
        // le projet implicite « main » est celui du lecteur de
        // workspace (WorkspaceLoader.MAIN_PROJECT, non public)
        Workspace espace = new Workspace(nom,
                Map.of("main", new Project("main",
                        modulesParChemin(modules))),
                reglages);

        List<FichierGenere> fichiers = new ArrayList<>();
        fichiers.add(new FichierGenere(
                jo.joide.model.WorkspaceLoader.WORKSPACE_FILE,
                ModuleWriter.writeWorkspace(espace)));
        for (Module module : modules) {
            fichiers.add(new FichierGenere(
                    module.path().substring(1) + "/"
                            + jo.joide.model.WorkspaceLoader.MODULE_FILE,
                    ModuleWriter.write(module)));
        }
        for (ModeleProjet.FichierModele f : modele.fichiers()) {
            if (f.module() != null && !choisis.contains(f.module())) {
                continue;
            }
            StringBuilder contenu = new StringBuilder(
                    f.lignes().size() * 40);
            for (String ligne : f.lignes()) {
                contenu.append(substituer(ligne, identite)).append('\n');
            }
            fichiers.add(new FichierGenere(
                    substituer(f.chemin(), identite),
                    contenu.toString()));
        }
        fichiers.sort(java.util.Comparator.comparing(
                FichierGenere::chemin));
        TypeProjet type = ClassifieurProjet.classifier(
                jo.joide.app.android.ProjectDetector.Kind.NATIVE, espace);
        return new Generation(fichiers, type, modules.size(),
                modele.fichierEntree() == null ? null
                        : substituer(modele.fichierEntree(), identite),
                problemes, List.copyOf(dossiers));
    }

    private static Module construireModule(
            ModeleProjet.ModuleModele m, ModeleProjet.Modele modele,
            List<String> choisis, Identite identite,
            List<String> problemes) {
        Map<String, SourceSet> ensembles = new LinkedHashMap<>();
        List<ContentRoot> principale = new ArrayList<>();
        List<ContentRoot> tests = new ArrayList<>();
        for (Map.Entry<String, List<String>> racine : m.racines()
                .entrySet()) {
            for (String chemin : racine.getValue()) {
                switch (racine.getKey()) {
                    case "source" -> principale.add(
                            new ContentRoot(chemin, ContentRole.SOURCE));
                    case "test" -> tests.add(
                            new ContentRoot(chemin, ContentRole.SOURCE));
                    case "resources" -> principale.add(
                            new ContentRoot(chemin,
                                    ContentRole.RESOURCES));
                    case "android-res" -> principale.add(
                            new ContentRoot(chemin,
                                    ContentRole.ANDROID_RES));
                    case "assets" -> principale.add(
                            new ContentRoot(chemin, ContentRole.ASSETS));
                    case "aidl" -> principale.add(
                            new ContentRoot(chemin, ContentRole.AIDL));
                    default -> problemes.add("creation : role inconnu "
                            + racine.getKey());
                }
            }
        }
        if (!principale.isEmpty()) {
            ensembles.put("main", new SourceSet("main", principale));
        }
        if (!tests.isEmpty()) {
            ensembles.put("test", new SourceSet("test", tests));
        }
        List<DependencyRef> dependances = new ArrayList<>();
        for (String requis : m.requiert()) {
            if (choisis.contains(requis)) {
                dependances.add(DependencyRef.toModule(":" + requis,
                        DependencyScope.API, java.util.Set.of()));
            }
        }
        for (ModeleProjet.DepExterne dep : modele.dependances()) {
            if (dep.module().equals(m.id())) {
                dependances.add(DependencyRef.external(dep.coordonnees(),
                        DependencyScope.parse(dep.portee()),
                        java.util.Set.of()));
            }
        }
        Map<String, Facet> facets = new LinkedHashMap<>();
        if (m.manifeste() != null) {
            facets.put("android", new Facet("android",
                    Map.of("manifest", substituer(m.manifeste(),
                            identite))));
        }
        if (m.classePrincipale() != null) {
            facets.put("application",
                    new Facet("application", Map.of("main-class",
                            substituer(m.classePrincipale(), identite))));
        }
        return new Module(":" + m.id(), m.id(),
                ModuleType.of(m.type()), ensembles, dependances, facets);
    }

    private static Map<String, Module> modulesParChemin(
            List<Module> modules) {
        Map<String, Module> out = new LinkedHashMap<>();
        for (Module module : modules) {
            out.put(module.path(), module);
        }
        return out;
    }

    /**
     * @param paquet le paquet saisi
     * @return vrai si légal : minuscules (les chiffres après le
     *         premier caractère d'un segment), soulignés, points —
     *         au moins deux segments
     */
    public static boolean paquetValide(String paquet) {
        return paquet != null && paquet.matches(
                "[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
    }

    /**
     * Le paquet par défaut dérivé du nom : « mon-application » →
     * « jo.exemple.monapplication » (les séparateurs du nom
     * disparaissent, ADR-É4).
     */
    public static String paquetDefaut(String nom) {
        String base = nom == null ? "" : nom.toLowerCase(
                java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "projet";
        }
        return PREFIXE_PAQUET + base;
    }

    /** Le nom assaini en identifiant de dossier (ADR-049). */
    public static String idDossier(String nom) {
        return ProjetsRecents.assainirId(nom);
    }

    private static String substituer(String texte, Identite identite) {
        return texte
                .replace("{paquet-chemin}",
                        identite.paquet() == null ? ""
                                : identite.paquet().replace('.', '/'))
                .replace("{paquet}",
                        identite.paquet() == null ? "" : identite.paquet())
                .replace("{min-sdk}",
                        String.valueOf(identite.minSdk()))
                .replace("{nom}",
                        identite.nom() == null ? "" : identite.nom());
    }
}
