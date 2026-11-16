package jo.joide.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La réparation d'historique (ADR-É9, leçon 4 — le
 * {@code pairToolResults} de CodeAssist) : une conversation
 * interrompue reste toujours VALIDE pour le fournisseur. Un appel
 * d'outil resté sans réponse (tour interrompu) reçoit un résultat
 * « interrompu » inséré au bon endroit ; un résultat orphelin (sans
 * appel) est retiré. Sans cela, la conversation sauvegardée est
 * refusée par le fournisseur à chaque message suivant.
 */
public final class ReparationHistorique {

    private ReparationHistorique() {
    }

    /**
     * Répare l'historique.
     *
     * @param historique l'historique tel que chargé (cassé possible)
     * @return l'historique réparé et vrai si quelque chose a changé
     */
    public static Reparation reparer(List<MessageAgent> historique) {
        boolean change = false;
        Set<String> appels = new HashSet<>();
        for (MessageAgent message : historique) {
            for (PartieContenu partie : message.parties) {
                if (partie.genre == PartieContenu.Genre.APPEL_OUTIL) {
                    appels.add(partie.idAppel);
                }
            }
        }
        Set<String> repondu = new HashSet<>();
        List<MessageAgent> resultat = new ArrayList<>();
        for (MessageAgent message : historique) {
            if (message.role == MessageAgent.Role.ASSISTANT) {
                resultat.add(message);
                continue;
            }
            // message utilisateur : les résultats orphelins partent,
            // puis les appels non répondus de TOUT l'historique
            // reçoivent leur résultat « interrompu » à la fin
            List<PartieContenu> gardees = new ArrayList<>();
            for (PartieContenu partie : message.parties) {
                if (partie.genre == PartieContenu.Genre.RESULTAT_OUTIL) {
                    if (appels.contains(partie.idAppel)) {
                        gardees.add(partie);
                        repondu.add(partie.idAppel);
                    } else {
                        change = true;
                    }
                } else {
                    gardees.add(partie);
                }
            }
            if (!gardees.isEmpty()) {
                resultat.add(new MessageAgent(message.role, gardees));
            } else if (!message.parties.isEmpty()) {
                change = true;
            }
        }
        List<String> interrompus = new ArrayList<>();
        for (String id : appels) {
            if (!repondu.contains(id)) {
                interrompus.add(id);
                change = true;
            }
        }
        if (!interrompus.isEmpty()) {
            List<PartieContenu> parties = new ArrayList<>();
            for (String id : interrompus) {
                parties.add(PartieContenu.resultat(id,
                        "The tool call was interrupted before its "
                                + "result was produced; treat it as "
                                + "not executed unless you verified "
                                + "otherwise.", true));
            }
            resultat.add(new MessageAgent(
                    MessageAgent.Role.UTILISATEUR, parties));
        }
        return new Reparation(resultat, change);
    }

    /** L'historique réparé et le drapeau de changement. */
    public static final class Reparation {
        /** L'historique réparé. */
        public final List<MessageAgent> historique;
        /** Vrai si la réparation a changé quelque chose. */
        public final boolean change;

        /** @param historique l'historique réparé @param change le drapeau */
        public Reparation(List<MessageAgent> historique, boolean change) {
            this.historique = historique;
            this.change = change;
        }
    }
}
