package br.com.vegas.independente;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class GateSessionTest {
    @Test public void launchDoesNotInventConnectedAndFailuresSuppressLateSuccess() {
        GateSession s = new GateSession();
        assertTrue(s.start("catalog-profile", "nonce"));
        assertEquals(GateSession.Phase.STARTING, s.phase());
        assertEquals(GateSession.Effect.IGNORE, s.event("unrelated", "nonce", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Phase.STARTING, s.phase());
        s.noteTerminal("catalog-profile", "nonce", GateSession.Event.PROTECT_FAILED);
        assertEquals(GateSession.Effect.IGNORE, s.event("catalog-profile", "nonce", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Effect.REQUEST_STOP, s.event("catalog-profile", "nonce", GateSession.Event.PROTECT_FAILED));
        assertEquals(GateSession.Phase.STOPPING, s.phase());
    }
    @Test public void networkAndPauseRequireMatchingPairAndClearConnected() {
        GateSession s = new GateSession(); assertTrue(s.start("same", "new"));
        assertEquals(GateSession.Effect.UPDATE, s.event("same", "new", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "old", GateSession.Event.NONETWORK));
        assertEquals(GateSession.Effect.IGNORE, s.event("other", "new", GateSession.Event.PAUSED));
        assertEquals(GateSession.Phase.CONNECTED, s.phase());
        assertEquals(GateSession.Effect.UPDATE, s.event("same", "new", GateSession.Event.NONETWORK));
        assertEquals(GateSession.Phase.STARTING, s.phase());
        assertEquals(GateSession.Effect.UPDATE, s.event("same", "new", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Effect.UPDATE, s.event("same", "new", GateSession.Event.PAUSED));
        assertEquals(GateSession.Phase.STARTING, s.phase());
        assertEquals(GateSession.Effect.UPDATE, s.event("same", "new", GateSession.Event.CONNECTING));
        assertEquals(GateSession.Phase.STARTING, s.phase());
        assertTrue(s.stop("same", "new"));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "new", GateSession.Event.NONETWORK));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "new", GateSession.Event.PAUSED));
    }
    @Test public void staleEventsCannotConnectOrEndNextSession() {
        GateSession s = new GateSession();
        assertTrue(s.start("old", "nonce"));
        assertEquals(GateSession.Effect.FINISHED, s.event("old", "nonce", GateSession.Event.PROCESS_EXITED));
        assertTrue(s.start("new", "nonce"));
        assertEquals(GateSession.Effect.IGNORE, s.event("old", "nonce", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Effect.IGNORE, s.event("old", "nonce", GateSession.Event.PROCESS_EXITED));
        assertEquals(GateSession.Phase.STARTING, s.phase());
        assertEquals(GateSession.Effect.UPDATE, s.event("new", "nonce", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Phase.CONNECTED, s.phase());
    }
    @Test public void reusedUuidOldNonceCannotMarkStopFinishOrReleaseNewReservation() {
        de.blinkt.openvpn.core.GateStartRegistry registry = new de.blinkt.openvpn.core.GateStartRegistry();
        GateSession s = new GateSession(); String old = registry.reserve("same");
        assertTrue(s.start("same", old));
        s.noteTerminal("same", old, GateSession.Event.PROCESS_EXITED);
        assertEquals(GateSession.Effect.FINISHED, s.event("same", old, GateSession.Event.PROCESS_EXITED));
        assertTrue(registry.release("same", old));
        String next = registry.reserve("same"); assertNotEquals(old, next); assertTrue(s.start("same", next));
        for (GateSession.Event event : GateSession.Event.values()) {
            s.noteTerminal("same", old, event);
            assertEquals(GateSession.Effect.IGNORE, s.event("same", old, event));
        }
        assertFalse(s.stop("same", old));
        assertFalse(registry.release("same", old)); assertNull(registry.reserve("another"));
        assertEquals(GateSession.Effect.UPDATE, s.event("same", next, GateSession.Event.CONNECTED));
        assertTrue(s.stop("same", next));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", old, GateSession.Event.PROCESS_EXITED));
        assertEquals(GateSession.Phase.STOPPING, s.phase());
        assertEquals(GateSession.Effect.FINISHED, s.event("same", next, GateSession.Event.PROCESS_EXITED));
        assertTrue(registry.release("same", next));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", next, GateSession.Event.PROCESS_EXITED));
        assertFalse(registry.release("same", next));
    }
    @Test public void queuedTerminalBlocksConnectedOnlyForExactIdentity() {
        GateSession s = new GateSession(); assertTrue(s.start("same", "new"));
        s.noteTerminal("same", "old", GateSession.Event.PROCESS_EXITED);
        assertEquals(GateSession.Effect.UPDATE, s.event("same", "new", GateSession.Event.CONNECTED));
        s.noteTerminal("same", "new", GateSession.Event.REVOKED);
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "new", GateSession.Event.CONNECTED));
        assertEquals(GateSession.Effect.REQUEST_STOP, s.event("same", "new", GateSession.Event.REVOKED));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "new", GateSession.Event.REVOKED));
        assertEquals(GateSession.Effect.FINISHED, s.event("same", "new", GateSession.Event.PROCESS_EXITED));
    }
    @Test public void cancellationNoChildOutcomeIsNotAnExitAndIsScoped() {
        GateSession s = new GateSession(); assertTrue(s.start("same", "new"));
        assertTrue(s.stop("same", "new"));
        s.noteTerminal("same", "old", GateSession.Event.START_ABORTED);
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "old", GateSession.Event.START_ABORTED));
        assertEquals(GateSession.Phase.STOPPING, s.phase());
        assertEquals(GateSession.Effect.FINISHED, s.event("same", "new", GateSession.Event.START_ABORTED));
        assertEquals(GateSession.Effect.IGNORE, s.event("same", "new", GateSession.Event.START_ABORTED));
    }
    @Test public void unscopedIdentityRejected() {
        GateSession s = new GateSession(); assertFalse(s.start("a", null)); assertFalse(s.start("a", ""));
        assertTrue(s.start("a", "n")); assertEquals(GateSession.Effect.IGNORE, s.event("a", null, GateSession.Event.PROCESS_EXITED));
    }
    @Test public void stopOrTimeoutWinsOverLateConnectedAndNeedsExit() {
        GateSession s = new GateSession(); assertTrue(s.start("a", "nonce"));
        assertTrue(s.stop("a", "nonce")); assertFalse(s.stop("a", "nonce"));
        assertEquals(GateSession.Effect.IGNORE, s.event("a", "nonce", GateSession.Event.CONNECTED));
        assertFalse(s.start("b", "nonce"));
        assertEquals(GateSession.Effect.FINISHED, s.event("a", "nonce", GateSession.Event.PROCESS_EXITED));
        assertTrue(s.start("b", "nonce"));
        assertFalse(s.stop("a", "nonce"));
    }
    @Test public void revokedFailureAndInputFailClosed() {
        for (GateSession.Event e : new GateSession.Event[]{GateSession.Event.REVOKED, GateSession.Event.AUTH_FAILED, GateSession.Event.INPUT_REQUIRED, GateSession.Event.PROTECT_FAILED}) {
            GateSession s = new GateSession(); assertTrue(s.start("a", "nonce"));
            assertEquals(GateSession.Effect.REQUEST_STOP, s.event("a", "nonce", e));
            assertEquals(GateSession.Phase.STOPPING, s.phase());
            assertEquals(GateSession.Effect.IGNORE, s.event("a", "nonce", GateSession.Event.CONNECTED));
            assertFalse(s.start("b", "nonce"));
        }
    }
    @Test public void reconnectRevokesConnectedState() {
        GateSession s = new GateSession(); assertTrue(s.start("a", "nonce"));
        s.event("a", "nonce", GateSession.Event.CONNECTED);
        s.event("a", "nonce", GateSession.Event.CONNECTING);
        assertEquals(GateSession.Phase.STARTING, s.phase());
        s.event("a", "nonce", GateSession.Event.NONETWORK); assertEquals(GateSession.Phase.STARTING, s.phase());
    }
    @Test public void concurrentStartOnlyOneWinner() throws Exception {
        for (int round=0; round<100; round++) {
            GateSession s = new GateSession(); CountDownLatch go = new CountDownLatch(1); AtomicInteger wins = new AtomicInteger();
            Thread a = new Thread(() -> { try { go.await(); if (s.start("a", "nonce")) wins.incrementAndGet(); } catch (InterruptedException ex) { throw new AssertionError(ex); } });
            Thread b = new Thread(() -> { try { go.await(); if (s.start("b", "nonce")) wins.incrementAndGet(); } catch (InterruptedException ex) { throw new AssertionError(ex); } });
            a.start(); b.start(); go.countDown(); a.join(); b.join(); assertEquals(1, wins.get());
        }
    }
    @Test public void concurrentStopAndConnectedNeverEndConnected() throws Exception {
        for (int round=0; round<1000; round++) {
            GateSession s = new GateSession(); s.start("a", "nonce"); CountDownLatch go = new CountDownLatch(1);
            Thread a = new Thread(() -> { try { go.await(); s.stop("a", "nonce"); } catch (InterruptedException ex) { throw new AssertionError(ex); } });
            Thread b = new Thread(() -> { try { go.await(); s.event("a", "nonce", GateSession.Event.CONNECTED); } catch (InterruptedException ex) { throw new AssertionError(ex); } });
            a.start(); b.start(); go.countDown(); a.join(); b.join(); assertEquals(GateSession.Phase.STOPPING, s.phase());
        }
    }
}
