package jo.joide.deps.repo;

import java.util.Objects;

/**
 * La réponse d'un transport à une requête GET. Trois états :
 * trouvé (avec octets), introuvable (404 ou fichier absent), erreur
 * (réseau, protocole, dépassement). Immuable.
 */
public final class TransportResponse {

    /** L'état de la réponse. */
    public enum Kind {
        HIT, MISS, ERROR
    }

    /** @return une réponse trouvée */
    public static TransportResponse hit(byte[] bytes) {
        return new TransportResponse(Kind.HIT, bytes, null, null);
    }

    /** @return une réponse introuvable */
    public static TransportResponse miss() {
        return new TransportResponse(Kind.MISS, null, null, null);
    }

    /** @param message raison de l'échec @return une réponse en erreur */
    public static TransportResponse error(String message) {
        return new TransportResponse(Kind.ERROR, null, null,
                Objects.requireNonNull(message, "message"));
    }

    public final Kind kind;
    public final byte[] bytes;
    public final String url;
    public final String message;

    private TransportResponse(Kind kind, byte[] bytes, String url,
                              String message) {
        this.kind = kind;
        this.bytes = bytes;
        this.url = url;
        this.message = message;
    }

    /** @return vrai si la ressource a été trouvée */
    public boolean isHit() {
        return kind == Kind.HIT;
    }

    /** @return vrai si introuvable (état normal pour un checksum absent) */
    public boolean isMiss() {
        return kind == Kind.MISS;
    }

    @Override
    public String toString() {
        switch (kind) {
            case HIT:
                return "hit(" + (bytes != null ? bytes.length : 0) + " o)";
            case MISS:
                return "miss";
            case ERROR:
                return "error: " + message;
            default:
                return kind.name();
        }
    }
}
