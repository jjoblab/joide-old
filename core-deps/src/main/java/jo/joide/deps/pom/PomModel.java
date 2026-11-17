package jo.joide.deps.pom;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Un POM résolu : chaîne de parents aplatie, propriétés interpolées,
 * gestion de dépendances développée (importations de nomenclatures
 * incluses). Immuable.
 */
public final class PomModel {

    /** Portée Maven d'une dépendance de POM. */
    public enum Scope {
        COMPIL("compile"), PROVIDED("provided"), RUNTIME("runtime"),
        TEST("test"), IMPORT("import");

        private final String text;

        Scope(String text) {
            this.text = text;
        }

        /** @return le texte Maven de la portée */
        public String text() {
            return text;
        }
    }

    /** Une dépendance (ou une entrée de gestion) d'un POM. */
    public static final class PomDependency {
        public final String groupId;
        public final String artifactId;
        public final String version;   // littéral interpolé, peut être null
        public final String classifier;
        public final Scope scope;
        public final boolean optional;
        public final Set<String> exclusions; // « g:a » en minuscules

        public PomDependency(String groupId, String artifactId, String version,
                             String classifier, Scope scope, boolean optional,
                             Set<String> exclusions) {
            this.groupId = Objects.requireNonNull(groupId, "groupId");
            this.artifactId = Objects.requireNonNull(artifactId, "artifactId");
            this.version = version;
            this.classifier = classifier;
            this.scope = Objects.requireNonNull(scope, "scope");
            this.optional = optional;
            this.exclusions = Set.copyOf(exclusions);
        }

        /** @return « g:a » en minuscules */
        public String ga() {
            return (groupId + ":" + artifactId).toLowerCase(
                    java.util.Locale.ROOT);
        }

        @Override
        public String toString() {
            return ga() + ":" + (version != null ? version : "?")
                    + (scope != Scope.COMPIL ? ":" + scope.text() : "");
        }
    }

    /** Coordonnées du POM. */
    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String packaging;
    /** Propriétés du document, héritées du parent puis propres. */
    public final Map<String, String> properties;
    public final List<PomDependency> dependencies;
    public final List<PomDependency> dependencyManagement;

    PomModel(String groupId, String artifactId, String version,
             String packaging, Map<String, String> properties,
             List<PomDependency> dependencies,
             List<PomDependency> dependencyManagement) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.properties = Map.copyOf(properties);
        this.dependencies = List.copyOf(dependencies);
        this.dependencyManagement = List.copyOf(dependencyManagement);
    }

    /**
     * @param ga « g:a » cherché
     * @return l'entrée de gestion correspondante, ou {@code null}
     */
    public PomDependency managed(String ga) {
        for (PomDependency d : dependencyManagement) {
            if (d.ga().equals(ga)) {
                return d;
            }
        }
        return null;
    }
}
