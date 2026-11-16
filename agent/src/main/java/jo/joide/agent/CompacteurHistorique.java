package jo.joide.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Le compacteur d'historique à hystérésis (ADR-É9, économie des
 * jetons) : rien n'est élagué sous 120 000 caractères ; au
 * déclenchement, UNE passe tronque à 4 000 caractères les résultats
 * d'outils plus vieux que les quatre plus récents, jusqu'à 60 000.
 * Les décisions sont PERMANENTES (un résultat réélargi au tour
 * suivant casserait le préfixe caché) ; les textes utilisateur et
 * assistant ne sont JAMAIS touchés.
 *
 * <p>Pour un fournisseur qui gère le contexte côté serveur
 * (Anthropic), le mode « délégué » désactive l'élagueur local —
 * deux élagueurs se battraient sur le même préfixe.</p>
 */
public final class CompacteurHistorique {

    /** Rien n'est élagué sous ce seuil (caractères). */
    public static final int SEUIL_DECLENCHEMENT = 120_000;
    /** La cible après une passe (caractères). */
    public static final int CIBLE = 60_000;
    /** Un résultat d'outil tronqué garde au plus ces caractères. */
    public static final int LIMITE_RESULTAT = 4_000;
    /** Les quatre résultats les plus récents ne sont jamais tronqués. */
    public static final int RESULTATS_EPARGNES = 4;

    /** Le compte de caractères de tout l'historique. */
    static int caracteres(List<MessageAgent> historique) {
        int total = 0;
        for (MessageAgent message : historique) {
            for (PartieContenu partie : message.parties) {
                total += partie.texte.length();
            }
        }
        return total;
    }

    /**
     * La passe d'élagage, si le seuil est dépassé.
     *
     * @param historique  l'historique à compacter (non modifié)
     * @param dejaCompactes les identifiants d'appels DÉJÀ tronqués
     *                     (décisions permanentes — jamais réélargis)
     * @param delegue     vrai : le fournisseur gère le contexte, la
     *                    passe ne fait rien
     * @return le résultat de la passe (historique et décisions)
     */
    public static Passe passer(List<MessageAgent> historique,
                               Set<String> dejaCompactes, boolean delegue) {
        Set<String> permanents = new HashSet<>(dejaCompactes);
        if (delegue || caracteres(historique) <= SEUIL_DECLENCHEMENT) {
            return new Passe(historique, permanents, false);
        }
        // les identifiants des quatre résultats les plus récents
        List<String> recents = historique.stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre == PartieContenu.Genre.RESULTAT_OUTIL)
                .map(p -> p.idAppel)
                .toList();
        Set<String> epargnes = new HashSet<>(
                recents.subList(Math.max(0,
                        recents.size() - RESULTATS_EPARGNES),
                        recents.size()));
        List<MessageAgent> compacte = new java.util.ArrayList<>();
        boolean auMoinsUn = false;
        for (MessageAgent message : historique) {
            List<PartieContenu> parties =
                    new java.util.ArrayList<>();
            boolean modifie = false;
            for (PartieContenu partie : message.parties) {
                if (partie.genre == PartieContenu.Genre.RESULTAT_OUTIL
                        && !epargnes.contains(partie.idAppel)
                        && !permanents.contains(partie.idAppel)
                        && partie.texte.length() > LIMITE_RESULTAT) {
                    parties.add(PartieContenu.resultat(partie.idAppel,
                            partie.texte.substring(0, LIMITE_RESULTAT)
                                    + "\\n[tool output truncated to "
                                    + LIMITE_RESULTAT
                                    + " characters — permanent]",
                            partie.erreur));
                    permanents.add(partie.idAppel);
                    modifie = true;
                    auMoinsUn = true;
                } else {
                    parties.add(partie);
                }
            }
            compacte.add(modifie
                    ? new MessageAgent(message.role, parties)
                    : message);
        }
        // une passe qui ne tronque rien (tout est déjà permanent)
        // n'est pas une passe : aucun événement, aucun mensonge
        return new Passe(compacte, permanents, auMoinsUn);
    }

    /** Le résultat d'une passe : historique, décisions permanentes, passage effectué. */
    public static final class Passe {
        /** L'historique après passe (le même si aucune passe). */
        public final List<MessageAgent> historique;
        /** Les décisions permanentes, cumulées. */
        public final Set<String> compactes;
        /** Vrai si une troncature a réellement eu lieu. */
        public final boolean passage;

        /** @param historique l'historique @param compactes les décisions @param passage la troncature */
        public Passe(List<MessageAgent> historique, Set<String> compactes,
                     boolean passage) {
            this.historique = historique;
            this.compactes = compactes;
            this.passage = passage;
        }
    }
}
