package jo.joide.agent;

import java.io.IOException;
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
 * L'adaptateur Anthropic (Messages API, flux SSE) — D-2 : la v1
 * couvre Anthropic et OpenAI-compatible. Traduction fidèle des deux
 * sens : le préfixe système stable part avec le point de rupture de
 * cache {@code cache_control} (ADR-É9 : la conversation est relue
 * depuis le cache du prompt), la note volatile part en SECOND bloc
 * système APRÈS la rupture — rafraîchie à chaque itération sans
 * toucher un octet en amont.
 *
 * <p>Les signatures de raisonnement voyagent telles quelles
 * ({@code signature_delta}) : le modèle neutre ne les interprète
 * jamais, elles rendent le raisonnement au tour suivant.</p>
 */
public final class FournisseurAnthropic implements FournisseurLlm {

    /** L'URL de base de l'API Anthropic. */
    public static final String URL_DEFAUT =
            "https://api.anthropic.com";

    private final String urlBase;
    private final String cleApi;

    /**
     * @param urlBase l'URL de base (surchargeable pour une passerelle)
     * @param cleApi  la clé d'API (jamais journalisée, jamais en dur)
     */
    public FournisseurAnthropic(String urlBase, String cleApi) {
        this.urlBase = urlBase == null || urlBase.isEmpty()
                ? URL_DEFAUT : urlBase;
        this.cleApi = cleApi == null ? "" : cleApi;
    }

    @Override
    public String nom() {
        return "anthropic";
    }

    @Override
    public MessageAgent envoyer(TransportHttp transport, RequeteLlm requete,
                                CancelToken jeton,
                                Consumer<EvenementFlux> ecouteur) {
        if (cleApi.isEmpty()) {
            throw ErreursLlm.cleManquante(nom());
        }
        String corps = JsonEcrire.objet(
                "model", requete.modele,
                "max_tokens", plafond(requete),
                "system", List.of(
                        Map.of("type", "text", "text", requete.systeme,
                                "cache_control",
                                Map.of("type", "ephemeral")),
                        Map.of("type", "text", "text",
                                requete.noteFinale)),
                "messages", messagesAnthropic(requete.messages),
                "tools", outilsAnthropic(requete.outils),
                "stream", true);
        Map<String, String> entetes = new LinkedHashMap<>();
        entetes.put("content-type", "application/json");
        entetes.put("accept", "text/event-stream");
        entetes.put("x-api-key", cleApi);
        entetes.put("anthropic-version", "2023-06-01");
        TransportHttp.ReponseSse reponse;
        try {
            reponse = transport.postSse(urlBase + "/v1/messages",
                    entetes, corps, jeton);
        } catch (TransportHttp.EchecHttp echec) {
            throw ErreursLlm.classifier(echec.statut, echec.entetes,
                    echec.corpsBrut);
        } catch (IOException e) {
            throw ErreursLlm.classifier(e, false);
        }
        List<TransportHttp.TrameSse> trames = new ArrayList<>();
        try {
            TransportHttp.TrameSse trame;
            while ((trame = reponse.suivante()) != null) {
                jeton.checkCancelled();
                trames.add(trame);
            }
        } catch (IOException e) {
            // des octets ont coulé : le tour s'arrête ici, jamais de
            // texte déjà montré dupliqué (ADR-É9, transport)
            throw ErreursLlm.classifier(e, true);
        }
        Lecture lecture = new Lecture();
        for (TransportHttp.TrameSse t : trames) {
            lecture.trame(t, ecouteur);
        }
        if (!lecture.termine) {
            throw ErreursLlm.illisible(
                    "stream ended without message_stop");
        }
        if (ecouteur != null) {
            ecouteur.accept(EvenementFlux.usage(lecture.usage));
        }
        return new MessageAgent(MessageAgent.Role.ASSISTANT,
                lecture.parties);
    }

    /** L'état de lecture d'un flux Anthropic. */
    private static final class Lecture {
        final List<PartieContenu> parties = new ArrayList<>();
        final Map<Integer, Bloc> blocs = new LinkedHashMap<>();
        UsageLlm usage = UsageLlm.zero();
        String raisonArret = "";
        boolean termine;

        void trame(TransportHttp.TrameSse trame,
                   Consumer<EvenementFlux> ecouteur) {
            Json.Document document = Json.parse(trame.donnees);
            JsonValue racine = document.root;
            if (racine == null || !document.problems.isEmpty()) {
                throw ErreursLlm.illisible("bad JSON frame: "
                        + trame.donnees);
            }
            String genre = racine.member("type").asString();
            if (genre == null) {
                return;
            }
            switch (genre) {
                case "message_start" -> usage = new UsageLlm(
                        racine.member("message").member("usage")
                                .member("input_tokens").asLong(),
                        0,
                        racine.member("message").member("usage")
                                .member("cache_read_input_tokens")
                                .asLong());
                case "content_block_start" -> {
                    JsonValue bloc = racine.member("content_block");
                    blocs.put((int) racine.member("index").asLong(),
                            new Bloc(bloc.member("type").asString(),
                                    bloc.member("id").asString(),
                                    bloc.member("name").asString()));
                }
                case "content_block_delta" -> {
                    Bloc bloc = blocs.get(
                            (int) racine.member("index").asLong());
                    if (bloc == null) {
                        throw ErreursLlm.illisible(
                                "delta without block");
                    }
                    JsonValue delta = racine.member("delta");
                    String genreDelta = delta.member("type").asString();
                    if ("text_delta".equals(genreDelta)) {
                        bloc.texte.append(
                                delta.member("text").asString());
                        if (ecouteur != null) {
                            ecouteur.accept(EvenementFlux.texte(
                                    delta.member("text").asString()));
                        }
                    } else if ("thinking_delta".equals(genreDelta)) {
                        bloc.texte.append(
                                delta.member("thinking").asString());
                        if (ecouteur != null) {
                            ecouteur.accept(EvenementFlux.raisonnement(
                                    delta.member("thinking").asString()));
                        }
                    } else if ("signature_delta".equals(genreDelta)) {
                        bloc.signature.append(
                                delta.member("signature").asString());
                    } else if ("input_json_delta".equals(genreDelta)) {
                        bloc.json.append(
                                delta.member("partial_json").asString());
                    }
                }
                case "content_block_stop" -> {
                    Bloc bloc = blocs.get(
                            (int) racine.member("index").asLong());
                    if (bloc != null) {
                        bloc.ferme = true;
                    }
                }
                case "message_delta" -> {
                    raisonArret = racine.member("delta")
                            .member("stop_reason").asString();
                    long sorties = racine.member("usage")
                            .member("output_tokens").asLong();
                    usage = new UsageLlm(usage.entrees, sorties,
                            usage.lecturesCache);
                }
                case "message_stop" -> {
                    for (Map.Entry<Integer, Bloc> entree
                            : blocs.entrySet()) {
                        PartieContenu partie = entree.getValue()
                                .partie();
                        if (partie != null) {
                            parties.add(partie);
                        }
                    }
                    if ("refusal".equals(raisonArret)) {
                        throw ErreursLlm.contenuRefuse(
                                "stop_reason=refusal");
                    }
                    if (ecouteur != null) {
                        ecouteur.accept(EvenementFlux.fin(parties,
                                raisonArret));
                    }
                    termine = true;
                }
                case "error" -> {
                    JsonValue erreur = racine.member("error");
                    String type = erreur.member("type").asString();
                    String message = erreur.member("message").asString();
                    throw erreurAnthropic(type, message);
                }
                default -> {
                    // ping, message.idle… : jamais une erreur
                }
            }
        }

        private ErreursLlm.ErreurLlm erreurAnthropic(String type,
                                                     String message) {
            if ("rate_limit_error".equals(type)) {
                return ErreursLlm.classifier(429, Map.of(), message);
            }
            if ("overloaded_error".equals(type)
                    || "api_error".equals(type)) {
                return ErreursLlm.classifier(503, Map.of(), message);
            }
            if ("invalid_request_error".equals(type) && message != null
                    && message.toLowerCase().contains("context")) {
                return ErreursLlm.contexteTropLong(-1);
            }
            if ("authentication_error".equals(type)) {
                return ErreursLlm.classifier(401, Map.of(), message);
            }
            if ("permission_error".equals(type)) {
                return ErreursLlm.classifier(403, Map.of(), message);
            }
            if ("not_found_error".equals(type)) {
                return ErreursLlm.classifier(404, Map.of(), message);
            }
            return ErreursLlm.illisible(type + ": " + message);
        }
    }

    /** Un bloc de contenu en cours de réception. */
    private static final class Bloc {
        final String type;
        final String id;
        final String nom;
        final StringBuilder texte = new StringBuilder();
        final StringBuilder signature = new StringBuilder();
        final StringBuilder json = new StringBuilder();
        boolean ferme;

        Bloc(String type, String id, String nom) {
            this.type = type;
            this.id = id == null ? "" : id;
            this.nom = nom == null ? "" : nom;
        }

        PartieContenu partie() {
            if ("text".equals(type)) {
                return PartieContenu.texte(texte.toString());
            }
            if ("thinking".equals(type)) {
                return PartieContenu.raisonnement(texte.toString(),
                        signature.toString());
            }
            if ("tool_use".equals(type)) {
                String arguments = json.toString().trim();
                if (arguments.isEmpty()) {
                    arguments = "{}";
                }
                // les arguments restent BRUTS : le modèle neutre ne
                // réécrit jamais ce que le modèle a dit, l'exécuteur
                // les analysera avec le parseur maison
                return PartieContenu.appel(id, nom, arguments);
            }
            return null;
        }
    }

    /** Traduit l'historique neutre en messages Anthropic. */
    static List<Object> messagesAnthropic(List<MessageAgent> messages) {
        List<Object> sortie = new ArrayList<>();
        for (MessageAgent message : messages) {
            List<Object> blocs = new ArrayList<>();
            for (PartieContenu partie : message.parties) {
                switch (partie.genre) {
                    case TEXTE -> blocs.add(Map.of("type", "text",
                            "text", partie.texte));
                    case RAISONNEMENT -> blocs.add(Map.of("type",
                            "thinking", "thinking", partie.texte,
                            "signature", partie.signature));
                    case APPEL_OUTIL -> blocs.add(Map.of("type",
                            "tool_use", "id", partie.idAppel, "name",
                            partie.nomOutil, "input",
                            argumentsObjet(partie.arguments)));
                    case RESULTAT_OUTIL -> {
                        Map<String, Object> resultat =
                                new LinkedHashMap<>();
                        resultat.put("type", "tool_result");
                        resultat.put("tool_use_id", partie.idAppel);
                        resultat.put("content", partie.texte);
                        if (partie.erreur) {
                            resultat.put("is_error", true);
                        }
                        blocs.add(resultat);
                    }
                    default -> {
                    }
                }
            }
            sortie.add(Map.of("role",
                    message.role == MessageAgent.Role.ASSISTANT
                            ? "assistant" : "user",
                    "content", blocs));
        }
        return sortie;
    }

    /** Traduit les outils neutres en outils Anthropic. */
    static List<Object> outilsAnthropic(List<DescOutil> outils) {
        List<Object> sortie = new ArrayList<>();
        for (DescOutil outil : outils) {
            sortie.add(Map.of("name", outil.nom, "description",
                    outil.description, "input_schema",
                    outil.schema.type() == JsonValue.Type.OBJECT
                            ? outil.schema : Map.of("type", "object",
                            "properties", Map.of())));
        }
        return sortie;
    }

    /** Les arguments bruts re-désérialisés en valeur (jamais inventés). */
    static Object argumentsObjet(String arguments) {
        Json.Document analyse = Json.parse(arguments);
        if (analyse.root != null && analyse.problems.isEmpty()) {
            return analyse.root;
        }
        return Map.of();
    }

    private static int plafond(RequeteLlm requete) {
        return requete.plafondSortie > 0 ? requete.plafondSortie : 4096;
    }

}
