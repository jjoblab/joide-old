package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Le classpath de la VM ouvrière avec l'APK de l'app (lot XX, la
 * manière l'IDE de référence (compilateur Kotlin dissocié, l. 67-76)) : plusieurs
 * entrées, le pack EN TÊTE, le registrar Compose dans l'APK — et
 * homeOuvriere qui extrait les ressources du pack, PREMIÈRE entrée
 * .dex.zip du classpath.
 */
class KotlinForkedDriverEntreePackTest {

    @Test
    void laPremiereEntreeDexZipEstLePack() {
        assertNull(KotlinForkedDriver.entreePack(null));
        assertNull(KotlinForkedDriver.entreePack("  "));
        assertNull(KotlinForkedDriver.entreePack(
                "/tmp/kotlin-stdlib.jar:/data/app/base.apk"),
                "aucun pack : essai bureau, jar réel");
        assertEquals("/data/toolchain/kotlin-compiler-2.3.0.dex.zip",
                KotlinForkedDriver.entreePack(
                        "/data/toolchain/kotlin-compiler-2.3.0.dex.zip"
                                + ":/data/app/base.apk"),
                "le pack EN TÊTE est l'entrée d'extraction");
    }

    @Test
    void leHomeExtraitDuPackMalgreLApkAuClasspath(@TempDir Path dossier)
            throws IOException {
        // un petit pack : une ressource jars/ (la garde du marqueur
        // échoue, l'extraction reprend à chaque appel — le contenu
        // reste correct)
        Path stdlib = dossier.resolve("kotlin-stdlib-2.3.0.jar");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(stdlib))) {
            jar.putNextEntry(new ZipEntry("vide"));
            jar.closeEntry();
        }
        Path pack = dossier.resolve("kotlin-compiler-2.3.0.dex.zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(new byte[] {1});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(
                    "jars/kotlin-stdlib-2.3.0.jar"));
            zip.write(Files.readAllBytes(stdlib));
            zip.closeEntry();
        }
        Path home = KotlinForkedDriver.homeOuvriere(
                pack + ":/data/app/base.apk");
        assertTrue(home != null
                        && Files.isRegularFile(home.resolve(
                                "jars/kotlin-stdlib-2.3.0.jar")),
                "les ressources du pack s'extraitent malgré l'APK "
                        + "ajouté au classpath (lot XX)");
        // l'APK n'est PAS la source d'extraction
        assertTrue(home == null
                || !Files.exists(home.resolve("base.apk")));
    }
}
