package de.blinkt.openvpn.core;

import java.util.concurrent.CopyOnWriteArraySet;

/** Separate, non-replaying event channel. The producer supplies the immutable session profile ID and reservation nonce. */
public final class SessionVpnStatus {
    public enum Event { CONNECTED, CONNECTING, NONETWORK, PAUSED, AUTH_FAILED, INPUT_REQUIRED, PROTECT_FAILED, REVOKED, PROCESS_EXITED, START_ABORTED }
    public interface Listener { void onSessionEvent(String sessionId, String nonce, Event event); }
    private static final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    public static void addListener(Listener listener) { listeners.add(listener); }
    public static void removeListener(Listener listener) { listeners.remove(listener); }
    static void emit(String sessionId, String nonce, Event event) {
        if (sessionId == null || sessionId.isEmpty() || nonce == null || nonce.isEmpty() || event == null) return;
        // One bad observer must not suppress delivery to the remaining observers.
        for (Listener listener : listeners) {
            try { listener.onSessionEvent(sessionId, nonce, event); }
            catch (RuntimeException failure) {
                java.util.logging.Logger.getLogger(SessionVpnStatus.class.getName())
                        .warning("Session listener failed: " + failure.getClass().getSimpleName());
            }
        }
    }
    private SessionVpnStatus() {}
}
