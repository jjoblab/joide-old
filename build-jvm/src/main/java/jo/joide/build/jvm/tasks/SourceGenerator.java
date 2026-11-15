package jo.joide.build.jvm.tasks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Générateur de sources du pipeline JVM (SPI) : produit des sources
 * dans un répertoire de sortie dédié, de façon déterministe — mêmes
 * entrées, mêmes sorties.
 *
 * <p>Les générateurs sont injectés explicitement à la fabrique de
 * tâches (aucune découverte implicite) ; leur version fait partie de
 * l'identité de la tâche.</p>
 */
public interface SourceGenerator {

    /**
     * @return le nom stable du générateur (lettres, chiffres, tirets)
     */
    String name();

    /**
     * @return la version du générateur : tout changement de sortie
     *         doit l'incrémenter
     */
    String version();

    /**
     * Génère les sources.
     *
     * @param context contexte d'écriture
     * @return les chemins relatifs des fichiers écrits, ordre stable
     * @throws Exception toute erreur est transformée en échec de tâche
     *         avec message clair, jamais en état silencieux
     */
    List<String> generate(Context context) throws Exception;

    /** Contexte d'écriture d'un générateur. */
    interface Context {
        /** @return le répertoire de sortie, vide à l'entrée */
        Path outputDir();
    }
}
