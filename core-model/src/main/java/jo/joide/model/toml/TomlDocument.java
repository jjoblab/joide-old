package jo.joide.model.toml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Document TOML analysé : la racine et les problèmes rencontrés.
 *
 * <p>La partie saine d'un document malformé reste lisible : les paires
 * et tables valides sont conservées, les anomalies sont listées.</p>
 */
public final class TomlDocument {

    private final TomlValue.TomlTable root;
    private final List<TomlProblem> problems;

    /**
     * @param root     table racine
     * @param problems problèmes rencontrés pendant l'analyse
     */
    public TomlDocument(TomlValue.TomlTable root, List<TomlProblem> problems) {
        this.root = java.util.Objects.requireNonNull(root, "root");
        this.problems = List.copyOf(problems);
    }

    /** @return la table racine */
    public TomlValue.TomlTable root() {
        return root;
    }

    /** @return les problèmes d'analyse, éventuellement vides */
    public List<TomlProblem> problems() {
        return problems;
    }

    /** @return vrai si le document est parfaitement sain */
    public boolean isClean() {
        return problems.isEmpty();
    }

    /**
     * @param pathSegments chemin de la valeur, segment par segment
     * @return la valeur à ce chemin, si présente
     */
    public Optional<TomlValue> find(String... pathSegments) {
        TomlValue current = root;
        for (String segment : pathSegments) {
            Map<String, TomlValue> table = current.asTable();
            if (table == null) {
                return Optional.empty();
            }
            current = table.get(segment);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /**
     * @param pathSegments chemin d'une chaîne
     * @return la chaîne à ce chemin, si présente et de ce type
     */
    public Optional<String> findString(String... pathSegments) {
        return find(pathSegments).map(TomlValue::asString);
    }

    /**
     * @param pathSegments chemin d'un entier
     * @return l'entier à ce chemin, si présent et de ce type
     */
    public Optional<Long> findInteger(String... pathSegments) {
        return find(pathSegments).map(TomlValue::asInteger);
    }

    /**
     * @param pathSegments chemin d'un tableau de chaînes
     * @return les chaînes de ce chemin, si présent et de ce type
     */
    public List<String> findStringArray(String... pathSegments) {
        List<TomlValue> array = find(pathSegments)
                .map(TomlValue::asArray)
                .orElseGet(ArrayList::new);
        List<String> strings = new ArrayList<>();
        for (TomlValue value : array) {
            String text = value.asString();
            if (text != null) {
                strings.add(text);
            }
        }
        return strings;
    }
}
