package br.com.vegas.independente;

/** Pure transition policy for catalog operations, NOT VPN connection state. */
public final class CatalogState {
    public enum Status { IDLE, LOADING, SUCCEEDED, FAILED, INTERRUPTED }
    private CatalogState() {}
    public static Status parse(String raw) {
        try { return Status.valueOf(raw); } catch (Exception ignored) { return Status.IDLE; }
    }
    public static boolean allows(Status from, Status to) {
        if (from == null || to == null) return false;
        if (to == Status.LOADING) return from != Status.LOADING;
        if (from == Status.LOADING) return to == Status.SUCCEEDED || to == Status.FAILED || to == Status.INTERRUPTED;
        return false;
    }
}
