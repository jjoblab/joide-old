package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.android.aapt2.Aapt2;
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
    private final String packageName;
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
     * @param packageName     identifiant d'application (renommage)
     * @param assetsDir       répertoire d'actifs, ou {@code null}
     */
    public Aapt2LinkTask(String moduleName, RootedPath manifest,
                         RootedPath manifestEnrichi,
                         List<RootedPath> flatDirs, RootedPath linkedApk,
                         RootedPath javaDir, RootedPath rTxt,
                         RootedPath protoDir, Path aapt2Binary,
                         Path platformJar, String packageName,
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
        this.packageName = packageName;
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
        if (packageName != null) {
            builder.property("joide.aapt2.package", packageName);
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
        return "link-v1" + (packageName == null ? "" : ":" + packageName);
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
                    context.resolve(manifestEnrichi), packageName,
                    moduleName, notes);
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
            if (packageName != null) {
                apkLink.packageName(packageName);
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
            if (packageName != null) {
                protoLink.packageName(packageName);
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
     * Le manifeste enrichie (lot XVII) : copie du manifeste source
     * vers la cible, avec {@code package="…"} injecté dans la balise
     * {@code &lt;manifest&gt;} s'il manque — aapt2 refuse l'attribut
     * absent (le terrain : « &lt;manifest&gt; must have a 'package'
     * attribute »). Le paquet se lit du manifeste, sinon de
     * l'identifiant d'application de la variante, sinon il se dérive
     * du nom du module (la règle d'{@code identifiantPaquet}) ; une
     * synthèse ajoute un WARN qui nomme le correctif recommandé.
     *
     * @param source      manifeste du projet (LU, jamais écrit)
     * @param cible       manifeste enrichie ($OUT du module)
     * @param applicationId identifiant de la variante, ou {@code null}
     * @param nomModule   nom du module (dérivation en dernier recours)
     * @param notes       les diagnostics accumulés (WARN de synthèse)
     * @return la cible
     * @throws IOException si la lecture ou l'écriture échoue
     */
    static Path enrichir(Path source, Path cible, String applicationId,
                         String nomModule, List<Problem> notes)
            throws IOException {
        String texte = Files.readString(source);
        String paquet = paquetDuManifeste(texte);
        if (paquet == null) {
            paquet = applicationId != null && !applicationId.isBlank()
                    ? applicationId
                    : paquetDerive(nomModule);
            if (texte.contains("<manifest")) {
                texte = texte.replaceFirst("<manifest\\b",
                        "<manifest package=\"" + paquet + "\"");
            } else {
                throw new IOException(
                        "balise <manifest> introuvable dans " + source);
            }
            notes.add(Problem.warn("aapt2.manifest",
                    "package synthétisé (« " + paquet + " », "
                            + (applicationId != null
                                    ? "depuis l'identifiant d'application"
                                    : "dérivé du nom du module")
                            + ") — ajoutez package=\"…\" à la balise "
                            + "<manifest> du manifeste du module"));
        }
        Files.createDirectories(cible.getParent());
        Files.writeString(cible, texte);
        return cible;
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
