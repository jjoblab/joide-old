package jo.joide.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * L'historique de conversation vu par le noyau : append-only (ADR-É9,
 * leçon 3), les décisions de compaction sont permanentes, l'usage
 * cumule les requêtes du tour. La double moitié — transcript vu de
 * l'utilisateur et historique modèle — vit dans
 * {@link SessionsAgent} ; ici est la partie que le fournisseur
 * reçoit.
 */
public final class HistoriqueAgent {

    private final List<MessageAgent> messages = new ArrayList<>();
    private final Set<String> compactes = new HashSet<>();
    private UsageLlm usageTour = UsageLlm.zero();

    /** @param messages l'état initial (chargé d'une session) */
    public HistoriqueAgent(List<MessageAgent> messages) {
        this.messages.addAll(messages);
    }

    /** Un historique vide. */
    public static HistoriqueAgent vide() {
        return new HistoriqueAgent(List.of());
    }

    /** Ajoute un message (jamais de réécriture en amont). */
    public synchronized void ajouter(MessageAgent message) {
        messages.add(message);
    }

    /** La vue courante (copie défensive). */
    public synchronized List<MessageAgent> messages() {
        return List.copyOf(messages);
    }

    /** Les identifiants d'appels déjà tronqués (décisions permanentes). */
    public synchronized Set<String> compactes() {
        return Set.copyOf(compactes);
    }

    /** Remplace l'historique après une passe de compaction (la seule réécriture sanctionnée). */
    synchronized void remplacer(List<MessageAgent> compacte,
                                Set<String> decisions) {
        messages.clear();
        messages.addAll(compacte);
        compactes.addAll(decisions);
    }

    /** Cumule l'usage d'une requête (un tour somme ses requêtes). */
    synchronized void ajouterUsage(UsageLlm usage) {
        usageTour = usageTour.plus(usage);
    }

    /** L'usage cumulé du tour. */
    public synchronized UsageLlm usage() {
        return usageTour;
    }

    /** Les caractères de l'historique courant. */
    public synchronized int caracteres() {
        return CompacteurHistorique.caracteres(messages);
    }
}
