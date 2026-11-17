package br.com.vegas.independente

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private val listener: () -> Unit = { renderCatalog() }
    private val vpnListener: () -> Unit = { runOnUiThread { renderVpn() } }
    private var awaitingConsent = false
    private var awaitingGateConsent = false
    private var selectedServer: VpnGateCatalog.Server? = null
    private var currentPage = 0
    private fun navigate(page: Int) {
        currentPage = page
        findViewById<android.view.View>(R.id.home_page).visibility = if (page == 0) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<android.view.View>(R.id.servers_page).visibility = if (page == 1) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<android.view.View>(R.id.settings_page).visibility = if (page == 2) android.view.View.VISIBLE else android.view.View.GONE
        listOf(R.id.nav_home, R.id.nav_servers, R.id.nav_settings).forEachIndexed { i, id ->
            findViewById<Button>(id).apply {
                setTextColor(android.graphics.Color.parseColor(if (page == i) "#007D80" else "#303538"))
                isSelected = page == i
            }
        }
        findViewById<android.widget.ScrollView>(R.id.page_scroll).scrollTo(0, 0)
    }
    private val gateListener: () -> Unit = { runOnUiThread { renderVpn(); renderCatalog() } }
    private fun showGate(row: VpnGateCatalog.Server) {
        if (!GateVpn.eligible(row)) {
            AlertDialog.Builder(this).setMessage("Perfil bloqueado: opções não reconhecidas, CA divergente ou parâmetros não suportados. Identidade individual não verificada.").setPositiveButton("OK", null).show(); return
        }
        if (!GateVpn.canLaunch()) {
            AlertDialog.Builder(this).setTitle("Teste indisponível").setMessage("A conexão exige Android 8 ou superior.").setPositiveButton("OK", null).show(); return
        }
        val hash = try { GateVpn.prepare(row) } catch (_: Exception) { return }
        AlertDialog.Builder(this).setTitle("Conexão")
            .setMessage("Servidor ${row.address} • operador declarado: ${row.operator} • logs declarados: ${row.logging}\n\nUso validado em Android: seu IP, DNS ou tráfego podem vazar durante falhas e desconexão. VPN Gate e voluntários podem registrar conexões e metadados. A identidade individual do voluntário NÃO é verificada. \n\nSHA-256 dos bytes exatos do perfil: $hash\n\nContinuar para permissão VPN do Android?")
            .setNegativeButton("Cancelar") { _, _ -> GateVpn.cancelConsent() }
            .setOnCancelListener { GateVpn.cancelConsent() }
            .setPositiveButton("Sim ✈︎") { _, _ ->
                try {
                    GateVpn.approveCandidate()
                    val intent = GateVpn.requestAndroidConsent(this)
                    if (intent == null) startGate() else { awaitingGateConsent=true; startActivityForResult(intent, 93) }
                } catch (_: Exception) { GateVpn.cancelConsent(); AlertDialog.Builder(this).setMessage("Não foi possível solicitar a permissão VPN. Selecione o servidor novamente.").setPositiveButton("OK", null).show() }
            }.show()
    }
    private fun startGate() {
        try { GateVpn.start(applicationContext) }
        catch (_: Exception) { GateVpn.cancelConsent(); AlertDialog.Builder(this).setMessage("Não foi possível iniciar: confira a seleção, o perfil e a permissão VPN do Android. A conexão não foi validada.").setPositiveButton("OK", null).show() }
    }
    private fun requestVpnConsent() {
        try {
            val intent = FixtureVpn.consent(this)
            if (intent == null) startFixture() else { awaitingConsent = true; startActivityForResult(intent, 91) }
        } catch (e: Exception) { awaitingConsent = false; renderVpn() }
    }
    @Deprecated("Permission result API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 92) requestVpnConsent()
    }
    private fun renderVpn() {
        findViewById<TextView>(R.id.vpn_status).text = if (FixtureVpn.isActive()) "Teste local: ${FixtureVpn.state}" else GateVpn.state
        val gateActive = GateVpn.isActive()
        val fixtureActive = FixtureVpn.isActive()

        if (gateActive || fixtureActive) selectedServer = null
        val serverText = ServerCardText.label(gateActive, fixtureActive, selectedServer?.let { "${it.country} • ${it.address}" })
        findViewById<TextView>(R.id.selected_server).text = serverText
        findViewById<android.view.View>(R.id.server_card).contentDescription = "Servidor: $serverText"
        findViewById<Button>(R.id.connect_button).apply {
            isEnabled = true
            text = if (GateVpn.isActive() || FixtureVpn.isActive()) "⏻\nDESCONECTAR" else "⏻\nCONECTAR"
            contentDescription = if (GateVpn.isActive() || FixtureVpn.isActive()) "Desconectar sessão VPN" else "Conectar VPN experimental"
        }
    }
    private fun startFixture() {
        try { FixtureVpn.connect(applicationContext) }
        catch (e: Exception) { AlertDialog.Builder(this).setMessage("Não foi possível iniciar ${e.javaClass.simpleName}").setPositiveButton("OK", null).show() }
        renderVpn()
    }
    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 93 && awaitingGateConsent) {
            awaitingGateConsent=false
            if (resultCode == RESULT_OK) startGate() else GateVpn.cancelConsent()
        }
        if (requestCode == 91 && awaitingConsent) {
            awaitingConsent = false
            if (resultCode == RESULT_OK) startFixture() else renderVpn()
        }
    }
    
    private fun mostrarIntroducao() {
 val prefs = getSharedPreferences("vegas_intro", MODE_PRIVATE)
 if (prefs.getBoolean("concluida", false)) return

 val tela = layoutInflater.inflate(R.layout.tela_introducao, null)
 addContentView(
 tela,
 android.view.ViewGroup.LayoutParams(
 android.view.ViewGroup.LayoutParams.MATCH_PARENT,
 android.view.ViewGroup.LayoutParams.MATCH_PARENT
 )
 )

 val texto = tela.findViewById<TextView>(R.id.texto_intro)
 val botao = tela.findViewById<Button>(R.id.botao_intro)
 val mensagem = """
 1. Bem-vindo ao Las Vegas VPN - Por Miatsuno! Para configurar um servidor e estabelecer uma conexão, navegue até "servidores" e realize uma busca.

 2. Escolha um servidor compatível e configure a conexão, permita o acesso à VPN.

 3. Na tela inicial, toque em Conectar e aceite a permissão VPN para conceder esse acesso.

 4. Confira o estado da conexão e toque em Desconectar para encerrá-la.5.
 """.trimIndent()

 texto.contentDescription = mensagem
 var posicao = 0

 val digitacao = object : Runnable {
 override fun run() {
 if (!tela.isAttachedToWindow) return
 botao.visibility = android.view.View.VISIBLE
 if (posicao < mensagem.length) {
 posicao++
 texto.text = mensagem.substring(0, posicao)
 texto.postDelayed(this, 18)
 } else {
 botao.text = "Começar"
 }
 }
 }

 botao.setOnClickListener {
 texto.removeCallbacks(digitacao)
 prefs.edit().putBoolean("concluida", true).apply()
 (tela.parent as? android.view.ViewGroup)?.removeView(tela)
 }

 texto.postDelayed(digitacao, 1200)
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val retained = lastNonConfigurationInstance as? VpnGateCatalog.Server
        selectedServer = retained?.takeIf {
            ServerCardText.mayRetain(GateVpn.isActive(), FixtureVpn.isActive(),
                CatalogRepository.results()?.any { row -> row === it } == true)
        }
        awaitingConsent = savedInstanceState?.getBoolean("awaitingConsent") ?: false
        awaitingGateConsent = savedInstanceState?.getBoolean("awaitingGateConsent") ?: false
        findViewById<Button>(R.id.nav_home).setOnClickListener { navigate(0) }
        findViewById<Button>(R.id.nav_servers).setOnClickListener { navigate(1) }
        findViewById<Button>(R.id.nav_settings).setOnClickListener { navigate(2) }
        findViewById<Button>(R.id.menu_button).setOnClickListener { navigate(2) }
        findViewById<android.view.View>(R.id.server_card).setOnClickListener { navigate(1) }
        findViewById<Button>(R.id.system_vpn).setOnClickListener {
            try { startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) }
            catch (_: android.content.ActivityNotFoundException) {
                AlertDialog.Builder(this).setMessage("Configurações VPN indisponíveis neste aparelho.").setPositiveButton("OK", null).show()
            }
        }
        navigate(savedInstanceState?.getInt("currentPage") ?: 0)
        findViewById<Button>(R.id.connect_button).setOnClickListener {
            if (GateVpn.isActive()) GateVpn.disconnect()
            else if (FixtureVpn.isActive()) FixtureVpn.disconnect(applicationContext)
            else {
                val selected = selectedServer
                if (selected == null || !GateVpn.eligible(selected)) {
                    selectedServer = null
                    renderVpn()
                    navigate(1)
                } else showGate(selected)
            }
        }
        findViewById<Button>(R.id.refresh_catalog).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.catalog_consent_title)
                .setMessage(R.string.catalog_consent_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.catalog_consent_yes) { _, _ -> CatalogRepository.refresh(applicationContext) }.show()
         }

 mostrarIntroducao()
 }

 override fun onStart() {
        super.onStart()
        CatalogRepository.addListener(listener)
        FixtureVpn.add(vpnListener)
        GateVpn.add(gateListener)
        renderVpn()
        renderCatalog()
    }
    override fun onStop() {
        CatalogRepository.removeListener(listener)
        FixtureVpn.remove(vpnListener)
        GateVpn.remove(gateListener)
        super.onStop()
    }
    @Suppress("DEPRECATION")
    override fun onRetainNonConfigurationInstance(): Any? {
        val selected = selectedServer ?: return null
        return selected.takeIf {
            ServerCardText.mayRetain(GateVpn.isActive(), FixtureVpn.isActive(),
                CatalogRepository.results()?.any { row -> row === it } == true)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("currentPage", currentPage)
        outState.putBoolean("awaitingConsent", awaitingConsent)
        outState.putBoolean("awaitingGateConsent", awaitingGateConsent)
        super.onSaveInstanceState(outState)
    }
    private fun renderCatalog() {
        val state = CatalogRepository.state(this)
        if (selectedServer != null && CatalogRepository.results()?.none { it === selectedServer } != false && !GateVpn.isActive()) {
            selectedServer = null
            renderVpn()
        }
        val status = findViewById<TextView>(R.id.catalog_status)
        val container = findViewById<LinearLayout>(R.id.server_list)
        findViewById<Button>(R.id.refresh_catalog).isEnabled = state != CatalogState.Status.LOADING
        container.removeAllViews()
        when (state) {
            CatalogState.Status.IDLE -> status.setText(R.string.catalog_initial)
            CatalogState.Status.LOADING -> status.setText(R.string.catalog_loading)
            CatalogState.Status.FAILED -> status.setText(R.string.catalog_error)
            CatalogState.Status.INTERRUPTED -> status.setText(R.string.catalog_interrupted)
            CatalogState.Status.SUCCEEDED -> {
                val rows = CatalogRepository.results()
                if (rows == null) status.setText(R.string.catalog_previous_success)
                else {
                    val time = DateFormat.getDateTimeInstance().format(Date(CatalogRepository.resultTime()))
                    status.text = getString(R.string.catalog_loaded, rows.size, time)
                    if (rows.isEmpty()) addServerLine(container, getString(R.string.catalog_empty))
                    rows.forEach { server ->
                        val note = when (server.inspection) {
                            ProfileInspection.Summary.UNREADABLE -> getString(R.string.profile_unreadable)
                            ProfileInspection.Summary.NO_EXPLICIT_SERVER_CHECK -> getString(R.string.profile_no_check)
                            ProfileInspection.Summary.HAS_SERVER_CHECK -> getString(R.string.profile_has_check)
                        }
                        val preflightFinding = server.screeningReasons.any {
                            it != VpnGateProfileValidator.Reason.ENGINE_UNAVAILABLE &&
                                it != VpnGateProfileValidator.Reason.SERVER_ROLE_NOT_SPECIFIED &&
                                it != VpnGateProfileValidator.Reason.CA_NOT_INDEPENDENTLY_TRUSTED &&
                                it != VpnGateProfileValidator.Reason.RELAY_IDENTITY_UNVERIFIED
                        }
                        val screeningNote = getString(if (preflightFinding) R.string.profile_screening_incompatible else R.string.profile_screening_no_finding)
                        val row = getString(R.string.catalog_row,
                            server.country, server.code, server.address, server.operator, server.logging, server.ping, server.speed) + "\n" + note + "\n" + screeningNote
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            background = getDrawable(R.drawable.card)
                            val inset = (16 * resources.displayMetrics.density).toInt()
                            setPadding(inset, inset / 2, inset, inset)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = inset }
                        }
                        container.addView(card)
                        addServerLine(card, row)
                        val select = Button(this)
                        val policyCompatible = CatalogRepository.passedPolicy(server)
                        val eligible = GateVpn.canLaunch() && !GateVpn.isActive() && !FixtureVpn.isActive() && policyCompatible
                        select.text = "Ver observações da triagem"
                        select.setOnClickListener {
                            val reasons = server.screeningReasons.filter { it != VpnGateProfileValidator.Reason.ENGINE_UNAVAILABLE }.map { reasonDescription(it) }.sorted().joinToString("\n• ", prefix = "• ")
                            AlertDialog.Builder(this).setTitle("Servidor ${server.address}")
                                .setMessage("Observações da triagem inicial:\n\n$reasons\n\nA política experimental também verifica a CA, o nome e as cifras. A consulta não estabelece a identidade individual do relay. Esta tela não inicia uma conexão.")
                                .setPositiveButton("Entendi", null).show()
                        }
                        select.isAllCaps = false
                        select.setTextColor(getColor(R.color.petrol))
                        select.background = getDrawable(R.drawable.button_secondary)
                        select.minHeight = (48 * resources.displayMetrics.density).toInt()
                        card.addView(select, LinearLayout.LayoutParams(-1, -2))
                        if (BuildConfig.DEBUG) {
                            val gate = Button(this)
                            gate.text = when {
                                GateVpn.isActive() || FixtureVpn.isActive() -> "Encerre a sessão para selecionar"
                                !GateVpn.canLaunch() -> "Teste indisponível neste aparelho"
                                !policyCompatible -> "Perfil incompatível para teste"
                                else -> "Selecionar servidor"
                            }
                            gate.isEnabled = eligible
                            gate.setOnClickListener { selectedServer = server; renderVpn(); navigate(0) }
                            gate.isAllCaps = false
                            gate.background = getDrawable(R.drawable.button_primary)
                            gate.setTextColor(getColorStateList(R.color.button_text))
                            gate.minHeight = (48 * resources.displayMetrics.density).toInt()
                            card.addView(gate, LinearLayout.LayoutParams(-1, -2).apply {
                                topMargin = (8 * resources.displayMetrics.density).toInt()
                            })
                        }
                    }
                }
            }
            else -> status.setText(R.string.catalog_initial)
        }
    }
    private fun reasonDescription(reason: VpnGateProfileValidator.Reason): String = when (reason) {
        VpnGateProfileValidator.Reason.INVALID_ENCODING -> "Perfil ausente ou codificação inválida."
        VpnGateProfileValidator.Reason.TOO_LARGE -> "Perfil excede os limites."
        VpnGateProfileValidator.Reason.INVALID_TEXT -> "Texto contém caracteres ou codificação inválidos."
        VpnGateProfileValidator.Reason.BAD_SYNTAX -> "Sintaxe ou bloco inválido."
        VpnGateProfileValidator.Reason.UNSUPPORTED_DIRECTIVE -> "Diretiva não permitida pela triagem restritiva."
        VpnGateProfileValidator.Reason.DUPLICATE_DIRECTIVE -> "Diretiva ou bloco duplicado."
        VpnGateProfileValidator.Reason.MISSING_FIELD -> "Campos essenciais ou credenciais inline ausentes."
        VpnGateProfileValidator.Reason.ENDPOINT_MISMATCH -> "Endereço do perfil difere do servidor selecionado."
        VpnGateProfileValidator.Reason.INVALID_CA -> "Certificado CA inválido ou não é CA."
        VpnGateProfileValidator.Reason.INVALID_CLIENT_CERT -> "Certificado de cliente inválido."
        VpnGateProfileValidator.Reason.INVALID_CLIENT_KEY -> "Chave RSA de cliente inválida ou incompatível."
        VpnGateProfileValidator.Reason.CLIENT_KEY_MISMATCH -> "Chave e certificado de cliente não correspondem."
        VpnGateProfileValidator.Reason.CA_NOT_INDEPENDENTLY_TRUSTED -> "CA do perfil não possui âncora de confiança independente."
        VpnGateProfileValidator.Reason.RELAY_IDENTITY_UNVERIFIED -> "Identidade específica do relay não foi estabelecida independentemente."
        VpnGateProfileValidator.Reason.SERVER_ROLE_NOT_SPECIFIED -> "Perfil não exige explicitamente certificado com função de servidor."
        VpnGateProfileValidator.Reason.ENGINE_UNAVAILABLE -> "Triagem histórica não autoriza conexões por si só."
    }
    private fun addServerLine(container: LinearLayout, text: String) {
        val item = TextView(this)
        item.text = text
        item.textSize = 15f
        item.setTextColor(getColor(R.color.petrol_dark))
        val padding = (12 * resources.displayMetrics.density).toInt()
        item.setPadding(0, padding, 0, padding)
        container.addView(item)
    }
}
