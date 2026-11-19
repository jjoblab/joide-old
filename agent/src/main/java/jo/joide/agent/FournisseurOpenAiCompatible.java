package jo.joide.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * L'adaptateur OpenAI-compatible (Chat Completions, flux SSE) — D-2 :
 * l'URL de base est LIBRE, elle couvre l'API officielle, les
 * passerelles (OpenRouter, LiteLLM) et les serveurs locaux
 * (l'en-tête d'authentification est omis si la clé est vide). Le
 * préfixe système stable part en PREMIER message système, la note
 * volatile en message système DE FIN — rafraîchie à chaque itération
 * sans toucher un octet en amont.
 *
 * <p>Le raisonnement (fournisseurs à réflexion, style
 * {@code reasoning_content}) est capturé en partie RAISONNEMENT à
 * signature opaque taguée {@code openai:*} — jamais confondue avec
 * celle d'un autre fournisseur ; elle repart telle quelle au tour
 * suivant.</p>
 *
 * <p>Le terrain du 2026-11-22 : les arguments d'un appel d'outil
 * partaient en OBJET — la spécification Chat Completions exige une
 * CHAÎNE JSON ({@code arguments}), un serveur Go qui la dé-sérialise
 * en string répond exactement « json: cannot unmarshal object »
 * (400 malformed_json, la « réponse illisible » d'hier) : ils repartent
 * en texte brut, tels que le modèle les a dits ; et {@code tools}
 * ne part JAMAIS vide — un tableau vide est refusé par des
 * passerelles.</p>
 */
public final class FournisseurOpenAiCompatible implements FournisseurLlm {

    /** L'URL de base officielle. */
    public static final String URL_DEFAUT = "https://api.openai.com/v1";

    private final String urlBase;
    private final String cleApi;

    /**
     * @param urlBase l'URL de base LIBRE (nettoyée des copier-coller)
     * @param cleApi  la clé d'API (vide : serveur local sans authentification)
     */
    public FournisseurOpenAiCompatible(String urlBase, String cleApi) {
        this.urlBase = nettoyerUrl(urlBase);
        this.cleApi = cleApi == null ? "" : cleApi;
    }

    /** Nettoie une URL de base copiée-collée : blancs, slash final, chemin complet. */
    static String nettoyerUrl(String url) {
        if (url == null || url.isBlank()) {
            return URL_DEFAUT;
        }
        String net = url.trim();
        while (net.endsWith("/")) {
            net = net.substring(0, net.length() - 1);
        }
        if (net.endsWith("/chat/completions")) {
            net = net.substring(0,
                    net.length() - "/chat/completions".length());
        }
        return net;
    }

    @Override
    public String nom() {
        return "openai-compatible";
    }

    @Override
    public MessageAgent envoyer(TransportHttp transport, RequeteLlm requete,
                                CancelToken jeton,
                                Consumer<EvenementFlux> ecouteur) {
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                requete.systeme));
        messages.addAll(messagesOpenAi(requete.messages));
        if (!requete.noteFinale.isEmpty()) {
            messages.add(Map.of("role", "system", "content",
                    requete.noteFinale));
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("model", requete.modele);
        corps.put("messages", messages);
        // le terrain du 2026-11-22 : un tableau tools VIDE est refusé
        // par des passerelles — le champ ne part que s'il porte des
        // outils (la passation du plafond d'itérations n'en porte pas)
        if (!requete.outils.isEmpty()) {
            corps.put("tools", outilsOpenAi(requete.outils));
        }
        if (requete.plafondSortie > 0) {
            corps.put("max_tokens", requete.plafondSortie);
        }
        corps.put("stream", true);
        corps.put("stream_options", Map.of("include_usage", true));
        Map<String, String> entetes = new LinkedHashMap<>();
        entetes.put("content-type", "application/json");
        entetes.put("accept", "text/event-stream");
        if (!cleApi.isEmpty()) {
            entetes.put("authorization", "Bearer " + cleApi);
        }
        TransportHttp.ReponseSse reponse;
        try {
            reponse = transport.postSse(
                    urlBase + "/chat/completions", entetes,
                    JsonEcrire.valeur(corps), jeton);
        } catch (TransportHttp.EchecHttp echec) {
            throw ErreursLlm.classifier(echec.statut, echec.entetes,
                    echec.corpsBrut);
        } catch (java.io.IOException e) {
            throw ErreursLlm.classifier(e, false);
        }
        // LA DIFFUSION EN DIRECT (lot C6) : chaque trame part à
        // l'écouteur AU MOMENT où elle arrive — le raisonnement et le
        // texte s'écrivent dans le tiroir pendant que le modèle
        // travaille (le tampon d'hier rejouait le flux APRÈS sa fin :
        // un message long ne montrait RIEN pendant toute la
        // génération — rapporté le 2026-11-20).
        Lecture lecture = new Lecture();
        try {
            TransportHttp.TrameSse trame;
            while ((trame = reponse.suivante()) != null) {
                jeton.checkCancelled();
                lecture.trame(trame, ecouteur);
            }
        } catch (java.io.IOException e) {
            throw ErreursLlm.classifier(e, true);
        }
        if (!lecture.termine) {
            throw ErreursLlm.illisible("stream ended before [DONE]");
        }
        if (ecouteur != null) {
            ecouteur.accept(EvenementFlux.usage(lecture.usage));
        }
        return new MessageAgent(MessageAgent.Role.ASSISTANT,
                lecture.parties());
    }

    /** L'état de lecture d'un flux Chat Completions. */
    private static final class Lecture {
        final StringBuilder texte = new StringBuilder();
        final StringBuilder raisonnement = new StringBuilder();
        final Map<Integer, AppelOuvert> appels = new LinkedHashMap<>();
        UsageLlm usage = UsageLlm.zero();
        String raisonArret = "";
        boolean termine;

        void trame(TransportHttp.TrameSse trame,
                   Consumer<EvenementFlux> ecouteur) {
            if ("[DONE]".equals(trame.donnees.trim())) {
                if (ecouteur != null) {
                    ecouteur.accept(EvenementFlux.fin(parties(),
                            raisonArret));
                }
                termine = true;
                return;
            }
            Json.Document document = Json.parse(trame.donnees);
            JsonValue racine = document.root;
            if (racine == null || !document.problems.isEmpty()) {
                throw ErreursLlm.illisible("bad JSON frame");
            }
            JsonValue erreur = racine.member("error");
            if (!erreur.isNull()) {
                throw ErreursLlm.illisible(
                        erreur.member("message").asString());
            }
            JsonValue usageChunk = racine.member("usage");
            if (!usageChunk.isNull()
                    && usageChunk.type() == JsonValue.Type.OBJECT) {
                usage = new UsageLlm(
                        usageChunk.member("prompt_tokens").asLong(),
                        usageChunk.member("completion_tokens").asLong(),
                        usageChunk.member("prompt_tokens_details")
                                .member("cached_tokens").asLong());
            }
            List<JsonValue> choix = racine.member("choices").items();
            for (JsonValue choixI : choix) {
                JsonValue delta = choixI.member("delta");
                String contenu = delta.member("content").asString();
                if (contenu != null && !contenu.isEmpty()) {
                    texte.append(contenu);
                    if (ecouteur != null) {
                        ecouteur.accept(
                                EvenementFlux.texte(contenu));
                    }
                }
                String reflechi = delta.member("reasoning_content")
                        .asString();
                if (reflechi == null) {
                    reflechi = delta.member("reasoning").asString();
                }
                if (reflechi != null && !reflechi.isEmpty()) {
                    raisonnement.append(reflechi);
                    if (ecouteur != null) {
                        ecouteur.accept(EvenementFlux.raisonnement(
                                reflechi));
                    }
                }
                for (JsonValue appelDelta : delta.member("tool_calls")
                        .items()) {
                    int index = (int) appelDelta.member("index")
                            .asLong();
                    AppelOuvert ouvert = appels.computeIfAbsent(index,
                            cle -> new AppelOuvert());
                    String id = appelDelta.member("id").asString();
                    if (id != null) {
                        ouvert.id = id;
                    }
                    JsonValue fonction = appelDelta.member("function");
                    String nom = fonction.member("name").asString();
                    if (nom != null) {
                        ouvert.nom.append(nom);
                    }
                    String morceau = fonction.member("arguments")
                            .asString();
                    if (morceau != null) {
                        ouvert.arguments.append(morceau);
                    }
                }
                String fin = choixI.member("finish_reason").asString();
                if (fin != null) {
                    raisonArret = fin;
                }
            }
        }

        List<PartieContenu> parties() {
            List<PartieContenu> parties = new ArrayList<>();
            if (raisonnement.length() > 0) {
                parties.add(PartieContenu.raisonnement(
                        raisonnement.toString(),
                        "openai:reasoning_content"));
            }
            if (texte.length() > 0) {
                parties.add(PartieContenu.texte(texte.toString()));
            }
            List<Integer> index = new ArrayList<>(appels.keySet());
            index.sort(Integer::compareTo);
            for (Integer i : index) {
                AppelOuvert ouvert = appels.get(i);
                parties.add(PartieContenu.appel(ouvert.id,
                        ouvert.nom.toString(),
                        ouvert.arguments.toString()));
            }
            return parties;
        }
    }

    /** Un appel d'outil en cours d'assemblage. */
    private static final class AppelOuvert {
        String id = "";
        final StringBuilder nom = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();
    }

    /** Les arguments bruts d'un appel, en CHAÎNE (jamais ré-écrits,
     * «{}» s'ils sont muets) — la forme que Chat Completions exige. */
    static String argumentsTexte(String arguments) {
        String brut = arguments == null ? "" : arguments.trim();
        return brut.isEmpty() ? "{}" : brut;
    }

    /** Traduit l'historique neutre en messages Chat Completions. */
    static List<Object> messagesOpenAi(List<MessageAgent> messages) {
        List<Object> sortie = new ArrayList<>();
        for (MessageAgent message : messages) {
            if (message.role == MessageAgent.Role.ASSISTANT) {
                Map<String, Object> vers = new LinkedHashMap<>();
                vers.put("role", "assistant");
                String texte = message.texte();
                if (!texte.isEmpty()) {
                    vers.put("content", texte);
                }
                List<Object> appels = new ArrayList<>();
                String raisonnement = null;
                for (PartieContenu partie : message.parties) {
                    if (partie.genre == PartieContenu.Genre.APPEL_OUTIL) {
                        Map<String, Object> fonction =
                                new LinkedHashMap<>();
                        fonction.put("name", partie.nomOutil);
                        // la spécification Chat Completions : arguments
                        // est une CHAÎNE JSON — le terrain du
                        // 2026-11-22 (« json: cannot unmarshal object »
                        // en 400 malformed_json) venait de l'OBJET
                        // d'hier ; le texte repart TEL QUEL, jamais
                        // ré-écrit
                        fonction.put("arguments",
                                argumentsTexte(partie.arguments));
                        Map<String, Object> appel =
                                new LinkedHashMap<>();
                        appel.put("id", partie.idAppel);
                        appel.put("type", "function");
                        appel.put("function", fonction);
                        appels.add(appel);
                    } else if (partie.genre
                            == PartieContenu.Genre.RAISONNEMENT
                            && partie.signature.startsWith("openai:")) {
                        raisonnement = partie.texte;
                    }
                }
                if (!appels.isEmpty()) {
                    vers.put("tool_calls", appels);
                }
                if (raisonnement != null) {
                    // la signature taguée openai:* revient au
                    // fournisseur qui l'a émise, jamais à un autre
                    vers.put("reasoning_content", raisonnement);
                }
                sortie.add(vers);
            } else {
                for (PartieContenu partie : message.parties) {
                    switch (partie.genre) {
                        case TEXTE -> sortie.add(Map.of("role", "user",
                                "content", partie.texte));
                        case RESULTAT_OUTIL -> {
                            Map<String, Object> resultat =
                                    new LinkedHashMap<>();
                            resultat.put("role", "tool");
                            resultat.put("tool_call_id", partie.idAppel);
                            resultat.put("content", partie.texte);
                            sortie.add(resultat);
                        }
                        case IMAGE -> {
                            // Chat Completions ne porte d'images que
                            // dans un message utilisateur : l'image
                            // d'un résultat arrive en message data URI
                            Map<String, Object> image =
                                    new LinkedHashMap<>();
                            image.put("type", "image_url");
                            image.put("image_url", Map.of("url",
                                    "data:" + partie.mediaType
                                            + ";base64,"
                                            + partie.donnees));
                            sortie.add(Map.of("role", "user",
                                    "content", List.of(image)));
                        }
                        default -> {
                        }
                    }
                }
            }
        }
        return sortie;
    }

    /** Traduit les outils neutres en fonctions Chat Completions. */
    static List<Object> outilsOpenAi(List<DescOutil> outils) {
        List<Object> sortie = new ArrayList<>();
        for (DescOutil outil : outils) {
            sortie.add(Map.of("type", "function", "function",
                    Map.of("name", outil.nom, "description",
                            outil.description, "parameters",
                            outil.schema.type() == JsonValue.Type.OBJECT
                                    ? outil.schema
                                    : Map.of("type", "object",
                                            "properties", Map.of()))));
        }
        return sortie;
    }

}
