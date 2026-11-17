package de.blinkt.openvpn.core;

/** Process-local reservation for the experimental Gate launch. Unknown/replayed starts fail closed.
 * This is coordination, not an authorization boundary. Loss of the process loses the reservation.
 */
public final class GateStartRegistry {
    public static final GateStartRegistry INSTANCE = new GateStartRegistry();
    public enum Claim { ACCEPTED, CANCELLED, REJECTED }
    private String id, nonce;
    private boolean claimed, cancelled;
    private Runnable onCancel;

    public synchronized String reserve(String id) {
        if (this.id != null || id == null || id.isEmpty()) return null;
        String nonce = java.util.UUID.randomUUID().toString();
        this.id = id; this.nonce = nonce; claimed = false; cancelled = false; onCancel = null;
        return nonce;
    }
    private boolean matches(String id, String nonce) {
        return this.id != null && this.id.equals(id) && this.nonce.equals(nonce);
    }
    public synchronized Claim claim(String id, String nonce) {
        if (!matches(id, nonce) || claimed) return Claim.REJECTED;
        claimed = true;
        return cancelled ? Claim.CANCELLED : Claim.ACCEPTED;
    }
    /** Install while holding the service process lock, before launching any child. */
    public synchronized boolean attach(String id, String nonce, Runnable onCancel) {
        if (!matches(id, nonce) || !claimed || cancelled || this.onCancel != null || onCancel == null) return false;
        this.onCancel = onCancel;
        return true;
    }
    public boolean cancel(String id, String nonce) {
        Runnable callback;
        synchronized (this) {
            if (!matches(id, nonce)) return false;
            cancelled = true;
            callback = onCancel;
        }
        // Never invoke service code under the registry lock. Repeated stops retry.
        if (callback != null) callback.run();
        return true;
    }
    public synchronized boolean cancelled(String id, String nonce) {
        return !matches(id, nonce) || cancelled;
    }
    /** Only the owner releases its reservation, after confirmed exit or no dispatch. */
    public synchronized boolean release(String id, String nonce) {
        if (!matches(id, nonce)) return false;
        this.id = null; this.nonce = null; claimed = false; cancelled = false; onCancel = null;
        return true;
    }
}
