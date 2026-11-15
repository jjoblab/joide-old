package jo.joide.tasks;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import jo.joide.platform.Hashing;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskInput.DirInput;
import jo.joide.tasks.TaskInput.FileInput;
import jo.joide.tasks.TaskInput.PropertyInput;

/**
 * Calcul de l'empreinte d'une tâche : fonction des entrées, de la
 * version de l'outil et des options.
 *
 * <p>La chaîne canonique qui précède le hachage est composée de
 * champs échappés et séparés par des sauts de ligne, dans l'ordre
 * stable de déclaration des entrées et sorties. Aucun chemin absolu
 * n'y figure : les chemins sont ancrés à des racines nommées, ce qui
 * rend l'empreinte relocalisable. L'empreinte finale est le SHA-256
 * de cette chaîne, en hexadécimal.</p>
 */
public final class TaskFingerprinter {

    private TaskFingerprinter() {
        // classe utilitaire
    }

    /**
     * Vue des empreintes de contenu et de signaux, tenue par le
     * moteur : instantané courant, instantané précédent et hachages
     * calculés pendant le build en cours.
     */
    public interface HashView {

        /**
         * @param path chemin ancré du fichier
         * @return l'empreinte de contenu en hexadécimal, ou
         *         {@code null} si elle doit être calculée
         */
        String fileHash(RootedPath path);

        /**
         * @param path chemin ancré du fichier
         * @return les signaux de changement du fichier (taille,
         *         date, clé), ou {@code null} si le fichier est
         *         absent de l'instantané
         */
        String fileSignals(RootedPath path);

        /**
         * @param path chemin ancré du dossier
         * @return l'empreinte d'arbre du dossier, ou {@code null}
         *         si elle doit être calculée
         */
        String dirHash(RootedPath path);
    }

    /**
     * Calcule l'empreinte d'une tâche.
     *
     * @param task  définition de la tâche
     * @param view  vue des empreintes, remplie pour toutes les
     *              entrées de la tâche
     * @return l'empreinte en hexadécimal
     * @throws IllegalStateException si une entrée n'a pas d'empreinte
     *                               disponible dans la vue
     */
    public static String fingerprint(TaskDefinition task, HashView view) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(view, "view");
        StringBuilder canonical = new StringBuilder();
        canonical.append("v1\n");
        canonical.append("tool\n").append(escape(task.toolVersion())).append('\n');
        canonical.append("options\n").append(escape(task.optionsFingerprint()))
                .append('\n');
        List<TaskInput> inputs = task.inputs().asList();
        for (int i = 0; i < inputs.size(); i++) {
            TaskInput input = inputs.get(i);
            canonical.append(i).append('\n');
            if (input instanceof FileInput file) {
                if (file.hashContent()) {
                    String hash = view.fileHash(file.path());
                    requireHash(hash, file.path());
                    canonical.append("file\n").append(escape(file.path().toString()))
                            .append('\n').append(hash).append('\n');
                } else {
                    String signals = view.fileSignals(file.path());
                    requireHash(signals, file.path());
                    canonical.append("signals\n")
                            .append(escape(file.path().toString()))
                            .append('\n').append(escape(signals)).append('\n');
                }
            } else if (input instanceof DirInput dir) {
                String hash = view.dirHash(dir.path());
                requireHash(hash, dir.path());
                canonical.append("dir\n").append(escape(dir.path().toString()))
                        .append('\n').append(hash).append('\n');
            } else if (input instanceof PropertyInput property) {
                canonical.append("property\n").append(escape(property.name()))
                        .append('\n').append(escape(property.value())).append('\n');
            }
        }
        List<TaskOutput> outputs = task.outputs().asList();
        for (int i = 0; i < outputs.size(); i++) {
            canonical.append("out").append(i).append('\n')
                    .append(escape(outputs.get(i).toString())).append('\n');
        }
        return Hashing.toHex(Hashing.sha256(
                canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void requireHash(String value, RootedPath path) {
        if (value == null) {
            throw new IllegalStateException(
                    "no fingerprint available for input: " + path);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }
}
