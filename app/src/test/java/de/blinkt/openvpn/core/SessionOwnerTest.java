package de.blinkt.openvpn.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionOwnerTest {
    @Test public void onlyInstalledInstanceCanRequestStop() {
        Object old = new String("same"), current = new String("same");
        assertFalse(SessionOwner.matches(current, old));
        assertTrue(SessionOwner.matches(current, current));
        assertFalse(SessionOwner.matches(null, null));
    }
}
