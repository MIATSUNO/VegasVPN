package de.blinkt.openvpn.core;

import org.junit.Test;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ProtectFdHandlerTest {
    private interface Operation { void run(FileDescriptor fd) throws Exception; }
    private void withFd(Operation op) throws Exception {
        try (FileInputStream f = new FileInputStream("/dev/null")) { op.run(f.getFD()); }
    }
    private List<String> run(boolean value) {
        List<String> events = new ArrayList<>();
        ProtectFdHandler.needOk(value, status -> { events.add(status); return true; }, () -> events.add("stop"));
        return events;
    }
    @Test public void trueProtectSendsOkAndClosesDuplicate() throws Exception {
        withFd(fd -> {
            AtomicInteger calls = new AtomicInteger(), closes = new AtomicInteger();
            boolean result = ProtectFdHandler.protect(fd, f -> { calls.incrementAndGet(); return true; }, f -> closes.incrementAndGet());
            assertTrue(result); assertEquals(1, calls.get()); assertEquals(1, closes.get());
            assertEquals(Arrays.asList("ok"), run(result));
        });
    }
    @Test public void falseProtectStopsAndAttemptsCancelAndClosesDuplicate() throws Exception {
        withFd(fd -> {
            AtomicInteger closes = new AtomicInteger();
            boolean result = ProtectFdHandler.protect(fd, f -> false, f -> closes.incrementAndGet());
            assertFalse(result); assertEquals(1, closes.get());
            assertEquals(Arrays.asList("stop", "cancel"), run(result));
        });
    }
    @Test public void missingAndInvalidDescriptorsCancelWithoutProtect() {
        AtomicInteger calls = new AtomicInteger(), closes = new AtomicInteger();
        ProtectFdHandler.Protector p = f -> { calls.incrementAndGet(); return true; };
        ProtectFdHandler.Closer c = f -> closes.incrementAndGet();
        assertEquals(Arrays.asList("stop", "cancel"), run(ProtectFdHandler.protect(null, p, c)));
        assertEquals(Arrays.asList("stop", "cancel"), run(ProtectFdHandler.protect(new FileDescriptor(), p, c)));
        assertEquals(0, calls.get()); assertEquals(1, closes.get());
    }
    @Test public void protectOrReflectionExceptionCancelsAndCloses() throws Exception {
        withFd(fd -> {
            AtomicInteger closes = new AtomicInteger();
            for (Exception e : Arrays.asList(new IOException("protect"), new NoSuchMethodException("reflection"), new SecurityException("blocked"))) {
                boolean result = ProtectFdHandler.protect(fd, f -> { throw e; }, f -> closes.incrementAndGet());
                assertEquals(Arrays.asList("stop", "cancel"), run(result));
            }
            assertEquals(3, closes.get());
        });
    }
    @Test public void closeFailureAlsoFailsClosed() throws Exception {
        withFd(fd -> assertEquals(Arrays.asList("stop", "cancel"), run(ProtectFdHandler.protect(fd, f -> true, f -> { throw new IOException(); }))));
    }
    @Test public void failedOrThrowingReplyStillStops() {
        AtomicInteger stops = new AtomicInteger();
        ProtectFdHandler.needOk(true, s -> false, stops::incrementAndGet);
        try { ProtectFdHandler.needOk(false, s -> { throw new IllegalStateException(); }, stops::incrementAndGet); fail(); } catch (IllegalStateException expected) { }
        try { ProtectFdHandler.needOk(true, s -> { throw new IllegalStateException(); }, stops::incrementAndGet); fail(); } catch (IllegalStateException expected) { }
        assertEquals(3, stops.get());
    }
}
