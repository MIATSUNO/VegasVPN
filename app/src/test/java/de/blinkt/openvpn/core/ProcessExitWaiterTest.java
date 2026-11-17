package de.blinkt.openvpn.core;

import org.junit.Test;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class ProcessExitWaiterTest {
    static final class DelayedProcess extends Process {
        final CountDownLatch entered = new CountDownLatch(1), exited = new CountDownLatch(1);
        final AtomicInteger destroyCalls = new AtomicInteger();
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public int waitFor() throws InterruptedException { entered.countDown(); exited.await(); return 19; }
        public int exitValue() { if (exited.getCount() != 0) throw new IllegalThreadStateException(); return 19; }
        public void destroy() { destroyCalls.incrementAndGet(); }
    }
    @Test public void destroyAndInterruptDoNotPretendExit() throws Exception {
        ProcessStartGate gate = new ProcessStartGate();
        DelayedProcess p = new DelayedProcess();
        gate.start(() -> p);
        AtomicBoolean completed = new AtomicBoolean(), interruptRestored = new AtomicBoolean();
        AtomicInteger exit = new AtomicInteger(-1);
        Thread t = new Thread(() -> { exit.set(ProcessExitWaiter.await(p)); interruptRestored.set(Thread.currentThread().isInterrupted()); completed.set(true); });
        t.start();
        try {
            assertTrue(p.entered.await(3, TimeUnit.SECONDS));
            gate.stop(); gate.stop();
            assertEquals(1, p.destroyCalls.get());
            t.interrupt();
            t.join(150);
            assertFalse("no completion before actual exit", completed.get());
        } finally { p.exited.countDown(); t.join(3000); }
        assertFalse(t.isAlive()); assertTrue(completed.get()); assertEquals(19, exit.get()); assertTrue(interruptRestored.get());
    }
    @Test public void preInterruptedWorkerStillWaitsForExit() throws Exception {
        DelayedProcess p = new DelayedProcess();
        AtomicBoolean done = new AtomicBoolean(), restored = new AtomicBoolean();
        Thread t = new Thread(() -> { Thread.currentThread().interrupt(); ProcessExitWaiter.await(p); restored.set(Thread.currentThread().isInterrupted()); done.set(true); });
        t.start();
        try { assertTrue(p.entered.await(3, TimeUnit.SECONDS)); t.join(150); assertFalse(done.get()); }
        finally { p.exited.countDown(); t.join(3000); }
        assertTrue(done.get()); assertTrue(restored.get());
    }
    @Test public void concurrentStartsCannotReplaceOwnedChild() throws Exception {
        ProcessStartGate gate = new ProcessStartGate();
        DelayedProcess p = new DelayedProcess();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread starter = new Thread(() -> { try { gate.start(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { throw new IOException(e); } return p; }); } catch (Throwable t) { err.set(t); } });
        starter.start();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            try { gate.start(DelayedProcess::new); fail("second start accepted"); } catch (IllegalStateException expected) { }
            gate.stop();
        } finally { release.countDown(); starter.join(3000); }
        assertNull(err.get()); assertFalse(starter.isAlive()); assertEquals(1, p.destroyCalls.get());
    }
    @Test public void noChildAndConfirmedExitProduceDifferentEvents() throws Exception {
        ProcessStartGate noChild = new ProcessStartGate(); noChild.stop();
        assertNull(noChild.start(DelayedProcess::new));
        assertEquals(SessionVpnStatus.Event.START_ABORTED, ProcessExitWaiter.eventFor(ProcessExitWaiter.awaitOwned(noChild)));
        ProcessStartGate withChild = new ProcessStartGate(); DelayedProcess p = new DelayedProcess();
        withChild.start(() -> p);
        AtomicReference<SessionVpnStatus.Event> event = new AtomicReference<>();
        Thread t = new Thread(() -> event.set(ProcessExitWaiter.eventFor(ProcessExitWaiter.awaitOwned(withChild))));
        t.start();
        try { assertTrue(p.entered.await(3, TimeUnit.SECONDS)); t.interrupt(); t.join(150); assertNull(event.get()); }
        finally { p.exited.countDown(); t.join(3000); }
        assertFalse(t.isAlive()); assertEquals(SessionVpnStatus.Event.PROCESS_EXITED, event.get());
    }
    @Test public void failedProcessStartIsAbortedNotExited() throws Exception {
        ProcessStartGate gate = new ProcessStartGate();
        try { gate.start(() -> { throw new IOException("start failed"); }); fail(); } catch (IOException expected) { }
        assertEquals(SessionVpnStatus.Event.START_ABORTED, ProcessExitWaiter.eventFor(ProcessExitWaiter.awaitOwned(gate)));
    }
    @Test public void realJvmChildIsWaitedAfterInterruption() throws Exception {
        ProcessStartGate gate = new ProcessStartGate();
        Process p = gate.start(() -> new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-version").redirectErrorStream(true).start());
        AtomicBoolean restored = new AtomicBoolean();
        AtomicInteger exit = new AtomicInteger(-1);
        AtomicReference<SessionVpnStatus.Event> event = new AtomicReference<>();
        Thread t = new Thread(() -> { Thread.currentThread().interrupt(); Integer result = ProcessExitWaiter.awaitOwned(gate); exit.set(result); event.set(ProcessExitWaiter.eventFor(result)); restored.set(Thread.currentThread().isInterrupted()); });
        try { t.start(); t.join(10000); assertFalse(t.isAlive()); assertEquals(0, exit.get()); assertEquals(SessionVpnStatus.Event.PROCESS_EXITED, event.get()); assertTrue(restored.get()); }
        finally { p.destroy(); }
    }
}
