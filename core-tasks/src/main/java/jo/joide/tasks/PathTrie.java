package jo.joide.tasks;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jo.joide.platform.RootedPath;

/**
 * Index de chemins ancrés par préfixe de segments, pour l'inférence
 * optionnelle des dépendances sortie vers entrée.
 *
 * <p>Chaque sortie déclarée insère son chemin dans le trie ; une
 * requête « qui produit ce chemin ? » descend le trie en suivant les
 * segments du chemin demandé et collecte les identifiants rencontrés
 * sur le passage. Le coût est proportionnel à la profondeur du
 * chemin, jamais au nombre de tâches : aucune comparaison de tâche à
 * tâche.</p>
 */
final class PathTrie {

    private final Node root = new Node();

    /**
     * Insère une sortie déclarée.
     *
     * @param output chemin ancré de la sortie
     * @param taskId identifiant de la tâche productrice
     */
    void insert(RootedPath output, String taskId) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(taskId, "taskId");
        Node node = descendOrCreate(output);
        node.producers.add(taskId);
    }

    /**
     * Cherche les tâches dont une sortie couvre le chemin demandé :
     * la sortie dossier couvre tout son sous-arbre, la sortie fichier
     * ne couvre qu'elle-même.
     *
     * @param path chemin ancré demandé
     * @return les identifiants des tâches productrices, sans doublon
     */
    Set<String> producersCovering(RootedPath path) {
        Objects.requireNonNull(path, "path");
        Set<String> producers = new LinkedHashSet<>();
        if (!root.children.containsKey(path.root().id())) {
            return producers;
        }
        Node node = root.children.get(path.root().id());
        node.collectInto(producers);
        String relative = path.relative();
        if (relative.isEmpty()) {
            return producers;
        }
        for (String segment : relative.split("/")) {
            Node next = node.children.get(segment);
            if (next == null) {
                break;
            }
            node = next;
            node.collectInto(producers);
        }
        return producers;
    }

    private Node descendOrCreate(RootedPath path) {
        Node node = root.child(root, path.root().id());
        String relative = path.relative();
        if (relative.isEmpty()) {
            return node;
        }
        for (String segment : relative.split("/")) {
            node = node.child(node, segment);
        }
        return node;
    }

    private static final class Node {
        private final Map<String, Node> children = new HashMap<>();
        private final Set<String> producers = new LinkedHashSet<>();

        Node child(Node parent, String segment) {
            return parent.children.computeIfAbsent(segment, key -> new Node());
        }

        void collectInto(Set<String> producers) {
            producers.addAll(this.producers);
        }
    }

    /**
     * Vérifie si un chemin couvre un autre chemin : un préfixe
     * dossier couvre tout son sous-arbre, un fichier ne couvre que
     * lui-même.
     *
     * @param prefix chemin ancré candidat couvrant
     * @param path   chemin ancré à tester
     * @return vrai si {@code path} est {@code prefix} ou un descendant
     */
    static boolean covers(RootedPath prefix, RootedPath path) {
        if (!prefix.root().equals(path.root())) {
            return false;
        }
        String prefixRelative = prefix.relative();
        if (prefixRelative.isEmpty()) {
            return true;
        }
        String pathRelative = path.relative();
        return pathRelative.equals(prefixRelative)
                || pathRelative.startsWith(prefixRelative + "/");
    }
}
