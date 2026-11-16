package jo.joide.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import jo.joide.platform.CancelToken;
import jo.joide.platform.Clock;

/**
 * Le décorateur de bascule multi-clés (lot Q, ADR-062 ; le
 * « KeyFailoverClient » de l'ADR-É9) : une {@link RequeteLlm}
 * qu'une clé refuse D'AVANT TOUTE SORTIE repart sur la clé suivante
 * du banc — une clé dépensée ou rejetée n'interrompt jamais la
 * conversation. Dès que le flux a émis un octet, la requête n'est
 * plus jamais renvoyée : l'utilisateur verrait le début de la
 * réponse deux fois.
 *
 * <p>Quand toutes les clés sont au banc, l'échec est HONNÊTE : si la
 * première clé qui revient n'attend qu'une limite à la minute courte
 * (≤ 120 s — le plafond que la boucle attend elle-même), l'erreur
 * est une LIMITE_DEBIT porteuse du délai, que la boucle attend
 * visiblement et continue ; sinon une erreur agrégée qui compte les
 * clés par raison et conseille quoi faire.</p>
 *
 * <p>La régulation préventive vit ICI (avant l'envoi réel) : un
 * {@link RegulateurDebit} par clé + modèle, alimenté par les limites
 * que le banc a apprises des erreurs — l'attente préventive est
 * courte et silencieuse (le précédent de la boucle : l'attente de
 * jitter), l'annulation la traverse.</p>
 *
 * <p>Un banc d'une clé ou zéro clé est un pass-through simple.</p>
 */
public final class FournisseurBascule implements FournisseurLlm {

    /** Le banc des clés du fournisseur. */
    public final BancCles banc;
    /** La fabrique du fournisseur pour une clé. */
    private final FabriqueParCle fabrique;
    private final Clock horloge;
    private final Map<String, FournisseurLlm> fournisseurs =
            new LinkedHashMap<>();
    private final Map<String, RegulateurDebit> regulateurs =
            new LinkedHashMap<>();

    /**
     * Le sommeil d'attente (millisecondes) — injecté : les tests
     * l'instantanent, la production dort vraiment.
     */
    LongConsumer veille = FournisseurBascule::dormirVraiment;

    /** La fabrique d'un fournisseur pour une clé donnée. */
    public interface FabriqueParCle {

        /**
         * @param cle la clé d'API (jamais journalisée)
         * @return le fournisseur configuré pour cette clé
         */
        FournisseurLlm fournir(String cle);
    }

    /**
     * @param banc      le banc des clés (une clé ou zéro :
     *                  pass-through)
     * @param fabrique  la fabrique par clé (mémoïsée par clé)
     * @param horloge   l'horloge injectée (null : système)
     */
    public FournisseurBascule(BancCles banc, FabriqueParCle fabrique,
                              Clock horloge) {
        this.banc = banc;
        this.fabrique = fabrique;
        this.horloge = horloge == null ? Clock.system() : horloge;
    }

    @Override
    public String nom() {
        return "bascule";
    }

    @Override
    public MessageAgent envoyer(TransportHttp transport,
                                RequeteLlm requete, CancelToken jeton,
                                Consumer<EvenementFlux> ecouteur) {
        if (banc.taille() <= 1) {
            String cle = banc.cles.isEmpty() ? "" : banc.cles.get(0);
            return fournir(cle).envoyer(transport, requete, jeton,
                    ecouteur);
        }
        ErreursLlm.ErreurLlm derniere = null;
        List<Integer> essayees = new ArrayList<>();
        while (true) {
            jeton.checkCancelled();
            int index = banc.prochaine();
            if (index < 0 || essayees.contains(index)) {
                break;
            }
            essayees.add(index);
            String cle = banc.cles.get(index);
            RegulateurDebit regulateur = regulateur(cle, requete.modele);
            long jetonsEstimes = RegulateurDebit.estimerJetons(requete);
            // la régulation PRÉVENTIVE : attendre avant, enregistrer
            // après — jamais un 429 récolté pour rien
            attendre(regulateur.delaiPour(jetonsEstimes), jeton);
            ErreursLlm.ErreurLlm refusee;
            try {
                regulateur.enregistrer(jetonsEstimes);
                MessageAgent assistant = fournir(cle).envoyer(transport,
                        requete, jeton, ecouteur);
                return assistant;
            } catch (ErreursLlm.ErreurLlm erreur) {
                refusee = erreur;
            }
            // l'apprentissage : une limite énoncée devient la borne
            // du régulateur de CE couple clé+modèle
            if (refusee.quota != null) {
                regulateur.apprendre(refusee.quota);
            }
            if (!BancCles.bascule(refusee.genre)) {
                // ce qui échouerait pareil partout ne bascule pas
                throw refusee;
            }
            banc.asseoir(index, refusee.genre,
                    refusee.delaiSecondes > 0
                            ? refusee.delaiSecondes * 1000 : -1);
            derniere = refusee;
        }
        throw echecAgregge(derniere);
    }

    /** Le fournisseur d'une clé (mémoïsé). */
    private synchronized FournisseurLlm fournir(String cle) {
        return fournisseurs.computeIfAbsent(cle, fabrique::fournir);
    }

    /** Le régulateur d'un couple clé + modèle (mémoïsé). */
    synchronized RegulateurDebit regulateur(String cle,
                                            String modele) {
        return regulateurs.computeIfAbsent(cle + "|" + modele,
                k -> new RegulateurDebit(null, null, horloge));
    }

    /**
     * L'occupation la plus HAUTE des fenêtres du modèle courant
     * (l'écran Usages la dessine, lot T — ADR-064 avait consigné
     * l'écart) : {occupation requêtes, occupation jetons}, 0..1, 0
     * sans plafond connu — une alerte sans référence mesurée serait
     * une invention, la bande reste normale.
     *
     * @param modele le modèle courant (le filtre des régulateurs)
     * @return {rpm, tpm} en fractions
     */
    public synchronized double[] occupationModele(String modele) {
        double rpm = 0;
        double tpm = 0;
        String suffixe = modele == null ? "" : "|" + modele;
        for (Map.Entry<String, RegulateurDebit> entree
                : regulateurs.entrySet()) {
            if (!suffixe.isEmpty()
                    && !entree.getKey().endsWith(suffixe)) {
                continue;
            }
            RegulateurDebit regulateur = entree.getValue();
            rpm = Math.max(rpm, regulateur.occupationRpm());
            tpm = Math.max(tpm, regulateur.occupationTpm(0));
        }
        return new double[]{rpm, tpm};
    }

    /**
     * L'échec quand toutes les clés sont au banc : attendable si la
     * première clé qui revient n'attend qu'une minute de débit
     * courte, agrégé et conseillé sinon.
     */
    private ErreursLlm.ErreurLlm echecAgregge(
            ErreursLlm.ErreurLlm derniere) {
        long delai = banc.delaiProchaine();
        ErreursLlm.Genre raison = banc.raisonProchaine();
        if (raison == ErreursLlm.Genre.LIMITE_DEBIT
                && delai <= SEUIL_ATTENTE_COURTE_MS) {
            return new ErreursLlm.ErreurLlm(
                    ErreursLlm.Genre.LIMITE_DEBIT,
                    "all " + banc.taille() + " API keys are rate "
                            + "limited. Try again in "
                            + delai / 1000 + "s",
                    Math.max(1, delai / 1000));
        }
        StringBuilder message = new StringBuilder();
        message.append("all ").append(banc.taille())
                .append(" API keys for this provider are unavailable");
        Map<ErreursLlm.Genre, Integer> parRaison = banc.bancParRaison();
        List<String> morceaux = new ArrayList<>();
        for (Map.Entry<ErreursLlm.Genre, Integer> entree
                : parRaison.entrySet()) {
            morceaux.add(entree.getValue() + " "
                    + BancCles.libelle(entree.getKey()));
        }
        if (!morceaux.isEmpty()) {
            message.append(" (").append(String.join(", ", morceaux))
                    .append(')');
        }
        if (raison != ErreursLlm.Genre.CLE_INVALIDE
                && raison != ErreursLlm.Genre.CLE_SANS_DROIT) {
            message.append(". The first one is tried again in ")
                    .append(delai / 1000).append("s");
        }
        message.append(". Add another key, or check your plan with "
                + "the provider.");
        return new ErreursLlm.ErreurLlm(
                derniere == null
                        ? ErreursLlm.Genre.QUOTA_EPUISE
                        : derniere.genre,
                message.toString(), -1);
    }

    /** L'attente préventive, découpée pour traverser l'annulation. */
    private void attendre(long millisecondes, CancelToken jeton) {
        if (millisecondes <= 0) {
            return;
        }
        long reste = millisecondes;
        while (reste > 0) {
            jeton.checkCancelled();
            long tranche = Math.min(250, reste);
            veille.accept(tranche);
            reste -= tranche;
        }
    }

    private static void dormirVraiment(long millisecondes) {
        try {
            Thread.sleep(millisecondes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CancellationException(
                    "wait interrupted");
        }
    }

    /** Le plafond d'une attente « courte » que la boucle traverse. */
    static final long SEUIL_ATTENTE_COURTE_MS = 120_000L;
}
