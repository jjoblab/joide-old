package jo.joide.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jo.joide.platform.CancelToken;

/**
 * Le faux transport des tests (ADR-É9 : « trames SSE rejouées via un
 * faux transport ») : il ne connaît pas le réseau, il rejoue les
 * trames qu'on lui pose et RETIENT la requête — les adaptateurs sont
 * testés hors ligne, ligne par ligne, contre des enregistrements.
 */
final class FauxTransport implements TransportHttp {

    /** Les requêtes reçues, dans l'ordre. */
    final List<RequeteCapturee> requetes = new ArrayList<>();

    /**
     * Les corps de réponse des POST simples, dans l'ordre (la
     * création de cache de Gemini se scripte ici) ; vide : "{}".
     */
    final List<String> reponsesPost = new ArrayList<>();

    private final List<List<TrameSse>> tours;
    private int tour;

    /** @param tours les trames de chaque réponse, dans l'ordre */
    FauxTransport(List<List<TrameSse>> tours) {
        this.tours = tours;
    }

    /** Une requête capturée : URL, en-têtes, corps. */
    record RequeteCapturee(String url, Map<String, String> entetes,
                           String corps) {
    }

    @Override
    public ReponseSse postSse(String url, Map<String, String> entetes,
                              String corps, CancelToken jeton) {
        requetes.add(new RequeteCapturee(url, entetes, corps));
        if (tour >= tours.size()) {
            throw new IllegalStateException(
                    "no more scripted responses");
        }
        List<TrameSse> trames = tours.get(tour++);
        return new ReponseSse() {
            private int index;

            @Override
            public int statut() {
                return 200;
            }

            @Override
            public String entete(String nom) {
                return null;
            }

            @Override
            public TrameSse suivante() {
                return index < trames.size()
                        ? trames.get(index++) : null;
            }
        };
    }

    @Override
    public Reponse post(String url, Map<String, String> entetes,
                        String corps, CancelToken jeton)
            throws IOException {
        requetes.add(new RequeteCapturee(url, entetes, corps));
        String reponse = reponsesPost.isEmpty() ? "{}"
                : reponsesPost.remove(0);
        return new Reponse() {
            @Override
            public int statut() {
                return 200;
            }

            @Override
            public String entete(String nom) {
                return null;
            }

            @Override
            public String corps() {
                return reponse;
            }
        };
    }

    @Override
    public Reponse get(String url, Map<String, String> entetes,
                       CancelToken jeton) throws IOException {
        requetes.add(new RequeteCapturee(url, entetes, ""));
        return new Reponse() {
            @Override
            public int statut() {
                return 200;
            }

            @Override
            public String entete(String nom) {
                return null;
            }

            @Override
            public String corps() {
                return "{}";
            }
        };
    }

    /** Un faux transport qui répond toujours une erreur HTTP. */
    static final class Erreur implements TransportHttp {
        final int statut;
        final Map<String, List<String>> entetes;
        final String corps;

        Erreur(int statut, Map<String, List<String>> entetes,
               String corps) {
            this.statut = statut;
            this.entetes = entetes;
            this.corps = corps;
        }

        @Override
        public ReponseSse postSse(String url,
                                  Map<String, String> entetesRequete,
                                  String corps, CancelToken jeton)
                throws IOException {
            throw new EchecHttp(statut, entetes, corps);
        }

        @Override
        public Reponse post(String url, Map<String, String> entetes,
                            String corps, CancelToken jeton)
                throws IOException {
            throw new EchecHttp(statut, new LinkedHashMap<>(), corps);
        }

        @Override
        public Reponse get(String url, Map<String, String> entetes,
                           CancelToken jeton) throws IOException {
            throw new EchecHttp(statut, new LinkedHashMap<>(), corps);
        }
    }
}
