package jo.joide.agent.outils;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * L'outil simple : une description et une invocation fonctionnelle —
 * le corps de tous les outils v1. Package privé par intention : la
 * surface publique passe par {@link OutilsV1}.
 */
final class OutilSimple implements OutilAgent {

    /** L'invocation fonctionnelle de l'outil. */
    interface Invocation {
        /**
         * @param arguments les arguments analysés
         * @param jeton     l'annulation
         * @return le résultat borné
         */
        String invoquer(JsonValue arguments, CancelToken jeton)
                throws Exception;
    }

    private final DescOutil description;
    private final Invocation invocation;

    private OutilSimple(DescOutil description,
                        Invocation invocation) {
        this.description = description;
        this.invocation = invocation;
    }

    /**
     * @param description la description
     * @param invocation  l'invocation
     * @return l'outil
     */
    static OutilSimple de(DescOutil description,
                          Invocation invocation) {
        return new OutilSimple(description, invocation);
    }

    @Override
    public DescOutil description() {
        return description;
    }

    @Override
    public String invoquer(JsonValue arguments, CancelToken jeton)
            throws Exception {
        return invocation.invoquer(arguments, jeton);
    }
}
