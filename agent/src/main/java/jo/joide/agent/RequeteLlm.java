package jo.joide.agent;

import java.util.List;
import java.util.Objects;

/**
 * La requête envoyée au fournisseur — construite par la boucle, lue
 * par l'adaptateur. Le prompt est COUPÉ PAR VOLATILITÉ (ADR-É9,
 * leçon 1) : l'ancrage stable part en préfixe système (identité,
 * règles, instructions — écrit UNE fois), l'état volatile part en
 * note système EN FIN d'historique, rafraîchie à chaque itération
 * sans toucher un octet en amont.
 *
 * <p>L'empreinte du préfixe stable ({@link #empreinteStable()}) sert
 * au garde-fou « préfixe stable » : la requête N+1 d'une boucle
 * d'outils doit contenir la requête N octet pour octet en préfixe
 * (hors la note finale, qui avance par conception) — une dérive
 * silencieuse ne se voit que sur la facture, le test échoue à sa
 * place.</p>
 */
public final class RequeteLlm {

    /** Le modèle demandé (identifiant fournisseur, jamais en dur dans le code). */
    public final String modele;
    /** Le préfixe système stable (ancrage : identité, règles). */
    public final String systeme;
    /** La conversation, historique append-only. */
    public final List<MessageAgent> messages;
    /** La note système volatile, en fin d'historique. */
    public final String noteFinale;
    /** Les outils proposés au modèle. */
    public final List<DescOutil> outils;
    /** Le plafond de jetons de sortie. */
    public final int plafondSortie;

    /** Construit la requête complète (les listes sont copiées). */
    public RequeteLlm(String modele, String systeme,
                      List<MessageAgent> messages, String noteFinale,
                      List<DescOutil> outils, int plafondSortie) {
        this.modele = Objects.requireNonNull(modele, "model");
        this.systeme = Objects.requireNonNull(systeme, "system");
        this.messages = List.copyOf(messages);
        this.noteFinale = Objects.requireNonNull(noteFinale, "note");
        this.outils = List.copyOf(outils);
        this.plafondSortie = plafondSortie;
    }

    /**
     * @return la requête sans la note finale (la partie qui doit
     *         rester octet pour octet stable au fil des itérations)
     */
    public RequeteLlm sansNoteFinale() {
        return new RequeteLlm(modele, systeme, messages, "", outils,
                plafondSortie);
    }

    /**
     * L'empreinte sérialisée de la partie STABLE : préfixe système +
     * messages. Déterministe (mêmes parties, mêmes octets) — le
     * garde-fou de préfixe compare des empreintes, jamais des
     * objets.
     */
    public String empreinteStable() {
        StringBuilder sortie = new StringBuilder(256);
        sortie.append("M:").append(modele).append('\n');
        sortie.append("S<").append(systeme.length()).append('>')
                .append(systeme).append('\n');
        for (MessageAgent message : messages) {
            sortie.append(message.role == MessageAgent.Role.ASSISTANT
                    ? 'A' : 'U').append('\n');
            for (PartieContenu partie : message.parties) {
                switch (partie.genre) {
                    case TEXTE -> sortie.append("t<")
                            .append(partie.texte.length()).append('>')
                            .append(partie.texte);
                    case RAISONNEMENT -> sortie.append("r<")
                            .append(partie.texte.length()).append('>')
                            .append(partie.texte).append('|')
                            .append(partie.signature);
                    case APPEL_OUTIL -> sortie.append("c<")
                            .append(partie.idAppel).append('|')
                            .append(partie.nomOutil).append('|')
                            .append(partie.arguments);
                    case RESULTAT_OUTIL -> sortie.append(partie.erreur
                            ? "e<" : "q<").append(partie.idAppel)
                            .append('|').append(partie.texte);
                    case IMAGE -> sortie.append("i<")
                            .append(partie.mediaType).append(':')
                            .append(partie.donnees.length()).append('>')
                            .append(partie.donnees);
                    default -> sortie.append('?');
                }
                sortie.append('\n');
            }
        }
        return sortie.toString();
    }

    @Override
    public String toString() {
        return "RequeteLlm[modele=" + modele + ", messages="
                + messages.size() + ", outils=" + outils.size() + "]";
    }
}
