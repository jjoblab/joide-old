package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche d'assemblage du bundle (AAB) : la sortie proto du lien aapt2
 * (manifeste et table en format proto) et les dex fusionnés forment
 * la charge du module de base, assemblée par le service de bundle.
 * Le bundle produit est validé — un AAB invalide est un échec
 * explicite.
 */
public final class AabTask implements TaskDefinition {

    private final String moduleName;
    private final String variantName;
    private final RootedPath protoZip;
    private final RootedPath dexDir;
    private final RootedPath outputAab;
    private final BundleAssembler assembler;

    /**
     * @param moduleName  nom du module
     * @param variantName nom de la variante
     * @param protoZip    archive proto du lien aapt2
     * @param dexDir      répertoire des classes.dex
     * @param outputAab   fichier .aab produit
     * @param assembler   service d'assemblage de bundle
     */
    public AabTask(String moduleName, String variantName,
                   RootedPath protoZip, RootedPath dexDir,
                   RootedPath outputAab, BundleAssembler assembler) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.variantName = Objects.requireNonNull(variantName);
        this.protoZip = Objects.requireNonNull(protoZip);
        this.dexDir = Objects.requireNonNull(dexDir);
        this.outputAab = Objects.requireNonNull(outputAab);
        this.assembler = Objects.requireNonNull(assembler);
    }

    @Override
    public String name() {
        return moduleName + ":bundle[" + variantName + "]";
    }

    @Override
    public String taskType() {
        return "package";
    }

    @Override
    public TaskInputs inputs() {
        return TaskInputs.builder()
                .file(protoZip)
                .dir(dexDir)
                .build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().file(outputAab).build();
    }

    @Override
    public String toolVersion() {
        return assembler.toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return "aab-v1:" + variantName;
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 512, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path aab = context.resolve(outputAab);
        try {
            Path protoDir = Files.createDirectories(
                    aab.resolveSibling(aab.getFileName() + "-proto"));
            Path manifest = null;
            Path resources = null;
            List<Path> protoResources = new java.util.ArrayList<>();
            try (ZipFile zip = new ZipFile(
                    context.resolve(protoZip).toFile())) {
                ZipEntry manifestEntry = zip.getEntry("AndroidManifest.xml");
                ZipEntry resourcesEntry = zip.getEntry("resources.pb");
                if (manifestEntry == null) {
                    return TaskExecution.failed(List.of(Problem.error(
                            "aab.proto", "manifest proto missing")));
                }
                manifest = protoDir.resolve("AndroidManifest.xml");
                Files.write(manifest, zip.getInputStream(manifestEntry)
                        .readAllBytes());
                if (resourcesEntry != null) {
                    resources = protoDir.resolve("resources.pb");
                    Files.write(resources,
                            zip.getInputStream(resourcesEntry)
                                    .readAllBytes());
                }
                // ressources compilées proto : extraites sous res/
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith("res/")) {
                        continue;
                    }
                    Path out = protoDir.resolve(name);
                    if (out.getParent() != null) {
                        Files.createDirectories(out.getParent());
                    }
                    Files.write(out, zip.getInputStream(entry).readAllBytes());
                    protoResources.add(out);
                }
            }
            List<Path> dexes = new java.util.ArrayList<>();
            try (var stream = Files.list(context.resolve(dexDir))) {
                stream.filter(p -> p.getFileName() != null
                        && p.getFileName().toString().endsWith(".dex"))
                        .sorted()
                        .forEach(dexes::add);
            }
            assembler.assemble(new BundleAssembler.BaseModule(
                    manifest, resources, protoResources, dexes,
                    new TreeMap<>(), new TreeMap<>(), new TreeMap<>()), aab);
            return TaskExecution.ok(List.of(Problem.info("aab.ok",
                    "bundle assembled: " + aab.getFileName())));
        } catch (IOException | RuntimeException e) {
            return TaskExecution.failed(List.of(Problem.error("aab.failed",
                    e.getMessage())));
        }
    }
}
