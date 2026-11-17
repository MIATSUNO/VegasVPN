package de.blinkt.openvpn.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NotificationSessionTest {
    @Test public void stopRequiresCurrentIdAndNonceEvenWhenIdIsReused() {
        NotificationSession s = new NotificationSession();
        assertFalse(NotificationSession.matches(s.snapshot(), "same", "first"));
        s.publish("same", "first");
        NotificationSession.Identity old = s.snapshot();
        assertFalse(NotificationSession.matches(old, "same", null));
        assertFalse(NotificationSession.matches(old, null, "first"));
        assertFalse(NotificationSession.matches(old, "other", "first"));
        assertTrue(NotificationSession.matches(old, "same", "first"));
        s.publish("same", "second");
        assertFalse(NotificationSession.matches(s.snapshot(), old.sessionId, old.nonce));
        assertFalse(NotificationSession.matches(s.snapshot(), "same", null));
        assertTrue(NotificationSession.matches(s.snapshot(), "same", "second"));
        s.clear();
        assertFalse(NotificationSession.matches(s.snapshot(), "same", "second"));
    }
    @Test public void snapshotsAreScopedImmutableAndClearable() {
        NotificationSession sessions = new NotificationSession();
        assertNull(sessions.snapshot());
        sessions.publish("uuid", "old-nonce");
        NotificationSession.Identity old = sessions.snapshot();
        sessions.publish("uuid", "new-nonce");
        NotificationSession.Identity current = sessions.snapshot();
        assertEquals("uuid", old.sessionId);
        assertEquals("old-nonce", old.nonce);
        assertEquals("uuid", current.sessionId);
        assertEquals("new-nonce", current.nonce);
        sessions.clear();
        assertNull(sessions.snapshot());
        assertEquals("old-nonce", old.nonce); // a caller can still hold a stale action
    }

    @Test public void callbackSnapshotDoesNotWaitForProcessLock() throws Exception {
        NotificationSession sessions = new NotificationSession();
        sessions.publish("uuid", "nonce");
        Object statusLock = new Object(), processLock = new Object();
        CountDownLatch processHeld = new CountDownLatch(1), statusHeld = new CountDownLatch(1);
        CountDownLatch snapshotRead = new CountDownLatch(1), finished = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread stop = new Thread(() -> {
            try {
                synchronized (processLock) {
                    processHeld.countDown();
                    if (!statusHeld.await(3, TimeUnit.SECONDS)) throw new AssertionError("status not held");
                    // Model management.stopVPN -> VpnStatus while processLock is held.
                    synchronized (statusLock) { }
                }
            } catch (Throwable e) { failure.set(e); }
            finally { finished.countDown(); }
        });
        Thread callback = new Thread(() -> {
            try {
                if (!processHeld.await(3, TimeUnit.SECONDS)) throw new AssertionError("process not held");
                synchronized (statusLock) {
                    statusHeld.countDown();
                    NotificationSession.Identity id = sessions.snapshot();
                    if (id == null || !"uuid".equals(id.sessionId) || !"nonce".equals(id.nonce))
                        throw new AssertionError("incorrect snapshot");
                    snapshotRead.countDown();
                }
            } catch (Throwable e) { failure.set(e); }
            finally { finished.countDown(); }
        });
        stop.setDaemon(true); callback.setDaemon(true);
        stop.start(); callback.start();
        assertTrue("snapshot waited for process lock", snapshotRead.await(4, TimeUnit.SECONDS));
        assertTrue("model did not finish", finished.await(4, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test public void pairIsNeverTornDuringConcurrentPublication() throws Exception {
        NotificationSession sessions = new NotificationSession();
        sessions.publish("A", "A");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100000; i++) {
                String v = (i & 1) == 0 ? "A" : "B";
                sessions.publish(v, v);
                if ((i & 255) == 0) sessions.clear();
            }
        });
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100000; i++) {
                NotificationSession.Identity id = sessions.snapshot();
                if (id != null && !id.sessionId.equals(id.nonce)) {
                    failure.set(new AssertionError("torn identity")); return;
                }
            }
        });
        writer.start(); reader.start(); writer.join(5000); reader.join(5000);
        assertFalse(writer.isAlive()); assertFalse(reader.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
