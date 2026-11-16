package jo.joide.app.android.atelier.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.agent.PartieContenu;

/**
 * Une pièce jointe du composeur de l'agent (lot S, ADR-064) : une
 * image (réduite dès l'attachement, multimodale jusqu'au
 * fournisseur), un fichier cité ou la structure du projet — ces deux
 * derniers se FONDENT en blocs balisés devant le message. Classe
 * PURE : la fusion, les noms d'affichage et l'extraction des images
 * vivent ICI, testées JVM ; la vue et l'atelier ne décident rien.
 */
public final class PieceJointe {

    /** Le genre de pièce. */
    public enum Genre {
        /** Une image réduite (mediaType + base64). */
        IMAGE,
        /** Un fichier du projet cité en bloc {@code <file>}. */
        FICHIER,
        /** La structure du projet en bloc {@code <project_tree>}. */
        PROJET
    }

    /** Le plafond du texte d'un fichier cité (le modèle a read_file). */
    public static final int TEXTE_MAX = 8_000;

    /** Le plafond de la structure du projet (en lignes). */
    public static final int ARBRE_MAX_LIGNES = 400;

    /** Le genre. */
    public final Genre genre;
    /** Le nom d'affichage (puce du composeur et de la carte). */
    public final String nom;
    /** Le type de média (IMAGE seule, ex. {@code image/png}). */
    public final String mediaType;
    /** Les octets base64 (IMAGE seule). */
    public final String base64;
    /** Le texte à fondre (FICHIER, PROJET). */
    public final String texte;

    private PieceJointe(Genre genre, String nom, String mediaType,
                        String base64, String texte) {
        this.genre = genre;
        this.nom = Objects.requireNonNull(nom, "nom");
        this.mediaType = mediaType;
        this.base64 = base64;
        this.texte = texte;
    }

    /** @return la pièce image */
    public static PieceJointe image(String nom, String mediaType,
                                    String base64) {
        return new PieceJointe(Genre.IMAGE, nom, mediaType, base64, "");
    }

    /** @return la pièce fichier (texte borné par l'appelant) */
    public static PieceJointe fichier(String nom, String texte) {
        return new PieceJointe(Genre.FICHIER, nom, "", "", texte);
    }

    /** @return la pièce structure du projet */
    public static PieceJointe projet(String texte) {
        return new PieceJointe(Genre.PROJET, "projet", "", "", texte);
    }

    /**
     * Fond les pièces texte DANS le message : les fichiers cités et
     * l'arbre du projet deviennent des blocs balisés DEVANT la
     * question — le modèle sait ce qu'il regarde, les images ne
     * passent pas par ici (elles sont multimodales).
     *
     * @param message la question de l'utilisateur
     * @param pieces  les pièces en attente
     * @return le message fondu (le message seul si rien à fondre)
     */
    public static String fondre(String message, List<PieceJointe> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return message == null ? "" : message;
        }
        StringBuilder fondu = new StringBuilder();
        for (PieceJointe piece : pieces) {
            if (piece.genre == Genre.FICHIER) {
                fondu.append("<file path=\"").append(piece.nom)
                        .append("\">\n").append(piece.texte)
                        .append("\n</file>\n\n");
            } else if (piece.genre == Genre.PROJET) {
                fondu.append("<project_tree>\n").append(piece.texte)
                        .append("\n</project_tree>\n\n");
            }
        }
        return fondu + (message == null ? "" : message);
    }

    /**
     * Les parties IMAGE du message, dans l'ordre — elles entrent dans
     * le message neutre AVANT le texte (multimodal natif).
     *
     * @param pieces les pièces en attente
     * @return les parties image
     */
    public static List<PartieContenu> images(List<PieceJointe> pieces) {
        List<PartieContenu> sortie = new ArrayList<>();
        if (pieces == null) {
            return sortie;
        }
        for (PieceJointe piece : pieces) {
            if (piece.genre == Genre.IMAGE && piece.base64 != null
                    && !piece.base64.isEmpty()) {
                sortie.add(PartieContenu.image(piece.mediaType,
                        piece.base64));
            }
        }
        return sortie;
    }

    /**
     * Les noms d'affichage (les puces du composeur et de la carte
     * message).
     *
     * @param pieces les pièces en attente
     * @return les noms, dans l'ordre
     */
    public static List<String> noms(List<PieceJointe> pieces) {
        List<String> noms = new ArrayList<>();
        if (pieces == null) {
            return noms;
        }
        for (PieceJointe piece : pieces) {
            noms.add(piece.nom);
        }
        return noms;
    }

    /**
     * Le texte d'un fichier cité, borné honnêtement : ce qui dépasse
     * est annoncé, jamais coupé en silence (le modèle a read_file
     * pour le reste).
     *
     * @param texte le contenu du fichier
     * @return le texte borné
     */
    public static String bornerTexte(String texte) {
        if (texte == null || texte.length() <= TEXTE_MAX) {
            return texte == null ? "" : texte;
        }
        return texte.substring(0, TEXTE_MAX) + "\n[… tronqué — "
                + (texte.length() - TEXTE_MAX)
                + " caractères de plus — read_file a le reste]";
    }

    /**
     * La structure du projet : les chemins de l'arbre, bornés en
     * lignes — ce qui dépasse est annoncé.
     *
     * @param chemins les chemins de l'arbre du projet
     * @return le texte de l'arbre
     */
    public static String arbre(List<String> chemins) {
        if (chemins == null || chemins.isEmpty()) {
            return "";
        }
        StringBuilder sortie = new StringBuilder();
        int lignes = 0;
        for (String chemin : chemins) {
            if (lignes >= ARBRE_MAX_LIGNES) {
                sortie.append("[… ").append(chemins.size() - lignes)
                        .append(" fichiers de plus]");
                break;
            }
            sortie.append(chemin).append('\n');
            lignes++;
        }
        return sortie.toString();
    }

    @Override
    public String toString() {
        return "PieceJointe[" + genre + ", " + nom + "]";
    }
}
