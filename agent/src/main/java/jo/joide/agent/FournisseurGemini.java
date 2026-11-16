package jo.joide.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * L'adaptateur Gemini (lot Q, ADR-061) — le troisième fournisseur de
 * l'ADR-É10 : {@code streamGenerateContent?alt=sse}, rôles
 * {@code user}/{@code model}, résultats d'outils en parts
 * {@code functionResponse}, appels ENTIERS en parts
 * {@code functionCall} (jamais diffusés). La clé part par en-tête
 * {@code x-goog-api-key} — jamais dans l'URL.
 *
 * <p>Les particularités que l'adaptateur couvre :</p>
 * <ul>
 *   <li>pas de rôle système en conversation : l'ancrage stable part
 *       en {@code system_instruction} (préfixe cachable), la note
 *       volatile se plie en part {@code <system-reminder>} du
 *       DERNIER tour utilisateur — rafraîchie sans toucher un octet
 *       en amont ;</li>
 *   <li>la {@code thoughtSignature} reçue par appel d'outil revient
 *       sur la {@code functionCall} correspondante au tour suivant,
 *       sans quoi l'outil est refusé — elle voyage dans la partie
 *       appel (signature opaque) ;</li>
 *   <li>pas d'identifiant d'appel : le nom de l'outil sert de
 *       corrélation, dédoublonné par un suffixe {@code #N} ;</li>
 *   <li>l'usage : {@code promptTokenCount} CONTIENT les lectures du
 *       cache — la soustraction se fait ici, le modèle neutre garde
 *       les entrées non cachées ;</li>
 *   <li>pas d'événement terminal : usage et fin s'émettent à la fin
 *       du flux ; un flux qui n'a rien rendu est ILLISIBLE, jamais
 *       un tour vide muet ;</li>
 *   <li>les schémas d'outils perdent {@code additionalProperties} à
 *       toute profondeur (Gemini les rejette) ;</li>
 *   <li>le cache de contexte serveur (lot Q) : l'instruction
 *       système + les outils se posent côté fournisseur à partir du
 *       deuxième tour, politique conservatrice de
 *       {@link CacheContexteGemini} — repli inline, jamais une perte
 *       nette.</li>
 * </ul>
 *
 * <p>Les budgets de réflexion : Gemini 3 prend un NIVEAU et rejette
 * un budget, la famille 2.5 prend un budget et rejette un niveau —
 * envoyer le mauvais échoue la requête entière. {@code
 * maxOutputTokens} plafonne réflexion ET réponse ENSEMBLE : la marge
 * de réflexion s'ajoute au plafond demandé.</p>
 */
public final class FournisseurGemini implements FournisseurLlm {

    /** L'URL de base de l'API Gemini. */
    public static final String URL_DEFAUT =
            "https://generativelanguage.googleapis.com";

    /** Le plafond absolu d'une réponse Gemini (réflexion incluse). */
    public static final int PLAFOND_SORTIE_MAX = 65_536;
    /** La marge de réflexion par défaut (niveau non posé). */
    public static final int MARGE_DEFAUT = 16_384;
    /** Le minimum de réflexion sur un modèle Pro (famille 2.5). */
    public static final int MINIMUM_PRO = 128;

    private final String urlBase;
    private final String cleApi;
    /** Le niveau de réflexion demandé (null : défaut du modèle). */
    private final String niveauReflexion;
    private final CacheContexteGemini cache;
    private final boolean compactionDeleguee;

    /**
     * @param cleApi la clé d'API Gemini
     */
    public FournisseurGemini(String cleApi) {
        this(URL_DEFAUT, cleApi, null, new CacheContexteGemini());
    }

    /**
     * @param urlBase l'URL de base (surchargeable pour une passerelle)
     * @param cleApi  la clé d'API
     */
    public FournisseurGemini(String urlBase, String cleApi) {
        this(urlBase, cleApi, null, new CacheContexteGemini());
    }

    /**
     * @param urlBase         l'URL de base
     * @param cleApi          la clé d'API
     * @param niveauReflexion le niveau de réflexion ({@code minimal},
     *                        {@code low}, {@code medium},
     *                        {@code high} ; null : défaut du modèle)
     * @param cache           le cache de contexte de la session
     */
    public FournisseurGemini(String urlBase, String cleApi,
                             String niveauReflexion,
                             CacheContexteGemini cache) {
        String net = urlBase == null || urlBase.isBlank() ? URL_DEFAUT
                : urlBase.trim();
        while (net.endsWith("/")) {
            net = net.substring(0, net.length() - 1);
        }
        this.urlBase = net;
        this.cleApi = cleApi == null ? "" : cleApi;
        this.niveauReflexion = niveauReflexion;
        this.cache = cache == null ? new CacheContexteGemini() : cache;
        this.compactionDeleguee = true;
    }

    @Override
    public String nom() {
        return "gemini";
    }

    /**
     * Gemini gère le contexte côté serveur (cache de contexte) :
     * l'élagage local se désactive quand CE fournisseur est choisi.
     *
     * @return vrai (mode délégué)
     */
    public boolean compactionDeleguee() {
        return compactionDeleguee;
    }

    @Override
    public MessageAgent envoyer(TransportHttp transport,
                                RequeteLlm requete, CancelToken jeton,
                                Consumer<EvenementFlux> ecouteur) {
        if (cleApi.isEmpty()) {
            throw ErreursLlm.cleManquante(nom());
        }
        // le cache résolu pour ce préfixe stable (null : inline)
        String nomCache = cache.resoudre(cleCache(requete),
                (int) Math.min(Integer.MAX_VALUE,
                        jetonsCachables(requete)),
                () -> creerCache(transport, requete, jeton));
        Map<String, Object> corps = new LinkedHashMap<>();
        if (nomCache != null) {
            // le système et les outils vivent dans le cache : les
            // renvoyer inline aussi est REJETÉ
            corps.put("cachedContent", nomCache);
        } else {
            corps.put("system_instruction", Map.of("parts",
                    List.of(Map.of("text", requete.systeme))));
            if (!requete.outils.isEmpty()) {
                corps.put("tools", List.of(Map.of(
                        "function_declarations",
                        declarations(requete.outils))));
            }
        }
        corps.put("contents", contents(requete.messages,
                requete.noteFinale));
        corps.put("generationConfig", generationConfig(requete));
        Map<String, String> entetes = new LinkedHashMap<>();
        entetes.put("content-type", "application/json");
        entetes.put("accept", "text/event-stream");
        entetes.put("x-goog-api-key", cleApi);
        TransportHttp.ReponseSse reponse;
        try {
            reponse = transport.postSse(urlBase + "/v1beta/models/"
                    + requete.modele
                    + ":streamGenerateContent?alt=sse", entetes,
                    JsonEcrire.valeur(corps), jeton);
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
        if (lecture.aRienRendu()) {
            // un flux qui n'a rien rendu est illisible : jamais un
            // tour vide muet que la boucle lirait comme une fin
            throw ErreursLlm.illisible(
                    "stream produced no candidates and no usage");
        }
        if (lecture.refuse) {
            throw ErreursLlm.contenuRefuse(
                    "finishReason=" + lecture.raisonArret);
        }
        if (ecouteur != null) {
            ecouteur.accept(EvenementFlux.fin(lecture.parties(),
                    lecture.raisonArret));
            ecouteur.accept(EvenementFlux.usage(lecture.usage()));
        }
        return new MessageAgent(MessageAgent.Role.ASSISTANT,
                lecture.parties());
    }

    /** La création du cache serveur : nom, ou null sur tout échec. */
    private String creerCache(TransportHttp transport,
                              RequeteLlm requete, CancelToken jeton) {
        try {
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("model", "models/" + requete.modele);
            if (!requete.systeme.isBlank()) {
                corps.put("system_instruction", Map.of("parts",
                        List.of(Map.of("text", requete.systeme))));
            }
            if (!requete.outils.isEmpty()) {
                corps.put("tools", List.of(Map.of(
                        "function_declarations",
                        declarations(requete.outils))));
            }
            corps.put("ttl", TTL + "s");
            TransportHttp.Reponse reponse = transport.post(
                    urlBase + "/v1beta/cachedContents",
                    Map.of("content-type", "application/json",
                            "x-goog-api-key", cleApi),
                    JsonEcrire.valeur(corps), jeton);
            Json.Document document = Json.parse(reponse.corps());
            JsonValue racine = document.root;
            if (racine == null || !document.problems.isEmpty()) {
                return null;
            }
            String nom = racine.member("name").asString();
            return nom == null || nom.isBlank() ? null : nom;
        } catch (Exception e) {
            // tout échec : repli inline, l'échec sera mémorisé par la
            // clé de charge utile (jamais une retente par tour)
            return null;
        }
    }

    /** La clé de charge utile : modèle + système + surface d'outils. */
    static String cleCache(RequeteLlm requete) {
        StringBuilder cle = new StringBuilder();
        cle.append(requete.modele).append('|')
                .append(requete.systeme.hashCode());
        for (DescOutil outil : requete.outils) {
            cle.append('|').append(outil.nom).append(':')
                    .append(outil.description.length());
        }
        return cle.toString();
    }

    /** L'estimation des jetons cachables (système + outils, /4). */
    static long jetonsCachables(RequeteLlm requete) {
        long caracteres = requete.systeme.length();
        for (DescOutil outil : requete.outils) {
            caracteres += outil.nom.length()
                    + outil.description.length();
            if (outil.schema != null && !outil.schema.isNull()) {
                caracteres += JsonEcrire.valeur(outil.schema).length();
            }
        }
        return caracteres / 4;
    }

    /**
     * La génération de contenu : plafond de sortie avec la MARGE de
     * réflexion (le plafond couvre réflexion ET réponse ensemble) et
     * la réflexion au dialecte de la génération du modèle.
     */
    private Map<String, Object> generationConfig(RequeteLlm requete) {
        int plafond = requete.plafondSortie > 0
                ? requete.plafondSortie : 4096;
        Map<String, Object> config = new LinkedHashMap<>();
        boolean gen3 = generation(requete.modele) >= 3;
        String niveau = niveauGemini3(requete.modele);
        Integer budget = budgetFamille25(requete.modele);
        int marge = gen3 ? margeNiveau(niveau) : margeBudget(budget);
        config.put("maxOutputTokens",
                Math.min(plafond + marge, PLAFOND_SORTIE_MAX));
        if (niveau != null) {
            config.put("thinkingConfig",
                    Map.of("thinkingLevel", niveau));
        } else if (budget != null) {
            config.put("thinkingConfig",
                    Map.of("thinkingBudget", budget));
        }
        return config;
    }

    /**
     * Le niveau Gemini 3 : le niveau demandé ; sur un Pro,
     * {@code minimal} n'existe pas (minimum forcé 128) et devient
     * {@code low}. Null hors niveau connu ou modèle 2.x.
     */
    private String niveauGemini3(String modele) {
        if (niveauReflexion == null || generation(modele) < 3) {
            return null;
        }
        boolean connu = "minimal".equals(niveauReflexion)
                || "low".equals(niveauReflexion)
                || "medium".equals(niveauReflexion)
                || "high".equals(niveauReflexion);
        if (!connu) {
            return null;
        }
        if (modele.toLowerCase().contains("pro")
                && "minimal".equals(niveauReflexion)) {
            return "low";
        }
        return niveauReflexion;
    }

    /**
     * Le budget de la famille 2.5 : le budget issu du niveau demandé
     * (minimal 0, low 2 048, medium 8 192, high 16 384) — un Pro ne
     * peut pas couper la réflexion (minimum 128 forcé). Null hors
     * niveau connu ou modèle 3+.
     */
    private Integer budgetFamille25(String modele) {
        if (niveauReflexion == null || generation(modele) >= 3) {
            return null;
        }
        int budget = switch (niveauReflexion) {
            case "minimal" -> 0;
            case "low" -> 2_048;
            case "medium" -> 8_192;
            case "high" -> 16_384;
            default -> -1;
        };
        if (budget < 0) {
            return null;
        }
        boolean pro = modele.toLowerCase().contains("pro");
        return pro ? Math.max(budget, MINIMUM_PRO) : budget;
    }

    /** La marge de réflexion d'un niveau Gemini 3. */
    static int margeNiveau(String niveau) {
        if (niveau == null) {
            return MARGE_DEFAUT;
        }
        return switch (niveau) {
            case "minimal" -> 1_024;
            case "low" -> 4_096;
            case "medium" -> 12_288;
            default -> 24_576;
        };
    }

    /** La marge de réflexion d'un budget 2.5. */
    static int margeBudget(Integer budget) {
        if (budget == null) {
            return MARGE_DEFAUT;
        }
        return switch (budget) {
            case 0 -> 1_024;
            default -> Math.min(24_576, budget + 4_096);
        };
    }

    /**
     * La génération majeure d'un identifiant de modèle
     * ({@code gemini-3.8-flash} → 3), 0 hors forme reconnue.
     */
    static int generation(String modele) {
        String m = modele.startsWith("models/") ? modele
                .substring("models/".length()) : modele;
        if (!m.startsWith("gemini-")) {
            return 0;
        }
        String suite = m.substring("gemini-".length());
        int point = suite.indexOf('.');
        if (point <= 0) {
            return 0;
        }
        try {
            return Integer.parseInt(suite.substring(0, point));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Les déclarations d'outils, schémas strippés de
     *  {@code additionalProperties} à toute profondeur. */
    static List<Object> declarations(List<DescOutil> outils) {
        List<Object> sortie = new ArrayList<>();
        for (DescOutil outil : outils) {
            Object schema = outil.schema != null
                    && outil.schema.type() == JsonValue.Type.OBJECT
                    ? striper(outil.schema)
                    : Map.of("type", "object", "properties", Map.of());
            Map<String, Object> declaration = new LinkedHashMap<>();
            declaration.put("name", outil.nom);
            declaration.put("description", outil.description);
            declaration.put("parameters", schema);
            sortie.add(declaration);
        }
        return sortie;
    }

    /** Le strippage récursif de {@code additionalProperties} —
     *  structures mutables équivalentes pour l'écriture compacte. */
    static Object striper(JsonValue valeur) {
        if (valeur.type() == JsonValue.Type.OBJECT) {
            Map<String, Object> objet = new LinkedHashMap<>();
            for (String cle : valeur.keys()) {
                if ("additionalProperties".equals(cle)) {
                    continue;
                }
                objet.put(cle, striper(valeur.member(cle)));
            }
            return objet;
        }
        if (valeur.type() == JsonValue.Type.ARRAY) {
            List<Object> items = new ArrayList<>();
            for (JsonValue item : valeur.items()) {
                items.add(striper(item));
            }
            return items;
        }
        return valeur;
    }

    /**
     * La conversation : rôles {@code user}/{@code model}, résultats
     * en {@code functionResponse}, la note volatile pliée en part
     * {@code <system-reminder>} du dernier tour utilisateur, les
     * images en {@code inline_data}.
     */
    static List<Object> contents(List<MessageAgent> messages,
                                 String noteFinale) {
        List<Object> sortie = new ArrayList<>();
        for (MessageAgent message : messages) {
            Map<String, Object> contenu = new LinkedHashMap<>();
            if (message.role == MessageAgent.Role.ASSISTANT) {
                contenu.put("role", "model");
                List<Object> parts = new ArrayList<>();
                for (PartieContenu partie : message.parties) {
                    switch (partie.genre) {
                        case TEXTE -> {
                            if (!partie.texte.isEmpty()) {
                                parts.add(Map.of("text", partie.texte));
                            }
                        }
                        case APPEL_OUTIL -> {
                            Map<String, Object> appel =
                                    new LinkedHashMap<>();
                            appel.put("name", partie.nomOutil);
                            appel.put("args", argumentsObjet(
                                    partie.arguments));
                            Map<String, Object> part =
                                    new LinkedHashMap<>();
                            part.put("functionCall", appel);
                            if (!partie.signature.isEmpty()) {
                                // la signature de réflexion reçue
                                // avec l'appel revient TELLE QUE —
                                // sans elle l'outil est refusé
                                part.put("thoughtSignature",
                                        partie.signature);
                            }
                            parts.add(part);
                        }
                        default -> {
                            // le raisonnement ne repart pas : Gemini
                            // le replace par sa propre réflexion
                        }
                    }
                }
                contenu.put("parts", parts);
            } else {
                contenu.put("role", "user");
                List<Object> parts = new ArrayList<>();
                for (PartieContenu partie : message.parties) {
                    switch (partie.genre) {
                        case TEXTE -> parts.add(Map.of("text",
                                partie.texte));
                        case IMAGE -> parts.add(inlineData(
                                partie.mediaType, partie.donnees));
                        case RESULTAT_OUTIL -> {
                            Map<String, Object> reponse =
                                    new LinkedHashMap<>();
                            reponse.put("name",
                                    nomCorrelation(partie.idAppel));
                            reponse.put("response", Map.of("result",
                                    partie.texte));
                            parts.add(Map.of("functionResponse",
                                    reponse));
                        }
                        default -> {
                        }
                    }
                }
                contenu.put("parts", parts);
            }
            sortie.add(contenu);
        }
        plierNote(sortie, noteFinale);
        return sortie;
    }

    /** Le nom de corrélation d'un résultat : l'identifiant avant
     *  {@code #} (le nom de l'outil Gemini). */
    static String nomCorrelation(String idAppel) {
        int diese = idAppel.indexOf('#');
        return diese >= 0 ? idAppel.substring(0, diese) : idAppel;
    }

    /** Les arguments d'un appel : objet JSON, jamais une chaîne. */
    static Object argumentsObjet(String arguments) {
        Json.Document document = Json.parse(arguments == null
                || arguments.isBlank() ? "{}" : arguments);
        if (document.root == null || !document.problems.isEmpty()
                || document.root.type() != JsonValue.Type.OBJECT) {
            return new LinkedHashMap<>();
        }
        return document.root;
    }

    /** Une image en {@code inline_data}. */
    static Object inlineData(String mediaType, String donnees) {
        return Map.of("inline_data", Map.of("mime_type", mediaType,
                "data", donnees));
    }

    /**
     * Plie la note volatile en part {@code <system-reminder>} du
     * DERNIER tour utilisateur (le tour courant est neuf à chaque
     * itération : le préfixe stable ne dérive pas) — ou ouvre un
     * tour utilisateur si l'historique n'en a pas encore.
     */
    static void plierNote(List<Object> contents, String noteFinale) {
        if (noteFinale == null || noteFinale.isEmpty()) {
            return;
        }
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", "<system-reminder>\n" + noteFinale
                + "\n</system-reminder>");
        if (!contents.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dernier =
                    (Map<String, Object>) contents.get(
                            contents.size() - 1);
            if ("user".equals(dernier.get("role"))) {
                @SuppressWarnings("unchecked")
                List<Object> parts =
                        (List<Object>) dernier.get("parts");
                List<Object> etendus = new ArrayList<>(parts);
                etendus.add(part);
                dernier.put("parts", etendus);
                return;
            }
        }
        Map<String, Object> tour = new LinkedHashMap<>();
        tour.put("role", "user");
        tour.put("parts", List.of(part));
        contents.add(tour);
    }

    /** L'état de lecture d'un flux Gemini. */
    private static final class Lecture {
        final List<PartieContenu> parties = new ArrayList<>();
        final Map<String, Integer> correlations = new HashMap<>();
        long entrees;
        long sorties;
        long lecturesCache;
        String raisonArret = "";
        boolean aCandidat;
        boolean refuse;

        void trame(TransportHttp.TrameSse trame,
                   Consumer<EvenementFlux> ecouteur) {
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
            JsonValue usage = racine.member("usageMetadata");
            if (usage.type() == JsonValue.Type.OBJECT) {
                entrees = usage.member("promptTokenCount").asLong();
                sorties = usage.member("candidatesTokenCount")
                        .asLong();
                // promptTokenCount CONTIENT le cache : le modèle
                // neutre garde le RESTE non caché
                lecturesCache = usage.member("cachedContentTokenCount")
                        .asLong();
            }
            JsonValue candidats = racine.member("candidates");
            if (candidats.type() != JsonValue.Type.ARRAY) {
                return;
            }
            JsonValue candidat = candidats.items().isEmpty() ? null
                    : candidats.items().get(0);
            if (candidat == null || candidat.isNull()) {
                return;
            }
            aCandidat = true;
            JsonValue content = candidat.member("content");
            if (content.type() == JsonValue.Type.OBJECT) {
                for (JsonValue part : content.member("parts").items()) {
                    String texte = part.member("text").asString();
                    boolean reflechi = part.member("thought")
                            .asBoolean() == Boolean.TRUE;
                    if (texte != null && !texte.isEmpty()) {
                        if (ecouteur != null) {
                            ecouteur.accept(reflechi
                                    ? EvenementFlux.raisonnement(texte)
                                    : EvenementFlux.texte(texte));
                        }
                        parties.add(reflechi
                                ? PartieContenu.raisonnement(texte,
                                        "gemini:thought")
                                : PartieContenu.texte(texte));
                    }
                    JsonValue appel = part.member("functionCall");
                    if (appel.type() == JsonValue.Type.OBJECT) {
                        String nom = appel.member("name").asString();
                        String id = correlation(nom);
                        JsonValue args = appel.member("args");
                        String arguments = args.isNull()
                                ? "{}" : JsonEcrire.valeur(args);
                        // la signature de réflexion portée par la
                        // part voyage avec l'appel (opaque, retournée
                        // telle quelle au tour suivant)
                        String signature = part.member(
                                "thoughtSignature").asString();
                        parties.add(PartieContenu.appelSigne(id, nom,
                                arguments,
                                signature == null ? "" : signature));
                    }
                }
            }
            String fin = candidat.member("finishReason").asString();
            if (fin != null) {
                raisonArret = fin;
                if ("MAX_TOKENS".equals(fin)) {
                    raisonArret = "max_tokens";
                } else if ("SAFETY".equals(fin)
                        || "RECITATION".equals(fin)
                        || "BLOCKLIST".equals(fin)
                        || "PROHIBITED_CONTENT".equals(fin)) {
                    refuse = true;
                }
            }
        }

        /** Rien rendu : un flux vide est illisible, jamais un tour
         *  vide muet. */
        boolean aRienRendu() {
            return !aCandidat && entrees == 0 && sorties == 0;
        }

        /** La corrélation par nom, dédoublonnée par suffixe {@code #N}. */
        private String correlation(String nom) {
            int count = correlations.getOrDefault(nom, 0);
            correlations.put(nom, count + 1);
            return count == 0 ? nom : nom + "#" + count;
        }

        List<PartieContenu> parties() {
            return parties;
        }

        UsageLlm usage() {
            // la soustraction du cache : les entrées facturées sont
            // le reste non caché
            return new UsageLlm(
                    Math.max(0, entrees - lecturesCache),
                    sorties, lecturesCache);
        }
    }

    /** Le TTL des caches serveur (secondes). */
    static final long TTL = CacheContexteGemini.TTL_SECONDES;
}
