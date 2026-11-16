package jo.joide.app.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jo.joide.build.android.toolchain.SafeZip;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.ide.sync.ExternalResolution;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.Workspace;
import jo.joide.platform.Hashing;

/**
 * Constitution de la description de build (protocole P6) depuis le
 * modèle du workspace : les modules JVM deviennent des modules du
 * pipeline, les externes résolus fournissent les jars, tout le reste
 * est rapporté — jamais une omission muette.
 *
 * <p>LOT U (ADR-066) : D8/R8 et aapt2 sont EMBARQUÉS — la chaîne
 * Android tourne SUR APPAREIL : un workspace qui contient au moins
 * un module Android part dans le pipeline ANDROID complet (tous ses
 * frères JVM suivent, compilés par la même fabrique) ; l'outillage
 * est localisé par le {@code ToolchainLocator} (aapt2 embarqué en
 * jniLibs, android.jar et r8 téléchargés épinglés). La JVM pure
 * garde son pipeline historique. Les AAR se EXTRAIENT ici (classes
 * et libs — leurs ressources restent un écart honnête).</p>
 */
public final class SpecsBuild {

    /** Niveau de source par défaut du pipeline (artefacts 17). */
    static final String NIVEAU_SOURCE = "17";

    /** Budget d'admission par défaut (Mio) — appareils modestes. */
    static final int TAS_PAR_DEFAUT_MIO = 448;

    /**
     * Fils de construction par défaut du démon sur appareil (public
     * depuis le lot H : l'écran IDE vit dans son propre paquet).
     */
    public static final int FILS_PAR_DEFAUT = 2;

    private SpecsBuild() {
    }

    /** Résultat de la constitution : la spec ou les écarts. */
    public static final class Constitution {

        /** La description, ou null si rien n'est constructible. */
        public final BuildSpec spec;
        /** Écarts et refus, un par ligne, jamais perdus. */
        public final List<String> ecarts;

        Constitution(BuildSpec spec, List<String> ecarts) {
            this.spec = spec;
            this.ecarts = List.copyOf(ecarts);
        }

        /** @return vrai si une spec constructible est sortie */
        public boolean constructible() {
            return spec != null;
        }
    }

    /**
     * Constitue la description du build du workspace — LOT U : la
     * route ANDROID s'ouvre dès qu'un module Android vit dans le
     * workspace (l'APK se construit sur l'appareil). Appeler sur un
     * fil de fond (l'extraction des AAR touche le disque).
     *
     * @param workspace modèle du workspace (core-model)
     * @param externals dépendances externes résolues (sync)
     * @param projet    racine du projet sur le disque
     * @param sorties   répertoire des sorties du build
     * @param m2        racine du cache des externes (jars)
     * @return la constitution (spec ou écarts, jamais null)
     */
    public static Constitution constituer(Workspace workspace,
                                          ExternalResolution externals,
                                          Path projet, Path sorties,
                                          Path m2) {
        boolean android = false;
        for (Module module : workspace.allModules().values()) {
            String type = module.type().id();
            if ("android-library".equals(type)
                    || "android-application".equals(type)) {
                android = true;
                break;
            }
        }
        return android
                ? android(workspace, externals, projet, sorties, m2)
                : jvm(workspace, externals, projet, sorties, m2);
    }

    /**
     * Constitue la description du build JVM du workspace — la route
     * JVM reste la voie des workspaces sans module Android (le
     * moteur build-jvm tourne sur l'appareil depuis P8).
     *
     * @param workspace modèle du workspace (core-model)
     * @param externals dépendances externes résolues (sync)
     * @param projet    racine du projet sur le disque
     * @param sorties   répertoire des sorties du build
     * @param m2        racine du cache des externes (jars)
     * @return la constitution (spec ou écarts, jamais null)
     */
    public static Constitution jvm(Workspace workspace,
                                   ExternalResolution externals,
                                   Path projet, Path sorties, Path m2) {
        List<String> ecarts = new ArrayList<>();
        List<BuildSpec.ModuleSpec> modules = new ArrayList<>();
        for (Module module : workspace.allModules().values()) {
            String type = module.type().id();
            if ("android-library".equals(type)
                    || "android-application".equals(type)) {
                // garde-fou : un module Android ici est une rupture de
                // routage (constituer doit ouvrir la route android)
                ecarts.add(module.path()
                        + " : module Android sur la route JVM"
                        + " (route android attendue)");
                continue;
            }
            if (!"jvm-library".equals(type)) {
                ecarts.add(module.path()
                        + " : type de module non construit ("
                        + type + ")");
                continue;
            }
            modules.add(moduleJvm(module, externals, ecarts));
        }
        if (modules.isEmpty()) {
            if (ecarts.isEmpty()) {
                ecarts.add("aucun module dans le workspace");
            }
            return new Constitution(null, ecarts);
        }
        BuildSpec spec = new BuildSpec(BuildSpec.Kind.JVM,
                projet.toString(), sorties.toString(), m2.toString(),
                NIVEAU_SOURCE, FILS_PAR_DEFAUT, TAS_PAR_DEFAUT_MIO,
                modules, List.of());
        return new Constitution(spec, ecarts);
    }

    /**
     * La route ANDROID sur appareil (lot U) : chaque module devient
     * un ModuleSpec du pipeline Android — sources, ressources
     * (rôle android-res), actifs, manifeste (facette android), jars
     * externes et AAR extraits. L'application porte une variante
     * debug honnête (identifiant depuis le manifeste, sinon un
     * identifiant dérivé du module).
     */
    public static Constitution android(Workspace workspace,
                                       ExternalResolution externals,
                                       Path projet, Path sorties,
                                       Path m2) {
        List<String> ecarts = new ArrayList<>();
        List<BuildSpec.ModuleSpec> modules = new ArrayList<>();
        for (Module module : workspace.allModules().values()) {
            String type = module.type().id();
            if (!"android-library".equals(type)
                    && !"android-application".equals(type)
                    && !"jvm-library".equals(type)) {
                ecarts.add(module.path()
                        + " : type de module non construit ("
                        + type + ")");
                continue;
            }
            modules.add(moduleAndroid(module, externals, projet, ecarts));
        }
        if (modules.isEmpty()) {
            if (ecarts.isEmpty()) {
                ecarts.add("aucun module dans le workspace");
            }
            return new Constitution(null, ecarts);
        }
        // la variante : UNE debug par application du graphe —
        // identifiant lu du manifeste (package=), sinon dérivé du nom
        List<Map<String, Object>> variantes = new ArrayList<>();
        for (BuildSpec.ModuleSpec module : modules) {
            if (!module.application) {
                continue;
            }
            variantes.add(Map.of(
                    "applicationId",
                    identifiantPaquet(projet, module),
                    "buildType", "debug",
                    "versionCode", 1,
                    "minSdk", 28,
                    "v2Signing", true));
        }
        BuildSpec spec = new BuildSpec(BuildSpec.Kind.ANDROID,
                projet.toString(), sorties.toString(), m2.toString(),
                NIVEAU_SOURCE, FILS_PAR_DEFAUT, TAS_PAR_DEFAUT_MIO,
                modules, variantes);
        return new Constitution(spec, ecarts);
    }

    /** Un module Android (ou frère JVM) du pipeline Android. */
    private static BuildSpec.ModuleSpec moduleAndroid(
            Module module, ExternalResolution externals, Path projet,
            List<String> ecarts) {
        String dossierModule = module.path().substring(1)
                .replace(':', '/');
        boolean application = "android-application"
                .equals(module.type().id());
        List<String> sources = new ArrayList<>();
        List<String> ressources = new ArrayList<>();
        String actifs = null;
        for (jo.joide.model.SourceSet ensemble : module.sourceSets()
                .values()) {
            for (jo.joide.model.ContentRoot racine : ensemble.roots()) {
                String chemin = dossierModule + "/" + racine.path();
                switch (racine.role().id()) {
                    case "source", "generated" -> sources.add(chemin);
                    case "android-res" -> ressources.add(chemin);
                    case "assets" -> {
                        if (actifs == null) {
                            actifs = chemin;
                        }
                    }
                    default -> {
                        // resources/AIDL/test : hors v1 appareil —
                        // rapporté comme écart, jamais silencieux
                        ecarts.add(module.path() + " : racine "
                                + racine.role().id()
                                + " non construite sur appareil ("
                                + chemin + ")");
                    }
                }
            }
        }
        if (sources.isEmpty()) {
            ecarts.add(module.path()
                    + " : aucune racine de sources, module vide");
        }
        String manifeste = null;
        Facet android = module.facets().get("android");
        if (android != null) {
            String declare = android.entry("manifest");
            if (declare != null && !declare.isBlank()) {
                manifeste = dossierModule + "/" + declare;
            }
        }
        List<String> freres = new ArrayList<>();
        List<String> jars = new ArrayList<>();
        List<String> aars = new ArrayList<>();
        for (jo.joide.model.DependencyRef dep : module.dependencies()) {
            if (dep.isModule()) {
                freres.add(dep.modulePath());
                continue;
            }
            Optional<ExternalResolution.Resolved> resolu =
                    externals.find(ga(dep.coordinates()));
            if (resolu.isEmpty()) {
                ecarts.add(module.path() + " : " + dep.coordinates()
                        + " non résolu, absent du classpath de build");
                continue;
            }
            ExternalResolution.Resolved artefact = resolu.get();
            if (artefact.aar) {
                Path dossier = extraireAar(artefact.file, projet,
                        dep.coordinates(), ecarts);
                if (dossier != null) {
                    aars.add(dossier.toString());
                }
                continue;
            }
            jars.add(artefact.file.toString());
        }
        return new BuildSpec.ModuleSpec(module.path(), application,
                sources, ressources, actifs, manifeste, freres, jars,
                aars);
    }

    /**
     * L'extraction d'un AAR vers sorties/aar/<ga> : classes.jar et
     * libs/ sont servis au pipeline ; ses ressources restent un
     * écart honnête (la liaison aapt2 d'un AAR tiers attend le lot
     * suivant).
     */
    private static Path extraireAar(Path fichier, Path projet,
                                    String coordonnees,
                                    List<String> ecarts) {
        try {
            Path cible = projet.resolve("build-joide").resolve("aar")
                    .resolve(coordonnees.replace(':', '_'));
            Path marqueur = cible.resolve(".aar-extrait");
            if (Files.isRegularFile(marqueur)
                    && Files.getLastModifiedTime(marqueur).toMillis()
                    >= Files.getLastModifiedTime(fichier).toMillis()) {
                return cible;
            }
            SafeZip.extract(fichier,
                    Hashing.toHex(Hashing.sha256(fichier)), cible);
            Files.write(marqueur, new byte[0]);
            if (Files.isDirectory(cible.resolve("res"))) {
                ecarts.add(coordonnees
                        + " : les ressources de l'AAR ne se lient "
                        + "pas encore sur l'appareil (classes et "
                        + "libs servis)");
            }
            return cible;
        } catch (IOException e) {
            ecarts.add(coordonnees + " : AAR inextractable ("
                    + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * L'identifiant de paquet de la variante : l'attribut package
     * du manifeste d'abord, un identifiant dérivé du module sinon
     * (jamais une valeur prétendue lue où elle ne l'est pas).
     */
    private static String identifiantPaquet(Path projet,
                                            BuildSpec.ModuleSpec module) {
        if (module.manifest != null) {
            try {
                String texte = Files.readString(
                        projet.resolve(module.manifest));
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("package\\s*=\\s*\"([^\"]+)\"")
                        .matcher(texte);
                if (m.find()) {
                    return m.group(1);
                }
            } catch (IOException e) {
                // manifeste illisible : l'identifiant dérivé suit
            }
        }
        String nom = module.name.replace(':', '_').replaceAll(
                "[^A-Za-z0-9_.]", "");
        if (nom.isEmpty() || Character.isDigit(nom.charAt(0))) {
            nom = "app" + nom;
        }
        return "joide.app." + nom.toLowerCase(java.util.Locale.ROOT);
    }

    private static BuildSpec.ModuleSpec moduleJvm(Module module,
                                                  ExternalResolution externals,
                                                  List<String> ecarts) {
        // « :a:b » → « a/b » : les sources de la spec sont relatives à
        // la racine projet (ancre PROJECT du protocole P6)
        String dossierModule = module.path().substring(1)
                .replace(':', '/');
        List<String> sources = new ArrayList<>();
        for (jo.joide.model.SourceSet ensemble : module.sourceSets()
                .values()) {
            for (jo.joide.model.ContentRoot racine : ensemble.roots()) {
                if ("source".equals(racine.role().id())
                        || "generated".equals(racine.role().id())) {
                    sources.add(dossierModule + "/" + racine.path());
                }
            }
        }
        if (sources.isEmpty()) {
            ecarts.add(module.path()
                    + " : aucune racine de sources, module vide");
        }
        List<String> freres = new ArrayList<>();
        List<String> jars = new ArrayList<>();
        for (jo.joide.model.DependencyRef dep : module.dependencies()) {
            if (dep.isModule()) {
                freres.add(dep.modulePath());
                continue;
            }
            Optional<ExternalResolution.Resolved> resolu =
                    externals.find(ga(dep.coordinates()));
            if (resolu.isEmpty()) {
                ecarts.add(module.path() + " : " + dep.coordinates()
                        + " non résolu, absent du classpath de build");
                continue;
            }
            ExternalResolution.Resolved artefact = resolu.get();
            if (artefact.aar) {
                ecarts.add(module.path() + " : " + dep.coordinates()
                        + " est un AAR, hors pipeline JVM");
                continue;
            }
            jars.add(artefact.file.toString());
        }
        return new BuildSpec.ModuleSpec(module.path(), false,
                sources, List.of(), null, null, freres, jars, List.of());
    }

    private static String ga(String coordinates) {
        String[] segments = coordinates.split(":");
        return segments.length >= 2
                ? segments[0] + ":" + segments[1] : coordinates;
    }
}
