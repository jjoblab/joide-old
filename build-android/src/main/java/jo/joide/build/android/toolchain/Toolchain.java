package jo.joide.build.android.toolchain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Emplacements de l'outillage Android d'un build : binaire aapt2,
 * jar de plateforme (android.jar), fichier AIDL de la plateforme et
 * jar de D8/R8. Les chemins sont fournis par un localisateur (chemins
 * locaux, variables d'environnement ou téléchargement épinglé) ; cette
 * valeur est un simple paquet immuable.
 */
public final class Toolchain {

    /** Chemin du binaire aapt2. */
    public final Path aapt2;
    /** Chemin de l'android.jar de la plateforme. */
    public final Path platformJar;
    /** Chemin du framework.aidl de la plateforme. */
    public final Path frameworkAidl;
    /** Chemin du jar de D8/R8 (délégation en processus ou par fork). */
    public final Path r8Jar;

    /**
     * @param aapt2         binaire aapt2
     * @param platformJar   jar de plateforme
     * @param frameworkAidl AIDL de la plateforme
     * @param r8Jar         jar de D8/R8
     */
    public Toolchain(Path aapt2, Path platformJar, Path frameworkAidl,
                     Path r8Jar) {
        this.aapt2 = Objects.requireNonNull(aapt2, "aapt2");
        this.platformJar = Objects.requireNonNull(platformJar,
                "platformJar");
        this.frameworkAidl = frameworkAidl;
        this.r8Jar = Objects.requireNonNull(r8Jar, "r8Jar");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Toolchain)) {
            return false;
        }
        Toolchain that = (Toolchain) other;
        return aapt2.equals(that.aapt2)
                && platformJar.equals(that.platformJar)
                && Objects.equals(frameworkAidl, that.frameworkAidl)
                && r8Jar.equals(that.r8Jar);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aapt2, platformJar, frameworkAidl, r8Jar);
    }

    @Override
    public String toString() {
        return "Toolchain[aapt2=" + aapt2 + ", platform=" + platformJar
                + ", r8=" + r8Jar + "]";
    }
}
