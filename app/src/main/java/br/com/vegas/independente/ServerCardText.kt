package br.com.vegas.independente

/** UI-only selection rules; no profiles are persisted or inferred from an active session. */
internal object ServerCardText {
    fun mayRetain(gateActive: Boolean, fixtureActive: Boolean, sameCatalogEntry: Boolean): Boolean =
        !gateActive && !fixtureActive && sameCatalogEntry

    fun label(gateActive: Boolean, fixtureActive: Boolean, selected: String?): String = when {
        fixtureActive -> "Iniciar localmente — servidor Las Vegas — VPN Gate indisponível"
        gateActive -> "Servidor da sessão indisponível"
        else -> selected ?: "Selecionar servidor"
    }
}
