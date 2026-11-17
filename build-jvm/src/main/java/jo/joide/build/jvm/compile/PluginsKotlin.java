package jo.joide.build.jvm.compile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Les plugins du compilateur Kotlin (lot XIX, la manière CodeAssist,
 * ComposeCompilerPlugin.kt) : ACTIVATION PAR SONDE DE CLASSPATH — la
 * présence de {@code androidx/compose/runtime/Composable.class} dans
 * un jar OU un répertoire du classpath de la demande déclenche le
 * plugin, AUCUN réglage utilisateur. Le plugin Compose reçoit ZÉRO
 * option {@code -P} (idem serialization et parcelize chez
 * CodeAssist) : le jar du plugin est le seul câblage.
 *
 * <p>Le JAR du plugin doit être un FICHIER sur le disque — kotlinc y
 * lit le descripteur {@code META-INF/services} du registrar. Deux
 * sources : le pack Kotlin sur l'appareil (ressource
 * {@code jars/kotlin-compose-compiler-plugin-<v>.jar}, extraite par
 * homeOuvrière comme la stdlib du lot XVI) ou la ressource du
 * classpath (bureau : le jar copié dans les ressources de
 * build-jvm au montage).</p>
 */
public final class PluginsKotlin {

    /** L'entrée qui déclenche le plugin Compose (la sonde). */
    public static final String ENTREE_COMPOSABLE
            = "androidx/compose/runtime/Composable.class";

    /** L'ID du plugin Compose (le même que CodeAssist). */
    public static final String ID_PLUGIN_COMPOSE
            = "androidx.compose.compiler.plugins.kotlin";

    /** Le préfixe du jar du plugin dans les jars du pack / du
     *  classpath (la version suit le compilateur). */
    public static final String PREFIXE_JAR_COMPOSE
            = "kotlin-compose-compiler-plugin-";

    /** La ressource classpath du jar du plugin (bureau). */
    static final String RESSOURCE_JAR_COMPOSE
            = "/kotlin-compose-compiler-plugin.jar";

    private PluginsKotlin() {
        // utilitaire statique
    }

    /**
     * @param entrees les entrées de classpath de la demande (jars et
     *                répertoires)
     * @return vrai si Compose est au classpath (la sonde) — le
     *         sondage tolère les entrées inexistantes (elles sont
     *         filtrées plus loin par KotlinArgs)
     * @throws IOException si une entrée jar est illisible
     */
    public static boolean composeDemande(List<Path> entrees)
            throws IOException {
        if (entrees == null) {
            return false;
        }
        for (Path entree : entrees) {
            if (!Files.exists(entree)) {
                continue;
            }
            if (Files.isDirectory(entree)) {
                if (Files.exists(entree.resolve(ENTREE_COMPOSABLE))) {
                    return true;
                }
            } else if (entree.toString().endsWith(".jar")) {
                try (ZipFile zip = new ZipFile(entree.toFile())) {
                    if (zip.getEntry(ENTREE_COMPOSABLE) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Les plugins à passer au compilateur pour cette demande :
     * aujourd'hui Compose seul (sonde + jar). Le jar du plugin suit
     * la version du compilateur (le pack le porte à l'identique).
     *
     * @param request la demande de compilation (son classpath est
     *                sondé)
     * @param homePack le home de la VM ouvrière sur l'appareil (le
     *                 pack extrait), ou {@code null} (bureau : la
     *                 ressource du classpath)
     * @return la liste des jars {@code -Xplugin}, vide si aucune
     *         sonde ne s'allume
     * @throws IOException sonde allumée et jar introuvable (échec
     *                     nommé, jamais un plugin silencieusement
     *                     absent — le diagnostic « @Composable
     *                     function is not allowed » d'un build sans
     *                     plugin serait incompréhensible)
     */
    public static List<Path> pluginsPour(CompileRequest request,
                                         Path homePack)
            throws IOException {
        if (!composeDemande(request.classpath)) {
            return List.of();
        }
        Path jar = jarCompose(homePack);
        if (jar == null) {
            throw new IOException("sonde Compose allumée ("
                    + ENTREE_COMPOSABLE + " au classpath) mais le jar "
                    + "du plugin est introuvable : sur l'appareil, "
                    + "l'APK doit porter l'asset toolchain/"
                    + "kotlin-compose-compiler-plugin.jar (lot XX, "
                    + "la propriété joide.compose.plugin.jar) ; au "
                    + "bureau, la ressource " + RESSOURCE_JAR_COMPOSE);
        }
        return List.of(jar);
    }

    /**
     * Le jar du plugin Compose : la propriété {@code
     * joide.compose.plugin.jar} d'abord (appareil : l'APK l'extrait
     * de ses assets — lot XX, la manière CodeAssist), le pack ensuite
     * (repli historique, le pack étant immuable il ne le porte plus),
     * la ressource du classpath enfin (bureau : extraite vers le
     * cache du tmpdir, réutilisée si présente).
     *
     * @param homePack le home du pack (appareil) ou {@code null}
     * @return le jar sur disque, ou {@code null}
     * @throws IOException si l'extraction de la ressource échoue
     */
    public static Path jarCompose(Path homePack) throws IOException {
        String propriete = System
                .getProperty("joide.compose.plugin.jar");
        if (propriete != null && !propriete.isBlank()
                && Files.isRegularFile(Path.of(propriete.trim()))) {
            return Path.of(propriete.trim());
        }
        if (homePack != null) {
            Path jars = homePack.resolve("jars");
            if (Files.isDirectory(jars)) {
                try (var flux = Files.list(jars)) {
                    Path trouve = flux.filter(p -> {
                                String nom = p.getFileName().toString();
                                return nom.startsWith(PREFIXE_JAR_COMPOSE)
                                        && nom.endsWith(".jar");
                            })
                            .sorted()
                            .findFirst()
                            .orElse(null);
                    if (trouve != null) {
                        return trouve;
                    }
                }
            }
        }
        try (InputStream ressource = PluginsKotlin.class
                .getResourceAsStream(RESSOURCE_JAR_COMPOSE)) {
            if (ressource == null) {
                return null;
            }
            Path cache = Path.of(System.getProperty("java.io.tmpdir"),
                    "joide", "compose-plugin");
            Files.createDirectories(cache);
            Path cible = cache.resolve("kotlin-compose-compiler-plugin.jar");
            if (!Files.exists(cible)) {
                Path temporaire = Files.createTempFile(cache,
                        "compose-", ".jar.tmp");
                Files.copy(ressource, temporaire,
                        StandardCopyOption.REPLACE_EXISTING);
                // le mouvement atomique : jamais un jar à moitié écrit
                try {
                    Files.move(temporaire, cible,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException atomique) {
                    Files.move(temporaire, cible,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return cible;
        }
    }
}
