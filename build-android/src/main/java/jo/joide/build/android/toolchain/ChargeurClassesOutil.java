package jo.joide.build.android.toolchain;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Chargeur d'un pack d'outils dex : le pont ANDROID vers
 * {@code dalvik.system.DexClassLoader}. L'implémentation vit dans
 * l'application ({@code app-android}), jamais dans les modules purs —
 * le contrôle API 28 des modules purs couvre la surface java/javax,
 * {@code dalvik.*} n'en fait pas partie. Le bureau n'offre aucune
 * implémentation : les packs restent une route appareil.
 *
 * <p>Découverte par ServiceLoader (même séparation que les
 * adaptateurs d'outils du source set tools de build-android) : le
 * démon {@code :build} du même APK trouve l'implémentation sans
 * dépendance directe.</p>
 */
public interface ChargeurClassesOutil {

    /**
     * Charge un pack d'outils dex.
     *
     * @param packDex le fichier du pack (zip de dex, lecture seule)
     * @param parent  le chargeur parent — ne porte QUE les interfaces
     *                SPI du pipeline ; le mur de chargeurs reste
     *                étanche : aucun type d'outil ne le traverse
     * @return le chargeur du pack
     * @throws IOException si le pack ne se charge pas
     */
    ClassLoader charger(Path packDex, ClassLoader parent) throws IOException;
}
