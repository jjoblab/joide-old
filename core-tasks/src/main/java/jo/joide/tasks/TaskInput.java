package jo.joide.tasks;

import java.util.Objects;

import jo.joide.platform.RootedPath;

/**
 * Entrée typée d'une tâche : fichier, dossier ou propriété scalaire.
 *
 * <p>Les fichiers et dossiers sont toujours désignés par un chemin
 * ancré à une racine nommée : aucune entrée ne contient de chemin
 * absolu, ce qui rend les empreintes relocalisables. Les classpaths
 * d'ABI des modules aval entrent comme propriétés scalaires (leur
 * valeur est déjà une empreinte) jusqu'au pipeline de compilation.</p>
 */
public sealed interface TaskInput {

    /**
     * Entrée fichier : le contenu participe à l'empreinte, ou seuls
     * les signaux de changement suffisent.
     */
    final class FileInput implements TaskInput {

        private final RootedPath path;
        private final boolean hashContent;

        /**
         * @param path        chemin ancré du fichier
         * @param hashContent vrai si le contenu participe à l'empreinte
         */
        public FileInput(RootedPath path, boolean hashContent) {
            this.path = Objects.requireNonNull(path, "path");
            this.hashContent = hashContent;
        }

        /** @return le chemin ancré du fichier */
        public RootedPath path() {
            return path;
        }

        /** @return vrai si le contenu participe à l'empreinte */
        public boolean hashContent() {
            return hashContent;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FileInput)) {
                return false;
            }
            FileInput that = (FileInput) other;
            return hashContent == that.hashContent && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return path.hashCode() * 31 + (hashContent ? 1 : 0);
        }

        @Override
        public String toString() {
            return "FileInput[path=" + path + ", hashContent=" + hashContent
                    + "]";
        }
    }

    /**
     * Entrée dossier : l'empreinte retient le hash d'arbre du dossier,
     * recalculé incrémentalement par le système de fichiers virtuel.
     */
    final class DirInput implements TaskInput {

        private final RootedPath path;

        /**
         * @param path chemin ancré du dossier
         */
        public DirInput(RootedPath path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        /** @return le chemin ancré du dossier */
        public RootedPath path() {
            return path;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DirInput)) {
                return false;
            }
            return path.equals(((DirInput) other).path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return "DirInput[path=" + path + "]";
        }
    }

    /**
     * Entrée propriété scalaire : option, version d'outil, empreinte
     * d'ABI de classpath. La valeur doit être stable : deux exécutions
     * de la même tâche avec la même valeur donnent la même empreinte.
     */
    final class PropertyInput implements TaskInput {

        private final String name;
        private final String value;

        /**
         * @param name  nom de la propriété, unique par tâche
         * @param value valeur textuelle
         */
        public PropertyInput(String name, String value) {
            this.name = Objects.requireNonNull(name, "name");
            this.value = Objects.requireNonNull(value, "value");
        }

        /** @return le nom de la propriété */
        public String name() {
            return name;
        }

        /** @return la valeur textuelle */
        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PropertyInput)) {
                return false;
            }
            PropertyInput that = (PropertyInput) other;
            return name.equals(that.name) && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + value.hashCode();
        }

        @Override
        public String toString() {
            return "PropertyInput[name=" + name + ", value=" + value + "]";
        }
    }
}
