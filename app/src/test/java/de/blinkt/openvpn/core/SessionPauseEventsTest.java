package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SessionPauseEventsTest {
    @Test public void emitsSessionBoundPauseAndRequiresNewConnectionAfterResume() {
        List<String> events = new ArrayList<>();
        SessionVpnStatus.Listener listener = (id, nonce, event) -> events.add(id + ":" + nonce + ":" + event);
        SessionVpnStatus.addListener(listener);
        try {
            SessionPauseEvents current = new SessionPauseEvents("same", "new");
            SessionPauseEvents old = new SessionPauseEvents("same", "old");
            current.progress(SessionVpnStatus.Event.CONNECTED);
            current.pause(OpenVPNManagement.pauseReason.noNetwork);
            current.progress(SessionVpnStatus.Event.CONNECTED); // late, while paused
            old.pause(OpenVPNManagement.pauseReason.userPause);
            current.pause(OpenVPNManagement.pauseReason.screenOff);
            current.resume();
            current.resume();
            assertEquals(4, events.size()); // request alone is not engine progress
            current.progress(SessionVpnStatus.Event.CONNECTED); // stale or unconfirmed; ignored
            assertEquals(4, events.size());
            current.progress(SessionVpnStatus.Event.CONNECTING); // observed engine progress
            current.progress(SessionVpnStatus.Event.CONNECTED);
            assertEquals(java.util.Arrays.asList("same:new:CONNECTED", "same:new:NONETWORK", "same:old:PAUSED", "same:new:PAUSED", "same:new:CONNECTING", "same:new:CONNECTED"), events);
        } finally { SessionVpnStatus.removeListener(listener); }
    }
    @Test public void legacyAndMissingIdentityEmitNothing() {
        List<String> events = new ArrayList<>();
        SessionVpnStatus.Listener l = (id, nonce, event) -> events.add(id);
        SessionVpnStatus.addListener(l);
        try { new SessionPauseEvents("id", null).pause(OpenVPNManagement.pauseReason.noNetwork); new SessionPauseEvents(null, "n").pause(OpenVPNManagement.pauseReason.userPause); assertTrue(events.isEmpty()); }
        finally { SessionVpnStatus.removeListener(l); }
    }
}
