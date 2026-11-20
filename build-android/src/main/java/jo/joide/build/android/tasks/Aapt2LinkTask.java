package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.android.aapt2.Aapt2;
import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de lien aapt2 : fichiers plats compilés + manifeste vers
 * l'archive liée (manifeste binaire + table de ressources), les
 * sources R.java et la table de symboles R.txt. Le lien est
 * global — toute modification de ressource reprend le lien entier,
 * mais le lien ne recompile pas les fichiers inchangés (la tâche de
 * compilation reste proportionnelle à la modification).
 *
 * <p><b>Lot XVII — le manifeste ENRICHIE</b> (le terrain du
 * 2026-11-17 : « &lt;manifest&gt; must have a 'package' attribute ») :
 * depuis AGP 7 le package vit dans {@code namespace} du build.gradle,
 * plus dans le manifeste — aapt2 (couche basse) exige pourtant
 * l'attribut. La manière AGP : le manifeste MERGÉ porte l'attribut
 * synthétisé. La tâche écrit donc TOUJOURS un manifeste enrichie
 * dans le répertoire d'outillage du module ($OUT, JAMAIS dans les
 * sources utilisateur) : copie du manifeste source, avec
 * {@code package="…"} injecté s'il manque — le paquet se lit du
 * manifeste, sinon de l'identifiant d'application de la variante,
 * sinon il se dérive du nom du module (la même règle que
 * {@code identifiantPaquet} de SpecsBuild) ; une synthèse émet un
 * WARN qui nomme le correctif recommandé. Les DEUX liens (APK et
 * proto) consomment le manifeste enrichie.</p>
 *
 * <p><b>Lot C10 — le manifeste PORTANT SA VERSION</b> (le terrain du
 * 2026-11-20 : « :app:bundle[debug] : bundle assembly failed:
 * Version code not found in manifest ») : bundletool lit le code de
 * version dans le manifeste du module de base — or depuis AGP 7 le
 * versionCode vit dans la variante (le build.gradle), pas dans le
 * manifeste. La même manière que le paquet : {@code enrichir()}
 * synthétise AUSSI {@code android:versionCode},
 * {@code android:versionName} et l'élément {@code <uses-sdk>}
 * (min/target SDK de la variante, celle-là même qui a taillé le dex)
 * quand ils manquent — toujours dans $OUT, jamais dans les sources
 * utilisateur ; une synthèse émet un WARN qui nomme le correctif.</p>
 */
public final class Aapt2LinkTask implements TaskDefinition {

    private final String moduleName;
    private final RootedPath manifest;
    private final RootedPath manifestEnrichi;
    private final List<RootedPath> flatDirs;
    private final RootedPath linkedApk;
    private final RootedPath javaDir;
    private final RootedPath rTxt;
    private final RootedPath protoDir;
    private final Path aapt2Binary;
    private final Path platformJar;
    private final AndroidVariant variante;
    private final RootedPath assetsDir;

    /**
     * @param moduleName      nom du module
     * @param manifest        manifeste source
     * @param manifestEnrichi manifeste enrichie en sortie ($OUT) :
     *                        le manifeste consommé par les liens —
     *                        {@code package="…"} synthétisé s'il
     *                        manque (lot XVII)
     * @param flatDirs        fichiers plats (sortie de compilation) :
     *                        les propres du module puis ceux des
     *                        bibliothèques pour le lien d'application
     * @param linkedApk       archive liée en sortie
     * @param javaDir         répertoire des sources R.java
     * @param rTxt            table de symboles en sortie
     * @param protoDir        répertoire des sorties proto (bundle)
     * @param aapt2Binary     binaire aapt2
     * @param platformJar     android.jar de la plateforme
     * @param variante        première variante du module d'application,
     *                        ou {@code null} (module bibliothèque) :
     *                        renommage de paquet, code et nom de version,
     *                        SDK minimum et cible du manifeste enrichi
     * @param assetsDir       répertoire d'actifs, ou {@code null}
     */
    public Aapt2LinkTask(String moduleName, RootedPath manifest,
                         RootedPath manifestEnrichi,
                         List<RootedPath> flatDirs, RootedPath linkedApk,
                         RootedPath javaDir, RootedPath rTxt,
                         RootedPath protoDir, Path aapt2Binary,
                         Path platformJar, AndroidVariant variante,
                         RootedPath assetsDir) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.manifest = Objects.requireNonNull(manifest);
        this.manifestEnrichi = Objects.requireNonNull(manifestEnrichi);
        this.flatDirs = List.copyOf(flatDirs);
        this.linkedApk = Objects.requireNonNull(linkedApk);
        this.javaDir = Objects.requireNonNull(javaDir);
        this.rTxt = Objects.requireNonNull(rTxt);
        this.protoDir = Objects.requireNonNull(protoDir);
        this.aapt2Binary = Objects.requireNonNull(aapt2Binary);
        this.platformJar = Objects.requireNonNull(platformJar);
        this.variante = variante;
        this.assetsDir = assetsDir;
    }

    @Override
    public String name() {
        return moduleName + ":linkAndroidResources";
    }

    @Override
    public String taskType() {
        return "aapt2";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        builder.file(manifest);
        for (RootedPath flatDir : flatDirs) {
            builder.dir(flatDir);
        }
        if (assetsDir != null) {
            builder.dir(assetsDir);
        }
        builder.property("joide.aapt2.platform", platformJar.toString());
        if (renommage() != null) {
            builder.property("joide.aapt2.package", renommage());
        }
        if (variante != null) {
            // le manifeste enrichi dépend des versions de la variante
            // (lot C10) : elles sont des ENTRÉES — l'empreinte du cache
            // les porte, une version qui change rejoue le lien
            builder.property("joide.aapt2.version-code",
                    String.valueOf(codeDeVersion(variante)));
            builder.property("joide.aapt2.version-name",
                    String.valueOf(nomDeVersion(variante)));
            builder.property("joide.aapt2.min-sdk",
                    String.valueOf(variante.minSdk));
            builder.property("joide.aapt2.target-sdk",
                    String.valueOf(variante.targetSdk));
        }
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder()
                .file(manifestEnrichi)
                .file(linkedApk)
                .dir(javaDir)
                .file(rTxt)
                .dir(protoDir)
                .file(protoZip())
                .build();
    }

    /** @return le chemin ancré de l'archive proto (bundle) */
    public RootedPath protoZip() {
        return protoDir.parent().resolve(protoDir.fileName() + ".zip");
    }

    @Override
    public String toolVersion() {
        return "aapt2";
    }

    @Override
    public String optionsFingerprint() {
        StringBuilder empreinte = new StringBuilder("link-v2");
        if (renommage() != null) {
            empreinte.append(":pkg=").append(renommage());
        }
        if (variante != null) {
            empreinte.append(":vc=").append(codeDeVersion(variante))
                    .append(":vn=").append(nomDeVersion(variante))
                    .append(":sdk=").append(variante.minSdk)
                    .append('/').append(variante.targetSdk);
        }
        return empreinte.toString();
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 256, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path apkOut = context.resolve(linkedApk);
        Path genDir = context.resolve(javaDir);
        Path protoOut = context.resolve(protoDir);
        try {
            Files.createDirectories(apkOut.getParent());
            if (genDir.getParent() != null) {
                Files.createDirectories(genDir.getParent());
            }
            Files.createDirectories(genDir);
            Files.createDirectories(protoOut);
            deleteTree(context.resolve(javaDir));
            Files.createDirectories(context.resolve(javaDir));
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("aapt2.io",
                    "cannot prepare output: " + e.getMessage())));
        }
        List<Path> flats = new ArrayList<>();
        for (RootedPath flatDir : flatDirs) {
            Path flatRoot = context.resolve(flatDir);
            if (!Files.isDirectory(flatRoot)) {
                continue;
            }
            try (var stream = Files.list(flatRoot)) {
                stream.filter(p -> p.getFileName() != null
                                && p.getFileName().toString()
                                        .endsWith(".flat"))
                        .sorted()
                        .forEach(flats::add);
            } catch (IOException e) {
                return TaskExecution.failed(List.of(Problem.error("aapt2.io",
                        "cannot list flat files: " + e.getMessage())));
            }
        }
        // le manifeste enrichie (lot XVII) : copie du manifeste
        // source avec package="…" synthétisé s'il manque — aapt2
        // refuse un manifeste sans attribut (les projets AGP 7+ le
        // portent dans namespace), et les sources utilisateur ne
        // s'écrivent JAMAIS
        Path manifesteSource = context.resolve(manifest);
        List<Problem> notes = new ArrayList<>();
        Path manifesteEffectif;
        try {
            manifesteEffectif = enrichir(manifesteSource,
                    context.resolve(manifestEnrichi), renommage(),
                    moduleName, variante, notes);
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("aapt2.io",
                    "cannot write enriched manifest: " + e.getMessage())));
        }
        Aapt2 aapt2 = new Aapt2(aapt2Binary, 300);
        try {
            // lien binaire (APK)
            Aapt2.LinkOptions.Builder apkLink = Aapt2.LinkOptions.builder()
                    .manifest(manifesteEffectif)
                    .platformJar(platformJar)
                    .output(apkOut)
                    .javaOut(genDir)
                    .rTxtOut(context.resolve(rTxt))
                    .autoAddOverlay(true);
            if (renommage() != null) {
                apkLink.packageName(renommage());
            }
            if (assetsDir != null) {
                apkLink.assetsDir(context.resolve(assetsDir));
            }
            for (Path flat : flats) {
                apkLink.compiled(flat);
            }
            aapt2.link(apkLink.build());
            // lien proto (bundle)
            Path protoZip = context.resolve(protoZip());
            Aapt2.LinkOptions.Builder protoLink = Aapt2.LinkOptions.builder()
                    .manifest(manifesteEffectif)
                    .platformJar(platformJar)
                    .output(protoZip)
                    .protoFormat(true)
                    .autoAddOverlay(true);
            if (renommage() != null) {
                protoLink.packageName(renommage());
            }
            if (assetsDir != null) {
                protoLink.assetsDir(context.resolve(assetsDir));
            }
            for (Path flat : flats) {
                protoLink.compiled(flat);
            }
            aapt2.link(protoLink.build());
            notes.add(Problem.info("aapt2.link",
                    flats.size() + " flat file(s) linked"));
            return TaskExecution.ok(notes);
        } catch (IOException e) {
            String message = e instanceof Aapt2.Aapt2Exception failure
                    ? failure.result.stderr : e.getMessage();
            return TaskExecution.failed(List.of(Problem.error("aapt2.link",
                    "link failed: " + message)));
        }
    }

    /**
     * Le manifeste enrichie (lots XVII et C10) : copie du manifeste
     * source vers la cible, avec {@code package="…"} (aapt2 refuse
     * l'attribut absent — « &lt;manifest&gt; must have a 'package'
     * attribute »), {@code android:versionCode} et
     * {@code android:versionName} (bundletool lit le code de version
     * dans le manifeste du module de base — « Version code not found
     * in manifest »), puis l'élément {@code <uses-sdk>} (l'API
     * déclarée suit celle de la variante qui a taillé le dex)
     * injectés quand ils manquent. Tout vient de la variante (la
     * manière AGP : le manifeste mergé porte les attributs
     * synthétisés du build) ; le paquet se lit du manifeste, sinon de
     * l'identifiant d'application de la variante, sinon il se dérive
     * du nom du module (la règle d'{@code identifiantPaquet}) ; une
     * synthèse émet un WARN qui nomme le correctif recommandé.
     *
     * @param source      manifeste du projet (LU, jamais écrit)
     * @param cible       manifeste enrichie ($OUT du module)
     * @param applicationId identifiant de la variante, ou {@code null}
     * @param nomModule   nom du module (dérivation en dernier recours)
     * @param variante    la variante porteuse des versions, ou
     *                    {@code null} (module bibliothèque : aucune
     *                    synthèse de version)
     * @param notes       les diagnostics accumulés (WARN de synthèse)
     * @return la cible
     * @throws IOException si la lecture ou l'écriture échoue
     */
    static Path enrichir(Path source, Path cible, String applicationId,
                         String nomModule, AndroidVariant variante,
                         List<Problem> notes)
            throws IOException {
        String texte = Files.readString(source);
        java.util.regex.Matcher ouverture = java.util.regex.Pattern
                .compile("<manifest\\b[^>]*>").matcher(texte);
        if (!ouverture.find()) {
            throw new IOException(
                    "balise <manifest> introuvable dans " + source);
        }
        String balise = ouverture.group();
        String baliseEnrichie = balise;
        String paquet = attributDe(baliseEnrichie, "package");
        if (paquet == null) {
            paquet = applicationId != null && !applicationId.isBlank()
                    ? applicationId
                    : paquetDerive(nomModule);
            baliseEnrichie = baliseEnrichie.replaceFirst("<manifest\\b",
                    "<manifest package=\"" + paquet + "\"");
            notes.add(Problem.warn("aapt2.manifest",
                    "package synthétisé (« " + paquet + " », "
                            + (applicationId != null
                                    ? "depuis l'identifiant d'application"
                                    : "dérivé du nom du module")
                            + ") — ajoutez package=\"…\" à la balise "
                            + "<manifest> du manifeste du module"));
        }
        if (variante != null) {
            if (attributDe(baliseEnrichie, "android:versionCode") == null) {
                baliseEnrichie = injecterAttribut(baliseEnrichie,
                        "android:versionCode",
                        String.valueOf(codeDeVersion(variante)));
                notes.add(Problem.warn("aapt2.manifest",
                        "android:versionCode synthétisé (« "
                                + codeDeVersion(variante)
                                + " », de la variante) — bundletool exige "
                                + "le code de version dans le manifeste du "
                                + "module de base : portez-le dans le "
                                + "manifeste ou la variante"));
            }
            if (nomDeVersion(variante) != null && attributDe(baliseEnrichie,
                    "android:versionName") == null) {
                baliseEnrichie = injecterAttribut(baliseEnrichie,
                        "android:versionName", nomDeVersion(variante));
                notes.add(Problem.warn("aapt2.manifest",
                        "android:versionName synthétisé (« "
                                + nomDeVersion(variante)
                                + " », de la variante) — portez-le dans le "
                                + "manifeste ou la variante"));
            }
        }
        texte = texte.replace(balise, baliseEnrichie);
        // l'élément uses-sdk : un manifeste auto-fermé ne peut pas
        // porter d'enfant — les attributs de version suffisent alors
        // (bundletool ne lit que le code de version à l'assemblage)
        if (variante != null && !baliseEnrichie.endsWith("/>")
                && !texte.contains("<uses-sdk")) {
            texte = texte.replace(baliseEnrichie, baliseEnrichie
                    + "\n    <uses-sdk"
                    + " android:minSdkVersion=\"" + variante.minSdk + "\""
                    + " android:targetSdkVersion=\"" + variante.targetSdk
                    + "\" />");
            notes.add(Problem.warn("aapt2.manifest",
                    "élément uses-sdk synthétisé (minSdkVersion "
                            + variante.minSdk + ", targetSdkVersion "
                            + variante.targetSdk + ", de la variante) — "
                            + "portez-le dans le manifeste ou la variante"));
        }
        Files.createDirectories(cible.getParent());
        Files.writeString(cible, texte);
        return cible;
    }

    /** L'identifiant de renommage de paquet de la variante, ou
     *  {@code null} (pas de renommage : le paquet du manifeste tient). */
    private String renommage() {
        return variante == null || variante.applicationId == null
                || variante.applicationId.isBlank()
                ? null : variante.applicationId;
    }

    /** Le code de version EFFECTIF : celui de la variante, sinon 1
     *  (−1 marque l'absence — un manifeste sans code de version ferait
     *  échouer bundletool, jamais un bundle silencieux).
     * @param variante la variante de l'application
     * @return le code de version porté au manifeste */
    private static int codeDeVersion(AndroidVariant variante) {
        return variante.versionCode > 0 ? variante.versionCode : 1;
    }

    /** Le nom de version de la variante, ou {@code null} (rien à
     *  écrire dans le manifeste).
     * @param variante la variante de l'application
     * @return le nom de version, ou {@code null} */
    private static String nomDeVersion(AndroidVariant variante) {
        return variante.versionName == null
                || variante.versionName.isBlank()
                ? null : variante.versionName;
    }

    /** La valeur d'un attribut de la balise d'ouverture du manifeste,
     *  ou {@code null} : le nom ne peut pas être le suffixe d'un autre
     *  (namespace:nom écarté par le regard arrière).
     * @param balise   la balise &lt;manifest&gt; d'ouverture
     * @param attribut le nom d'attribut cherché
     * @return la valeur, ou {@code null} si l'attribut manque */
    static String attributDe(String balise, String attribut) {
        java.util.regex.Matcher trouve = java.util.regex.Pattern
                .compile("(?<![\\w:-])"
                        + java.util.regex.Pattern.quote(attribut)
                        + "\\s*=\\s*\"([^\"]*)\"")
                .matcher(balise);
        return trouve.find() ? trouve.group(1) : null;
    }

    /** La balise avec l'attribut ajouté en queue : l'auto-fermeture
     *  {@code />} reste valide, le séparateur ne se double jamais.
     * @param balise   la balise d'ouverture (finissant par {@code >}
     *                 ou {@code />})
     * @param attribut le nom d'attribut à ajouter
     * @param valeur   la valeur de l'attribut
     * @return la balise enrichie */
    static String injecterAttribut(String balise, String attribut,
                                   String valeur) {
        boolean autoFermee = balise.endsWith("/>");
        int point = autoFermee ? balise.length() - 2 : balise.length() - 1;
        String tete = balise.substring(0, point);
        String separateur = tete.endsWith(" ") || tete.endsWith("\n")
                || tete.endsWith("\t") || tete.endsWith("\r") ? "" : " ";
        return tete + separateur + attribut + "=\"" + valeur + "\""
                + (autoFermee ? "/>" : ">");
    }

    /** L'attribut {@code package} de la balise manifeste, ou
     *  {@code null} : la balise d'ouverture seulement (l'attribut ne
     *  peut pas apparaître ailleurs dans un manifeste bien formé). */
    static String paquetDuManifeste(String texte) {
        java.util.regex.Matcher ouverture = java.util.regex.Pattern
                .compile("<manifest\\b[^>]*>")
                .matcher(texte);
        if (!ouverture.find()) {
            return null;
        }
        java.util.regex.Matcher attribut = java.util.regex.Pattern
                .compile("\\bpackage\\s*=\\s*\"([^\"]+)\"")
                .matcher(ouverture.group());
        return attribut.find() ? attribut.group(1) : null;
    }

    /** La dérivation d'identifiant d'{@code identifiantPaquet}
     *  (SpecsBuild) : « :core » → « joide.app.core ». */
    static String paquetDerive(String nomModule) {
        String nom = nomModule.replace(':', '_').replaceAll(
                "[^A-Za-z0-9_.]", "");
        if (nom.isEmpty() || Character.isDigit(nom.charAt(0))) {
            nom = "app" + nom;
        }
        return "joide.app." + nom.toLowerCase(java.util.Locale.ROOT);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
