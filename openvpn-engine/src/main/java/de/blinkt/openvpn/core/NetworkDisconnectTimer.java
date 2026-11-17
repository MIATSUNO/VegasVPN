package de.blinkt.openvpn.core;

/** Serializes receiver operations and timer effects; never calls management under policy. */
final class NetworkDisconnectTimer {
    interface Scheduler {
        void postDelayed(Runnable callback, long delayMillis);
        void remove(Runnable callback);
    }
    enum Return { CLOSED, PENDING, OTHER }

    private final PauseRunPolicy policy;
    private final Scheduler scheduler;
    private final long delayMillis;
    private final Runnable pause;
    // Protected by this monitor. Receiver actions must use runIfOpen, not policy's monitor.
    private boolean closed;
    private long generation;
    private Runnable scheduled;
    private Thread effectOwner;

    private void rejectEffectReentry() {
        if (effectOwner == Thread.currentThread())
            throw new IllegalStateException("receiver operation reentered during timeout pause");
    }

    NetworkDisconnectTimer(PauseRunPolicy policy, Scheduler scheduler, long delayMillis, Runnable pause) {
        this.policy = policy;
        this.scheduler = scheduler;
        this.delayMillis = delayMillis;
        this.pause = pause;
    }

    // The monitor covers the complete receiver operation including management effects.
    // It may block on socket I/O. Never acquire it from a policy/management callback.
    synchronized boolean runIfOpen(Runnable operation) {
        rejectEffectReentry();
        if (closed) return false;
        effectOwner = Thread.currentThread();
        try { operation.run(); }
        finally { effectOwner = null; }
        return true;
    }

    synchronized Return networkReturned() {
        if (closed) return Return.CLOSED;
        boolean pending = policy.network() == PauseRunPolicy.State.PENDINGDISCONNECT;
        invalidateLocked();
        policy.network(PauseRunPolicy.State.SHOULDBECONNECTED);
        return pending ? Return.PENDING : Return.OTHER;
    }

    synchronized void networkLost(boolean delayed) {
        if (closed || !policy.networkLost(delayed) || !delayed) return;
        final long token = ++generation;
        scheduled = () -> expire(token);
        scheduler.postDelayed(scheduled, delayMillis);
    }

    synchronized void close() {
        rejectEffectReentry();
        if (closed) return;
        closed = true;
        invalidateLocked();
        policy.close();
    }

    private void invalidateLocked() {
        ++generation;
        if (scheduled != null) scheduler.remove(scheduled);
        scheduled = null;
    }

    private synchronized void expire(long token) {
        if (closed || token != generation || scheduled == null) return;
        scheduled = null;
        if (!policy.disconnectPendingNetwork()) return;
        // Hold the operation monitor until the effect finishes, but not the policy monitor:
        // return/close cannot complete between the transition and this call.
        effectOwner = Thread.currentThread();
        try { pause.run(); }
        finally { effectOwner = null; }
    }
}
