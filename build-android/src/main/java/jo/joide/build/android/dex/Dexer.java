package jo.joide.build.android.dex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Service de dexage : conversion de classes ou jars Java en fichiers
 * dex, fusion de dex, et rétrécissement R8. Deux réalisations
 * existent : la délégation en processus (jar de D8/R8 chargé par le
 * daemon, compilateurs chauds) et le fork à gros tas (machine virtuel
 * fille dédiée aux grosses fusions). L'interface vit dans le source
 * set principal ; les implémentations qui référencent l'outillage
 * Google vivent dans le source set tools et s'enregistrent par
 * ServiceLoader.
 */
public interface Dexer {

    /** Une demande de dexage d'un lot d'entrées. */
    final class DexRequest {
        /** Fichiers de classes ou jars à convertir. */
        public final List<Path> program;
        /** Classpath complet pour la résolution (android.jar…). */
        public final List<Path> classpath;
        /** Niveau d'API minimum (désucrage). */
        public final int minApi;
        /** Répertoire de sortie (existant). */
        public final Path outputDir;

        /**
         * @param program    entrées à convertir
         * @param classpath  classpath de résolution
         * @param minApi     niveau d'API minimum
         * @param outputDir  répertoire de sortie existant
         */
        public DexRequest(List<Path> program, List<Path> classpath,
                          int minApi, Path outputDir) {
            this.program = List.copyOf(program);
            this.classpath = List.copyOf(classpath);
            this.minApi = minApi;
            this.outputDir = outputDir;
        }
    }

    /** Une demande de fusion de dex. */
    final class MergeRequest {
        /** Fichiers dex à fusionner. */
        public final List<Path> dexFiles;
        /** Classpath complet (types manquants tolérés selon contexte). */
        public final List<Path> classpath;
        /** Niveau d'API minimum. */
        public final int minApi;
        /** Répertoire de sortie (existant). */
        public final Path outputDir;

        /**
         * @param dexFiles  dex à fusionner
         * @param classpath classpath de résolution
         * @param minApi    niveau d'API minimum
         * @param outputDir répertoire de sortie existant
         */
        public MergeRequest(List<Path> dexFiles, List<Path> classpath,
                            int minApi, Path outputDir) {
            this.dexFiles = List.copyOf(dexFiles);
            this.classpath = List.copyOf(classpath);
            this.minApi = minApi;
            this.outputDir = outputDir;
        }
    }

    /** Une demande de rétrécissement R8. */
    final class ShrinkRequest {
        /** Fichiers de classes ou jars à rétrécir. */
        public final List<Path> program;
        /** Classpath complet (bibliothèques jamais rétrécies). */
        public final List<Path> classpath;
        /** Niveau d'API minimum. */
        public final int minApi;
        /** Règles de conservation, une par ligne. */
        public final List<String> keepRules;
        /** Répertoire de sortie (existant). */
        public final Path outputDir;
        /** Chemin du fichier de mapping de renommage, ou {@code null}. */
        public final Path mappingOut;

        /**
         * @param program   entrées à rétrécir
         * @param classpath bibliothèques
         * @param minApi    niveau d'API minimum
         * @param keepRules règles de conservation
         * @param outputDir répertoire de sortie existant
         * @param mappingOut sortie du mapping, ou {@code null}
         */
        public ShrinkRequest(List<Path> program, List<Path> classpath,
                             int minApi, List<String> keepRules,
                             Path outputDir, Path mappingOut) {
            this.program = List.copyOf(program);
            this.classpath = List.copyOf(classpath);
            this.minApi = minApi;
            this.keepRules = List.copyOf(keepRules);
            this.outputDir = outputDir;
            this.mappingOut = mappingOut;
        }
    }

    /**
     * Dexage d'un lot d'entrées. Le résultat contient un ou plusieurs
     * fichiers dex dans le répertoire de sortie.
     *
     * @param request demande
     * @throws IOException si la conversion échoue (diagnostics dans
     *                     l'exception)
     */
    void dex(DexRequest request) throws IOException;

    /**
     * Fusion de fichiers dex en un ou plusieurs dex de sortie.
     *
     * @param request demande de fusion
     * @throws IOException si la fusion échoue
     */
    void merge(MergeRequest request) throws IOException;

    /**
     * Rétrécissement R8.
     *
     * @param request demande de rétrécissement
     * @throws IOException si R8 échoue
     */
    void shrink(ShrinkRequest request) throws IOException;

    /** @return la version de l'outil (pour l'empreinte des tâches) */
    String toolVersion();
}
