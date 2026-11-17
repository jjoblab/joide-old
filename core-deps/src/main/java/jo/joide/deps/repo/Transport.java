package jo.joide.deps.repo;

import jo.joide.platform.CancelToken;

/**
 * Le transport d'un dépôt distant : une seule opération, GET, avec
 * annulation coopérative. Les implémentations ne lèvent pas :
 * tout échec se rend comme {@link TransportResponse#error(String)}.
 */
public interface Transport {

    /**
     * @param url   URL absolue https
     * @param token jeton d'annulation (peut être {@code null})
     * @return la réponse du serveur
     */
    TransportResponse get(String url, CancelToken token);
}
