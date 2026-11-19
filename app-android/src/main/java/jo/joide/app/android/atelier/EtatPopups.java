package jo.joide.app.android.atelier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L'état des popups de l'atelier (lot A7, ADR-085 décision 12) : pur,
 * testé JVM. Le popup n'est JAMAIS restauré (recréation, rotation) —
 * ce qui compte vit ICI :
 *
 * <ul>
 * <li><b>l'ordre du retour système</b> : un popup ouvert se ferme EN
 * PREMIER, avant le tiroir agent, la feuille, le tiroir et la sortie
 * ({@link #surRetour(boolean, boolean, EtatAtelier)} — la décision
 * pure que l'écran déroule) ;</li>
 * <li><b>les brouillons de saisie</b> : le texte frappé dans un popup
 * de saisie (nouveau fichier, renommage, modèle personnalisé) vit
 * ici, pas dans la vue — la vue est jetable, l'état reste (un
 * brouillon se relit à la réouverture du popup ; A8 l'exportera aux
 * recréations qui restent).</li>
 * </ul>
 *
 * <p>La MÉCANIQUE des popups (fenêtres, voile, clavier) vit dans
 * {@code ui/PortePopups} (les gabarits {@code PopupAncre} et
 * {@code PopupCentre}) ; l'écran les ferme explicitement en
 * onPause/onDestroy/onConfigurationChanged.</p>
 */
public final class EtatPopups {

    /** Les brouillons de saisie (clé → texte frappé, jamais nul). */
    private final Map<String, String> brouillons = new LinkedHashMap<>();

    /**
     * Ce que le retour système fait quand un popup est ouvert : le
     * FERMER, une seule fois — AVANT le tiroir agent (la surcouche la
     * plus récente), AVANT les surcouches de l'état (feuille, tiroir,
     * aperçu, confirmation, sortie). Sans popup : l'ordre d'hier.
     *
     * @param popupOuvert      un popup est-il ouvert (le registre du
     *                         porte-popups le dit)
     * @param tiroirAgentOuvert le tiroir agent est-il ouvert
     * @param etat             l'état de l'atelier (surcouches)
     * @return l'action du retour (jamais deux choses)
     */
    public EtatAtelier.ActionRetour surRetour(boolean popupOuvert,
                                              boolean tiroirAgentOuvert,
                                              EtatAtelier etat) {
        if (popupOuvert) {
            return EtatAtelier.ActionRetour.FERMER_POPUP;
        }
        if (tiroirAgentOuvert) {
            return EtatAtelier.ActionRetour.FERMER_TIROIR_AGENT;
        }
        return etat.surRetour();
    }

    /**
     * Pose le brouillon d'une saisie (chaque frappe le pose — le
     * TextWatcher de la vue délègue ici, la vue ne garde rien).
     *
     * @param cle   la clé de la saisie (nouveau, renommage, modèle…)
     * @param texte le texte frappé (vide admis)
     */
    public void poserBrouillon(String cle, String texte) {
        brouillons.put(cle, texte == null ? "" : texte);
    }

    /**
     * Le brouillon d'une saisie (préremplir la réouverture du popup).
     *
     * @param cle la clé de la saisie
     * @return le texte frappé, ou {@code null} si la saisie n'a
     *         jamais été frappée (le défaut du cas s'applique)
     */
    public String brouillon(String cle) {
        return brouillons.get(cle);
    }

    /** Oublie un brouillon (la saisie validée vit dans le store). */
    public void oublierBrouillon(String cle) {
        brouillons.remove(cle);
    }
}
