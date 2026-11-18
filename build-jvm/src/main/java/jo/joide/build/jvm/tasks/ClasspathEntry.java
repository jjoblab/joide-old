package jo.joide.build.jvm.tasks;

import java.util.Objects;

import jo.joide.platform.RootedPath;

/**
 * Entrée de classpath d'une tâche de compilation : l'emplacement des
 * classes (répertoire ou jar) pour le pilote, et le manifeste ABI qui
 * représente son contenu pour l'empreinte et l'analyse d'impact.
 *
 * <p>Deux représentations du manifeste : pour une entrée de module du
 * projet, un fichier pointeur stable (contenu : le condensé du
 * manifeste immuable associé) ; pour une entrée externe, le fichier
 * de manifeste adressé par condensé, mis en cache par
 * {@link jo.joide.build.jvm.abi.AbiCache}. Dans les deux cas, le
 * contenu du manifeste — pas les octets des classes — entre dans
 * l'empreinte : un corps de méthode modifié en amont ne réexécute
 * pas l'aval.</p>
 */
public final class ClasspathEntry {

    /** Origine de l'entrée. */
    public enum Kind {
        /** Sortie Java d'un module du projet. */
        MODULE_JAVA,
        /** Sortie Kotlin d'un module du projet. */
        MODULE_KOTLIN,
        /** Jar ou répertoire hors du build (bibliothèque). */
        EXTERNAL,
        /** Le R.jar du module (lot XX/h, la manière CodeAssist) :
         *  classpath de la compilation JAVA et entrée d'empreinte
         *  partout — mais JAMAIS dans le -classpath du compilateur
         *  KOTLIN : kotlinc résout R depuis les sources R.java
         *  générées (les racines de résolution), pas depuis un jar
         *  à ouvrir — « Classpath entry points to a non-existent
         *  location: app/r.jar » devient structurellement impossible
         *  (le terrain du 2026-11-18 : le jar existe côté moteur et
         *  côté daemon, kotlinc dexé ne l'ouvre jamais). */
        EXTERNAL_R
    }

    /** Origine. */
    public final Kind kind;
    /** Emplacement des classes (répertoire ou jar). */
    public final RootedPath location;
    /** Fichier d'entrée pour l'empreinte : pointeur (module) ou
     *  manifeste adressé (externe). */
    public final RootedPath manifestInput;
    /** Répertoire des manifestes immuables du module (Kind module). */
    public final RootedPath manifestDir;
    /** Libellé stable (nom de module ou chemin d'entrée). */
    public final String label;

    /**
     * @param kind          origine
     * @param label         libellé stable
     * @param location      classes (répertoire ou jar)
     * @param manifestInput entrée d'empreinte
     * @param manifestDir   répertoire des manifestes (module seulement)
     */
    public ClasspathEntry(Kind kind, String label, RootedPath location,
                          RootedPath manifestInput,
                          RootedPath manifestDir) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.label = Objects.requireNonNull(label, "label");
        this.location = Objects.requireNonNull(location, "location");
        this.manifestInput = Objects.requireNonNull(manifestInput,
                "manifestInput");
        this.manifestDir = manifestDir;
    }

    /** @return vrai si l'entrée provient d'un module du projet */
    public boolean isModule() {
        return kind == Kind.MODULE_JAVA || kind == Kind.MODULE_KOTLIN;
    }
}
