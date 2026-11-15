package jo.joide.deps.repo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import jo.joide.deps.Checksums;
import jo.joide.platform.CancelToken;

/**
 * Transport de test : sert les fichiers d'un répertoire de fixture
 * comme un dépôt distant, en comptant les requêtes, en retenant la
 * concurrence maximale, avec délai et corruption injectables.
 * Aucune socket : tout est lu en local.
 */
public final class FakeTransport implements Transport {

    public final List<String> calls = new CopyOnWriteArrayList<>();
    public final AtomicInteger concurrent = new AtomicInteger();
    public final AtomicInteger maxConcurrent = new AtomicInteger();

    private final Path base;
    private final String baseUrl;
    private volatile boolean failAll;
    private volatile String failure;
    private volatile long delayMs;
    private final java.util.Map<String, byte[]> overrides =
            new java.util.concurrent.ConcurrentHashMap<>();

    public FakeTransport(Path base, String baseUrl) {
        this.base = base;
        this.baseUrl = baseUrl;
    }

    /** @param message raison factice @return this, en échec systématique */
    public FakeTransport failAll(String message) {
        this.failAll = true;
        this.failure = message;
        return this;
    }

    /** @param ms délai par requête @return this */
    public FakeTransport delay(long ms) {
        this.delayMs = ms;
        return this;
    }

    /**
     * @param url    URL demandée
     * @param bytes  contenu servi à la place
     * @return this
     */
    public FakeTransport override(String url, byte[] bytes) {
        overrides.put(url, bytes);
        return this;
    }

    @Override
    public TransportResponse get(String url, CancelToken token) {
        calls.add(url);
        int now = concurrent.incrementAndGet();
        maxConcurrent.accumulateAndGet(now, Math::max);
        try {
            if (failAll) {
                return TransportResponse.error(failure);
            }
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return TransportResponse.error("interrompu");
                }
            }
            byte[] override = overrides.get(url);
            if (override != null) {
                return TransportResponse.hit(override);
            }
            if (!url.startsWith(baseUrl + "/")) {
                return TransportResponse.error("hors base : " + url);
            }
            String relPath = url.substring(baseUrl.length() + 1);
            Path file = base.resolve(relPath);
            if (!Files.isRegularFile(file)) {
                return TransportResponse.miss();
            }
            return TransportResponse.hit(Files.readAllBytes(file));
        } catch (IOException e) {
            return TransportResponse.error("lecture : " + e.getMessage());
        } finally {
            concurrent.decrementAndGet();
        }
    }

    /** @return le nombre de requêtes servies */
    public int callCount() {
        return calls.size();
    }

    /** @param content contenu visé @return le texte d'un .sha256 */
    public static String sha256Text(byte[] content) {
        return Checksums.sha256Hex(content) + "\n";
    }

    /** @param text texte @return les octets UTF-8 */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
