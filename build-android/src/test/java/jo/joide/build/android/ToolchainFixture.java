package jo.joide.build.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Assumptions;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;
import jo.joide.build.android.sign.ApkSignerService;
import jo.joide.build.android.toolchain.Toolchain;
import jo.joide.build.android.toolchain.ToolchainLocator;

/**
 * Harnais d'outillage des tests Android : localise aapt2, la
 * plateforme et D8/R8 (variables d'environnement, classpath de test,
 * cache local), en téléchargeant les pièces manquantes une seule fois
 * dans un répertoire de cache partagé. Les tests qui exigent
 * l'outillage se sautent explicitement (hypothèse) quand le réseau
 * et le cache sont indisponibles — jamais en erreur.
 */
public final class ToolchainFixture {

    private static Toolchain cached;
    private static boolean attempted;

    private ToolchainFixture() {
    }

    /** @return l'outillage complet, ou hypothèse non satisfaite */
    public static synchronized Toolchain toolchain() {
        if (!attempted) {
            attempted = true;
            Path dir = cacheDir();
            try {
                Files.createDirectories(dir);
                cached = new ToolchainLocator(dir).locate();
            } catch (IOException | RuntimeException e) {
                cached = null;
            }
        }
        Assumptions.assumeTrue(cached != null,
                "outillage Android indisponible (réseau et cache absents)");
        return cached;
    }

    /** @return le répertoire de cache de l'outillage */
    public static Path cacheDir() {
        String override = System.getenv("JOIDE_TOOLCHAIN_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".cache",
                "joide", "toolchain");
    }

    /** @return le dexeur en processus (ServiceLoader du classpath) */
    public static Dexer inProcessDexer() {
        Optional<ServiceLoader.Provider<Dexer>> provider =
                ServiceLoader.load(Dexer.class).stream().findFirst();
        Assumptions.assumeTrue(provider.isPresent(),
                "dexeur en processus absent du classpath");
        return provider.get().get();
    }

    /** @return le service de signature (ServiceLoader du classpath) */
    public static ApkSignerService signer() {
        Optional<ServiceLoader.Provider<ApkSignerService>> provider =
                ServiceLoader.load(ApkSignerService.class).stream()
                        .findFirst();
        Assumptions.assumeTrue(provider.isPresent(),
                "signataire absent du classpath");
        return provider.get().get();
    }

    /** @return l'assembleur de bundle (ServiceLoader du classpath) */
    public static BundleAssembler assembler() {
        Optional<ServiceLoader.Provider<BundleAssembler>> provider =
                ServiceLoader.load(BundleAssembler.class).stream()
                        .findFirst();
        Assumptions.assumeTrue(provider.isPresent(),
                "assembleur de bundle absent du classpath");
        return provider.get().get();
    }

    /** @return le dexeur par fork à gros tas sur le jar de D8/R8 */
    public static Dexer forkDexer() {
        Toolchain toolchain = toolchain();
        return new jo.joide.build.android.dex.ForkDexer(toolchain.r8Jar,
                "1g", 600);
    }
}
