package de.blinkt.openvpn.core;

/** Session-bound management observations; not proof of tunnel or traffic state. */
final class SessionPauseEvents {
    private final String id, nonce;
    private boolean paused, resumeRequested;
    SessionPauseEvents(String id, String nonce) { this.id = id; this.nonce = nonce; }
    synchronized void pause(OpenVPNManagement.pauseReason reason) {
        paused = true;
        resumeRequested = false;
        SessionVpnStatus.emit(id, nonce, reason == OpenVPNManagement.pauseReason.noNetwork
                ? SessionVpnStatus.Event.NONETWORK : SessionVpnStatus.Event.PAUSED);
    }
    // Requesting resume is not evidence the engine accepted it.
    synchronized void resume() {
        if (paused) resumeRequested = true;
    }
    synchronized void progress(SessionVpnStatus.Event event) {
        if (paused) {
            if (!resumeRequested || event != SessionVpnStatus.Event.CONNECTING) return;
            paused = false;
            resumeRequested = false;
        }
        SessionVpnStatus.emit(id, nonce, event);
    }
}
