package jo.joide.deps.aar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Le contenu extrait d'un AAR : le répertoire d'extraction et les
 * chemins utiles du pipeline aval (P5 : dex, fusion, paquetage).
 * Immuable en pratique ; les chemins n'existent que si l'extraction
 * a eu lieu.
 */
public final class AarContents {

    public final Path dir;
    public final Path manifest;
    public final Path classesJar;
    public final List<Path> libs;
    public final List<Path> resources;

    AarContents(Path dir, Path manifest, Path classesJar, List<Path> libs,
                List<Path> resources) {
        this.dir = dir;
        this.manifest = manifest;
        this.classesJar = classesJar;
        this.libs = List.copyOf(libs);
        this.resources = List.copyOf(resources);
    }

    /**
     * @param dir répertoire d'extraction
     * @return le contenu décrit depuis le répertoire (sans extraction)
     */
    public static AarContents of(Path dir) {
        List<Path> libs = new ArrayList<>();
        Path libsDir = dir.resolve("libs");
        if (Files.isDirectory(libsDir)) {
            try (java.util.stream.Stream<Path> stream = Files.list(libsDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .sorted().forEach(libs::add);
            } catch (java.io.IOException e) {
                // listage best-effort
            }
        }
        List<Path> resources = new ArrayList<>();
        for (String name : new String[]{"res", "assets"}) {
            Path p = dir.resolve(name);
            if (Files.isDirectory(p)) {
                resources.add(p);
            }
        }
        return new AarContents(dir, dir.resolve("AndroidManifest.xml"),
                dir.resolve("classes.jar"), libs, resources);
    }
}
