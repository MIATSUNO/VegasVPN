package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class SessionStopDispatcherTest {
    private static SessionStopDispatcher.Starter threads() {
        return (r, name) -> { Thread t = new Thread(r, name); t.setDaemon(true); t.start(); };
    }
    @Test public void blockedManagementAndDestroyDoNotBlockCallerOrEachOther() throws Exception {
        CountDownLatch managementIn = new CountDownLatch(1), processIn = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger mgmt = new AtomicInteger(), proc = new AtomicInteger();
        Runnable a = () -> { mgmt.incrementAndGet(); managementIn.countDown(); try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); } finally { done.countDown(); } };
        Runnable b = () -> { proc.incrementAndGet(); processIn.countDown(); try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); } finally { done.countDown(); } };
        SessionStopDispatcher d = new SessionStopDispatcher(a, b, threads(), (n,e) -> fail(n));
        try {
            d.request();
            assertTrue(managementIn.await(2, TimeUnit.SECONDS));
            assertTrue(processIn.await(2, TimeUnit.SECONDS));
            Thread[] callers = new Thread[30];
            for (int i=0; i<callers.length; i++) { callers[i] = new Thread(d::request); callers[i].start(); }
            for (Thread t : callers) { t.join(2000); assertFalse(t.isAlive()); }
            assertEquals(1, mgmt.get()); assertEquals(1, proc.get());
        } finally { release.countDown(); }
        assertTrue(done.await(2, TimeUnit.SECONDS));
    }
    @Test public void staleCapturedDispatcherOnlyTouchesOldResources() throws Exception {
        CountDownLatch oldIn = new CountDownLatch(1), release = new CountDownLatch(1), oldDone = new CountDownLatch(1);
        AtomicInteger oldCalls = new AtomicInteger(), newCalls = new AtomicInteger();
        SessionStopDispatcher old = new SessionStopDispatcher(null, () -> {
            oldIn.countDown(); try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
            oldCalls.incrementAndGet(); oldDone.countDown();
        }, threads(), (n,e) -> fail(n));
        SessionStopDispatcher next = new SessionStopDispatcher(null, newCalls::incrementAndGet, threads(), (n,e) -> fail(n));
        try { old.request(); assertTrue(oldIn.await(2, TimeUnit.SECONDS)); assertEquals(0, newCalls.get()); }
        finally { release.countDown(); }
        assertTrue(oldDone.await(2, TimeUnit.SECONDS)); assertEquals(1, oldCalls.get()); assertEquals(0, newCalls.get());
        CountDownLatch nextDone = new CountDownLatch(1);
        next = new SessionStopDispatcher(null, () -> { newCalls.incrementAndGet(); nextDone.countDown(); }, threads(), (n,e) -> fail(n));
        next.request(); assertTrue(nextDone.await(2, TimeUnit.SECONDS)); assertEquals(1, newCalls.get());
    }
    @Test public void rejectedWorkerStillStartsOtherAndCanRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger(); CountDownLatch process = new CountDownLatch(1), retry = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        SessionStopDispatcher.Starter starter = (r,n) -> { if (n.equals("VPNManagementStop") && attempts.getAndIncrement() == 0) throw new IllegalStateException("rejected"); threads().start(r,n); };
        SessionStopDispatcher d = new SessionStopDispatcher(retry::countDown, process::countDown, starter, (n,e) -> failures.incrementAndGet());
        d.request(); assertTrue(process.await(2, TimeUnit.SECONDS)); assertEquals(1, failures.get());
        d.request(); assertTrue(retry.await(2, TimeUnit.SECONDS));
    }
    @Test public void thrownStopStillAllowsIndependentStopAndRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger(), failures = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1), second = new CountDownLatch(1), process = new CountDownLatch(1);
        SessionStopDispatcher d = new SessionStopDispatcher(() -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("failed");
            second.countDown();
        }, process::countDown, threads(), (n,e) -> { failures.incrementAndGet(); first.countDown(); });
        d.request(); assertTrue(first.await(2, TimeUnit.SECONDS)); assertTrue(process.await(2, TimeUnit.SECONDS));
        // failure callback fires before the worker clears its running flag: retry until the first worker has returned.
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (second.getCount() != 0 && System.nanoTime() < until) { d.request(); Thread.yield(); }
        assertTrue(second.await(2, TimeUnit.SECONDS)); assertEquals(1, failures.get());
    }
}
