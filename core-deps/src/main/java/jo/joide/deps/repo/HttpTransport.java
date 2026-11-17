package jo.joide.deps.repo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.HttpsURLConnection;
import jo.joide.deps.Checksums;
import jo.joide.platform.CancelToken;

/**
 * Transport HTTPS maison sur {@link HttpsURLConnection} (ADR-031) :
 * TLS vérifié et vérification d'hôte actifs — jamais désactivés ;
 * HTTP au clair refusé d'entrée ; redirections suivies à la main,
 * sans rétrogradation vers HTTP, plafonnées à cinq ; taille des
 * réponses plafonnée ; annulation coopérative entre les lectures.
 */
public final class HttpTransport implements Transport {

    /** Réglages du transport. Immuable. */
    public static final class Settings {
        public final int connectTimeoutMs;
        public final int readTimeoutMs;
        public final long maxBytes;
        public final int maxRedirects;
        public final String userAgent;

        public Settings(int connectTimeoutMs, int readTimeoutMs,
                        long maxBytes, int maxRedirects, String userAgent) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.maxBytes = maxBytes;
            this.maxRedirects = maxRedirects;
            this.userAgent = userAgent;
        }

        /** @return les réglages par défaut (15 s, 30 s, 512 Mio, 5) */
        public static Settings defaults() {
            return new Settings(15000, 30000, 512L * 1024 * 1024, 5,
                    "JoIDE/0.4 (+https://github.com/jjoblab/joide)");
        }
    }

    private final Settings settings;

    public HttpTransport() {
        this(Settings.defaults());
    }

    public HttpTransport(Settings settings) {
        this.settings = settings;
    }

    @Override
    public TransportResponse get(String url, CancelToken token) {
        String current = url;
        for (int hop = 0; hop <= settings.maxRedirects; hop++) {
            if (token != null && token.isCancelled()) {
                return TransportResponse.error("annulé avant la requête");
            }
            if (!current.startsWith("https://")) {
                return TransportResponse.error(
                        "transport non https refuse : " + current);
            }
            HttpsURLConnection connection;
            try {
                connection = (HttpsURLConnection) URI.create(current)
                        .toURL().openConnection();
            } catch (IOException | RuntimeException e) {
                return TransportResponse.error(
                        "ouverture impossible : " + current + " : "
                                + e.getMessage());
            }
            connection.setConnectTimeout(settings.connectTimeoutMs);
            connection.setReadTimeout(settings.readTimeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", settings.userAgent);
            connection.setRequestProperty("Accept", "*/*");
            try {
                connection.connect();
                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        return TransportResponse.error(
                                "redirection " + status + " sans Location : "
                                        + current);
                    }
                    String next = resolve(current, location);
                    if (next == null) {
                        return TransportResponse.error(
                                "redirection " + status + " vers URL invalide : "
                                        + location);
                    }
                    if (!next.startsWith("https://")) {
                        return TransportResponse.error(
                                "retrogradation https vers http refusee : "
                                        + location);
                    }
                    current = next;
                    continue;
                }
                if (status == 404) {
                    return TransportResponse.miss();
                }
                if (status != 200) {
                    return TransportResponse.error(
                            "statut " + status + " : " + current);
                }
                long declared = connection.getContentLengthLong();
                if (declared > settings.maxBytes) {
                    return TransportResponse.error(
                            "reponse trop grande (" + declared + " o) : "
                                    + current);
                }
                int hint = declared > 0 ? (int) Math.min(declared,
                        Integer.MAX_VALUE - 8) : 8192;
                try (InputStream in = connection.getInputStream()) {
                    byte[] body = cappedRead(in, hint);
                    return TransportResponse.hit(body);
                }
            } catch (IOException e) {
                return TransportResponse.error(
                        "echec reseau : " + current + " : " + e.getMessage());
            } finally {
                connection.disconnect();
            }
        }
        return TransportResponse.error(
                "trop de redirections (max " + settings.maxRedirects + ")");
    }

    private byte[] cappedRead(InputStream in, int hint) throws IOException {
        byte[] body = Checksums.readAll(in, hint);
        if (body.length > settings.maxBytes) {
            throw new IOException("corps trop grand (" + body.length + " o)");
        }
        return body;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303
                || status == 307 || status == 308;
    }

    /** Résout un en-tête Location contre l'URL courante. */
    private static String resolve(String base, String location) {
        try {
            URI uri = new URI(location);
            if (uri.isAbsolute()) {
                return uri.toString();
            }
            return URI.create(base).resolve(uri).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Les redirections et le refus du clair sont pures et testables
     * sans réseau : ce prédicat expose la politique de redirection.
     *
     * @param status code HTTP
     * @return vrai si le code est une redirection suivie
     */
    static boolean redirectFollowed(int status) {
        return isRedirect(status);
    }
}
