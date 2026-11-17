package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Deterministic interleavings around the real timer's operation monitor. */
public class NetworkDisconnectOrderingTest {
    private static class Scheduler implements NetworkDisconnectTimer.Scheduler {
        Runnable callback;
        public void postDelayed(Runnable r, long ms) { callback = r; }
        public void remove(Runnable r) { }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timeout"); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }
    private static void blocked(Thread t) {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (t.isAlive() && t.getState() != Thread.State.BLOCKED && System.nanoTime() < limit) Thread.yield();
        assertEquals(Thread.State.BLOCKED, t.getState());
    }
    private static final java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
    private static Thread thread(Runnable body) {
        Thread t = new Thread(() -> { try { body.run(); } catch (Throwable e) { failure.compareAndSet(null, e); } });
        return t;
    }
    private static void join(Thread t) throws Exception {
        t.join(5000); assertFalse(t.isAlive());
        Throwable e = failure.getAndSet(null); if (e != null) throw new AssertionError(e);
    }

    @Test public void timeoutWinnerFinishesEffectBeforeReturnAndResume() throws Exception {
        PauseRunPolicy p = new PauseRunPolicy(); Scheduler s = new Scheduler();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        NetworkDisconnectTimer timer = new NetworkDisconnectTimer(p, s, 20000, () -> {
            assertFalse(Thread.holdsLock(p)); entered.countDown(); await(release); events.add("pause");
        });
        timer.networkReturned(); timer.networkLost(true);
        Thread expiry = thread(s.callback); expiry.start(); await(entered);
        assertEquals(PauseRunPolicy.State.DISCONNECTED, p.network());
        Thread returned = thread(() -> timer.runIfOpen(() -> {
            timer.networkReturned(); events.add("resume");
        }));
        returned.start();
        try { blocked(returned); assertTrue(events.isEmpty()); } finally { release.countDown(); }
        join(expiry); join(returned);
        assertEquals(Arrays.asList("pause", "resume"), events); assertTrue(p.shouldRun());
    }

    @Test public void timeoutWinnerFinishesEffectBeforeClose() throws Exception {
        PauseRunPolicy p = new PauseRunPolicy(); Scheduler s = new Scheduler();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        NetworkDisconnectTimer timer = new NetworkDisconnectTimer(p, s, 20000, () -> {
            entered.countDown(); await(release); events.add("pause");
        });
        timer.networkReturned(); timer.networkLost(true);
        Thread expiry = thread(s.callback); expiry.start(); await(entered);
        Thread closer = thread(() -> { timer.close(); events.add("closed"); }); closer.start();
        try { blocked(closer); assertTrue(events.isEmpty()); } finally { release.countDown(); }
        join(expiry); join(closer);
        assertEquals(Arrays.asList("pause", "closed"), events);
        assertFalse(p.shouldRun()); assertFalse(p.runIfAllowed(() -> fail("release after close")));
        assertFalse(timer.runIfOpen(() -> fail("receiver after close")));
        s.callback.run(); assertEquals(2, events.size());
    }

    @Test public void earlierUserPauseCompletesBeforeResume() throws Exception {
        PauseRunPolicy p = new PauseRunPolicy(); Scheduler s = new Scheduler();
        NetworkDisconnectTimer timer = new NetworkDisconnectTimer(p, s, 20000, () -> {});
        timer.networkReturned();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        Thread first = thread(() -> timer.runIfOpen(() -> {
            assertEquals(PauseRunPolicy.Action.PAUSE, p.setUserPaused(true));
            entered.countDown(); await(release); events.add("pause");
        }));
        first.start(); await(entered);
        Thread second = thread(() -> timer.runIfOpen(() -> {
            assertEquals(PauseRunPolicy.Action.RESUME, p.setUserPaused(false)); events.add("resume");
        }));
        second.start(); try { blocked(second); } finally { release.countDown(); }
        join(first); join(second); assertEquals(Arrays.asList("pause", "resume"), events);
    }

    @Test public void reentrantReceiverAndCloseDuringEffectAreRejected() {
        PauseRunPolicy p = new PauseRunPolicy(); Scheduler s = new Scheduler();
        NetworkDisconnectTimer[] ref = new NetworkDisconnectTimer[1];
        ref[0] = new NetworkDisconnectTimer(p, s, 20000, () -> {
            try { ref[0].runIfOpen(() -> {}); fail("reentry accepted"); } catch (IllegalStateException expected) { }
            try { ref[0].close(); fail("close accepted"); } catch (IllegalStateException expected) { }
        });
        ref[0].networkReturned(); ref[0].networkLost(true); s.callback.run(); ref[0].close();
    }
}
