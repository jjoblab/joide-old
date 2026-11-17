package jo.joide.ide.sync;

import java.nio.file.Path;

/**
 * Fournisseur de la plateforme de compilation Android : le
 * {@code android.jar} contre lequel les modules Android sont compilés.
 *
 * <p>Injecté à l'adaptateur pour rester pur et testable ; l'implémentation
 * de production s'appuie sur la chaîne d'outillage JoIDE (localisateur
 * de plateforme du pipeline Android), les tests fournissent un fichier
 * fixe.</p>
 */
public interface AndroidPlatform {

    /**
     * @param compileSdkMajeur niveau d'API de compilation (partie
     *                        majeure, ex. « 35 » pour « 35.1 »)
     * @return le chemin du {@code android.jar}, ou {@code null} si la
     *         plateforme n'est pas disponible localement (jamais une
     *         exception : l'absence est un état signalé, pas un échec)
     */
    Path androidJar(int compileSdkMajeur);

    /**
     * @param jar chemin fixe
     * @return un fournisseur qui répond toujours {@code jar}
     */
    static AndroidPlatform fixed(Path jar) {
        return compileSdkMajeur -> jar;
    }

    /** @return un fournisseur qui répond toujours « absent » */
    static AndroidPlatform absent() {
        return compileSdkMajeur -> null;
    }
}
