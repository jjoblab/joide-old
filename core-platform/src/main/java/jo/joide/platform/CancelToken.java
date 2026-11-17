package jo.joide.platform;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Jeton d'annulation coopérative, interrogé par le code exécuté.
 *
 * <p>L'annulation est un état, pas une interruption : les tâches
 * longues appellent {@link #checkCancelled()} à leurs points de
 * reprise et terminent proprement. Le code de production ne crée jamais
 * de jeton lui-même : il reçoit celui de l'opération en cours.</p>
 */
public interface CancelToken {

    /** Jeton jamais annulé, partagé par toutes les opérations de lecture. */
    CancelToken NEVER = new CancelToken() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void onCancel(Runnable listener) {
            // jamais annulé : l'écouteur ne sera jamais appelé
        }
    };

    /** @return vrai si l'opération a été annulée */
    boolean isCancelled();

    /**
     * @throws CancellationException si l'opération a été annulée
     */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException("operation cancelled");
        }
    }

    /**
     * Enregistre un écouteur appelé au plus une fois, au moment de
     * l'annulation. Les écouteurs ajoutés après l'annulation sont
     * appelés immédiatement sur le fil appelant.
     *
     * @param listener action à exécuter, jamais {@code null}
     */
    void onCancel(Runnable listener);

    /**
     * Crée une paire annulation.
     *
     * @return la poignée qui crée le jeton et déclenche l'annulation
     */
    static Cancellation create() {
        return new Cancellation();
    }

    /**
     * Poignée de création d'un jeton : côté donneur d'ordre.
     */
    final class Cancellation {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final CopyOnWriteArrayList<OnceRunnable> listeners = new CopyOnWriteArrayList<>();
        private final CancelToken token = new Token();

        Cancellation() {
        }

        /** @return le jeton observé par le code exécuté */
        public CancelToken token() {
            return token;
        }

        /**
         * Déclenche l'annulation ; les écouteurs enregistrés sont
         * appelés sur le fil appelant, chacun au plus une fois et isolé
         * des exceptions des autres.
         */
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            for (OnceRunnable listener : listeners) {
                listener.run();
            }
        }

        private final class Token implements CancelToken {
            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public void onCancel(Runnable listener) {
                java.util.Objects.requireNonNull(listener, "listener");
                OnceRunnable once = new OnceRunnable(listener);
                if (cancelled.get()) {
                    once.run();
                    return;
                }
                listeners.add(once);
                if (cancelled.get()) {
                    // annulation concurrente à l'enregistrement : la
                    // garde d'unicité interdit le double appel
                    once.run();
                }
            }
        }
    }

    /**
     * Écouteur protégé contre les exécutions multiples.
     */
    final class OnceRunnable implements Runnable {
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final Runnable delegate;

        OnceRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            if (done.compareAndSet(false, true)) {
                try {
                    delegate.run();
                } catch (RuntimeException e) {
                    // un écouteur défaillant ne doit pas bloquer les autres
                }
            }
        }
    }
}
