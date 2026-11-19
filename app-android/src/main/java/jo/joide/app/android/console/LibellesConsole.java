package jo.joide.app.android.console;

import android.content.Context;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.R;
import jo.joide.app.android.console.EtatConsole.Filtre;

/**
 * Les libellés localisés de la console (lot A5) : les mots du panneau
 * — phase du pipeline, nature courte d'une ligne, filtre « Tout /
 * Erreurs (n) / Système » — partagés entre la vue et les adaptateurs.
 * Aucune décision : l'état pur reste {@link EtatConsole}, ces
 * traductions ne font que lire les ressources.
 */
final class LibellesConsole {

    private LibellesConsole() {
    }

    /** « Préparation / Compilation / Assemblage » (null → « … »). */
    static String libellePhase(Context contexte, ConsoleBuild.Phase phase) {
        if (phase == null) {
            return contexte.getString(R.string.console_phase_aucune);
        }
        return switch (phase) {
            case PREPARATION -> contexte.getString(
                    R.string.console_phase_preparation);
            case COMPILATION -> contexte.getString(
                    R.string.console_phase_compilation);
            case ASSEMBLAGE -> contexte.getString(
                    R.string.console_phase_assemblage);
        };
    }

    /** « système / phase / tâche / erreur / avertissement ». */
    static String natureCourte(Context contexte, ConsoleBuild.Nature nature) {
        return switch (nature) {
            case SYSTEME -> contexte.getString(R.string.console_nature_systeme);
            case PHASE -> contexte.getString(R.string.console_nature_phase);
            case TACHE -> contexte.getString(R.string.console_nature_tache);
            case ERREUR -> contexte.getString(R.string.console_nature_erreur);
            case AVERTISSEMENT -> contexte.getString(
                    R.string.console_nature_avertissement);
        };
    }

    /** « Tout / Erreurs (n) / Système » — le compte d'erreurs est exact. */
    static String libelleFiltre(Context contexte, Filtre nature,
                                 int erreurs) {
        return switch (nature) {
            case TOUT -> contexte.getString(R.string.console_filtre_tout);
            case ERREURS -> contexte.getString(
                    R.string.console_filtre_erreurs, erreurs);
            case SYSTEME -> contexte.getString(R.string.console_filtre_systeme);
        };
    }
}
