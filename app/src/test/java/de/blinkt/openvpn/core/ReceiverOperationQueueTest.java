package de.blinkt.openvpn.core;

import org.junit.Test;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** JVM simulations; not an Android lifecycle or LocalSocket test. */
public class ReceiverOperationQueueTest {
    private static void await(CountDownLatch latch) throws Exception {
        assertTrue("timeout", latch.await(4, TimeUnit.SECONDS));
    }
    private static void uninterruptible(CountDownLatch latch) {
        while (true) try { latch.await(); return; } catch (InterruptedException ignored) { }
    }
    @Test public void blockedListenerDoesNotBlockCloseOrRunQueuedWork() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy(); policy.network(PauseRunPolicy.State.SHOULDBECONNECTED);
        CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1), exited = new CountDownLatch(1);
        AtomicBoolean queued = new AtomicBoolean(); AtomicInteger failures = new AtomicInteger();
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, failures::incrementAndGet, 8, 10000);
        try {
            q.submit(() -> { entered.countDown(); uninterruptible(unblock); exited.countDown(); });
            await(entered); assertTrue(q.submit(() -> queued.set(true)));
            CountDownLatch closed = new CountDownLatch(1);
            new Thread(() -> { q.close(); closed.countDown(); }).start(); await(closed);
            assertFalse(q.submit(() -> queued.set(true))); assertFalse(policy.shouldRun());
        } finally { unblock.countDown(); await(exited); q.close(); }
        assertFalse(queued.get()); assertEquals(0, failures.get());
    }
    @Test public void watchdogClosesGateAndSignalsStopWhilePolicyWriteStuck() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy(); policy.network(PauseRunPolicy.State.SHOULDBECONNECTED);
        CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1), exited = new CountDownLatch(1), stopped = new CountDownLatch(1);
        AtomicBoolean queued = new AtomicBoolean();
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, stopped::countDown, 8, 200);
        try {
            q.submit(() -> policy.runIfAllowed(() -> { entered.countDown(); uninterruptible(unblock); exited.countDown(); }));
            await(entered); assertTrue(q.submit(() -> queued.set(true))); await(stopped);
            assertFalse(q.submit(() -> queued.set(true)));
            // Even though policy's monitor is held by the stuck write, close is nonblocking.
            CountDownLatch closed = new CountDownLatch(1);
            new Thread(() -> { policy.close(); assertFalse(policy.shouldRun()); assertFalse(policy.runIfAllowed(() -> fail("closed gate"))); closed.countDown(); }).start(); await(closed);
        } finally { unblock.countDown(); await(exited); q.close(); }
        assertFalse(policy.shouldRun()); assertFalse(queued.get());
    }
    @Test public void blockedPipeWriteTimesOutAndCloseNeverWaitsForWriter() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy(); policy.network(PauseRunPolicy.State.SHOULDBECONNECTED);
        PipedInputStream input = new PipedInputStream(16); PipedOutputStream output = new PipedOutputStream(input);
        CountDownLatch entered = new CountDownLatch(1), failure = new CountDownLatch(1), exited = new CountDownLatch(1);
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, failure::countDown, 4, 200);
        try {
            q.submit(() -> { entered.countDown(); try { output.write(new byte[65536]); } catch (Exception expected) { } finally { exited.countDown(); } });
            await(entered); await(failure); q.close(); assertFalse(policy.shouldRun());
        } finally { input.close(); output.close(); await(exited); q.close(); }
    }
    @Test public void saturationRejectsAndStopsInsteadOfSilentlyDroppingPause() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy(); policy.network(PauseRunPolicy.State.SHOULDBECONNECTED);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1), failed = new CountDownLatch(1);
        AtomicBoolean pendingRan = new AtomicBoolean();
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, failed::countDown, 1, 10000);
        try {
            q.submit(() -> { entered.countDown(); uninterruptible(release); exited.countDown(); });
            await(entered); assertTrue(q.submit(() -> pendingRan.set(true)));
            assertFalse(q.submit(() -> {})); await(failed); assertFalse(policy.shouldRun());
        } finally { release.countDown(); await(exited); q.close(); }
        assertFalse(pendingRan.get());
    }
    @Test public void timerExpiryAndNetworkReturnRemainOrderedInQueue() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), resumed = new CountDownLatch(1);
        java.util.List<String> order = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.atomic.AtomicReference<Runnable> callback = new java.util.concurrent.atomic.AtomicReference<>();
        NetworkDisconnectTimer timer = new NetworkDisconnectTimer(policy, new NetworkDisconnectTimer.Scheduler() {
            public void postDelayed(Runnable r, long ms) { callback.set(r); }
            public void remove(Runnable r) { }
        }, 20000, () -> { entered.countDown(); uninterruptible(release); order.add("pause"); });
        timer.networkReturned(); timer.networkLost(true);
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, () -> fail("unexpected failure"), 8, 10000);
        try {
            assertTrue(q.submit(callback.get())); await(entered);
            assertTrue(q.submit(() -> timer.runIfOpen(() -> { timer.networkReturned(); order.add("resume"); resumed.countDown(); })));
            assertTrue(order.isEmpty()); release.countDown(); await(resumed);
            assertEquals(java.util.Arrays.asList("pause", "resume"), order);
        } finally { release.countDown(); q.close(); }
    }
    @Test public void realSessionListenerCanBlockWithoutBlockingQueueClose() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        SessionVpnStatus.Listener listener = (id, nonce, event) -> {
            entered.countDown(); uninterruptible(release); exited.countDown();
        };
        SessionVpnStatus.addListener(listener);
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, () -> {}, 8, 10000);
        try {
            SessionPauseEvents events = new SessionPauseEvents("test-id", "test-nonce");
            q.submit(() -> events.pause(OpenVPNManagement.pauseReason.userPause)); await(entered);
            CountDownLatch closed = new CountDownLatch(1);
            new Thread(() -> { q.close(); closed.countDown(); }).start(); await(closed);
        } finally { release.countDown(); await(exited); SessionVpnStatus.removeListener(listener); q.close(); }
    }
    @Test public void blockedLoopbackSocketTriggersWatchdogAndClose() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy();
        java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        java.net.Socket writer = new java.net.Socket(); writer.setSendBufferSize(4096);
        writer.connect(server.getLocalSocketAddress());
        java.net.Socket peer = server.accept(); peer.setReceiveBufferSize(4096);
        CountDownLatch writing = new CountDownLatch(1), failed = new CountDownLatch(1), exited = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, () -> {
            try { writer.close(); } catch (Exception ignored) { } finally { failed.countDown(); }
        }, 8, 400);
        try {
            q.submit(() -> {
                try {
                    writing.countDown(); byte[] bytes = new byte[65536];
                    for (int i = 0; i < 1024; i++) writer.getOutputStream().write(bytes);
                    completed.set(true);
                } catch (Exception expected) { } finally { exited.countDown(); }
            });
            await(writing); await(failed); await(exited); assertFalse(completed.get()); assertFalse(policy.shouldRun());
        } finally { writer.close(); peer.close(); server.close(); q.close(); }
    }
    @Test public void exceptionTriggersStopAndClosesQueue() throws Exception {
        PauseRunPolicy policy = new PauseRunPolicy(); CountDownLatch failed = new CountDownLatch(1);
        ReceiverOperationQueue q = new ReceiverOperationQueue(policy, failed::countDown, 8, 10000);
        try { assertTrue(q.submit(() -> { throw new IllegalStateException(); })); await(failed); assertFalse(q.submit(() -> {})); assertFalse(policy.shouldRun()); }
        finally { q.close(); }
    }
}
