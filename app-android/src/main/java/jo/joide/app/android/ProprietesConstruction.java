package jo.joide.app.android;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Les propriétés d'outillage (lot U) — UNE vérité pour les deux
 * processus qui construisent (le service, l'écran atelier) : le
 * cache, nativeLibraryDir, et depuis le lot XX (la manière
 * CodeAssist, ForkedKotlinCompiler.kt:67-76) l'APK de l'app lui-même
 * et le jar du plugin Compose :
 *
 * <ul>
 *   <li>{@code joide.app.apk} : le classpath de la VM ouvrière
 *       Kotlin porte l'APK — le registrar Compose y est dexé
 *       (sur ART, un jar ne définit pas de classes) ;</li>
 *   <li>{@code joide.compose.plugin.jar} : le jar ORIGINAL du
 *       plugin, extrait UNE fois des assets vers les fichiers
 *       privés — kotlinc y lit le descripteur
 *       {@code META-INF/services} pour {@code -Xplugin}.</li>
 * </ul>
 *
 * <p>L'échec d'extraction est SILENCIEUX ici (l'asset peut manquer
 * dans un APK de bureau) : la sonde Compose nommera l'échec seulement
 * si un projet en a réellement besoin — jamais avant.</p>
 */
public final class ProprietesConstruction {

    /** L'asset du jar du plugin Compose (stagerPluginCompose). */
    static final String ASSET_PLUGIN_COMPOSE
            = "toolchain/kotlin-compose-compiler-plugin.jar";

    /**
     * L'asset des stubs d'invokedynamic du compile-time (le rôle
     * d'AGP core-lambda-stubs, la doctrine CodeAssist) : android.jar
     * OMET les fabriques java.lang.invoke et, à compliance ≥ 9, la
     * moindre concaténation ou lambda du Java utilisateur émet un
     * invokedynamic dont la bootstrap doit se résoudre — jamais dexé,
     * D8 désugare l'indy au dexage. La cible est le MÊME cache que le
     * localisateur d'outillage ({@code joide.toolchain.dir}), qui y
     * trouve la pièce sans rien télécharger.
     */
    static final String ASSET_STUBS = "toolchain/core-lambda-stubs.jar";

    private ProprietesConstruction() {
        // utilitaire statique
    }

    /**
     * Pose TOUTES les propriétés d'outillage et extrait l'asset du
     * plugin Compose. À appeler depuis {@code onCreate} des deux
     * points d'entrée de build (les propriétés système ne traversent
     * PAS les processus).
     *
     * @param contexte le service ou l'écran (n'importe quel Context)
     */
    public static void poser(Context contexte) {
        System.setProperty("joide.toolchain.dir",
                Paths.get(contexte.getFilesDir().getAbsolutePath(),
                        "toolchain").toString());
        System.setProperty("joide.native.dir",
                contexte.getApplicationInfo().nativeLibraryDir);
        String apk = contexte.getApplicationInfo().sourceDir;
        if (apk != null && !apk.isBlank()) {
            System.setProperty("joide.app.apk", apk);
        }
        extrairePluginCompose(contexte);
        extraireStubs(contexte);
    }

    /** La lecture intégrale d'un flux (API 1) : readAllBytes exige
     *  l'API 33 — les assets d'outillage (jar, keystore) se lisent à
     *  la main, minSdk 28 (lot C1, la dette NewApi du lint). */
    private static byte[] lireTout(InputStream entree)
            throws IOException {
        java.io.ByteArrayOutputStream tampon =
                new java.io.ByteArrayOutputStream();
        byte[] morceau = new byte[65_536];
        int lus;
        while ((lus = entree.read(morceau)) > 0) {
            tampon.write(morceau, 0, lus);
        }
        return tampon.toByteArray();
    }

    /** Le jar du plugin Compose : asset extrait une fois (la
     *  fraîcheur tenant à la taille), publié par renommage — la
     *  manière CodeAssist (jar bundlé, extrait sur disque). */
    private static void extrairePluginCompose(Context contexte) {
        try {
            Path cible = Paths.get(contexte.getFilesDir()
                            .getAbsolutePath(), "toolchain", "compose",
                    "kotlin-compose-compiler-plugin.jar");
            byte[] contenu;
            try (InputStream asset = contexte.getAssets()
                    .open(ASSET_PLUGIN_COMPOSE)) {
                // lot C1 : lecture bornée à la main — readAllBytes
                // exige l'API 33, minSdk 28
                contenu = lireTout(asset);
            }
            if (!Files.isRegularFile(cible)
                    || Files.size(cible) != contenu.length) {
                Files.createDirectories(cible.getParent());
                Path temporaire = Files.createTempFile(
                        cible.getParent(), "compose-", ".jar");
                Files.write(temporaire, contenu);
                Files.move(temporaire, cible,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            System.setProperty("joide.compose.plugin.jar",
                    cible.toString());
        } catch (IOException e) {
            // pas d'asset (APK de bureau, APK allégé) : PluginsKotlin
            // nommera l'échec SI Compose est demandé — pas avant
        }
    }

    /** Les stubs d'invokedynamic : asset extrait une fois dans le
     *  cache d'outillage — la même idempotence que le plugin Compose
     *  (comparaison de taille, publication par renommage). L'échec est
     *  SILENCIEUX (l'asset peut manquer) : le localisateur retombe sur
     *  la ressource du classpath, et le terrain nommera l'échec si un
     *  projet Java en a réellement besoin. */
    private static void extraireStubs(Context contexte) {
        try {
            Path cible = Paths.get(contexte.getFilesDir()
                            .getAbsolutePath(), "toolchain",
                    "core-lambda-stubs.jar");
            byte[] contenu;
            try (InputStream asset = contexte.getAssets()
                    .open(ASSET_STUBS)) {
                // lot C1 : lecture bornée à la main — readAllBytes
                // exige l'API 33, minSdk 28
                contenu = lireTout(asset);
            }
            if (!Files.isRegularFile(cible)
                    || Files.size(cible) != contenu.length) {
                Files.createDirectories(cible.getParent());
                Path temporaire = Files.createTempFile(
                        cible.getParent(), "stubs-", ".jar");
                Files.write(temporaire, contenu);
                Files.move(temporaire, cible,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // pas d'asset : le localisateur retombe sur la ressource
            // du classpath (le bureau) — le compileJava nommera
            // l'échec seulement si un projet Java en a besoin
        }
    }
}
