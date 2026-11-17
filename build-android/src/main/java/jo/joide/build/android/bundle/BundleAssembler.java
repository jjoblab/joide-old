package jo.joide.build.android.bundle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service d'assemblage de bundle (AAB) : construit la charge du
 * module de base (manifeste proto, table de ressources proto, dex,
 * actifs, racine, bibliothèques natives) puis l'archive finale. Les
 * implémentations qui parlent à bundletool vivent dans le source set
 * tools ; l'interface reste hors de toute référence tierce.
 */
public interface BundleAssembler {

    /** Une charge de module de base. */
    final class BaseModule {
        /** Manifeste binaire proto (issu du lien aapt2 --proto-format). */
        public final Path manifestProto;
        /** Table de ressources proto (resources.pb). */
        public final Path resourcesProto;
        /** Ressources compilées proto (répertoire res/). */
        public final List<Path> protoResources;
        /** Fichiers dex, ordre du bucket principal d'abord. */
        public final List<Path> dexFiles;
        /** Actifs, chemin d'archive vers fichier. */
        public final Map<String, Path> assets;
        /** Fichiers racine (Java resources brutes). */
        public final Map<String, Path> rootFiles;
        /** Bibliothèques natives, chemin d'archive vers fichier. */
        public final Map<String, Path> nativeLibs;

        /**
         * @param manifestProto  manifeste proto
         * @param resourcesProto table de ressources proto
         * @param protoResources ressources proto
         * @param dexFiles       dex du module
         * @param assets         actifs
         * @param rootFiles      fichiers racine
         * @param nativeLibs     bibliothèques natives
         */
        public BaseModule(Path manifestProto, Path resourcesProto,
                          List<Path> protoResources, List<Path> dexFiles,
                          Map<String, Path> assets,
                          Map<String, Path> rootFiles,
                          Map<String, Path> nativeLibs) {
            this.manifestProto = manifestProto;
            this.resourcesProto = resourcesProto;
            this.protoResources = List.copyOf(protoResources);
            this.dexFiles = List.copyOf(dexFiles);
            this.assets = Map.copyOf(assets);
            this.rootFiles = Map.copyOf(rootFiles);
            this.nativeLibs = Map.copyOf(nativeLibs);
        }
    }

    /**
     * Assemble le bundle.
     *
     * @param module charge du module de base
     * @param output fichier .aab de sortie
     * @throws IOException si l'assemblage échoue
     */
    void assemble(BaseModule module, Path output) throws IOException;

    /** @return la version de l'outil (pour l'empreinte des tâches) */
    String toolVersion();
}
