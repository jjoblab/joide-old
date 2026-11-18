package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lot L3 de l'atelier v2 (ADR-083) — l'état PUR de la section
 * Problèmes (§3.6) : les diagnostics du moteur de langage (LSP),
 * groupés par fichier, avec les compteurs d'en-tête (✕ erreurs /
 * ▲ avertissements), la première erreur du FICHIER ACTIF (la zone
 * vivante) et le saut « problème suivant » (le bouton ↓ qui avance
 * dans les erreurs SANS déplier la feuille). Aucune vue.
 */
public final class EtatProblemes {

    /** Un diagnostic d'une ligne. */
    public record Probleme(String chemin, String nomFichier,
                           int ligne, String message,
                           boolean erreur) {
    }

    /** Les diagnostics, dans l'ordre d'arrivée. */
    private final List<Probleme> problemes = new ArrayList<>();

    /** Remplace les diagnostics d'un fichier (le pont pousse par uri). */
    public void poserFichier(String chemin, String nomFichier,
                             List<Probleme> nouveaux) {
        problemes.removeIf(p -> p.chemin().equals(chemin));
        problemes.addAll(nouveaux);
    }

    /** Vide tout (les fichiers fermés). */
    public void vider() {
        problemes.clear();
    }

    /** @return les diagnostics, groupés par fichier (ordre stable). */
    public Map<String, List<Probleme>> parFichier() {
        Map<String, List<Probleme>> groupes = new LinkedHashMap<>();
        for (Probleme probleme : problemes) {
            groupes.computeIfAbsent(probleme.chemin(),
                    k -> new ArrayList<>()).add(probleme);
        }
        return groupes;
    }

    /** @return le nombre d'erreurs (✕ de l'en-tête). */
    public int erreurs() {
        return (int) problemes.stream().filter(Probleme::erreur).count();
    }

    /** @return le nombre d'avertissements (▲ de l'en-tête). */
    public int avertissements() {
        return problemes.size() - erreurs();
    }

    /** La première erreur du fichier actif (la zone vivante, §3.6) :
     *  une erreur d'abord, sinon le premier avertissement du fichier,
     *  sinon null (aucun problème pour ce fichier). */
    public Probleme premierDuFichier(String chemin) {
        Probleme premierAvertissement = null;
        for (Probleme probleme : problemes) {
            if (!probleme.chemin().equals(chemin)) {
                continue;
            }
            if (probleme.erreur()) {
                return probleme;
            }
            if (premierAvertissement == null) {
                premierAvertissement = probleme;
            }
        }
        return premierAvertissement;
    }

    /** Le SAUT « problème suivant » (bouton ↓, §3.6) : l'erreur
     *  suivante après la position donnée (fichier, ligne) — bouclage
     *  sur la fin ; s'il n'y a AUCUNE erreur, null (le bouton ne
     *  saute jamais vers un avertissement : §3.6 dit « erreur
     *  suivante dans l'éditeur »). */
    public Probleme suivant(String cheminActif, int ligneActuelle) {
        List<Probleme> erreurs = problemes.stream()
                .filter(Probleme::erreur).toList();
        if (erreurs.isEmpty()) {
            return null;
        }
        // la première erreur STRICTEMENT après la position courante
        for (Probleme probleme : erreurs) {
            boolean apres = probleme.chemin().compareTo(cheminActif)
                    > 0 || (probleme.chemin().equals(cheminActif)
                    && probleme.ligne() > ligneActuelle);
            if (apres) {
                return probleme;
            }
        }
        // BOUCLAGE : la première erreur de la liste
        return erreurs.get(0);
    }
}
