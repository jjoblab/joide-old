package jo.joide.deps.gmm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Un fichier « .module » (Gradle Module Metadata) résolu en valeurs
 * immuables. Les variantes conservent leurs attributs bruts ; la
 * sélection (usage java-api/java-runtime, plateforme androidjvm/jvm)
 * se fait au résolveur, qui connaît le contexte.
 */
public final class GmmModule {

    /** Un fichier d'une variante : nom, taille, empreintes publiées. */
    public static final class GmmFile {
        public final String name;
        public final String url;
        public final long size;
        public final String sha1;
        public final String sha256;

        public GmmFile(String name, String url, long size, String sha1,
                       String sha256) {
            this.name = Objects.requireNonNull(name, "name");
            this.url = url;
            this.size = size;
            this.sha1 = sha1;
            this.sha256 = sha256;
        }

        /** @return l'extension du nom (« jar », « aar »…) */
        public String extension() {
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : "";
        }

        @Override
        public String toString() {
            return name + (sha256 != null ? "#" + sha256.substring(0, 8) : "");
        }
    }

    /** Une dépendance déclarée par une variante. */
    public static final class GmmDependency {
        public final String group;
        public final String module;
        public final String version; // littéral ou plage, peut être null
        /** Vrai quand la version vient d'un « strictly » GMM. */
        public final boolean versionStrictly;
        public final boolean optional;
        public final List<String> excludes; // « g:module » en minuscules

        public GmmDependency(String group, String module, String version,
                             boolean versionStrictly, boolean optional,
                             List<String> excludes) {
            this.group = Objects.requireNonNull(group, "group");
            this.module = Objects.requireNonNull(module, "module");
            this.version = version;
            this.versionStrictly = versionStrictly;
            this.optional = optional;
            this.excludes = List.copyOf(excludes);
        }

        /** @return « g:module » en minuscules */
        public String ga() {
            return (group + ":" + module).toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public String toString() {
            return ga() + ":" + (version != null ? version : "?")
                    + (optional ? " (optional)" : "");
        }
    }

    /** Une variante : attributs, fichiers, dépendances, renvoi éventuel. */
    public static final class GmmVariant {
        public final String name;
        public final Map<String, String> attributes;
        public final List<GmmFile> files;
        public final List<GmmDependency> dependencies;
        public final String availableAtUrl;      // relatif au même dépôt
        public final Map<String, String> availableAtAttributes;

        public GmmVariant(String name, Map<String, String> attributes,
                          List<GmmFile> files, List<GmmDependency> dependencies,
                          String availableAtUrl,
                          Map<String, String> availableAtAttributes) {
            this.name = Objects.requireNonNull(name, "name");
            this.attributes = Map.copyOf(attributes);
            this.files = List.copyOf(files);
            this.dependencies = List.copyOf(dependencies);
            this.availableAtUrl = availableAtUrl;
            this.availableAtAttributes = Map.copyOf(availableAtAttributes);
        }

        /** @param key clé d'attribut @return la valeur, ou {@code null} */
        public String attr(String key) {
            return attributes.get(key);
        }

        @Override
        public String toString() {
            return name + "(" + files.size() + "f," + dependencies.size()
                    + "d)";
        }
    }

    public final String formatVersion;
    public final List<GmmVariant> variants;

    public GmmModule(String formatVersion, List<GmmVariant> variants) {
        this.formatVersion = formatVersion;
        this.variants = List.copyOf(variants);
    }

    /**
     * @param variantName nom cherché
     * @return la variante, ou {@code null}
     */
    public GmmVariant variant(String variantName) {
        for (GmmVariant v : variants) {
            if (v.name.equals(variantName)) {
                return v;
            }
        }
        return null;
    }
}
