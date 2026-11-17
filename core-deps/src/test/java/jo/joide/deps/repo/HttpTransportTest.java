package jo.joide.deps.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import jo.joide.platform.CancelToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Transport HTTPS : le clair est refusé d'entrée, la politique de
 * redirection est poinçonnée, et un serveur TLS local au certificat
 * auto-signé est rejeté — la preuve que la vérification est active.
 */
class HttpTransportTest {

    @TempDir
    Path tmp;

    @Test
    void plainHttpIsRefusedUpFront() {
        HttpTransport transport = new HttpTransport();
        TransportResponse response = transport.get("http://example.com/x",
                CancelToken.NEVER);
        assertEquals(TransportResponse.Kind.ERROR, response.kind);
        assertTrue(response.message.contains("refuse"));
    }

    @Test
    void redirectPolicyCoversTheFollowedCodes() {
        assertTrue(HttpTransport.redirectFollowed(301));
        assertTrue(HttpTransport.redirectFollowed(302));
        assertTrue(HttpTransport.redirectFollowed(303));
        assertTrue(HttpTransport.redirectFollowed(307));
        assertTrue(HttpTransport.redirectFollowed(308));
        assertTrue(!HttpTransport.redirectFollowed(200));
        assertTrue(!HttpTransport.redirectFollowed(404));
        assertTrue(!HttpTransport.redirectFollowed(500));
    }

    @Test
    void selfSignedCertificateIsRejected() throws Exception {
        // Un magasin auto-signé généré par keytool (test seulement ;
        // aucun certificat de production n'est impliqué).
        Path store = tmp.resolve("test.p12");
        Process keytool = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/keytool",
                "-genkeypair", "-alias", "test",
                "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost",
                "-validity", "1",
                "-keystore", store.toString(),
                "-storetype", "PKCS12",
                "-storepass", "changeit")
                .redirectErrorStream(true)
                .start();
        assertEquals(0, keytool.waitFor(), () -> {
            try {
                return new String(keytool.getInputStream().readAllBytes());
            } catch (java.io.IOException e) {
                return "keytool: " + e.getMessage();
            }
        });

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(store)) {
            ks.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(
                new java.net.InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ctx));
        server.createContext("/", exchange -> {
            byte[] body = "contenu".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            String url = "https://localhost:" + server.getAddress().getPort()
                    + "/fichier.jar";
            HttpTransport transport = new HttpTransport();
            TransportResponse response = transport.get(url, CancelToken.NEVER);
            // Le certificat auto-signé doit être refusé par la
            // vérification TLS par défaut : jamais désactivée ici.
            assertEquals(TransportResponse.Kind.ERROR, response.kind,
                    () -> "réponse inattendue : " + response);
            assertTrue(response.message.contains("echec reseau")
                            || response.message.contains("TLS")
                            || response.message.contains("certificate")
                            || response.message.contains("PKIX"),
                    () -> "message inattendu : " + response.message);
        } finally {
            server.stop(0);
        }
    }
}
