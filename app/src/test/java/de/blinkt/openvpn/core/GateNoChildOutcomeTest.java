package de.blinkt.openvpn.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class GateNoChildOutcomeTest {
    @Test public void cancelledBeforeLaunchOnlyEndsWhenNoProducerIsKnown() {
        GateStartRegistry r = new GateStartRegistry(); String n = r.reserve("same");
        assertTrue(r.cancel("same", n));
        assertEquals(GateStartRegistry.Claim.CANCELLED, r.claim("same", n));
        assertTrue(GateNoChildOutcome.mayEmit(false, false, null, "same", n));
        assertEquals(GateStartRegistry.Claim.REJECTED, r.claim("same", n));
        assertTrue(r.release("same", n));
    }
    @Test public void busyAndCleanupFailureRemainFailClosedEvenForOtherProducer() {
        NotificationSession.Identity other = new NotificationSession.Identity("same", "other");
        assertFalse(GateNoChildOutcome.mayEmit(false, true, other, "same", "n"));
        assertFalse(GateNoChildOutcome.mayEmit(false, true, null, "same", "n"));
        assertFalse(GateNoChildOutcome.mayEmit(false, false, other, "same", "n"));
        assertFalse(GateNoChildOutcome.mayEmit(true, false, null, "same", "n"));
        assertFalse(GateNoChildOutcome.mayEmit(false, false, null, "same", null));
    }
}
