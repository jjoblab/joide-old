package jo.joide.app.android;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.ide.sync.ExternalResolution;
import jo.joide.model.Module;
import jo.joide.model.Workspace;

/**
 * Constitution de la description de build (protocole P6) depuis le
 * modèle du workspace : les modules JVM deviennent des modules du
 * pipeline, les externes résolus fournissent les jars, tout le reste
 * est rapporté — jamais une omission muette.
 *
 * <p>En P8, le build sur appareil couvre le pipeline JVM : les modules
 * Android exigent D8/R8 (non embarqués dans l'APK — décision de taille
 * reportée au propriétaire) et sont listés comme écarts, pas construits
 * en silence.</p>
 */
public final class SpecsBuild {

    /** Niveau de source par défaut du pipeline (artefacts 17). */
    static final String NIVEAU_SOURCE = "17";

    /** Budget d'admission par défaut (Mio) — appareils modestes. */
    static final int TAS_PAR_DEFAUT_MIO = 448;

    /** Fils de construction par défaut du démon sur appareil. */
    static final int FILS_PAR_DEFAUT = 2;

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
     * Constitue la description du build JVM du workspace.
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
                ecarts.add(module.path()
                        + " : module Android non construit sur"
                        + " appareil en P8 (D8/R8 non embarqués)");
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
