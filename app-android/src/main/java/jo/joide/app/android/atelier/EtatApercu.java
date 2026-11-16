package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.List;

/**
 * L'état du volet d'aperçu de mise en page (lot L, ADR-É7) : les
 * contrôles RÉELS du {@code ConfigurationApercu} (densité segmentée,
 * mode nuit, dimensions cibles en dp), les stats MESURÉES de
 * l'écouteur (vues, durée, dimensions — jamais inventées) et les
 * bannières de franchise : frappe (dernier rendu valide conservé),
 * ressources manquantes (repli natif annoncé), MockView (classe
 * inconnue), échec de rendu nominal, avertissements de constitution.
 * Pur, testé JVM : la vue traduit, elle ne décide pas.
 *
 * <p>Le nom du thème n'est jamais deviné (lot E) : null s'affiche
 * « repli hôte » et le dit.</p>
 */
public final class EtatApercu {

    /** La gravité d'une bannière — l'ordre d'affichage. */
    public enum Gravite {
        /** Une erreur de rendu (le dernier rendu valide reste). */
        ERREUR,
        /** Un repli annoncé (ressources manquantes, MockView). */
        ALERTE,
        /** Une information honnête (frappe, constitution). */
        INFO
    }

    /** Le genre d'une bannière — la vue fabrique le texte localisé. */
    public enum Genre {
        /** XML en frappe : le dernier rendu valide est conservé. */
        FRAPPE,
        /** Ressources manquantes : repli aux ressources natives. */
        RESSOURCES,
        /** Classes inconnues rendues en MockView. */
        MOCKVIEW,
        /** Échec de rendu nominal (message de l'écouteur). */
        ERREUR,
        /** Avertissements de constitution de la configuration. */
        CONFIG
    }

    /** Une bannière affichée sous la surface de rendu. */
    public record Banniere(Gravite gravite, Genre genre, String detail) {
    }

    /** Les stats mesurées du dernier rendu réussi. */
    public record Stats(int vues, long dureeMs, int largeur, int hauteur) {
    }

    /** Largeur cible par défaut (dp) — la maquette ADR-É7. */
    public static final int LARGEUR_DEFAUT_DP = 360;

    /** Hauteur cible par défaut (dp). */
    public static final int HAUTEUR_DEFAUT_DP = 640;

    private int densite = 2;
    private boolean nuit;
    private int largeurDp = LARGEUR_DEFAUT_DP;
    private int hauteurDp = HAUTEUR_DEFAUT_DP;
    private Stats stats;
    private boolean frappe;
    private List<String> manquantes = List.of();
    private int mockViews;
    private String erreur;
    private List<String> avertissementsConfig = List.of();
    private String nomTheme;
    private boolean renduValide;

    /** La densité segmentée (1, 2 ou 3). */
    public int densite() {
        return densite;
    }

    /** Change la densité (bornée à 1..3, la maquette est segmentée). */
    public void setDensite(int nouvelle) {
        densite = Math.max(1, Math.min(3, nouvelle));
    }

    /** Le mode nuit (qualificateur {@code -night} du lot E). */
    public boolean nuit() {
        return nuit;
    }

    /** Bascule le mode nuit. */
    public void setNuit(boolean nouveau) {
        nuit = nouveau;
    }

    /** La largeur cible en dp. */
    public int largeurDp() {
        return largeurDp;
    }

    /** La hauteur cible en dp. */
    public int hauteurDp() {
        return hauteurDp;
    }

    /**
     * Change les dimensions cibles (dp) — le rendu reçoit des pixels :
     * {@code dp × densité} (ADR-053).
     */
    public void setDimensions(int largeur, int hauteur) {
        largeurDp = Math.max(120, Math.min(480, largeur));
        hauteurDp = Math.max(240, Math.min(960, hauteur));
    }

    /** La largeur cible en pixels pour la façade (dp × densité). */
    public int largeurPx() {
        return largeurDp * densite;
    }

    /** La hauteur cible en pixels pour la façade (dp × densité). */
    public int hauteurPx() {
        return hauteurDp * densite;
    }

    /** Les stats mesurées du dernier rendu réussi, ou null. */
    public Stats stats() {
        return stats;
    }

    /** Un rendu a réussi : stats mesurées, plus de frappe ni d'erreur. */
    public void surSucces(int vues, long dureeMs, int largeur, int hauteur) {
        stats = new Stats(vues, dureeMs, largeur, hauteur);
        renduValide = true;
        frappe = false;
        erreur = null;
    }

    /** Le XML est en frappe : le dernier rendu valide reste affiché. */
    public void surXmlInvalide() {
        frappe = true;
    }

    /** Un rendu a échoué (message nominal de l'écouteur). */
    public void surErreur(String message) {
        erreur = message;
        frappe = false;
    }

    /** Les références de ressources manquantes du XML courant. */
    public void surRessourcesManquantes(List<String> references) {
        manquantes = references == null ? List.of()
                : List.copyOf(references);
    }

    /** Les classes inconnues rendues en MockView (compte de l'arbre). */
    public void surMockViews(int compte) {
        mockViews = Math.max(0, compte);
    }

    /** Les avertissements de constitution (dossier res/ absent…). */
    public void surAvertissementsConfig(List<String> avertissements) {
        avertissementsConfig = avertissements == null ? List.of()
                : List.copyOf(avertissements);
    }

    /** Le nom du thème, ou null : « repli hôte », jamais deviné. */
    public String nomTheme() {
        return nomTheme;
    }

    /** Pose le nom du thème (null = repli hôte). */
    public void setTheme(String theme) {
        nomTheme = theme;
    }

    /** Vrai dès le premier rendu réussi. */
    public boolean renduValide() {
        return renduValide;
    }

    /**
     * Les bannières à afficher, la plus grave d'abord — l'état honnête
     * du moment : plusieurs bannières coexistent (la maquette empile
     * info et alerte), aucune n'est inventée, aucune ne masque une
     * autre.
     */
    public List<Banniere> bannieres() {
        List<Banniere> bannieres = new ArrayList<>();
        if (erreur != null) {
            bannieres.add(new Banniere(Gravite.ERREUR, Genre.ERREUR,
                    erreur));
        }
        if (!manquantes.isEmpty()) {
            bannieres.add(new Banniere(Gravite.ALERTE,
                    Genre.RESSOURCES, String.join(", ", manquantes)));
        }
        if (mockViews > 0) {
            bannieres.add(new Banniere(Gravite.ALERTE, Genre.MOCKVIEW,
                    String.valueOf(mockViews)));
        }
        for (String avertissement : avertissementsConfig) {
            bannieres.add(new Banniere(Gravite.INFO, Genre.CONFIG,
                    avertissement));
        }
        if (frappe) {
            bannieres.add(new Banniere(Gravite.INFO, Genre.FRAPPE,
                    stats == null ? null
                            : String.valueOf(stats.vues())));
        }
        return bannieres;
    }
}
