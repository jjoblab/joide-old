package jo.joide.app.android.logcat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import jo.joide.app.android.atelier.EtatLogcat;

/**
 * ADR-084 — le tampon du relais de journal. Le provider (fil Binder)
 * y dépose les lignes reçues de l'application de l'utilisateur
 * (brique jo.joide:relais-logcat, permission signature) ; l'atelier
 * lit sur le fil UI et alimente {@link EtatLogcat}. Thread-safe, le
 * plafond est celui de l'état (400, annoncé).
 */
public final class FluxLogcat {

    /** Le maximum de lignes retenues entre deux lectures de l'UI. */
    public static final int PLAFOND_TAMPON = 400;

    /** Une ligne reçue du relais : niveau, tag, message. */
    public record LigneRelais(EtatLogcat.Niveau niveau, String tag,
                              String message) {
    }

    /** Les lignes en attente de lecture par l'UI. */
    private final Deque<LigneRelais> tampon = new ArrayDeque<>();
    /** L'auditeur UI (notifié après chaque dépôt, sur SON fil). */
    private volatile Auditeur auditeur;

    /** L'auditeur du flux (la section logcat de l'atelier). */
    public interface Auditeur {
        /** Des lignes sont arrivées (appelé sur le fil UI par
         *  l'abonné — le dépôt reste sur le fil Binder). */
        void surLignes();
    }

    FluxLogcat() {
    }

    private static final FluxLogcat INSTANCE = new FluxLogcat();

    /** Le tampon unique du processus (le provider et l'atelier
     *  partagent le même). */
    public static FluxLogcat tampon() {
        return INSTANCE;
    }

    /** Branche l'auditeur UI (l'atelier se réabonne à chaque montage). */
    public void poserAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /**
     * Dépose les lignes d'un lot (fil Binder du provider) : les
     * chaînes attendues sont « N|Tag|Message » — les lignes mal
     * formées sont SAUTÉES (le logcat de l'utilisateur peut contenir
     * n'importe quoi ; le relais ne bloque jamais dessus). Les plus
     * anciennes sortent au plafond ; l'auditeur est notifié à chaque
     * dépôt.
     */
    public synchronized void deposer(List<String> brutes) {
        if (brutes == null) {
            return;
        }
        for (String brute : brutes) {
            LigneRelais ligne = parser(brute);
            if (ligne == null) {
                continue;
            }
            tampon.addLast(ligne);
            while (tampon.size() > PLAFOND_TAMPON) {
                tampon.removeFirst();
            }
        }
        Auditeur ecouteur = auditeur;
        if (ecouteur != null && !tampon.isEmpty()) {
            ecouteur.surLignes();
        }
    }

    /**
     * Le protocole du relais : « N|Tag|Message » — N ∈ {D, I, W, E}.
     * @return la ligne analysée, null si mal formée.
     */
    public static LigneRelais parser(String brute) {
        if (brute == null) {
            return null;
        }
        int premier = brute.indexOf('|');
        if (premier != 1 || brute.length() < 3) {
            return null;
        }
        EtatLogcat.Niveau niveau = switch (brute.charAt(0)) {
            case 'D' -> EtatLogcat.Niveau.D;
            case 'I' -> EtatLogcat.Niveau.I;
            case 'W' -> EtatLogcat.Niveau.W;
            case 'E' -> EtatLogcat.Niveau.E;
            default -> null;
        };
        if (niveau == null) {
            return null;
        }
        int second = brute.indexOf('|', 2);
        if (second < 0) {
            return null;
        }
        return new LigneRelais(niveau, brute.substring(2, second),
                brute.substring(second + 1));
    }

    /** @return les lignes en attente (les plus récentes en dernier)
     *  et VIDE le tampon. */
    public synchronized List<LigneRelais> lire() {
        List<LigneRelais> lignes = new ArrayList<>(tampon);
        tampon.clear();
        return lignes;
    }

    /** @return le nombre de lignes en attente. */
    public synchronized int enAttente() {
        return tampon.size();
    }
}
