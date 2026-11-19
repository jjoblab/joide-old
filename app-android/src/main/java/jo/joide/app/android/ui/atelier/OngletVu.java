package jo.joide.app.android.ui.atelier;

import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.atelier.ModeleOnglets;

/**
 * Lot A6 (ADR-085 décision 11) — l'onglet TEL QUE L'ADAPTATEUR le
 * voit : l'état pur de {@link ModeleOnglets} PLUS le drapeau actif,
 * une valeur IMMUABLE (le diff de DiffUtil court en tâche de fond :
 * jamais un champ muable que la course lirait en dessous). Pur, testé
 * JVM — la vue ne décide rien, le modèle non plus, le diff seul vit
 * ici.
 *
 * <p>La fermeture d'un onglet, l'éviction au plafond et le recalcul de
 * l'actif restent dans {@link ModeleOnglets}/{@code EtatAtelier} —
 * ceci n'est que la valeur de RENDU.</p>
 */
record OngletVu(ModeleOnglets.EtatOnglet onglet, boolean actif) {

    /** Les onglets vus depuis le modèle pur (l'actif porte le drapeau). */
    static List<OngletVu> depuis(ModeleOnglets modele) {
        List<OngletVu> vus = new ArrayList<>();
        for (ModeleOnglets.EtatOnglet onglet : modele.onglets()) {
            vus.add(new OngletVu(onglet,
                    onglet.chemin.equals(modele.actif())));
        }
        return vus;
    }

    /**
     * Même IDENTITÉ : le même fichier ouvert (le chemin) — l'onglet se
     * déplace ou disparaît, il ne se re-lier jamais de zéro.
     */
    boolean memeOnglet(OngletVu autre) {
        return onglet.chemin.equals(autre.onglet.chemin);
    }

    /**
     * Même RENDU : le drapeau actif, le badge de type, les points
     * d'état (modifié, erreur), le nom affiché — tout ce que la
     * reliature re-pose.
     */
    boolean memeRendu(OngletVu autre) {
        return actif == autre.actif
                && onglet.badge == autre.onglet.badge
                && onglet.modifie == autre.onglet.modifie
                && onglet.erreur == autre.onglet.erreur
                && onglet.nom.equals(autre.onglet.nom);
    }
}
