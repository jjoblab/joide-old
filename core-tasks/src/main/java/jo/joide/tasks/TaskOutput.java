package jo.joide.tasks;

import java.util.Objects;

import jo.joide.platform.RootedPath;

/**
 * Sortie typée d'une tâche : fichier ou dossier produit.
 *
 * <p>Les sorties sont ancrées à des racines nommées ; le moteur les
 * hache après une exécution réussie pour alimenter l'état incrémental
 * et le cache de sorties.</p>
 */
public sealed interface TaskOutput {

    /**
     * Sortie fichier.
     */
    final class FileOutput implements TaskOutput {

        private final RootedPath path;

        /**
         * @param path chemin ancré du fichier produit
         */
        public FileOutput(RootedPath path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        /** @return le chemin ancré du fichier produit */
        public RootedPath path() {
            return path;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FileOutput)) {
                return false;
            }
            return path.equals(((FileOutput) other).path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return "FileOutput[path=" + path + "]";
        }
    }

    /**
     * Sortie dossier.
     */
    final class DirOutput implements TaskOutput {

        private final RootedPath path;

        /**
         * @param path chemin ancré du dossier produit
         */
        public DirOutput(RootedPath path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        /** @return le chemin ancré du dossier produit */
        public RootedPath path() {
            return path;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DirOutput)) {
                return false;
            }
            return path.equals(((DirOutput) other).path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return "DirOutput[path=" + path + "]";
        }
    }
}
