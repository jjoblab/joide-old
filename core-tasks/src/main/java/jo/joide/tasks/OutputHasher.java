package jo.joide.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import jo.joide.platform.Hashing;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.platform.RootSet;
import jo.joide.tasks.TaskOutput.DirOutput;
import jo.joide.tasks.TaskOutput.FileOutput;
import jo.joide.vfs.StableHasher;

/**
 * Hachage des sorties d'une tâche : chaque fichier est haché de façon
 * stable, chaque dossier par une empreinte combinée de ses fichiers
 * triés.
 *
 * <p>Une sortie déclarée qui n'existe pas est un problème explicite :
 * jamais une empreinte silencieuse d'une sortie absente.</p>
 */
public final class OutputHasher {

    /**
     * Résultat du hachage des sorties : fichiers produits, empreintes
     * et problèmes rencontrés.
     */
    public static final class Result {

        private final Map<RootedPath, Path> produced;
        private final Map<String, String> hashes;
        private final List<Problem> problems;

        /**
         * @param produced fichiers et dossiers produits, par chemin
         *                 ancré déclaré (pour le cache de sorties)
         * @param hashes   empreinte par chemin ancré textuel
         * @param problems problèmes rencontrés (sortie absente,
         *                 lecture impossible)
         */
        public Result(Map<RootedPath, Path> produced,
                      Map<String, String> hashes, List<Problem> problems) {
            this.produced = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(
                            Objects.requireNonNull(produced, "produced")));
            this.hashes = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(
                            Objects.requireNonNull(hashes, "hashes")));
            this.problems = List.copyOf(
                    Objects.requireNonNull(problems, "problems"));
        }

        /** @return les fichiers et dossiers produits, par chemin ancré */
        public Map<RootedPath, Path> produced() {
            return produced;
        }

        /** @return l'empreinte par chemin ancré textuel */
        public Map<String, String> hashes() {
            return hashes;
        }

        /** @return les problèmes rencontrés */
        public List<Problem> problems() {
            return problems;
        }
    }

    private final StableHasher hasher;

    /**
     * @param hasher hacheur stable injecté
     */
    public OutputHasher(StableHasher hasher) {
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    /**
     * Hache toutes les sorties déclarées d'une tâche.
     *
     * @param outputs sorties déclarées
     * @param roots   racines pour résoudre les chemins
     * @return le résultat du hachage
     */
    public Result hash(TaskOutputs outputs, RootSet roots) {
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(roots, "roots");
        Map<RootedPath, Path> produced = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (TaskOutput output : outputs.asList()) {
            if (output instanceof FileOutput file) {
                Path path = roots.resolve(file.path());
                if (!Files.isRegularFile(path)) {
                    problems.add(Problem.error("output-missing",
                            "declared output does not exist: " + file.path())
                            .at(file.path().toString()));
                    continue;
                }
                try {
                    hashes.put(file.path().toString(),
                            hasher.hashStable(path).toHex());
                    produced.put(file.path(), path);
                } catch (IOException unreadable) {
                    problems.add(Problem.error("output-read-error",
                            "cannot hash output: " + file.path() + ": "
                                    + unreadable.getMessage())
                            .at(file.path().toString()));
                }
            } else if (output instanceof DirOutput dir) {
                Path path = roots.resolve(dir.path());
                if (!Files.isDirectory(path)) {
                    problems.add(Problem.error("output-missing",
                            "declared output directory does not exist: "
                                    + dir.path()).at(dir.path().toString()));
                    continue;
                }
                Map<String, String> fileHashes = new TreeMap<>();
                try (Stream<Path> walk = Files.walk(path)) {
                    for (Path file : walk.filter(Files::isRegularFile)
                            .sorted().toList()) {
                        String relative = path.relativize(file).toString()
                                .replace('\\', '/');
                        fileHashes.put(relative, hasher.hashStable(file).toHex());
                    }
                } catch (IOException unreadable) {
                    problems.add(Problem.error("output-read-error",
                            "cannot hash output directory: " + dir.path() + ": "
                                    + unreadable.getMessage())
                            .at(dir.path().toString()));
                    continue;
                }
                StringBuilder combined = new StringBuilder();
                for (Map.Entry<String, String> entry : fileHashes.entrySet()) {
                    combined.append(entry.getKey()).append('\n')
                            .append(entry.getValue()).append('\n');
                }
                hashes.put(dir.path().toString(), Hashing.toHex(Hashing.sha256(
                        combined.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                produced.put(dir.path(), path);
            }
        }
        return new Result(produced, hashes, problems);
    }
}
