package de.blinkt.openvpn.core;

/** Identity, not profile ID or object equality, identifies the installed producer. */
final class SessionOwner {
    static boolean matches(Object installed, Object caller) {
        return caller != null && installed == caller;
    }
}
