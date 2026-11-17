package de.blinkt.openvpn.core;

import org.junit.Test;
import static de.blinkt.openvpn.core.PauseRunPolicy.Action.*;
import static de.blinkt.openvpn.core.PauseRunPolicy.State.*;
import static org.junit.Assert.*;

/** JVM policy tests; Android broadcasts and the management socket are not simulated. */
public class PauseRunPolicyRegressionTest {
    @Test public void screenOnDoesNotClearPendingNetworkLoss() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        assertTrue(p.networkLost(true));
        assertEquals(NONE, p.screenOn());
        assertEquals(PENDINGDISCONNECT, p.network());
        assertTrue(p.disconnectPendingNetwork());
        assertEquals(DISCONNECTED, p.network()); assertFalse(p.shouldRun());
    }
    @Test public void returnBeforeNetworkTimeoutMakesTimeoutNoOp() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        assertTrue(p.networkLost(true)); p.network(SHOULDBECONNECTED);
        p.screenOn(); assertFalse(p.disconnectPendingNetwork()); assertTrue(p.shouldRun());
    }
    @Test public void duplicateResumeDoesNotPauseAnAlreadyRunningSession() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        assertEquals(NONE, p.setUserPaused(false));
        assertEquals(PAUSE, p.setUserPaused(true));
        assertEquals(NONE, p.setUserPaused(true));
        assertEquals(RESUME, p.setUserPaused(false));
        assertEquals(NONE, p.setUserPaused(false)); assertTrue(p.shouldRun());
    }
    @Test public void lossWithoutAutoReconnectStillDeniesReleaseAndNetworkChange() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        assertTrue(p.networkLost(false)); assertEquals(DISCONNECTED, p.network());
        final int[] writes = {0};
        HoldReleaseController h = new HoldReleaseController(p::runIfAllowed, () -> 10_000, d -> {},
                new HoldReleaseController.Commands() {
                    public boolean release() { writes[0]++; return true; }
                    public void networkChange(boolean same) { writes[0]++; }
                });
        h.hold(); h.releaseIfAllowed(); assertFalse(h.networkChange(false));
        assertEquals(0, writes[0]); assertTrue(h.waiting());
        p.network(SHOULDBECONNECTED); h.releaseIfAllowed(); assertEquals(1, writes[0]);
    }
    @Test public void repeatedLossDoesNotRearmOrExtendGracePeriod() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        assertTrue(p.networkLost(true)); assertFalse(p.networkLost(true));
        assertTrue(p.disconnectPendingNetwork()); assertFalse(p.networkLost(true));
        p.network(SHOULDBECONNECTED); assertTrue(p.networkLost(true));
    }
    @Test public void screenAndUserResumeRemainDeniedDuringNetworkLoss() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        assertEquals(PAUSE, p.setUserPaused(true)); p.screen(DISCONNECTED);
        assertTrue(p.networkLost(false));
        assertEquals(NONE, p.setUserPaused(false));
        assertEquals(NONE, p.screenOn()); assertFalse(p.shouldRun());
    }
}
