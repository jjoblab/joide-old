package jo.joide.app.android;

import java.io.IOException;
import java.nio.file.Path;

import jo.joide.build.android.toolchain.ChargeurClassesOutil;

/**
 * Le chargeur de packs dex SUR APPAREIL : un
 * {@code dalvik.system.DexClassLoader} au-dessus du zip téléchargé,
 * posé en lecture seule au mieux (l'art impose W^X aux VM filles ;
 * pour la charge en processus, ART garde ses sorties optimisées hors
 * du fichier d'entrée). Le parent ne porte QUE les interfaces SPI —
 * les classes d'outils du pack ne fuient jamais dans le chargeur de
 * l'application.
 *
 * <p>Enregistré par ServiceLoader (META-INF/services) : le démon
 * {@code :build} du même APK découvre ce pont sans dépendance
 * directe — le module build-android reste pur (contrôle API 28 :
 * {@code dalvik.*} hors signatures java/javax).</p>
 */
public final class ChargeurDexAndroid implements ChargeurClassesOutil {

    @Override
    public ClassLoader charger(Path packDex, ClassLoader parent)
            throws IOException {
        if (parent == null) {
            throw new IOException(
                    "pack loader needs a parent classloader: " + packDex);
        }
        // lecture seule au mieux : jamais un échec si le système de
        // fichiers refuse — la charge en processus s'en accommode
        try {
            packDex.toFile().setReadOnly();
        } catch (RuntimeException ignore) {
            // au mieux : ART ne réécrit pas le pack d'entrée
        }
        try {
            return new dalvik.system.DexClassLoader(
                    packDex.toAbsolutePath().toString(), null, null,
                    parent);
        } catch (RuntimeException e) {
            throw new IOException("pack dex ne se charge pas : "
                    + packDex + " (" + e.getMessage() + ")", e);
        }
    }
}
