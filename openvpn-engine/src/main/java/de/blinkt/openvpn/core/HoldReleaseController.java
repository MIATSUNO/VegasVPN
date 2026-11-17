package de.blinkt.openvpn.core;

/** The permission gate runs actions atomically with pause-state changes. */
final class HoldReleaseController {
    interface Permission { boolean runIfAllowed(Runnable action); }
    interface Clock { long now(); }
    interface Scheduler { void retryAfter(long delayMs); }
    interface Commands {
        boolean release();
        void networkChange(boolean sameNetwork);
    }
    private final Permission permission;
    private final Clock clock;
    private final Scheduler scheduler;
    private final Commands commands;
    private boolean waiting;
    private long lastRelease = Long.MIN_VALUE;

    HoldReleaseController(Permission permission, Clock clock, Scheduler scheduler, Commands commands) {
        this.permission = permission;
        this.clock = clock;
        this.scheduler = scheduler;
        this.commands = commands;
    }
    synchronized void hold() { waiting = true; }
    synchronized boolean waiting() { return waiting; }

    void releaseIfAllowed() { permission.runIfAllowed(this::releaseLockedIfWaiting); }

    // network-change can itself reconnect, so neither branch runs while paused.
    boolean networkChange(boolean sameNetwork) {
        return permission.runIfAllowed(() -> {
            synchronized (this) {
                if (waiting) releaseLockedIfWaiting();
                else commands.networkChange(sameNetwork);
            }
        });
    }

    // Called only with permission held. Never sleep while holding either lock.
    private synchronized void releaseLockedIfWaiting() {
        if (!waiting) return;
        long now = clock.now();
        long elapsed = lastRelease == Long.MIN_VALUE ? Long.MAX_VALUE : now - lastRelease;
        if (elapsed >= 0 && elapsed < 5000) {
            scheduler.retryAfter(5000 - elapsed);
            return;
        }
        if (commands.release()) {
            waiting = false;
            lastRelease = now;
        }
    }
}
