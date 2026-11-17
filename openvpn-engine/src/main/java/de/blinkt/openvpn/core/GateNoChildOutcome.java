package de.blinkt.openvpn.core;

/** Whether a claimed but unlaunched Gate request can report its own no-child outcome.
 * Unknown ownership or failed cleanup stays blocked; never attribute another producer's exit. */
final class GateNoChildOutcome {
    static boolean mayEmit(boolean cleanupFailed, boolean busy, NotificationSession.Identity installed,
                           String id, String nonce) {
        if (cleanupFailed || id == null || id.isEmpty() || nonce == null || nonce.isEmpty()) return false;
        // A different producer may still be running, or its identity may be unknown.
        // Do not report a terminal state or free the Gate reservation in either case.
        return !busy && installed == null;
    }
    private GateNoChildOutcome() {}
}
