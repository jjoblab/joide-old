package jo.joide.agent.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * Un outil d'un serveur MCP connecté, proposé au modèle sous
 * {@code mcp__<serveur>__<outil>} (lot R, ADR-063).
 *
 * <p>MUTANT sauf {@code readOnlyHint} du serveur — donc soumis au
 * même gate que les écritures locales : une approbation porte sur ce
 * qui peut changer quelque chose HORS du projet (un problème ouvert,
 * un message envoyé), comme pour une édition de fichier. La
 * description est préfixée du nom du serveur (tronquée à 1 000) et le
 * schéma est NORMALISÉ avant envoi aux fournisseurs.</p>
 */
public final class OutilMcpDistant implements OutilAgent {

    /** Le préfixe des outils distants. */
    public static final String PREFIXE = "mcp__";

    /** La troncature d'une description tiers. */
    static final int DESCRIPTION_MAX = 1_000;

    /** Le nom du serveur. */
    public final String nomServeur;
    /** L'info du serveur. */
    public final ClientMcp.OutilInfo info;
    /** Le nom offert au modèle (dédoublonné). */
    public final String nomOutil;

    private final DescOutil description;
    private final Appel appel;

    /** L'appel réellement délégué au gestionnaire. */
    public interface Appel {
        /**
         * @param nomLocal  le nom du serveur configuré
         * @param outil     le nom de l'outil CHEZ LE SERVEUR
         * @param arguments les arguments en objet JSON
         * @return le résultat aplati
         */
        ClientMcp.ResultatAppel appeler(String nomLocal, String outil,
                                        JsonValue arguments);
    }

    /**
     * @param nomServeur le nom du serveur configuré
     * @param info       l'outil tel que listé
     * @param nomOutil   le nom dédoublonné offert au modèle
     * @param appel      la délégation au gestionnaire
     */
    public OutilMcpDistant(String nomServeur, ClientMcp.OutilInfo info,
                           String nomOutil, Appel appel) {
        this.nomServeur = nomServeur;
        this.info = info;
        this.nomOutil = nomOutil;
        this.appel = appel;
        String descriptionServeur = info.description != null
                && !info.description.isBlank() ? info.description.trim()
                : info.titre != null && !info.titre.isBlank()
                ? info.titre.trim() : info.nom;
        if (descriptionServeur.length() > DESCRIPTION_MAX) {
            descriptionServeur = descriptionServeur.substring(0,
                    DESCRIPTION_MAX);
        }
        JsonValue schema = NormalisationSchemas.normaliser(info.schema);
        this.description = new DescOutil(nomOutil,
                "[MCP server \"" + nomServeur + "\"] "
                        + descriptionServeur,
                schema, info.lectureSeule == null
                || !info.lectureSeule, 20_000);
    }

    @Override
    public DescOutil description() {
        return description;
    }

    @Override
    public String invoquer(JsonValue arguments, CancelToken jeton) {
        ClientMcp.ResultatAppel resultat = appel.appeler(nomServeur,
                info.nom, arguments);
        if (!resultat.images.isEmpty()) {
            // les images distantes voyagent en note nommée (la partie
            // IMAGE du lot Q est portée par le résultat texte ici —
            // l'hôte les remonte à sa discrétion)
            StringBuilder texte = new StringBuilder(resultat.texte);
            for (String[] image : resultat.images) {
                texte.append("\n[remote image: ").append(image[0])
                        .append(", ").append(image[1].length())
                        .append(" base64 characters]");
            }
            return texte.toString();
        }
        return resultat.texte;
    }

    /**
     * {@code mcp__<serveur>__<outil>} dans les caractères que tous
     * les fournisseurs acceptent, 64 au plus ; deux serveurs dont les
     * noms se nettoient pareil survivent par suffixe.
     *
     * @param serveur le nom du serveur configuré
     * @param outil   le nom de l'outil chez le serveur
     * @param pris    les noms déjà pris
     * @return le nom offert au modèle
     */
    public static String nomOutil(String serveur, String outil,
                                  Set<String> pris) {
        String base = (PREFIXE + nettoyer(serveur) + "__"
                + nettoyer(outil));
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        if (!pris.contains(base)) {
            return base;
        }
        int suffixe = 2;
        while (true) {
            String fin = "_" + suffixe;
            String candidat = base.substring(0, 64 - fin.length())
                    + fin;
            if (!pris.contains(candidat)) {
                return candidat;
            }
            suffixe++;
        }
    }

    /** Les caractères que tous les fournisseurs acceptent. */
    private static String nettoyer(String nom) {
        StringBuilder sortie = new StringBuilder();
        for (char c : nom.toCharArray()) {
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9' || c == '_' || c == '-') {
                sortie.append(c);
            } else {
                sortie.append('_');
            }
        }
        String net = sortie.toString();
        int premier = 0;
        while (premier < net.length() && net.charAt(premier) == '_') {
            premier++;
        }
        int dernier = net.length();
        while (dernier > premier && net.charAt(dernier - 1) == '_') {
            dernier--;
        }
        net = net.substring(premier, dernier);
        return net.isEmpty() ? "x" : net;
    }

    /** Le résumé d'arguments pour la permission et le transcript (les trois premiers scalaires). */
    public static String resume(String nomServeur,
                                ClientMcp.OutilInfo info,
                                String arguments) {
        String libelle = nomServeur + ": "
                + (info.titre != null && !info.titre.isBlank()
                ? info.titre : info.nom);
        Json.Document document = Json.parse(arguments == null ? "{}"
                : arguments);
        JsonValue racine = document.root;
        List<String> morceaux = new ArrayList<>();
        if (racine != null && document.problems.isEmpty()
                && racine.type() == JsonValue.Type.OBJECT) {
            for (String cle : racine.keys()) {
                if (morceaux.size() >= 3) {
                    break;
                }
                JsonValue valeur = racine.member(cle);
                if (valeur.type() == JsonValue.Type.STRING) {
                    String t = valeur.asString();
                    morceaux.add(cle + ": " + (t.length() > 40
                            ? t.substring(0, 40) : t));
                } else if (valeur.type() == JsonValue.Type.NUMBER
                        || valeur.type() == JsonValue.Type.BOOLEAN) {
                    morceaux.add(cle + ": "
                            + JsonEcrire.valeur(valeur));
                }
            }
        }
        return morceaux.isEmpty() ? libelle : libelle + " ("
                + String.join(", ", morceaux) + ")";
    }
}
