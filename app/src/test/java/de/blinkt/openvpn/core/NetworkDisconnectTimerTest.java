package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static de.blinkt.openvpn.core.PauseRunPolicy.State.*;
import static de.blinkt.openvpn.core.PauseRunPolicy.Action.*;

/** Exercises the real timer controller without Android or a management socket. */
public class NetworkDisconnectTimerTest {
    static final class Scheduler implements NetworkDisconnectTimer.Scheduler {
        final List<Runnable> posted = new ArrayList<>();
        final List<Runnable> removed = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        public void postDelayed(Runnable r, long delay) { posted.add(r); delays.add(delay); }
        public void remove(Runnable r) { removed.add(r); }
    }
    static final class Fixture {
        final PauseRunPolicy p = new PauseRunPolicy();
        final Scheduler scheduler = new Scheduler();
        int pauses;
        final NetworkDisconnectTimer timer = new NetworkDisconnectTimer(p, scheduler, 20_000L, () -> pauses++);
        Fixture() { assertEquals(NetworkDisconnectTimer.Return.OTHER, timer.networkReturned()); }
    }
    @Test public void staleCallbackCannotConsumeNewLossAndDuplicateDoesNotExtend() {
        Fixture f = new Fixture();
        f.timer.networkLost(true); Runnable old = f.scheduler.posted.get(0);
        assertEquals(Long.valueOf(20_000), f.scheduler.delays.get(0));
        f.timer.networkLost(true); assertEquals(1, f.scheduler.posted.size());
        assertEquals(NetworkDisconnectTimer.Return.PENDING, f.timer.networkReturned());
        assertSame(old, f.scheduler.removed.get(0));
        f.timer.networkLost(true); Runnable current = f.scheduler.posted.get(1);
        assertNotSame(old, current); assertEquals(Long.valueOf(20_000), f.scheduler.delays.get(1));
        old.run(); assertEquals(PENDINGDISCONNECT, f.p.network()); assertEquals(0, f.pauses);
        current.run(); assertEquals(DISCONNECTED, f.p.network()); assertEquals(1, f.pauses);
        current.run(); old.run(); assertEquals(1, f.pauses);
    }
    @Test public void callbackAlreadyEnteredButWaitingForOperationCannotConsumeNewLoss() throws Exception {
        Fixture f = new Fixture(); f.timer.networkLost(true);
        Runnable old = f.scheduler.posted.get(0);
        CountDownLatch entered = new CountDownLatch(1);
        Thread thread;
        synchronized (f.timer) {
            thread = new Thread(() -> { entered.countDown(); old.run(); });
            thread.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            f.timer.networkReturned(); f.timer.networkLost(true);
        }
        thread.join(2000); assertFalse(thread.isAlive());
        assertEquals(PENDINGDISCONNECT, f.p.network()); assertEquals(0, f.pauses);
        f.scheduler.posted.get(1).run(); assertEquals(1, f.pauses);
    }
    @Test public void unregisterCancelsAndInvalidatesQueuedCallbacksAndFutureLoss() {
        Fixture f = new Fixture(); f.timer.networkLost(true); Runnable old = f.scheduler.posted.get(0);
        f.timer.close(); f.timer.close(); assertEquals(1, f.scheduler.removed.size());
        assertSame(old, f.scheduler.removed.get(0)); old.run();
        assertEquals(0, f.pauses); assertEquals(PENDINGDISCONNECT, f.p.network());
        assertEquals(NetworkDisconnectTimer.Return.CLOSED, f.timer.networkReturned());
        f.timer.networkLost(true); assertEquals(1, f.scheduler.posted.size());
    }
    @Test public void unregisterInvalidatesCallbackAlreadyWaitingForPolicy() throws Exception {
        Fixture f = new Fixture(); f.timer.networkLost(true);
        Runnable old = f.scheduler.posted.get(0);
        CountDownLatch entered = new CountDownLatch(1);
        Thread thread;
        synchronized (f.timer) {
            thread = new Thread(() -> { entered.countDown(); old.run(); });
            thread.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            f.timer.close();
        }
        thread.join(2000); assertFalse(thread.isAlive());
        assertEquals(0, f.pauses); assertEquals(PENDINGDISCONNECT, f.p.network());
    }
    @Test public void screenAndUserPauseSurviveReturnAndTimeout() {
        Fixture f = new Fixture();
        assertEquals(PAUSE, f.p.setUserPaused(true)); f.p.screenOff();
        f.timer.networkLost(true); Runnable first = f.scheduler.posted.get(0);
        f.timer.networkReturned(); first.run();
        assertEquals(DISCONNECTED, f.p.user()); assertFalse(f.p.shouldRun());
        f.timer.networkLost(true); f.scheduler.posted.get(1).run();
        assertEquals(DISCONNECTED, f.p.user()); assertEquals(DISCONNECTED, f.p.screen());
        assertEquals(DISCONNECTED, f.p.network()); assertFalse(f.p.shouldRun());
        assertEquals(1, f.pauses);
    }
    @Test public void immediateLossDoesNotScheduleOrPause() {
        Fixture f = new Fixture(); f.p.screenOff(); f.timer.networkLost(false);
        assertEquals(DISCONNECTED, f.p.network()); assertEquals(DISCONNECTED, f.p.screen());
        assertEquals(0, f.scheduler.posted.size()); assertEquals(0, f.pauses);
    }
    @Test public void screenOnDoesNotCancelPendingTimeout() {
        Fixture f = new Fixture(); f.timer.networkLost(true); f.p.screenOff();
        assertEquals(NONE, f.p.screenOn());
        assertTrue(f.scheduler.removed.isEmpty());
        f.scheduler.posted.get(0).run(); assertEquals(DISCONNECTED, f.p.network());
        assertEquals(1, f.pauses);
    }
}
