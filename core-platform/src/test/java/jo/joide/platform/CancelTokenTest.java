package jo.joide.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Contrôle du jeton d'annulation coopérative : états, exceptions et
 * écouteurs.
 */
class CancelTokenTest {

    @Test
    void neverIsNeverCancelled() {
        assertFalse(CancelToken.NEVER.isCancelled());
        CancelToken.NEVER.checkCancelled();
        CancelToken.NEVER.onCancel(() -> {
            throw new AssertionError("never-cancelled token ran listener");
        });
    }

    @Test
    void cancelFlipsStateAndThrows() {
        CancelToken.Cancellation cancellation = CancelToken.create();
        CancelToken token = cancellation.token();
        assertFalse(token.isCancelled());
        cancellation.cancel();
        assertTrue(token.isCancelled());
        assertThrows(CancellationException.class, token::checkCancelled);
    }

    @Test
    void listenersRunOnceAndAfterCancelImmediately() {
        CancelToken.Cancellation cancellation = CancelToken.create();
        CancelToken token = cancellation.token();
        AtomicInteger before = new AtomicInteger();
        token.onCancel(before::incrementAndGet);
        token.onCancel(before::incrementAndGet);
        assertEquals(0, before.get());
        cancellation.cancel();
        assertEquals(2, before.get());
        AtomicInteger after = new AtomicInteger();
        token.onCancel(after::incrementAndGet);
        assertEquals(1, after.get());
        cancellation.cancel();
        assertEquals(2, before.get());
        assertEquals(1, after.get());
    }

    @Test
    void failingListenerDoesNotBlockOthers() {
        CancelToken.Cancellation cancellation = CancelToken.create();
        AtomicInteger good = new AtomicInteger();
        cancellation.token().onCancel(() -> {
            throw new IllegalStateException("boom");
        });
        cancellation.token().onCancel(good::incrementAndGet);
        cancellation.cancel();
        assertEquals(1, good.get());
    }

    @Test
    void concurrentRegistrationDuringCancel() throws InterruptedException {
        CancelToken.Cancellation cancellation = CancelToken.create();
        AtomicInteger calls = new AtomicInteger();
        Thread canceller = new Thread(cancellation::cancel);
        canceller.start();
        for (int i = 0; i < 100; i++) {
            cancellation.token().onCancel(calls::incrementAndGet);
        }
        canceller.join(5000L);
        cancellation.cancel();
        assertEquals(100, calls.get());
    }
}
