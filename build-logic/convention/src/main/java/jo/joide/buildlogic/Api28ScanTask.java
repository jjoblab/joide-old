package jo.joide.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Contrôle de compatibilité API 28 (D4, ADR-013).
 *
 * <p>Chaque classe compilée du source set principal est lue au pool de
 * constantes ; toute référence aux espaces java.* ou javax.* doit exister
 * dans la liste des signatures du platform 28 ; tout préfixe interdit
 * (sun/, jdk/, android/, androidx/, com/android/, dalvik/) est rejeté.
 * Les références
 * aux constructions que D8 réécrit à la dexification (fabriques de
 * lambdas et de concaténation de chaînes) sont autorisées : elles
 * n'atteignent jamais ART telles quelles.</p>
 *
 * <p>Limite documentée : le contrôle ne voit ni réflexion ni chargement
 * dynamique ; il ne préjuge pas du désucrage des constructions Java 17
 * récentes (records, sealed) — absentes du niveau 28, elles sont donc
 * rejetées ici jusqu'à validation sur appareil en P5 (ADR-013).</p>
 */
public abstract class Api28ScanTask extends org.gradle.api.DefaultTask {

    /** Préfixes toujours interdits dans le code des modules JoIDE. */
    private static final List<String> FORBIDDEN_PREFIXES = Arrays.asList(
            "sun/", "jdk/", "android/", "androidx/", "com/android/", "dalvik/");

    /**
     * Constructions réécrites par D8 avant ART : jamais visibles à
     * l'exécution, donc autorisées malgré leur absence du platform 28.
     * Limitées aux seuls membres que javac émet (fabriques de lambdas,
     * concaténation de chaînes par invokedynamic).
     */
    private static boolean isDesugaredConstruct(ClassFileReferences.Ref ref) {
        if ("java/lang/invoke/LambdaMetafactory".equals(ref.owner)) {
            return ref.name == null || "metafactory".equals(ref.name)
                    || "altMetafactory".equals(ref.name);
        }
        if ("java/lang/invoke/StringConcatFactory".equals(ref.owner)) {
            return ref.name == null
                    || "makeConcatWithConstants".equals(ref.name);
        }
        return false;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClassesDirs();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract org.gradle.api.file.RegularFileProperty getSignaturesFile();

    /** Résultat : liste des violations (format stable, tests en dépendent). */
    @TaskAction
    public void scan() {
        Path signatures = getSignaturesFile().get().getAsFile().toPath();
        Set<String> allowed = loadSignatures(signatures);
        List<String> violations = new ArrayList<>();
        Set<File> roots = getClassesDirs().getFiles();
        for (File root : roots) {
            if (!root.isDirectory()) {
                continue;
            }
            scanTree(root.toPath(), root.toPath(), allowed, violations);
        }
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder(
                    "API 28 check failed (" + violations.size()
                            + " violation(s)):");
            for (String violation : violations) {
                message.append("\n  - ").append(violation);
            }
            message.append("\n  Signatures: ")
                    .append(signatures)
                    .append(" (see scripts/generate_api28_signatures.py)");
            throw new GradleException(message.toString());
        }
        getLogger().lifecycle("API 28 check: no violation in {} class root(s)",
                roots.size());
    }

    private void scanTree(Path root, Path dir, Set<String> allowed,
                          List<String> violations) {
        File[] children = dir.toFile().listFiles();
        if (children == null) {
            return;
        }
        Arrays.sort(children);
        for (File child : children) {
            Path path = child.toPath();
            if (child.isDirectory()) {
                scanTree(root, path, allowed, violations);
                continue;
            }
            String name = child.getName();
            if (!name.endsWith(".class")
                    || name.equals("module-info.class")
                    || name.equals("package-info.class")) {
                continue;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException e) {
                throw new GradleException(
                        "API 28 check: cannot read " + path, e);
            }
            List<ClassFileReferences.Ref> refs;
            try {
                refs = ClassFileReferences.read(bytes);
            } catch (IOException e) {
                throw new GradleException(
                        "API 28 check: invalid class file " + path, e);
            }
            String relative = root.relativize(path).toString()
                    .replace(File.separatorChar, '/');
            for (ClassFileReferences.Ref ref : refs) {
                String owner = ref.owner;
                if (owner.startsWith("[")) {
                    continue; // classes-tableaux : jamais contrôlées
                }
                boolean javaNamespace = owner.startsWith("java/")
                        || owner.startsWith("javax/");
                if (javaNamespace) {
                    if (!isDesugaredConstruct(ref)
                            && !allowed.contains(ref.signature())) {
                        violations.add(relative + ": reference outside the"
                                + " Android 28 API: " + ref.signature());
                    }
                } else {
                    for (String prefix : FORBIDDEN_PREFIXES) {
                        if (owner.startsWith(prefix)) {
                            violations.add(relative + ": forbidden reference: "
                                    + ref.signature());
                            break;
                        }
                    }
                }
            }
        }
    }

    private static Set<String> loadSignatures(Path file) {
        Set<String> allowed = new HashSet<>();
        try (InputStream raw = Files.newInputStream(file);
             InputStream gz = new GZIPInputStream(raw)) {
            byte[] all = readAll(gz);
            String text = new String(all, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (!line.isEmpty()) {
                    allowed.add(line);
                }
            }
        } catch (IOException e) {
            throw new GradleException(
                    "API 28 check: cannot load signatures " + file, e);
        }
        if (allowed.isEmpty()) {
            throw new GradleException(
                    "API 28 check: signature file is empty: " + file);
        }
        return allowed;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
