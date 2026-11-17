package br.com.vegas.independente;

import java.util.EnumSet;

/** Offline design model only. Does not start Tor, VpnService, TUN or filter packets. */
final class TorSafetyModel {
    enum Evidence {
        CONSENT, TUN_IPV4_IPV6, ENGINE_SOCKET_PROTECTION,
        BOOTSTRAP, AUTHENTICATED_SOCKS_OWNER, BRIDGE,
        DNS_REMOTE_NO_FALLBACK, UNSUPPORTED_TRAFFIC_BLOCKED,
        EXIT_TLS_VERIFIED, LIFECYCLE_MONITORING
    }
    enum Phase { STOPPED, STARTING, CANDIDATE, FAILED }

    private long generation = 0;
    private Phase phase = Phase.STOPPED;
    private final EnumSet<Evidence> evidence = EnumSet.noneOf(Evidence.class);

    synchronized long start() {
        generation = Math.addExact(generation, 1);
        evidence.clear();
        phase = Phase.STARTING;
        return generation;
    }

    synchronized void report(long run, Evidence fact) {
        if (run != generation || phase != Phase.STARTING || fact == null) return;
        evidence.add(fact);
        if (evidence.size() == Evidence.values().length) phase = Phase.CANDIDATE;
    }

    synchronized void fail(long run) {
        if (run != generation || phase == Phase.STOPPED || phase == Phase.FAILED) return;
        evidence.clear();
        phase = Phase.FAILED;
    }

    synchronized void stop() {
        generation = Math.addExact(generation, 1);
        evidence.clear();
        phase = Phase.STOPPED;
    }

    synchronized Phase phase() { return phase; }
}
