package jo.joide.agent;

import java.util.List;
import java.util.Objects;

/**
 * Un message de la conversation neutre : un rôle et des parties. Les
 * résultats d'outils voyagent dans un message UTILISATEUR (la forme
 * d'Anthropic) — chaque fournisseur les remappe à son dialecte
 * (OpenAI : messages d' rôle « tool »), le noyau n'en connaît qu'une.
 */
public final class MessageAgent {

    /** Le rôle du message. */
    public enum Role {
        /** Ce que dit l'utilisateur (et les résultats d'outils). */
        UTILISATEUR,
        /** Ce que répond le modèle. */
        ASSISTANT
    }

    /** Le rôle. */
    public final Role role;
    /** Les parties, dans l'ordre. */
    public final List<PartieContenu> parties;

    /** @param role le rôle @param parties les parties (copiées) */
    public MessageAgent(Role role, List<PartieContenu> parties) {
        this.role = Objects.requireNonNull(role, "role");
        this.parties = List.copyOf(parties);
    }

    /** @param texte le texte unique du message @return un message utilisateur */
    public static MessageAgent utilisateur(String texte) {
        return new MessageAgent(Role.UTILISATEUR,
                List.of(PartieContenu.texte(texte)));
    }

    /** @param texte le texte unique du message @return un message assistant */
    public static MessageAgent assistant(String texte) {
        return new MessageAgent(Role.ASSISTANT,
                List.of(PartieContenu.texte(texte)));
    }

    /** @return les appels d'outils du message, dans l'ordre */
    public List<PartieContenu> appelsOutils() {
        return parties.stream()
                .filter(p -> p.genre == PartieContenu.Genre.APPEL_OUTIL)
                .toList();
    }

    /** @return le texte assemblé du message (les parties texte) */
    public String texte() {
        StringBuilder sortie = new StringBuilder();
        for (PartieContenu partie : parties) {
            if (partie.genre == PartieContenu.Genre.TEXTE) {
                sortie.append(partie.texte);
            }
        }
        return sortie.toString();
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof MessageAgent message
                && role == message.role && parties.equals(message.parties);
    }

    @Override
    public int hashCode() {
        return 31 * role.hashCode() + parties.hashCode();
    }

    @Override
    public String toString() {
        return "MessageAgent[" + role + ", " + parties.size()
                + " partie(s)]";
    }
}
