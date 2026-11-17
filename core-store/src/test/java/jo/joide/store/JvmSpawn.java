package jo.joide.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lancement de machines virtuelles filles pour les tests de pannes du store.
 *
 * <p>Les filles héritent du classpath du test en cours : elles
 * exécutent des points d'entrée de fixtures et meurent par signal,
 * sans protocole de sortie.</p>
 */
public final class JvmSpawn {

    private JvmSpawn() {
    }

    /**
     * Lance une machine virtuelle fille.
     *
     * @param mainClass classe porteuse du point d'entrée {@code main}
     * @param args      arguments transmis au point d'entrée
     * @return le processus lancé, sortie standard en lecture ligne à ligne
     * @throws IOException si le lancement échoue
     */
    public static Process spawn(String mainClass, String... args) throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-XX:TieredStopAtLevel=1");
        command.add("-Xms16m");
        command.add("-Xmx64m");
        command.add("-cp");
        command.add(classpath());
        command.add(mainClass);
        for (String arg : args) {
            command.add(arg);
        }
        return new ProcessBuilder(command).start();
    }

    /**
     * @return le classpath d'exécution des tests, transmis aux filles
     * @throws IllegalStateException si aucun classpath exploitable
     */
    static String classpath() {
        String fromProperty = System.getProperty("joide.test.classpath");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromJvm = System.getProperty("java.class.path");
        if (fromJvm != null && !fromJvm.isBlank()) {
            return fromJvm;
        }
        throw new IllegalStateException("no test classpath available for child JVM");
    }
}
