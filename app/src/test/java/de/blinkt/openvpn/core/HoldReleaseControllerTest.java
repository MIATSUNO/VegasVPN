package de.blinkt.openvpn.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static de.blinkt.openvpn.core.PauseRunPolicy.State.*;
import static org.junit.Assert.*;

public class HoldReleaseControllerTest {
    private static class Fixture {
        final PauseRunPolicy policy = new PauseRunPolicy();
        final List<String> commands = new ArrayList<>();
        long now = 10000;
        long retry = -1;
        boolean writeSucceeds = true;
        final HoldReleaseController hold = new HoldReleaseController(policy::runIfAllowed,
                () -> now, delay -> retry = delay, new HoldReleaseController.Commands() {
            public boolean release() { commands.add("release"); return writeSucceeds; }
            public void networkChange(boolean same) { commands.add(same ? "same" : "different"); }
        });
        void online() { policy.network(SHOULDBECONNECTED); }
    }
    @Test public void userPauseSurvivesSameAndDifferentNetworkAndExplicitResumeReleases() {
        Fixture f = new Fixture(); f.online(); f.policy.user(DISCONNECTED); f.hold.hold();
        assertFalse(f.hold.networkChange(true)); assertFalse(f.hold.networkChange(false));
        f.hold.releaseIfAllowed(); assertTrue(f.hold.waiting()); assertTrue(f.commands.isEmpty());
        f.policy.user(SHOULDBECONNECTED); f.hold.releaseIfAllowed();
        assertEquals(java.util.Arrays.asList("release"), f.commands); assertFalse(f.hold.waiting());
    }
    @Test public void screenPauseAndNetworkAbsenceBlockAllReconnectPaths() {
        Fixture f = new Fixture(); f.online(); f.policy.screen(DISCONNECTED); f.hold.hold();
        assertFalse(f.hold.networkChange(true)); f.hold.releaseIfAllowed();
        f.policy.screen(SHOULDBECONNECTED); f.policy.network(DISCONNECTED); f.hold.releaseIfAllowed();
        f.policy.network(PENDINGDISCONNECT); f.hold.releaseIfAllowed();
        assertTrue(f.commands.isEmpty()); assertTrue(f.hold.waiting());
        f.online(); assertTrue(f.hold.networkChange(false)); assertEquals("release", f.commands.get(0));
    }
    @Test public void withoutHoldOnlyAuthorizedNetworkCommandsAreSent() {
        Fixture f = new Fixture(); f.online(); f.policy.user(DISCONNECTED);
        assertFalse(f.hold.networkChange(true)); assertTrue(f.commands.isEmpty());
        f.policy.user(SHOULDBECONNECTED); assertTrue(f.hold.networkChange(true));
        assertTrue(f.hold.networkChange(false)); assertEquals(java.util.Arrays.asList("same", "different"), f.commands);
    }
    @Test public void delayedRetryRechecksPermissionAndDeduplicatesRelease() {
        Fixture f = new Fixture(); f.online(); f.hold.hold(); f.hold.releaseIfAllowed();
        f.hold.hold(); f.now += 1000; f.hold.releaseIfAllowed(); assertEquals(4000, f.retry);
        f.policy.user(DISCONNECTED); f.now += 4000; f.hold.releaseIfAllowed();
        assertEquals(1, f.commands.size()); assertTrue(f.hold.waiting());
        f.policy.user(SHOULDBECONNECTED); f.hold.releaseIfAllowed(); f.hold.releaseIfAllowed();
        assertEquals(2, f.commands.size()); assertFalse(f.hold.waiting());
    }
    @Test public void unknownCallbackFailsClosed() {
        OpenVPNManagement.PausedStateCallback callback = () -> true;
        List<String> writes = new ArrayList<>();
        HoldReleaseController c = new HoldReleaseController(callback::runIfAllowed, () -> 10000,
                d -> {}, new HoldReleaseController.Commands() {
            public boolean release() { writes.add("release"); return true; }
            public void networkChange(boolean same) { writes.add("network"); }
        });
        c.hold(); c.releaseIfAllowed(); assertFalse(c.networkChange(true));
        assertTrue(c.waiting()); assertTrue(writes.isEmpty());
    }
    @Test public void failedWriteRetainsHoldForLaterAuthorizedAttempt() {
        Fixture f = new Fixture(); f.online(); f.hold.hold(); f.writeSucceeds = false;
        f.hold.releaseIfAllowed(); assertTrue(f.hold.waiting()); f.writeSucceeds = true;
        f.hold.releaseIfAllowed(); assertFalse(f.hold.waiting()); assertEquals(2, f.commands.size());
    }
    @Test public void lateNetworkTimeoutCannotOverrideReturnAndScreenTimeoutCannotOverrideOn() {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(PENDINGDISCONNECT);
        p.network(SHOULDBECONNECTED); assertFalse(p.disconnectPendingNetwork());
        p.screenOff(); assertEquals(PENDINGDISCONNECT, p.screen()); p.screen(SHOULDBECONNECTED);
        assertFalse(p.disconnectPendingScreen()); assertTrue(p.shouldRun());
        p.user(DISCONNECTED); p.screenOff(); assertEquals(DISCONNECTED, p.screen());
        p.user(SHOULDBECONNECTED); p.screen(SHOULDBECONNECTED);
        p.network(PENDINGDISCONNECT); p.screen(PENDINGDISCONNECT);
        assertTrue(p.disconnectPendingNetwork()); assertEquals(DISCONNECTED, p.screen());
        assertFalse(p.shouldRun());
    }
    @Test public void permissionChangeAndReleaseHaveOneLinearizationPoint() throws Exception {
        PauseRunPolicy p = new PauseRunPolicy(); p.network(SHOULDBECONNECTED);
        CountDownLatch inside = new CountDownLatch(1), finish = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(1), paused = new CountDownLatch(1);
        AtomicBoolean observedPausedAtWrite = new AtomicBoolean();
        HoldReleaseController c = new HoldReleaseController(p::runIfAllowed, () -> 10000, d -> {},
                new HoldReleaseController.Commands() {
            public boolean release() { inside.countDown(); try { if (!finish.await(3, TimeUnit.SECONDS)) throw new AssertionError("timeout"); } catch (InterruptedException e) { throw new AssertionError(e); } observedPausedAtWrite.set(!p.shouldRun()); return true; }
            public void networkChange(boolean same) {}
        });
        c.hold(); Thread release = new Thread(c::releaseIfAllowed); release.start(); assertTrue(inside.await(3, TimeUnit.SECONDS));
        Thread pause = new Thread(() -> { attempted.countDown(); p.user(DISCONNECTED); paused.countDown(); }); pause.start();
        assertTrue(attempted.await(3, TimeUnit.SECONDS)); assertFalse(paused.await(100, TimeUnit.MILLISECONDS));
        finish.countDown(); release.join(3000); pause.join(3000); assertFalse(release.isAlive()); assertFalse(pause.isAlive());
        assertFalse(observedPausedAtWrite.get()); assertFalse(p.shouldRun());
        c.hold(); c.releaseIfAllowed(); assertTrue(c.waiting());
    }
}
