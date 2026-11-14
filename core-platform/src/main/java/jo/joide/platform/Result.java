package jo.joide.platform;

import java.util.Objects;

/**
 * Résultat d'opération tolérante : soit une valeur, soit un problème.
 *
 * <p>Ce type s'utilise quand l'échec fait partie du contrat normal
 * (fichier illisible, entrée corrompue) et doit être rapporté plutôt
 * que levé. Les exceptions restent la règle pour les erreurs de
 * programmation.</p>
 *
 * @param <T> type de la valeur de succès
 */
public interface Result<T> {

    /**
     * @param value valeur de succès, jamais {@code null}
     * @param <T>   type de la valeur
     * @return un succès
     */
    static <T> Result<T> ok(T value) {
        return new Ok<>(Objects.requireNonNull(value, "value"));
    }

    /**
     * @param problem problème d'échec, jamais {@code null}
     * @param <T>    type de la valeur
     * @return un échec
     */
    static <T> Result<T> err(Problem problem) {
        return new Err<>(Objects.requireNonNull(problem, "problem"));
    }

    /** @return vrai si l'opération a réussi */
    boolean isOk();

    /**
     * @return la valeur de succès
     * @throws IllegalStateException si ce résultat est un échec
     */
    T value();

    /**
     * @return le problème d'échec
     * @throws IllegalStateException si ce résultat est un succès
     */
    Problem problem();

    /**
     * Succès portant une valeur.
     *
     * @param <T> type de la valeur
     */
    final class Ok<T> implements Result<T> {
        private final T value;

        Ok(T value) {
            this.value = value;
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public Problem problem() {
            throw new IllegalStateException("no problem on success");
        }
    }

    /**
     * Échec portant un problème.
     *
     * @param <T> type de la valeur
     */
    final class Err<T> implements Result<T> {
        private final Problem problem;

        Err(Problem problem) {
            this.problem = problem;
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public T value() {
            throw new IllegalStateException("no value on failure");
        }

        @Override
        public Problem problem() {
            return problem;
        }
    }
}
