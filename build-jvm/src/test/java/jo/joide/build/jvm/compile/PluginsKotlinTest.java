package jo.joide.build.jvm.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * La sonde des plugins Kotlin (lot XIX, la manière CodeAssist) : la
 * présence de {@code androidx/compose/runtime/Composable.class} dans
 * un jar OU un répertoire du classpath active le plugin Compose —
 * zéro réglage utilisateur, zéro option -P ; le jar du plugin sort du
 * pack (appareil) ou des ressources (bureau) et part en
 * {@code -Xplugin}.
 */
class PluginsKotlinTest {

    /** Une demande dont le classpath est {@code entrees}. */
    private static CompileRequest demande(List<Path> entrees,
                                          Path sortie) {
        return new CompileRequest(List.of(sortie.resolve("Rien.kt")),
                entrees, List.of(), sortie, "17", "UTF-8", false);
    }

    private static Path jarAvecCompose(Path dossier, String nom)
            throws IOException {
        Path jar = dossier.resolve(nom);
        try (JarOutputStream flux = new JarOutputStream(
                Files.newOutputStream(jar))) {
            flux.putNextEntry(new ZipEntry(
                    "androidx/compose/runtime/Composable.class"));
            flux.write(new byte[] {-54, -2, -70, -66});
            flux.closeEntry();
        }
        return jar;
    }

    @Test
    void laSondeAllumeSurJarEtRepertoire(@TempDir Path dossier)
            throws IOException {
        Path jar = jarAvecCompose(dossier, "compose-runtime.jar");
        assertTrue(PluginsKotlin.composeDemande(List.of(jar)),
                "la sonde lit les JARS (ZipFile.getEntry)");
        Path dir = dossier.resolve("classes");
        Files.createDirectories(dir.resolve(
                "androidx/compose/runtime"));
        Files.writeString(dir.resolve(
                "androidx/compose/runtime/Composable.class"), "x");
        assertTrue(PluginsKotlin.composeDemande(List.of(dir)),
                "la sonde lit les RÉPERTOIRES (classes extraites d'AAR)");
        assertFalse(PluginsKotlin.composeDemande(List.of(
                        dossier.resolve("absent.jar"), dir.getParent())),
                "sans Composable au classpath, la sonde reste éteinte");
        assertFalse(PluginsKotlin.composeDemande(null));
    }

    @Test
    void leJarDuPluginSortDuPack(@TempDir Path dossier)
            throws IOException {
        Path jar = jarAvecCompose(dossier, "compose-runtime.jar");
        Path home = dossier.resolve("kotlin-home");
        Path plugin = home.resolve("jars")
                .resolve("kotlin-compose-compiler-plugin-2.3.0.jar");
        Files.createDirectories(plugin.getParent());
        Files.writeString(plugin, "jar du pack");
        List<Path> plugins = PluginsKotlin.pluginsPour(
                demande(List.of(jar), dossier), home);
        assertEquals(1, plugins.size(), "le plugin Compose, lui seul");
        assertEquals(plugin, plugins.get(0),
                "le jar RÉEL du pack (le descripteur META-INF/services "
                        + "se lit sur disque)");
    }

    @Test
    void sansComposeLePluginNePartPas(@TempDir Path dossier)
            throws IOException {
        Path biblio = dossier.resolve("biblio.jar");
        try (JarOutputStream flux = new JarOutputStream(
                Files.newOutputStream(biblio))) {
            flux.putNextEntry(new ZipEntry("autre/Classe.class"));
            flux.closeEntry();
        }
        assertTrue(PluginsKotlin.pluginsPour(
                        demande(List.of(biblio), dossier), null).isEmpty(),
                "sonde éteinte : AUCUN -Xplugin — les projets non-"
                        + "Compose ne paient rien");
    }

    @Test
    void lePluginPartEnXPluginSansOptionP(@TempDir Path dossier)
            throws IOException {
        Path jar = jarAvecCompose(dossier, "compose-runtime.jar");
        Path plugin = jarAvecCompose(dossier,
                "kotlin-compose-compiler-plugin-2.3.0.jar");
        CompileRequest demande = demande(List.of(jar), dossier);
        List<String> args = KotlinArgs.args(demande, List.of(),
                List.of(plugin));
        int position = args.indexOf("-Xplugin");
        assertTrue(position >= 0, "-Xplugin est posé : " + args);
        assertEquals(plugin.toString(), args.get(position + 1),
                "le jar du plugin suit l'option");
        assertFalse(args.contains("-P"),
                "ZÉRO option -P pour Compose (la manière CodeAssist)");
    }

    @Test
    void leJarDuPluginVientDeLaProprieteDabord(@TempDir Path dossier)
            throws IOException {
        Path plugin = dossier.resolve("kotlin-compose-compiler-plugin.jar");
        Files.writeString(plugin, "jar extrait des assets de l'APK");
        System.setProperty("joide.compose.plugin.jar", plugin.toString());
        try {
            assertEquals(plugin, PluginsKotlin.jarCompose(null),
                    "la propriété de l'APK gagne (lot XX, la manière "
                            + "CodeAssist — jar extrait des assets)");
        } finally {
            System.clearProperty("joide.compose.plugin.jar");
        }
    }

    @Test
    void uneProprieteVideOuMorteEstIgnoree(@TempDir Path dossier)
            throws IOException {
        System.setProperty("joide.compose.plugin.jar",
                dossier.resolve("absent.jar").toString());
        try {
            Path jar = PluginsKotlin.jarCompose(null);
            // la propriété morte est écartée : le repli ressource du
            // bureau (présent dans les ressources de build-jvm) ou
            // l'échec nommé — jamais un fichier inexistant
            assertTrue(jar == null || Files.isRegularFile(jar),
                    "le jar rendu EXISTE, ou null (échec nommé)");
        } finally {
            System.clearProperty("joide.compose.plugin.jar");
        }
    }
}
