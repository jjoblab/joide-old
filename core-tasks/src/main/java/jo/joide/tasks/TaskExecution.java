package jo.joide.tasks;

import java.util.List;
import java.util.Objects;

import jo.joide.platform.Problem;

/**
 * Résultat de l'exécution du code d'une tâche, distinct du statut
 * final décidé par le moteur (à jour, restauré du cache, échec).
 */
public sealed interface TaskExecution {

    /**
     * @return une exécution réussie, sans avertissement
     */
    static TaskExecution ok() {
        return new Ok(List.of());
    }

    /**
     * @param warnings avertissements émis pendant l'exécution
     * @return une exécution réussie avec avertissements
     */
    static TaskExecution ok(List<Problem> warnings) {
        return new Ok(warnings);
    }

    /**
     * @param diagnostics problèmes qui font échouer la tâche
     * @return une exécution échouée
     */
    static TaskExecution failed(List<Problem> diagnostics) {
        return new Failed(diagnostics);
    }

    /**
     * Exécution réussie.
     */
    final class Ok implements TaskExecution {

        private final List<Problem> warnings;

        /**
         * @param warnings avertissements
         */
        public Ok(List<Problem> warnings) {
            this.warnings = List.copyOf(
                    Objects.requireNonNull(warnings, "warnings"));
        }

        /** @return les avertissements émis */
        public List<Problem> warnings() {
            return warnings;
        }
    }

    /**
     * Exécution échouée : les diagnostics sont structurés et seront
     * rapportés tels quels par le moteur.
     */
    final class Failed implements TaskExecution {

        private final List<Problem> diagnostics;

        /**
         * @param diagnostics au moins un problème
         */
        public Failed(List<Problem> diagnostics) {
            this.diagnostics = List.copyOf(
                    Objects.requireNonNull(diagnostics, "diagnostics"));
        }

        /** @return les diagnostics de l'échec */
        public List<Problem> diagnostics() {
            return diagnostics;
        }
    }
}
