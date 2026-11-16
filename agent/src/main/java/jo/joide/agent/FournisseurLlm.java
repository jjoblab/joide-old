package jo.joide.agent;

import java.util.function.Consumer;

import jo.joide.platform.CancelToken;

/**
 * Le SPI fournisseur : un adaptateur traduit la requête neutre en
 * wire-protocol, l'envoie par le transport, et traduit le flux en
 * événements neutres. Deux implémentations au lot N (D-2) :
 * Anthropic (Messages API) et OpenAI-compatible (Chat Completions,
 * URL de base libre — passerelles et serveurs locaux).
 */
public interface FournisseurLlm {

    /** @return le nom du fournisseur (diagnostics, réglages) */
    String nom();

    /**
     * Envoie la requête en flux.
     *
     * @param transport le transport HTTP (D-5)
     * @param requete   la requête neutre
     * @param jeton     l'annulation du tour
     * @param ecouteur  reçoit les deltas vivants (fil de la boucle)
     * @return le message assistant complet (parties + usage)
     * @throws ErreursLlm.ErreurLlm l'erreur CLASSÉE — jamais une
     *                              exception brute du transport
     */
    MessageAgent envoyer(TransportHttp transport, RequeteLlm requete,
                         CancelToken jeton,
                         Consumer<EvenementFlux> ecouteur);
}
