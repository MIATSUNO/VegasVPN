package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class GateStartRegistryTest {
    @Test public void stopBeforeStartCannotLaunchAndReplayFails() {
        GateStartRegistry r = new GateStartRegistry(); String n = r.reserve("a");
        assertTrue(r.cancel("a", n)); assertTrue(r.cancel("a", n));
        assertEquals(GateStartRegistry.Claim.CANCELLED, r.claim("a", n));
        assertEquals(GateStartRegistry.Claim.REJECTED, r.claim("a", n));
        assertFalse(r.attach("a", n, () -> fail()));
    }
    @Test public void duplicateAndOldCommandsCannotTouchNewAttempt() {
        GateStartRegistry r = new GateStartRegistry(); String old = r.reserve("same");
        assertEquals(GateStartRegistry.Claim.ACCEPTED, r.claim("same", old));
        assertEquals(GateStartRegistry.Claim.REJECTED, r.claim("same", old));
        assertFalse(r.release("same", "wrong")); assertNull(r.reserve("other"));
        assertTrue(r.release("same", old)); String next = r.reserve("same");
        assertNotEquals(old, next); assertFalse(r.cancel("same", old));
        assertFalse(r.release("same", old));
        assertEquals(GateStartRegistry.Claim.ACCEPTED, r.claim("same", next));
        assertFalse(r.cancelled("same", next));
    }
    @Test public void lostProcessRejectsRedeliveryAndUnknownStopDoesNotConsumeCapacity() {
        GateStartRegistry oldProcess = new GateStartRegistry(); String n = oldProcess.reserve("a");
        GateStartRegistry newProcess = new GateStartRegistry();
        assertFalse(newProcess.cancel("a", n));
        assertEquals(GateStartRegistry.Claim.REJECTED, newProcess.claim("a", n));
        assertNotNull(newProcess.reserve("b"));
    }
    @Test public void cancelBeforeAttachRefusesLaunch() {
        GateStartRegistry r = new GateStartRegistry(); String n = r.reserve("a");
        assertEquals(GateStartRegistry.Claim.ACCEPTED, r.claim("a", n));
        assertTrue(r.cancel("a", n)); assertFalse(r.attach("a", n, () -> fail()));
        assertTrue(r.cancelled("a", n));
    }
    @Test public void cancelAfterAttachRetriesAndDoesNotHoldRegistryLock() {
        GateStartRegistry r = new GateStartRegistry(); String n = r.reserve("a"); AtomicInteger calls = new AtomicInteger();
        assertEquals(GateStartRegistry.Claim.ACCEPTED, r.claim("a", n));
        assertTrue(r.attach("a", n, () -> { assertTrue(r.cancelled("a", n)); calls.incrementAndGet(); }));
        assertTrue(r.cancel("a", n)); assertTrue(r.cancel("a", n)); assertEquals(2, calls.get());
    }
    @Test public void concurrentCancelAndAttachCannotLoseStop() throws Exception {
        for (int i=0; i<1000; i++) {
            GateStartRegistry r = new GateStartRegistry(); String n = r.reserve("a");
            assertEquals(GateStartRegistry.Claim.ACCEPTED, r.claim("a", n));
            CountDownLatch go = new CountDownLatch(1); AtomicInteger installed = new AtomicInteger(), stopped = new AtomicInteger();
            Thread a = new Thread(() -> { try { go.await(); if (r.attach("a", n, stopped::incrementAndGet)) installed.incrementAndGet(); } catch (InterruptedException e) { throw new AssertionError(e); } });
            Thread b = new Thread(() -> { try { go.await(); r.cancel("a", n); } catch (InterruptedException e) { throw new AssertionError(e); } });
            a.start(); b.start(); go.countDown(); a.join(); b.join();
            assertTrue(r.cancelled("a", n));
            if (installed.get() == 1) assertEquals(1, stopped.get());
        }
    }
}
