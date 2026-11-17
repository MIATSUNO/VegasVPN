package br.com.vegas.independente;

import org.junit.Test;
import static org.junit.Assert.*;

public class VpnStatusTextTest {
    @Test public void bothFinalMessagesRemainVisibleEvenWhenIdle() {
        String text = VpnStatusText.format("Início VPN cancelado ou indisponível", "Desconectado (estado do motor)");
        assertTrue(text.contains("VPN Gate: Início VPN cancelado ou indisponível"));
        assertTrue(text.contains("Fixture: Desconectado (estado do motor)"));
    }
}
