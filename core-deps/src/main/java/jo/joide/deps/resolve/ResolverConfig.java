package jo.joide.deps.resolve;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.Repository;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;

/**
 * La configuration d'une résolution : dépôts dans leur ordre
 * d'essai, cache partagé, chemin du lockfile, mode hors-ligne strict,
 * plafond de téléchargements parallèles, jeton d'annulation.
 * Immuable.
 */
public final class ResolverConfig {

    public final List<Repository> repositories;
    public final DependencyCache cache;
    public final Path lockfilePath;
    public final boolean offlineStrict;
    public final int maxParallelDownloads;
    public final CancelToken token;

    private ResolverConfig(List<Repository> repositories, DependencyCache cache,
                           Path lockfilePath, boolean offlineStrict,
                           int maxParallelDownloads, CancelToken token) {
        this.repositories = List.copyOf(repositories);
        this.cache = Objects.requireNonNull(cache, "cache");
        this.lockfilePath = lockfilePath;
        this.offlineStrict = offlineStrict;
        this.maxParallelDownloads = maxParallelDownloads;
        this.token = token != null ? token : CancelToken.NEVER;
    }

    /** Bâtisseur de configuration. */
    public static final class Builder {
        private final List<Repository> repositories = new ArrayList<>();
        private DependencyCache cache;
        private Path lockfilePath;
        private boolean offlineStrict;
        private int maxParallelDownloads =
                Math.max(2, Math.min(4, WorkerPools.ioParallelism()));
        private CancelToken token;

        /** @param repository dépôt à essayer (l'ordre d'ajout est l'ordre d'essai)
         *  @return le bâtisseur */
        public Builder repository(Repository repository) {
            repositories.add(Objects.requireNonNull(repository, "repo"));
            return this;
        }

        /** @param cache cache disque partagé @return le bâtisseur */
        public Builder cache(DependencyCache cache) {
            this.cache = cache;
            return this;
        }

        /** @param lockfilePath chemin du joide.lock (null : sans lockfile)
         *  @return le bâtisseur */
        public Builder lockfile(Path lockfilePath) {
            this.lockfilePath = lockfilePath;
            return this;
        }

        /** @return le bâtisseur, en mode hors-ligne strict */
        public Builder offlineStrict() {
            this.offlineStrict = true;
            return this;
        }

        /** @param max plafond de téléchargements simultanés
         *  @return le bâtisseur */
        public Builder maxParallelDownloads(int max) {
            this.maxParallelDownloads = Math.max(1, max);
            return this;
        }

        /** @param token jeton d'annulation @return le bâtisseur */
        public Builder token(CancelToken token) {
            this.token = token;
            return this;
        }

        /** @return la configuration */
        public ResolverConfig build() {
            if (repositories.isEmpty()) {
                throw new IllegalStateException("no repository configured");
            }
            if (cache == null) {
                throw new IllegalStateException("cache not configured");
            }
            return new ResolverConfig(repositories, cache, lockfilePath,
                    offlineStrict, maxParallelDownloads, token);
        }
    }
}
