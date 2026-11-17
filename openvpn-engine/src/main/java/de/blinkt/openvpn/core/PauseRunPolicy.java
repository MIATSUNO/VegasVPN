package de.blinkt.openvpn.core;

/** Serializes permission changes with the management command that consumes them. */
final class PauseRunPolicy {
    enum State { SHOULDBECONNECTED, PENDINGDISCONNECT, DISCONNECTED }
    private State network = State.DISCONNECTED;
    private State screen = State.SHOULDBECONNECTED;
    private State user = State.SHOULDBECONNECTED;
    // Closing must not wait for an in-flight management write under this monitor.
    private volatile boolean closed;
    void close() { closed = true; }

    synchronized State network() { return network; }
    synchronized State screen() { return screen; }
    synchronized State user() { return user; }
    synchronized void network(State value) { network = value; }
    synchronized void screen(State value) { screen = value; }
    synchronized void user(State value) { user = value; }
    enum Action { NONE, PAUSE, RESUME }
    synchronized Action setUserPaused(boolean paused) {
        State next = paused ? State.DISCONNECTED : State.SHOULDBECONNECTED;
        if (user == next) return Action.NONE;
        boolean before = shouldRun();
        user = next;
        if (shouldRun() && !before) return Action.RESUME;
        return paused ? Action.PAUSE : Action.NONE;
    }
    synchronized Action screenOn() {
        boolean before = shouldRun();
        screen = State.SHOULDBECONNECTED;
        if (shouldRun() && !before) return Action.RESUME;
        return Action.NONE;
    }
    // True only for a new loss; repeated broadcasts must not extend the grace period.
    synchronized boolean networkLost(boolean delayed) {
        if (network != State.SHOULDBECONNECTED) return false;
        network = delayed ? State.PENDINGDISCONNECT : State.DISCONNECTED;
        if (!delayed && screen == State.PENDINGDISCONNECT) screen = State.DISCONNECTED;
        return true;
    }
    synchronized void screenOff() {
        screen = network == State.DISCONNECTED || user == State.DISCONNECTED
                ? State.DISCONNECTED : State.PENDINGDISCONNECT;
    }
    synchronized boolean disconnectPendingNetwork() {
        if (network != State.PENDINGDISCONNECT) return false;
        network = State.DISCONNECTED;
        if (screen == State.PENDINGDISCONNECT) screen = State.DISCONNECTED;
        return true;
    }
    synchronized boolean disconnectPendingScreen() {
        if (screen != State.PENDINGDISCONNECT) return false;
        screen = State.DISCONNECTED;
        return true;
    }
    boolean shouldRun() {
        if (closed) return false;
        synchronized (this) {
            return !closed && network == State.SHOULDBECONNECTED && screen == State.SHOULDBECONNECTED
                    && user == State.SHOULDBECONNECTED;
        }
    }
    boolean runIfAllowed(Runnable action) {
        if (closed) return false;
        synchronized (this) {
            if (!shouldRun()) return false;
            action.run();
            return true;
        }
    }
}
