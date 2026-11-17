package jo.joide.build.android.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.android.tools.build.bundletool.commands.BuildBundleCommand;
import com.android.tools.build.bundletool.commands.ValidateBundleCommand;
import com.google.common.collect.ImmutableList;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.packaging.ApkWriter;

/**
 * Assemblage de bundle par bundletool en processus : la charge du
 * module de base est écrite à la main (zip standard, entrées triées,
 * horodatages fixes) puis bundletool construit l'archive finale. La
 * charge est validée avant l'assemblage — un module invalide est un
 * échec explicite, jamais un bundle corrompu.
 */
public final class BundletoolAssembler implements BundleAssembler {

    /** Version de bundletool, alignée sur le catalogue de versions. */
    public static final String BUNDLETOOL_VERSION = "1.18.3";

    private final String version;

    /** Enregistrement ServiceLoader : version du catalogue. */
    public BundletoolAssembler() {
        this(BUNDLETOOL_VERSION);
    }

    /** @param version libellé de version pour l'empreinte des tâches */
    public BundletoolAssembler(String version) {
        this.version = "bundletool:" + version;
    }

    @Override
    public void assemble(BaseModule module, Path output) throws IOException {
        Path payload = output.resolveSibling(
                output.getFileName() + ".module.zip");
        writeModulePayload(module, payload);
        try {
            BuildBundleCommand.builder()
                    .setModulesPaths(ImmutableList.of(payload))
                    .setOutputPath(output)
                    .setOverwriteOutput(true)
                    .build()
                    .execute();
        } catch (RuntimeException e) {
            throw new IOException("bundle assembly failed: "
                    + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(payload);
        }
    }

    /**
     * Valide un bundle assemblé (commande validate de bundletool).
     *
     * @param bundle fichier .aab
     * @throws IOException si la validation échoue
     */
    public static void validate(Path bundle) throws IOException {
        try {
            ValidateBundleCommand.builder()
                    .setBundlePath(bundle)
                    .build()
                    .execute();
        } catch (RuntimeException e) {
            throw new IOException("bundle validation failed: "
                    + e.getMessage(), e);
        }
    }

    private static void writeModulePayload(BaseModule module, Path payload)
            throws IOException {
        ApkWriter writer = new ApkWriter();
        writer.deflatedFile("manifest/AndroidManifest.xml",
                module.manifestProto);
        if (module.resourcesProto != null
                && Files.isRegularFile(module.resourcesProto)) {
            writer.deflatedFile("resources.pb", module.resourcesProto);
        }
        for (Path resource : module.protoResources) {
            String relative = protoResourceName(resource);
            if (relative != null) {
                writer.deflatedFile(relative, resource);
            }
        }
        int dexIndex = 1;
        for (Path dex : module.dexFiles) {
            writer.deflatedFile(dexIndex == 1 ? "dex/classes.dex"
                    : "dex/classes" + dexIndex + ".dex", dex);
            dexIndex++;
        }
        for (Map.Entry<String, Path> asset
                : new TreeMap<>(module.assets).entrySet()) {
            writer.deflatedFile("assets/" + asset.getKey(), asset.getValue());
        }
        for (Map.Entry<String, Path> root
                : new TreeMap<>(module.rootFiles).entrySet()) {
            writer.deflatedFile("root/" + root.getKey(), root.getValue());
        }
        for (Map.Entry<String, Path> lib
                : new TreeMap<>(module.nativeLibs).entrySet()) {
            writer.deflatedFile("lib/" + lib.getKey(), lib.getValue());
        }
        writer.writeTo(payload);
    }

    private static String protoResourceName(Path resource) {
        // les ressources compilées vivent sous res/ avec leur nom de
        // fichier plat ; le chemin relatif est reconstruit par balayage
        Path cursor = resource;
        List<String> parts = new ArrayList<>();
        while (cursor != null && cursor.getFileName() != null) {
            String name = cursor.getFileName().toString();
            if (name.equals("res")) {
                break;
            }
            parts.add(0, name);
            cursor = cursor.getParent();
        }
        if (cursor == null || parts.isEmpty()) {
            return null;
        }
        return "res/" + String.join("/", parts);
    }

    @Override
    public String toolVersion() {
        return version;
    }
}
