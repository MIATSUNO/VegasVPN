package br.com.vegas.independente;

/** Fail-closed reducer keyed by the immutable producer UUID and reservation nonce. */
final class GateSession {
    enum Phase { IDLE, STARTING, CONNECTED, STOPPING }
    enum Event { CONNECTED, CONNECTING, NONETWORK, PAUSED, AUTH_FAILED, INPUT_REQUIRED, PROTECT_FAILED, REVOKED, PROCESS_EXITED, START_ABORTED }
    enum Effect { IGNORE, UPDATE, REQUEST_STOP, FINISHED }
    private String id, nonce;
    private boolean terminalPending;
    private Phase phase = Phase.IDLE;

    synchronized boolean matches(String sessionId, String sessionNonce) {
        return id != null && id.equals(sessionId) && nonce.equals(sessionNonce);
    }
    synchronized boolean start(String sessionId, String sessionNonce) {
        if (phase != Phase.IDLE || sessionId == null || sessionId.isEmpty() ||
                sessionNonce == null || sessionNonce.isEmpty()) return false;
        id = sessionId; nonce = sessionNonce; terminalPending = false; phase = Phase.STARTING; return true;
    }
    synchronized boolean stop(String sessionId, String sessionNonce) {
        if (!matches(sessionId, sessionNonce) || phase == Phase.STOPPING) return false;
        terminalPending = true; phase = Phase.STOPPING; return true;
    }
    // Called on the producer thread before the callback is queued on the main thread.
    synchronized void noteTerminal(String sessionId, String sessionNonce, Event event) {
        if (matches(sessionId, sessionNonce) && (event == Event.REVOKED || event == Event.AUTH_FAILED ||
                event == Event.INPUT_REQUIRED || event == Event.PROTECT_FAILED || event == Event.PROCESS_EXITED || event == Event.START_ABORTED)) terminalPending = true;
    }
    synchronized Effect event(String sessionId, String sessionNonce, Event event) {
        if (!matches(sessionId, sessionNonce) || event == null) return Effect.IGNORE;
        if (event == Event.PROCESS_EXITED || event == Event.START_ABORTED) {
            id = null; nonce = null; terminalPending = false; phase = Phase.IDLE; return Effect.FINISHED;
        }
        if (phase == Phase.STOPPING || (terminalPending && event == Event.CONNECTED)) return Effect.IGNORE;
        switch (event) {
            case CONNECTED: phase = Phase.CONNECTED; return Effect.UPDATE;
            case AUTH_FAILED: case INPUT_REQUIRED: case PROTECT_FAILED: case REVOKED: terminalPending = true; phase = Phase.STOPPING; return Effect.REQUEST_STOP;
            case CONNECTING: case NONETWORK: case PAUSED: phase = Phase.STARTING; return Effect.UPDATE;
            default: return Effect.IGNORE;
        }
    }
    synchronized String id() { return id; }
    synchronized String nonce() { return nonce; }
    synchronized Phase phase() { return phase; }
}
