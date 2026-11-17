package br.com.vegas.independente

import org.junit.Assert.*
import org.junit.Test

class ServerCardTextTest {
    @Test fun inactiveSelectionAndEmptyState() {
        assertEquals("Brasil • 192.0.2.1", ServerCardText.label(false, false, "Brasil • 192.0.2.1"))
        assertEquals("Selecionar servidor", ServerCardText.label(false, false, null))
    }
    @Test fun activeGateNeverDisplaysOldOrMissingSelectionAsCurrent() {
        assertEquals("Servidor da sessão indisponível", ServerCardText.label(true, false, "servidor antigo"))
        assertEquals("Servidor da sessão indisponível", ServerCardText.label(true, false, null))
    }
    @Test fun fixtureNeverDisplaysGateSelection() {
        assertEquals("Teste local — servidor VPN Gate indisponível", ServerCardText.label(false, true, "servidor antigo"))
    }
    @Test fun retentionRequiresInactiveAndSameObjectInCatalog() {
        assertTrue(ServerCardText.mayRetain(false, false, true))
        assertFalse(ServerCardText.mayRetain(false, false, false))
        assertFalse(ServerCardText.mayRetain(true, false, true))
        assertFalse(ServerCardText.mayRetain(false, true, true))
        assertFalse(ServerCardText.mayRetain(true, true, true))
    }
}
