package jo.joide.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.AtomicFiles;

/**
 * La persistance des sessions (ADR-É9, leçon 14) : DOUBLE MOITIÉ —
 * le transcript vu de l'utilisateur (ce que l'UI rejoue) et
 * l'historique modèle (parties, signatures opaques, résultats
 * bornés) — écrites ENTIÈRES et ATOMIQUEMENT (fichier temporaire
 * voisin puis renommage, {@link AtomicFiles}) après chaque tour.
 * Un fichier illisible est SIGNALÉ, jamais réparé en silence.
 *
 * <p>Format {@code joide.session/1} : un objet JSON, clés stables,
 * lisible par le parseur maison.</p>
 */
public final class SessionsAgent {

    private SessionsAgent() {
    }

    /** La session persistée. */
    public static final class Session {
        /** Le nom du fournisseur. */
        public final String fournisseur;
        /** Le modèle utilisé. */
        public final String modele;
        /** Le transcript vu de l'utilisateur (lignes affichables). */
        public final List<String> transcript;
        /** L'historique modèle complet (avec signatures). */
        public final List<MessageAgent> historique;
        /** Les identifiants d'appels compactés (décisions permanentes). */
        public final Set<String> compactes;
        /** L'usage cumulé de la conversation. */
        public final UsageLlm usage;

        /** Construit la session. */
        public Session(String fournisseur, String modele,
                       List<String> transcript,
                       List<MessageAgent> historique,
                       Set<String> compactes, UsageLlm usage) {
            this.fournisseur = fournisseur;
            this.modele = modele;
            this.transcript = List.copyOf(transcript);
            this.historique = List.copyOf(historique);
            this.compactes = Set.copyOf(compactes);
            this.usage = usage;
        }
    }

    /** Une session illisible : le problème est nommé, jamais caché. */
    public static final class SessionIllisible extends IOException {
        private static final long serialVersionUID = 1L;

        /** @param detail le problème rencontré */
        public SessionIllisible(String detail) {
            super(detail);
        }
    }

    /**
     * Écrit la session, entière et atomiquement.
     *
     * @param cible   le fichier de session
     * @param session la session
     * @throws IOException si l'écriture échoue
     */
    public static void ecrire(Path cible, Session session)
            throws IOException {
        List<Object> transcriptJson = new ArrayList<>();
        for (String ligne : session.transcript) {
            transcriptJson.add(ligne);
        }
        List<Object> historiqueJson = new ArrayList<>();
        for (MessageAgent message : session.historique) {
            historiqueJson.add((Object) messageJson(message));
        }
        List<Object> compactesJson = new ArrayList<>();
        compactesJson.addAll(session.compactes);
        String corps = jo.joide.deps.json.JsonEcrire.objet(
                "schema", "joide.session/1",
                "fournisseur", session.fournisseur,
                "modele", session.modele,
                "transcript", transcriptJson,
                "historique", historiqueJson,
                "compactes", compactesJson,
                "usage", usageJson(session.usage));
        AtomicFiles.writeBytes(cible,
                corps.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Lit une session.
     *
     * @param cible le fichier de session
     * @return la session relue
     * @throws SessionIllisible si le fichier ne se lit pas (schéma
     *                          inconnu, JSON cassé, structure fausse)
     */
    public static Session lire(Path cible) throws IOException {
        byte[] octets = Files.readAllBytes(cible);
        Json.Document document = Json.parse(
                new String(octets, StandardCharsets.UTF_8));
        if (document.root == null || !document.problems.isEmpty()
                || document.root.type() != JsonValue.Type.OBJECT) {
            throw new SessionIllisible("unreadable session JSON: "
                    + cible.getFileName());
        }
        JsonValue racine = document.root;
        if (!"joide.session/1".equals(
                racine.member("schema").asString())) {
            throw new SessionIllisible("unknown session schema: "
                    + racine.member("schema").asString());
        }
        List<String> transcript = new ArrayList<>();
        for (JsonValue ligne : racine.member("transcript").items()) {
            transcript.add(String.valueOf(ligne.asString()));
        }
        List<MessageAgent> historique = new ArrayList<>();
        for (JsonValue message : racine.member("historique").items()) {
            historique.add(messageDepuis(message));
        }
        Set<String> compactes = new LinkedHashSet<>();
        for (JsonValue id : racine.member("compactes").items()) {
            compactes.add(String.valueOf(id.asString()));
        }
        JsonValue usage = racine.member("usage");
        return new Session(
                racine.member("fournisseur").asString(),
                racine.member("modele").asString(),
                transcript, historique, compactes,
                new UsageLlm(usage.member("entrees").asLong(),
                        usage.member("sorties").asLong(),
                        usage.member("lecturesCache").asLong()));
    }

    private static Map<String, Object> usageJson(UsageLlm usage) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("entrees", usage.entrees);
        objet.put("sorties", usage.sorties);
        objet.put("lecturesCache", usage.lecturesCache);
        return objet;
    }

    private static Map<String, Object> messageJson(
            MessageAgent message) {
        List<Map<String, Object>> parties = new ArrayList<>();
        for (PartieContenu partie : message.parties) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("genre", partie.genre.name());
            objet.put("texte", partie.texte);
            objet.put("signature", partie.signature);
            objet.put("idAppel", partie.idAppel);
            objet.put("outil", partie.nomOutil);
            objet.put("arguments", partie.arguments);
            objet.put("erreur", partie.erreur);
            if (partie.genre == PartieContenu.Genre.IMAGE) {
                // lot S : l'historique modèle garde ce que le modèle
                // a réellement vu — les pixels suivent la session
                objet.put("mediaType", partie.mediaType);
                objet.put("donnees", partie.donnees);
            }
            parties.add(objet);
        }
        Map<String, Object> sortie = new LinkedHashMap<>();
        sortie.put("role", message.role.name());
        sortie.put("parties", parties);
        return sortie;
    }

    private static MessageAgent messageDepuis(JsonValue json) {
        MessageAgent.Role role = "ASSISTANT".equals(
                json.member("role").asString())
                ? MessageAgent.Role.ASSISTANT
                : MessageAgent.Role.UTILISATEUR;
        List<PartieContenu> parties = new ArrayList<>();
        for (JsonValue partie : json.member("parties").items()) {
            String genre = partie.member("genre").asString();
            if ("TEXTE".equals(genre)) {
                parties.add(PartieContenu.texte(
                        partie.member("texte").asString()));
            } else if ("RAISONNEMENT".equals(genre)) {
                parties.add(PartieContenu.raisonnement(
                        partie.member("texte").asString(),
                        partie.member("signature").asString()));
            } else if ("APPEL_OUTIL".equals(genre)) {
                parties.add(PartieContenu.appel(
                        partie.member("idAppel").asString(),
                        partie.member("outil").asString(),
                        partie.member("arguments").asString()));
            } else if ("RESULTAT_OUTIL".equals(genre)) {
                parties.add(PartieContenu.resultat(
                        partie.member("idAppel").asString(),
                        partie.member("texte").asString(),
                        Boolean.TRUE.equals(
                                partie.member("erreur").asBoolean())));
            } else if ("IMAGE".equals(genre)) {
                // lot S : les pixels reviennent avec la session (les
                // fichiers d'avant n'ont pas ces champs — tolérant)
                parties.add(PartieContenu.image(
                        partie.member("mediaType").asString(),
                        partie.member("donnees").asString()));
            }
        }
        return new MessageAgent(role, parties);
    }
}
