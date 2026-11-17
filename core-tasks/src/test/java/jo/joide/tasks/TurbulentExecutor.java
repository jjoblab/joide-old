package jo.joide.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Exécuteur délégué qui perturbe les délais : avant et après chaque
 * commande, une courte rotation d'attente active dont la durée
 * dépend d'un générateur à graine fixe. La turbulence est
 * reproductible : la même graine donne les mêmes perturbations.
 */
public final class TurbulentExecutor implements ExecutorService {

    private final ExecutorService delegate;
    private final Random random;

    /**
     * @param delegate exécuteur réel délégué
     * @param seed     graine des perturbations
     */
    public TurbulentExecutor(ExecutorService delegate, long seed) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.random = new Random(seed);
    }

    private void churn() {
        long spin = random.nextInt(200_000);
        for (long i = 0; i < spin; i++) {
            // rotation d'attente active, sans sommeil
        }
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    private Runnable wrap(Runnable command) {
        return () -> {
            churn();
            try {
                command.run();
            } finally {
                churn();
            }
        };
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }
}
