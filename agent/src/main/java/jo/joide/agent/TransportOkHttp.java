package jo.joide.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jo.joide.platform.CancelToken;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Le transport OkHttp (décision D-5 du propriétaire, ADR-056) — la
 * seule dépendance tierce du module agent. TLS, redirections et
 * pools de connexions sont ceux d'OkHttp ; les certificats
 * personnalisés s'ajoutent PAR-DESSUS les ancres système, jamais un
 * trust-all. Garde de lecture : 120 s entre deux lectures (un
 * serveur muet ne tient pas le flux indéfiniment — écart n° 6 de
 * l'ADR-É9), aucun plafond de durée totale (un tour peut être long).
 *
 * <p>OkHttp retente de lui-même les échecs de connexion
 * d'avant-émission (corps en mémoire, rejouable) ; un flux coupé en
 * pleine réponse remonte en IOException — la boucle décide, jamais
 * le transport.</p>
 */
public final class TransportOkHttp implements TransportHttp {

    private static final MediaType JSON = MediaType.parse(
            "application/json; charset=utf-8");
    private static final long LECTURE_S = 120;
    private static final long CONNEXION_S = 30;
    private static final long ECRITURE_S = 30;

    private final OkHttpClient client;

    /** Un client par application suffit (pools, cache). */
    public TransportOkHttp() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNEXION_S, TimeUnit.SECONDS)
                .readTimeout(LECTURE_S, TimeUnit.SECONDS)
                .writeTimeout(ECRITURE_S, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public ReponseSse postSse(String url, Map<String, String> entetes,
                              String corps, CancelToken jeton)
            throws IOException {
        Response reponse = appeler("POST", url, entetes, corps, jeton);
        ResponseBody corpsReponse = reponse.body();
        if (corpsReponse == null) {
            reponse.close();
            throw new IOException("empty body");
        }
        if (!reponse.isSuccessful()) {
            try {
                throw new EchecHttp(reponse.code(),
                        entetesDe(reponse),
                        corpsReponse.string());
            } finally {
                reponse.close();
            }
        }
        return new ReponseSseOkHttp(reponse, corpsReponse);
    }

    @Override
    public Reponse post(String url, Map<String, String> entetes,
                        String corps, CancelToken jeton) throws IOException {
        Response reponse = appeler("POST", url, entetes, corps, jeton);
        return fermerApresLecture(reponse);
    }

    @Override
    public Reponse get(String url, Map<String, String> entetes,
                       CancelToken jeton) throws IOException {
        Response reponse = appeler("GET", url, entetes, null, jeton);
        return fermerApresLecture(reponse);
    }

    @Override
    public ReponseOuverte postOuvert(String url,
                                     Map<String, String> entetes,
                                     String corps, CancelToken jeton)
            throws IOException {
        Response reponse = appeler("POST", url, entetes, corps, jeton);
        return ouvrir(reponse);
    }

    @Override
    public ReponseOuverte getSse(String url, Map<String, String> entetes,
                                 CancelToken jeton) throws IOException {
        Response reponse = appeler("GET", url, entetes, null, jeton);
        return ouvrir(reponse);
    }

    @Override
    public Reponse supprimer(String url, Map<String, String> entetes,
                             CancelToken jeton) throws IOException {
        Response reponse = appeler("DELETE", url, entetes, null, jeton);
        return fermerApresLecture(reponse);
    }

    /** La réponse vivante : statut, en-têtes, corps lu ligne à ligne. */
    private static ReponseOuverte ouvrir(Response reponse)
            throws IOException {
        ResponseBody corps = reponse.body();
        if (!reponse.isSuccessful()) {
            try {
                throw new EchecHttp(reponse.code(),
                        entetesDe(reponse),
                        corps == null ? "" : corps.string());
            } finally {
                reponse.close();
            }
        }
        BufferedReader lecteur = corps == null ? null
                : new BufferedReader(new InputStreamReader(
                        corps.byteStream(), StandardCharsets.UTF_8));
        BufferedReader tamponFinal = lecteur;
        Response reponseVivante = reponse;
        ResponseBody corpsVivant = corps;
        return new ReponseOuverte() {
            @Override
            public int statut() {
                return reponseVivante.code();
            }

            @Override
            public String entete(String nom) {
                return reponseVivante.header(nom);
            }

            @Override
            public String ligne() throws IOException {
                String ligne = tamponFinal == null ? null
                        : tamponFinal.readLine();
                if (ligne == null) {
                    fermer();
                }
                return ligne;
            }

            @Override
            public void fermer() {
                if (tamponFinal != null) {
                    try {
                        tamponFinal.close();
                    } catch (IOException e) {
                        // déjà fermé : rien à faire
                    }
                }
                if (corpsVivant != null) {
                    corpsVivant.close();
                }
                reponseVivante.close();
            }
        };
    }

    private Response appeler(String methode, String url,
                             Map<String, String> entetes, String corps,
                             CancelToken jeton) throws IOException {
        Request.Builder construction = new Request.Builder().url(url);
        for (Map.Entry<String, String> entete : entetes.entrySet()) {
            construction.header(entete.getKey(), entete.getValue());
        }
        if ("GET".equals(methode)) {
            construction.get();
        } else if ("DELETE".equals(methode)) {
            construction.delete();
        } else {
            construction.post(RequestBody.create(
                    corps == null ? "" : corps, JSON));
        }
        Call appel = client.newCall(construction.build());
        if (jeton != null && jeton != CancelToken.NEVER) {
            jeton.onCancel(appel::cancel);
        }
        return appel.execute();
    }

    private static Reponse fermerApresLecture(Response reponse)
            throws IOException {
        try {
            ResponseBody corps = reponse.body();
            String texte = corps == null ? ""
                    : new String(corps.bytes(), StandardCharsets.UTF_8);
            int statut = reponse.code();
            Map<String, List<String>> entetes = entetesDe(reponse);
            if (!reponse.isSuccessful()) {
                throw new EchecHttp(statut, entetes, texte);
            }
            return new ReponseSimple(statut, entetes, texte);
        } finally {
            reponse.close();
        }
    }

    private static Map<String, List<String>> entetesDe(
            Response reponse) {
        Map<String, List<String>> entetes = new LinkedHashMap<>();
        for (String nom : reponse.headers().names()) {
            entetes.put(nom, List.copyOf(reponse.headers().values(nom)));
        }
        return entetes;
    }

    /** La réponse SSE vivante : chaque trame lue ferme la connexion au bon moment. */
    private static final class ReponseSseOkHttp implements ReponseSse {
        private final Response reponse;
        private final ResponseBody corps;
        private final LecteurSse lecteur;

        ReponseSseOkHttp(Response reponse, ResponseBody corps) {
            this.reponse = reponse;
            this.corps = corps;
            this.lecteur = new LecteurSse(new InputStreamReader(
                    corps.byteStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int statut() {
            return reponse.code();
        }

        @Override
        public String entete(String nom) {
            return reponse.header(nom);
        }

        @Override
        public TrameSse suivante() throws IOException {
            TrameSse trame = lecteur.suivante();
            if (trame == null) {
                fermer();
            }
            return trame;
        }

        private void fermer() {
            corps.close();
            reponse.close();
        }
    }

    /** La réponse simple entièrement lue. */
    private static final class ReponseSimple implements Reponse {
        private final int statut;
        private final Map<String, List<String>> entetes;
        private final String texte;

        ReponseSimple(int statut, Map<String, List<String>> entetes,
                      String texte) {
            this.statut = statut;
            this.entetes = entetes;
            this.texte = texte;
        }

        @Override
        public int statut() {
            return statut;
        }

        @Override
        public String entete(String nom) {
            List<String> valeurs = entetes.get(nom);
            return valeurs == null || valeurs.isEmpty() ? null
                    : valeurs.get(0);
        }

        @Override
        public String corps() {
            return texte;
        }
    }

    /** Le lecteur SSE incrémental (le parseur maison, trame par trame). */
    static final class LecteurSse {
        private final BufferedReader lecteur;
        private String evenement;
        private final StringBuilder donnees = new StringBuilder();
        private boolean vide = true;

        LecteurSse(Reader brut) {
            this.lecteur = brut instanceof BufferedReader tampon
                    ? tampon : new BufferedReader(brut);
        }

        /** @return la trame suivante, ou null à la fin du flux */
        TrameSse suivante() throws IOException {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                if (ligne.isEmpty()) {
                    if (!vide) {
                        TrameSse trame = new TrameSse(evenement,
                                donnees.toString());
                        evenement = null;
                        donnees.setLength(0);
                        vide = true;
                        return trame;
                    }
                    continue;
                }
                if (ligne.startsWith(":")) {
                    continue;
                }
                vide = false;
                int deuxPoints = ligne.indexOf(':');
                String champ = deuxPoints < 0 ? ligne
                        : ligne.substring(0, deuxPoints);
                String valeur = deuxPoints < 0 ? ""
                        : ligne.substring(deuxPoints + 1);
                if (valeur.startsWith(" ")) {
                    valeur = valeur.substring(1);
                }
                if ("event".equals(champ)) {
                    evenement = valeur;
                } else if ("data".equals(champ)) {
                    if (donnees.length() > 0) {
                        donnees.append('\n');
                    }
                    donnees.append(valeur);
                }
            }
            if (!vide) {
                // la dernière trame sans ligne vide finale part telle
                // quelle — l'adaptateur juge la complétude
                vide = true;
                return new TrameSse(evenement, donnees.toString());
            }
            return null;
        }
    }
}
