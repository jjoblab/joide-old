package jo.joide.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import jo.joide.platform.Hash128;
import jo.joide.platform.Hashing;

/**
 * Un module : type, ensembles de sources, dépendances et facettes.
 * Immuable.
 *
 * <p>Chaque module porte un identifiant de contenu : l'empreinte de
 * sa définition canonique. Deux modules de même identifiant ont la
 * même définition, quels que soient leur emplacement et leur
 * historique — c'est la clé de cache stable exigée par le noyau.</p>
 */
public final class Module {

    private final String path;
    private final String name;
    private final ModuleType type;
    private final Map<String, SourceSet> sourceSets;
    private final List<DependencyRef> dependencies;
    private final Map<String, Facet> facets;
    private final Hash128 contentId;

    /**
     * @param path       chemin du module (ex. « :app »)
     * @param name       nom court du module
     * @param type       type du module
     * @param sourceSets ensembles de sources, par nom
     * @param dependencies dépendances déclarées, dans l'ordre
     * @param facets     facettes, par domaine
     */
    public Module(String path, String name, ModuleType type,
                  Map<String, SourceSet> sourceSets,
                  List<DependencyRef> dependencies,
                  Map<String, Facet> facets) {
        if (path == null || !path.startsWith(":") || path.length() < 2
                || name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "invalid module path or name: " + path + " / " + name);
        }
        this.path = path;
        this.name = name;
        this.type = Objects.requireNonNull(type, "type");
        this.sourceSets = Collections.unmodifiableMap(
                new TreeMap<>(sourceSets));
        this.dependencies = List.copyOf(dependencies);
        this.facets = Collections.unmodifiableMap(
                new TreeMap<>(facets));
        this.contentId = Hashing.fast128(
                canonicalForm().getBytes(StandardCharsets.UTF_8), CANON_SEED);
    }

    /** Graine de l'identifiant de contenu des modules. */
    static final long CANON_SEED = 0x4D4F444CL;

    /** @return le chemin du module (ex. « :app ») */
    public String path() {
        return path;
    }

    /** @return le nom court */
    public String name() {
        return name;
    }

    /** @return le type du module */
    public ModuleType type() {
        return type;
    }

    /** @return les ensembles de sources, par nom, triés */
    public Map<String, SourceSet> sourceSets() {
        return sourceSets;
    }

    /** @return les dépendances déclarées, dans l'ordre */
    public List<DependencyRef> dependencies() {
        return dependencies;
    }

    /** @return les facettes, par domaine, triées */
    public Map<String, Facet> facets() {
        return facets;
    }

    /**
     * @return l'identifiant de contenu : empreinte de la définition
     *         canonique, sans aucun chemin absolu
     */
    public Hash128 contentId() {
        return contentId;
    }

    /**
     * @return la forme canonique : chaque partie triée, sans espace
     *         ni chemin absolu, préfixée pour éviter les ambiguïtés
     */
    String canonicalForm() {
        StringBuilder out = new StringBuilder(256);
        out.append("v1|name=").append(name)
                .append("|type=").append(type.id());
        List<String> sourceLines = new ArrayList<>();
        for (Map.Entry<String, SourceSet> entry : sourceSets.entrySet()) {
            StringBuilder line = new StringBuilder(entry.getKey());
            for (ContentRoot root : entry.getValue().roots()) {
                line.append(';').append(root.path()).append(',')
                        .append(root.role().id());
            }
            sourceLines.add(line.toString());
        }
        Collections.sort(sourceLines);
        for (String line : sourceLines) {
            out.append("|src=").append(line);
        }
        for (DependencyRef dependency : dependencies) {
            out.append("|dep=").append(dependency.canonical());
        }
        List<String> facetLines = new ArrayList<>();
        for (Map.Entry<String, Facet> entry : facets.entrySet()) {
            StringBuilder line = new StringBuilder(entry.getKey());
            new TreeMap<>(entry.getValue().entries())
                    .forEach((key, value) -> line.append(';')
                            .append(key).append('=').append(value));
            facetLines.add(line.toString());
        }
        Collections.sort(facetLines);
        for (String line : facetLines) {
            out.append("|facet=").append(line);
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Module)) {
            return false;
        }
        Module that = (Module) other;
        return path.equals(that.path) && contentId.equals(that.contentId);
    }

    @Override
    public int hashCode() {
        return path.hashCode() * 31 + contentId.hashCode();
    }

    @Override
    public String toString() {
        return "Module[" + path + " " + type.id() + "]";
    }
}
