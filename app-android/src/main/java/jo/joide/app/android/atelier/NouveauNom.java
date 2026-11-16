package jo.joide.app.android.atelier;

/**
 * La validation du nom d'un nouveau fichier ou dossier du tiroir (lot
 * J, ADR-É5) : la règle est pure et testée JVM, la vue traduit les
 * raisons. Un nom refusé ne crée jamais rien sur le disque.
 *
 * <p>Règles : non vide, 100 caractères au plus, aucun séparateur de
 * chemin (le nouveau fichier vit DANS le dossier cible affiché par le
 * dialogue — jamais un chemin déguisé), pas « . » ni « .. », aucun
 * caractère de contrôle, pas de point final (portabilité bureau).</p>
 */
public final class NouveauNom {

    /** Longueur maximale d'un nom (caractères). */
    public static final int LONGUEUR_MAX = 100;

    /** Pourquoi un nom est refusé (traduit par la vue). */
    public enum Raison {
        /** Le nom est vide. */
        VIDE,
        /** Le nom dépasse la longueur maximale. */
        TROP_LONG,
        /** Le nom porte un séparateur de chemin. */
        SEPARATEUR,
        /** Le nom est « . » ou « .. ». */
        POINT,
        /** Le nom porte un caractère de contrôle. */
        CONTROLE,
        /** Le nom se termine par un point. */
        POINT_FINAL
    }

    private NouveauNom() {
    }

    /**
     * Valide un nom de fichier ou de dossier.
     *
     * @return la raison du refus, ou null si le nom est recevable
     */
    public static Raison valider(String nom) {
        if (nom == null || nom.isEmpty()) {
            return Raison.VIDE;
        }
        if (nom.length() > LONGUEUR_MAX) {
            return Raison.TROP_LONG;
        }
        if (nom.indexOf('/') >= 0 || nom.indexOf('\\') >= 0) {
            return Raison.SEPARATEUR;
        }
        if (".".equals(nom) || "..".equals(nom)) {
            return Raison.POINT;
        }
        for (int i = 0; i < nom.length(); i++) {
            if (Character.isISOControl(nom.charAt(i))) {
                return Raison.CONTROLE;
            }
        }
        if (nom.charAt(nom.length() - 1) == '.') {
            return Raison.POINT_FINAL;
        }
        return null;
    }
}
