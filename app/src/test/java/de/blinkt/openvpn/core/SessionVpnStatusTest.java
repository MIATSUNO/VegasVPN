package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SessionVpnStatusTest {
    @Test public void failingListenerDoesNotSuppressOtherExitObservers() {
        List<String> received = new ArrayList<>();
        SessionVpnStatus.Listener bad = (id, nonce, event) -> { throw new IllegalStateException("failure"); };
        SessionVpnStatus.Listener good = (id, nonce, event) -> received.add(id + ":" + nonce + ":" + event.name());
        SessionVpnStatus.addListener(bad); SessionVpnStatus.addListener(good);
        try {
            SessionVpnStatus.emit("s", "n", SessionVpnStatus.Event.PROCESS_EXITED);
            assertEquals(1, received.size());
            assertEquals("s:n:PROCESS_EXITED", received.get(0));
        } finally { SessionVpnStatus.removeListener(bad); SessionVpnStatus.removeListener(good); }
    }
    @Test public void eventsPreserveProducerIdAndDoNotReplay() {
        List<String> events = new ArrayList<>();
        SessionVpnStatus.emit("old", "n", SessionVpnStatus.Event.CONNECTED);
        SessionVpnStatus.Listener listener = (id, nonce, event) -> events.add(id + ":" + nonce + ":" + event.name());
        SessionVpnStatus.addListener(listener);
        try {
            assertTrue(events.isEmpty());
            SessionVpnStatus.emit("new", "n", SessionVpnStatus.Event.CONNECTING);
            SessionVpnStatus.emit("old", "n", SessionVpnStatus.Event.CONNECTED);
            SessionVpnStatus.emit("old", "n", SessionVpnStatus.Event.PROCESS_EXITED);
            SessionVpnStatus.emit(null, "n", SessionVpnStatus.Event.CONNECTED);
            SessionVpnStatus.emit("old", null, SessionVpnStatus.Event.PROCESS_EXITED);
            SessionVpnStatus.emit("old", "", SessionVpnStatus.Event.PROCESS_EXITED);
            SessionVpnStatus.emit("old", "other", SessionVpnStatus.Event.PROCESS_EXITED);
            assertEquals(4, events.size());
            assertEquals("old:other:PROCESS_EXITED", events.get(3));
            assertEquals("new:n:CONNECTING", events.get(0));
            assertEquals("old:n:CONNECTED", events.get(1));
            assertEquals("old:n:PROCESS_EXITED", events.get(2));
        } finally { SessionVpnStatus.removeListener(listener); }
    }
}
