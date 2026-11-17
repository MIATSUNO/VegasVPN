package de.blinkt.openvpn.core;

import org.junit.Test;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class ProcessStartGateTest {
    static class FakeProcess extends Process {
        volatile boolean destroyed;
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public int waitFor() throws InterruptedException { return 0; }
        public int exitValue() { return 0; }
        public void destroy() { destroyed = true; }
    }
    @Test public void stopBeforeStartPreventsFactory() throws Exception {
        ProcessStartGate g = new ProcessStartGate();
        AtomicBoolean called = new AtomicBoolean();
        g.stop();
        assertNull(g.start(() -> { called.set(true); return new FakeProcess(); }));
        assertFalse(called.get());
    }
    @Test public void stopAfterStartDestroysOnlyOwnedProcess() throws Exception {
        ProcessStartGate a = new ProcessStartGate(), b = new ProcessStartGate();
        FakeProcess first = new FakeProcess(), second = new FakeProcess();
        assertSame(first, a.start(() -> first));
        assertSame(second, b.start(() -> second));
        a.stop();
        assertTrue(first.destroyed);
        assertFalse(second.destroyed);
    }
    @Test public void stopDuringFactoryWaitsThenDestroysChild() throws Exception {
        ProcessStartGate g = new ProcessStartGate();
        FakeProcess p = new FakeProcess();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread start = new Thread(() -> { try { g.start(() -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout"); }
            catch (InterruptedException e) { throw new IOException(e); }
            return p;
        }); } catch (Throwable e) { error.set(e); } });
        start.start(); assertTrue(entered.await(5, TimeUnit.SECONDS));
        Thread stop = new Thread(g::stop); stop.start();
        stop.join(2000);
        assertFalse("stop must not block on process factory", stop.isAlive());
        assertFalse(p.destroyed);
        release.countDown();
        start.join(5000); stop.join(5000);
        assertFalse(start.isAlive()); assertFalse(stop.isAlive());
        assertNull(error.get()); assertTrue(p.destroyed);
    }
    @Test public void throwingDestroyAfterCancelledStartKeepsChildUntilConfirmedExit() throws Exception {
        ProcessStartGate g = new ProcessStartGate();
        CountDownLatch factoryEntered = new CountDownLatch(1), releaseFactory = new CountDownLatch(1);
        CountDownLatch waitEntered = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        AtomicBoolean signalledStopped = new AtomicBoolean();
        Process real = new ProcessBuilder("sh", "-c", "read line").start();
        Process p = new FakeProcess() {
            @Override public void destroy() { throw new IllegalStateException("destroy failed"); }
            @Override public int waitFor() throws InterruptedException {
                waitEntered.countDown();
                return real.waitFor();
            }
        };
        Thread worker = new Thread(() -> {
            try {
                g.start(() -> {
                    factoryEntered.countDown();
                    try { releaseFactory.await(); } catch (InterruptedException e) { throw new IOException(e); }
                    return p;
                });
            } catch (Throwable t) { observedFailure.set(t); }
            finally {
                ProcessExitWaiter.awaitOwned(g); // same completion path as OpenVPNThread
                signalledStopped.set(true);
                completed.countDown();
            }
        });
        worker.start();
        try {
            assertTrue(factoryEntered.await(5, TimeUnit.SECONDS));
            g.stop();
            releaseFactory.countDown();
            assertTrue(waitEntered.await(5, TimeUnit.SECONDS));
            assertTrue(observedFailure.get() instanceof IllegalStateException);
            assertEquals("destroy failed", observedFailure.get().getMessage());
            assertSame(p, g.child());
            assertTrue("real subprocess still alive", real.isAlive());
            assertFalse("live child must not be reported stopped", signalledStopped.get());
            assertFalse(completed.await(100, TimeUnit.MILLISECONDS));
            real.getOutputStream().write('\n');
            real.getOutputStream().flush();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertFalse(real.isAlive());
            assertTrue(signalledStopped.get());
        } finally {
            releaseFactory.countDown();
            real.destroyForcibly();
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
    }
    @Test public void destroyDoesNotHoldGateLockAndFailureCanBeRetried() throws Exception {
        ProcessStartGate g = new ProcessStartGate();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Process p = new FakeProcess() {
            @Override public void destroy() {
                if (attempts.incrementAndGet() == 1) {
                    entered.countDown();
                    try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                    throw new IllegalStateException("destroy failed");
                }
            }
        };
        assertSame(p, g.start(() -> p));
        Thread first = new Thread(() -> { try { g.stop(); } catch (Throwable t) { failure.set(t); } });
        first.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Thread second = new Thread(g::stop);
            second.start(); second.join(1000);
            assertFalse("another stop must not wait on destroy's gate lock", second.isAlive());
            assertSame(p, g.child());
        } finally { release.countDown(); first.join(5000); }
        assertTrue(failure.get() instanceof IllegalStateException);
        g.stop();
        assertEquals(2, attempts.get());
    }
    @Test public void failedFactoryThenStopPreventsRetry() throws Exception {
        ProcessStartGate g = new ProcessStartGate();
        try { g.start(() -> { throw new IOException("failure"); }); fail(); } catch (IOException expected) { }
        g.stop(); assertNull(g.start(FakeProcess::new));
    }
}
