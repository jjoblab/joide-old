package jo.joide.deps.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jo.joide.platform.CancelToken;

/**
 * Un dépôt Maven : un identifiant (pour le lockfile et les rapports)
 * et un accès par chemin relatif à la racine du dépôt (disposition
 * Maven 2). L'ordre d'essai des dépôts est décidé par le résolveur.
 */
public interface Repository {

    /** @return l'identifiant du dépôt (stable, pour « repo= » du lockfile) */
    String id();

    /** @return vrai si ce dépôt peut émettre des requêtes réseau */
    boolean remote();

    /**
     * @param relPath chemin relatif à la racine du dépôt
     * @return l'URL ou le chemin canonique de la ressource (rapports)
     */
    String describe(String relPath);

    /**
     * @param relPath chemin relatif à la racine du dépôt
     * @param token   jeton d'annulation (peut être {@code null})
     * @return la réponse du dépôt
     */
    TransportResponse fetch(String relPath, CancelToken token);

    /** Dépôt local : lectures directes sur disque, aucune requête. */
    final class Local implements Repository {
        private final String id;
        private final Path base;

        public Local(String id, Path base) {
            this.id = id;
            this.base = base;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean remote() {
            return false;
        }

        @Override
        public String describe(String relPath) {
            return base.resolve(relPath).toString();
        }

        @Override
        public TransportResponse fetch(String relPath, CancelToken token) {
            if (token != null && token.isCancelled()) {
                return TransportResponse.error("annulé avant la lecture");
            }
            Path file = base.resolve(relPath);
            if (!Files.isRegularFile(file)) {
                return TransportResponse.miss();
            }
            try {
                return TransportResponse.hit(Files.readAllBytes(file));
            } catch (IOException e) {
                return TransportResponse.error(
                        "lecture impossible : " + file + " : " + e.getMessage());
            }
        }
    }

    /** Dépôt distant : transport https sur une base URL. */
    final class Remote implements Repository {
        private final String id;
        private final String baseUrl;
        private final Transport transport;

        public Remote(String id, String baseUrl, Transport transport) {
            this.id = id;
            this.baseUrl = baseUrl.endsWith("/")
                    ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.transport = transport;
        }

        @Override
        public String id() {
            return id;
        }

        /** @return la base du dépôt, sans barre finale */
        public String baseUrl() {
            return baseUrl;
        }

        @Override
        public boolean remote() {
            return true;
        }

        @Override
        public String describe(String relPath) {
            return url(relPath);
        }

        @Override
        public TransportResponse fetch(String relPath, CancelToken token) {
            return transport.get(url(relPath), token);
        }

        private String url(String relPath) {
            return baseUrl + "/" + relPath;
        }
    }
}
