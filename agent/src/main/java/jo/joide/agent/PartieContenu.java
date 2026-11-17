package jo.joide.agent;

import java.util.Objects;

/**
 * Une partie de contenu d'un message de conversation — le modèle
 * NEUTRE de l'ADR-É9 : la conversation ne connaît aucun dialecte
 * fournisseur, mais l'aller-retour conversation-fournisseur ne perd
 * RIEN. Chaque partie porte une SIGNATURE OPAQUE que les
 * fournisseurs remplissent (signature de raisonnement d'Anthropic,
 * raisonnement chiffré d'OpenAI tagué) : opaque pour le noyau, vitale
 * pour le fournisseur au tour suivant.
 *
 * <p>Cinq genres fermés : texte, raisonnement (avec signature),
 * appel d'outil, résultat d'outil, image (lot Q). Immuable.</p>
 */
public final class PartieContenu {

    /** Le genre de la partie. */
    public enum Genre {
        /** Un morceau de texte affichable. */
        TEXTE,
        /** Un raisonnement du modèle (replié à l'affichage). */
        RAISONNEMENT,
        /** Un appel d'outil demandé par le modèle. */
        APPEL_OUTIL,
        /** Le résultat d'un appel d'outil (succès ou erreur). */
        RESULTAT_OUTIL,
        /**
         * Une image portée par la conversation (lot Q, ADR-062) —
         * produite par un outil (capture, rendu) et rangée juste
         * après son résultat ; les fournisseurs la sérialisent à
         * leur dialecte, la réduction côté client passe par
         * {@link ReductionImages} AVANT l'insertion dans
         * l'historique.
         */
        IMAGE
    }

    /** Le genre de cette partie. */
    public final Genre genre;
    /** Le texte (TEXTE, RAISONNEMENT) ou le contenu du résultat. */
    public final String texte;
    /** La signature opaque (RAISONNEMENT), ou vide. */
    public final String signature;
    /** L'identifiant de l'appel (APPEL_OUTIL, RESULTAT_OUTIL). */
    public final String idAppel;
    /** Le nom de l'outil appelé (APPEL_OUTIL), ou vide. */
    public final String nomOutil;
    /** Les arguments bruts JSON (APPEL_OUTIL), ou vide. */
    public final String arguments;
    /** Vrai si le résultat est une erreur d'outil (RESULTAT_OUTIL). */
    public final boolean erreur;
    /** Le type de média (IMAGE, ex. {@code image/png}), ou vide. */
    public final String mediaType;
    /** Les octets de l'image en base64 (IMAGE), ou vide. */
    public final String donnees;

    private PartieContenu(Genre genre, String texte, String signature,
                          String idAppel, String nomOutil,
                          String arguments, boolean erreur,
                          String mediaType, String donnees) {
        this.genre = genre;
        this.texte = texte;
        this.signature = signature;
        this.idAppel = idAppel;
        this.nomOutil = nomOutil;
        this.arguments = arguments;
        this.erreur = erreur;
        this.mediaType = mediaType;
        this.donnees = donnees;
    }

    /** @param texte le texte @return une partie texte */
    public static PartieContenu texte(String texte) {
        return new PartieContenu(Genre.TEXTE,
                Objects.requireNonNull(texte, "text"), "", "", "", "",
                false, "", "");
    }

    /**
     * @param texte     le raisonnement
     * @param signature la signature opaque du fournisseur (jamais
     *                  interprétée ici, rendue telle quelle)
     * @return une partie raisonnement
     */
    public static PartieContenu raisonnement(String texte,
                                             String signature) {
        return new PartieContenu(Genre.RAISONNEMENT,
                Objects.requireNonNull(texte, "text"),
                Objects.requireNonNull(signature, "signature"), "", "",
                "", false, "", "");
    }

    /**
     * @param id        l'identifiant de l'appel (celui du fournisseur)
     * @param nom       le nom de l'outil
     * @param arguments les arguments en JSON compact
     * @return une partie appel d'outil
     */
    public static PartieContenu appel(String id, String nom,
                                      String arguments) {
        return new PartieContenu(Genre.APPEL_OUTIL, "", "", id, nom,
                arguments, false, "", "");
    }

    /**
     * Un appel d'outil PORTEUR d'une signature opaque du fournisseur
     * (lot Q, Gemini : la {@code thoughtSignature} reçue avec l'appel
     * doit revenir sur la {@code functionCall} correspondante au tour
     * suivant, sans quoi l'outil est refusé) — opaque pour le noyau,
     * vitale pour le fournisseur.
     *
     * @param id        l'identifiant de l'appel
     * @param nom       le nom de l'outil
     * @param arguments les arguments en JSON compact
     * @param signature la signature opaque du fournisseur
     * @return une partie appel d'outil signée
     */
    public static PartieContenu appelSigne(String id, String nom,
                                           String arguments,
                                           String signature) {
        return new PartieContenu(Genre.APPEL_OUTIL, "", signature == null
                ? "" : signature, id, nom, arguments, false, "", "");
    }

    /**
     * @param idAppel l'identifiant de l'appel répondu
     * @param contenu le contenu du résultat (borné côté outil,
     *                ADR-É9 leçon 10)
     * @param erreur  vrai si l'outil a échoué
     * @return une partie résultat d'outil
     */
    public static PartieContenu resultat(String idAppel, String contenu,
                                         boolean erreur) {
        return new PartieContenu(Genre.RESULTAT_OUTIL,
                Objects.requireNonNull(contenu, "content"), "", idAppel,
                "", "", erreur, "", "");
    }

    /**
     * Une image de la conversation (lot Q, ADR-062) : type de média
     * et octets base64. La réduction côté client (budget d'octets,
     * ré-encodage par l'hôte) se fait AVANT cette fabrique — une
     * partie IMAGE insérée est déjà à sa taille finale, l'historique
     * ne se réécrit jamais.
     *
     * @param mediaType le type de média (ex. {@code image/png})
     * @param donnees   les octets en base64
     * @return une partie image
     */
    public static PartieContenu image(String mediaType, String donnees) {
        return new PartieContenu(Genre.IMAGE, "", "", "", "", "",
                false,
                Objects.requireNonNull(mediaType, "media type"),
                Objects.requireNonNull(donnees, "data"));
    }

    @Override
    public boolean equals(Object autre) {
        if (!(autre instanceof PartieContenu partie)) {
            return false;
        }
        return genre == partie.genre
                && texte.equals(partie.texte)
                && signature.equals(partie.signature)
                && idAppel.equals(partie.idAppel)
                && nomOutil.equals(partie.nomOutil)
                && arguments.equals(partie.arguments)
                && erreur == partie.erreur
                && mediaType.equals(partie.mediaType)
                && donnees.equals(partie.donnees);
    }

    @Override
    public int hashCode() {
        int resultat = genre.hashCode();
        resultat = 31 * resultat + texte.hashCode();
        resultat = 31 * resultat + signature.hashCode();
        resultat = 31 * resultat + idAppel.hashCode();
        resultat = 31 * resultat + nomOutil.hashCode();
        resultat = 31 * resultat + arguments.hashCode();
        resultat = 31 * resultat + (erreur ? 1 : 0);
        resultat = 31 * resultat + mediaType.hashCode();
        return 31 * resultat + donnees.hashCode();
    }

    @Override
    public String toString() {
        return "PartieContenu[" + genre + "]";
    }
}
