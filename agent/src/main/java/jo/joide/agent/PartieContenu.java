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
 * <p>Quatre genres fermés : texte, raisonnement (avec signature),
 * appel d'outil, résultat d'outil. Immuable.</p>
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
        RESULTAT_OUTIL
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

    private PartieContenu(Genre genre, String texte, String signature,
                          String idAppel, String nomOutil,
                          String arguments, boolean erreur) {
        this.genre = genre;
        this.texte = texte;
        this.signature = signature;
        this.idAppel = idAppel;
        this.nomOutil = nomOutil;
        this.arguments = arguments;
        this.erreur = erreur;
    }

    /** @param texte le texte @return une partie texte */
    public static PartieContenu texte(String texte) {
        return new PartieContenu(Genre.TEXTE,
                Objects.requireNonNull(texte, "text"), "", "", "", "",
                false);
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
                "", false);
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
                arguments, false);
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
                "", "", erreur);
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
                && erreur == partie.erreur;
    }

    @Override
    public int hashCode() {
        int resultat = genre.hashCode();
        resultat = 31 * resultat + texte.hashCode();
        resultat = 31 * resultat + signature.hashCode();
        resultat = 31 * resultat + idAppel.hashCode();
        resultat = 31 * resultat + nomOutil.hashCode();
        resultat = 31 * resultat + arguments.hashCode();
        return 31 * resultat + (erreur ? 1 : 0);
    }

    @Override
    public String toString() {
        return "PartieContenu[" + genre + "]";
    }
}
