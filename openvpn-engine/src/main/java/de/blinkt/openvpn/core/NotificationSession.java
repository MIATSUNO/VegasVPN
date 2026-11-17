package de.blinkt.openvpn.core;

/** Lock-free read of one immutable notification action identity.
 * The service serializes publish/clear with its process lock. A reader may see
 * an old identity; the disconnect handler must still validate it against the
 * current session under the process lock.
 */
final class NotificationSession {
    static final class Identity {
        final String sessionId;
        final String nonce;

        Identity(String sessionId, String nonce) {
            if (sessionId == null || nonce == null) throw new IllegalArgumentException("identity must be scoped");
            this.sessionId = sessionId;
            this.nonce = nonce;
        }
    }

    private volatile Identity identity;

    void publish(String sessionId, String nonce) { identity = new Identity(sessionId, nonce); }
    void clear() { identity = null; }
    Identity snapshot() { return identity; }
    static boolean matches(Identity current, String sessionId, String nonce) {
        return current != null && sessionId != null && nonce != null &&
                current.sessionId.equals(sessionId) && current.nonce.equals(nonce);
    }
}
