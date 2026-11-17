package jo.joide.build.android.aapt2;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Pilote du binaire aapt2 en processus externe : compilation de
 * fichiers de ressources en fichiers plats, puis lien complet
 * (manifeste, table de ressources, classes R). Les sorties et erreurs
 * sont lues intégralement ; un code de retour non nul produit un
 * diagnostic structuré avec la sortie d'aapt2.
 */
public final class Aapt2 {

    /** Une exécution d'aapt2 terminée. */
    public static final class Result {
        /** Code de retour du processus. */
        public final int exitCode;
        /** Sortie standard, jamais {@code null}. */
        public final String stdout;
        /** Sortie d'erreur, jamais {@code null}. */
        public final String stderr;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = Objects.requireNonNull(stdout);
            this.stderr = Objects.requireNonNull(stderr);
        }

        /** @return vrai si le code de retour est zéro */
        public boolean success() {
            return exitCode == 0;
        }
    }

    /** Un échec d'exécution d'aapt2. */
    public static final class Aapt2Exception extends IOException {
        private static final long serialVersionUID = 1L;

        /** Le résultat complet de l'exécution (non sérialisé). */
        public final transient Result result;

        Aapt2Exception(String message, Result result) {
            super(message);
            this.result = result;
        }
    }

    private final Path aapt2;
    private final long timeoutSeconds;

    /**
     * @param aapt2           chemin du binaire aapt2
     * @param timeoutSeconds  plafond d'exécution par invocation
     */
    public Aapt2(Path aapt2, long timeoutSeconds) {
        this.aapt2 = Objects.requireNonNull(aapt2, "aapt2");
        if (!Files.isRegularFile(aapt2)) {
            throw new IllegalArgumentException("aapt2 not found: " + aapt2);
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Compile un fichier de ressource unique en fichier plat.
     *
     * @param resource fichier source (par ex. {@code values/strings.xml})
     * @param outDir   répertoire de sortie (existant)
     * @return le résultat d'exécution
     * @throws IOException   si le lancement échoue
     * @throws Aapt2Exception si aapt2 échoue
     */
    public Result compileFile(Path resource, Path outDir) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("compile");
        args.add("-o");
        args.add(outDir.toString());
        args.add(resource.toString());
        return run(args);
    }

    /**
     * Compile tout un arbre de ressources ({@code --dir}) en un zip
     * de fichiers plats.
     *
     * @param resDir  racine des ressources
     * @param outFile zip de sortie (fichier plat unique)
     * @return le résultat d'exécution
     * @throws IOException   si le lancement échoue
     * @throws Aapt2Exception si aapt2 échoue
     */
    public Result compileDir(Path resDir, Path outFile) throws IOException {
        return run(List.of("compile", "--dir", resDir.toString(),
                "-o", outFile.toString()));
    }

    /** Options d'un lien aapt2. */
    public static final class LinkOptions {
        /** Manifeste source. */
        public final Path manifest;
        /** Fichiers plats ou zips de compilation. */
        public final List<Path> compiled;
        /** Jar de plateforme pour la résolution des attributs. */
        public final Path platformJar;
        /** Sortie : archive liée (apk vide) ou répertoire proto. */
        public final Path output;
        /** Identifiant d'application (-I android.jar --package-id). */
        public final String packageName;
        /** Version cible de l'identifiant de ressource (0x7f). */
        public final int packageId;
        /** Émettre les fichiers au format proto (bundle). */
        public final boolean protoFormat;
        /** Générer les sources R.java dans ce répertoire. */
        public final Path javaOut;
        /** Émettre la table texte des symboles. */
        public final Path rTxtOut;
        /** Répertoire des actifs à inclure. */
        public final Path assetsDir;
        /** Auto-add-overlay pour les ressources superposées. */
        public final boolean autoAddOverlay;

        LinkOptions(Builder b) {
            this.manifest = b.manifest;
            this.compiled = List.copyOf(b.compiled);
            this.platformJar = b.platformJar;
            this.output = b.output;
            this.packageName = b.packageName;
            this.packageId = b.packageId;
            this.protoFormat = b.protoFormat;
            this.javaOut = b.javaOut;
            this.rTxtOut = b.rTxtOut;
            this.assetsDir = b.assetsDir;
            this.autoAddOverlay = b.autoAddOverlay;
        }

        /** Bâtisseur d'options de lien. */
        public static Builder builder() {
            return new Builder();
        }

        /** Bâtisseur d'options de lien. */
        public static final class Builder {
            private Path manifest;
            private final List<Path> compiled = new ArrayList<>();
            private Path platformJar;
            private Path output;
            private String packageName;
            private int packageId = 0x7f;
            private boolean protoFormat;
            private Path javaOut;
            private Path rTxtOut;
            private Path assetsDir;
            private boolean autoAddOverlay;

            /** @param manifest manifeste source
             * @return ce bâtisseur */
            public Builder manifest(Path manifest) {
                this.manifest = manifest;
                return this;
            }

            /** @param compiled archive plate issue de la compilation
             * @return ce bâtisseur */
            public Builder compiled(Path compiled) {
                this.compiled.add(compiled);
                return this;
            }

            /** @param platformJar android.jar de la plateforme
             * @return ce bâtisseur */
            public Builder platformJar(Path platformJar) {
                this.platformJar = platformJar;
                return this;
            }

            /** @param output sortie de lien
             * @return ce bâtisseur */
            public Builder output(Path output) {
                this.output = output;
                return this;
            }

            /** @param packageName nom de paquet forcé
             * @return ce bâtisseur */
            public Builder packageName(String packageName) {
                this.packageName = packageName;
                return this;
            }

            /** @param packageId identifiant de paquet de ressources
             * @return ce bâtisseur */
            public Builder packageId(int packageId) {
                this.packageId = packageId;
                return this;
            }

            /** @param protoFormat émission au format proto
             * @return ce bâtisseur */
            public Builder protoFormat(boolean protoFormat) {
                this.protoFormat = protoFormat;
                return this;
            }

            /** @param javaOut répertoire des sources R
             * @return ce bâtisseur */
            public Builder javaOut(Path javaOut) {
                this.javaOut = javaOut;
                return this;
            }

            /** @param rTxtOut fichier de table de symboles
             * @return ce bâtisseur */
            public Builder rTxtOut(Path rTxtOut) {
                this.rTxtOut = rTxtOut;
                return this;
            }

            /** @param assetsDir répertoire d'actifs
             * @return ce bâtisseur */
            public Builder assetsDir(Path assetsDir) {
                this.assetsDir = assetsDir;
                return this;
            }

            /** @param autoAddOverlay superposition automatique
             * @return ce bâtisseur */
            public Builder autoAddOverlay(boolean autoAddOverlay) {
                this.autoAddOverlay = autoAddOverlay;
                return this;
            }

            /** @return les options de lien */
            public LinkOptions build() {
                Objects.requireNonNull(manifest, "manifest");
                Objects.requireNonNull(output, "output");
                Objects.requireNonNull(platformJar, "platformJar");
                return new LinkOptions(this);
            }
        }
    }

    /**
     * Lie manifeste et fichiers plats compilés.
     *
     * @param options options de lien
     * @return le résultat d'exécution
     * @throws IOException   si le lancement échoue
     * @throws Aapt2Exception si aapt2 échoue
     */
    public Result link(LinkOptions options) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("link");
        args.add("-o");
        args.add(options.output.toString());
        args.add("--manifest");
        args.add(options.manifest.toString());
        args.add("-I");
        args.add(options.platformJar.toString());
        if (options.packageName != null) {
            args.add("--rename-manifest-package");
            args.add(options.packageName);
        }
        if (options.packageId != 0x7f) {
            args.add("--package-id");
            args.add("0x" + Integer.toHexString(options.packageId));
        }
        if (options.protoFormat) {
            args.add("--proto-format");
        }
        if (options.javaOut != null) {
            args.add("--java");
            args.add(options.javaOut.toString());
        }
        if (options.rTxtOut != null) {
            args.add("--output-text-symbols");
            args.add(options.rTxtOut.toString());
        }
        if (options.assetsDir != null) {
            args.add("-A");
            args.add(options.assetsDir.toString());
        }
        if (options.autoAddOverlay) {
            args.add("--auto-add-overlay");
        }
        for (Path compiled : options.compiled) {
            args.add(compiled.toString());
        }
        return run(args);
    }

    /** @return la version d'aapt2 (sans lancer le binaire) */
    public String describe() {
        return "aapt2 " + aapt2.getFileName();
    }

    private Result run(List<String> args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(aapt2.toString());
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Process process = builder.start();
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread tout = reader(process.getInputStream(), out);
        Thread terr = reader(process.getErrorStream(), err);
        int code;
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("aapt2 timed out after "
                        + timeoutSeconds + " s : " + command);
            }
            code = process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("aapt2 interrupted", e);
        }
        try {
            tout.join(TimeUnit.SECONDS.toMillis(5));
            terr.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Result result = new Result(code, out.toString(), err.toString());
        if (code != 0) {
            throw new Aapt2Exception("aapt2 failed (exit " + code
                    + ") : " + String.join(" ", args), result);
        }
        return result;
    }

    private static Thread reader(java.io.InputStream in,
                                 StringBuilder sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append('\n');
                }
            } catch (IOException e) {
                // flux fermé par la mort du processus
            }
        }, "aapt2-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
