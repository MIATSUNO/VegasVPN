package br.com.vegas.independente

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus

/** Test-only fixed endpoint; never imports a catalog profile. */
object FixtureVpn : VpnStatus.StateListener {
    private const val FIXTURE_LIFECYCLE_VERIFIED = false
    @Volatile var state = "Não conectado"; private set
    @Volatile private var active = false
    fun isActive() = active
    private val observers = mutableSetOf<() -> Unit>()
    fun init() { VpnStatus.addStateListener(this) }
    fun add(observer: () -> Unit) { synchronized(observers) { observers.add(observer) } }
    fun remove(observer: () -> Unit) { synchronized(observers) { observers.remove(observer) } }
    private fun publish(s: String) {
        state = s
        val copy = synchronized(observers) { observers.toList() }
        copy.forEach { it() }
    }
    private fun load(ctx: Context, name: String): String {
        return ctx.openFileInput("fixture_$name").bufferedReader(Charsets.US_ASCII).use { it.readText().also { s -> require(s.length <= 16384) } }
    }
    fun available(ctx: Context): Boolean = try {
        if (!BuildConfig.DEBUG || !FIXTURE_LIFECYCLE_VERIFIED || Build.VERSION.SDK_INT < 26) false else {
            FixturePolicy.validateConfig(load(ctx, "profile.conf"))
            FixturePolicy.validateCredentials(load(ctx, "ca.pem"), load(ctx, "client.pem"), load(ctx, "client.key"))
            true
        }
    } catch (_: Exception) { false }
    fun consent(ctx: Context): Intent? = VpnService.prepare(ctx)
    fun connect(ctx: Context) {
        check(BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 26 && !active && available(ctx))
        check(VpnService.prepare(ctx) == null) { "Consentimento VPN ausente" }
        val p = VpnProfile("Vegas fixture local — TESTE")
        p.mAuthenticationType = VpnProfile.TYPE_CERTIFICATES
        p.mCaFilename = VpnProfile.INLINE_TAG + load(ctx, "ca.pem")
        p.mClientCertFilename = VpnProfile.INLINE_TAG + load(ctx, "client.pem")
        p.mClientKeyFilename = VpnProfile.INLINE_TAG + load(ctx, "client.key")
        p.mConnections[0].mServerName = FixturePolicy.HOST
        p.mConnections[0].mServerPort = FixturePolicy.PORT.toString()
        p.mConnections[0].mUseUdp = true
        p.mCheckRemoteCN = true
        p.mRemoteCN = FixturePolicy.SERVER_NAME
        p.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_RDN
        p.mExpectTLSCert = true
        p.mUseLzo = false
        p.mUseCustomConfig = false
        p.mBlockUnusedAddressFamilies = true
        ProfileManager.setEphemeralProfile(ctx, p)
        active = true
        publish("Iniciando fixture de teste…")
        try { VPNLaunchHelper.startOpenVpn(p, ctx.applicationContext) }
        catch (e: Exception) { active = false; publish("Falha ao iniciar o serviço"); throw e }
    }
    fun disconnect(ctx: Context) {
        if (!active) return
        publish("Desconectando…")
        try { ctx.startService(Intent(ctx, OpenVPNService::class.java).setAction(OpenVPNService.DISCONNECT_VPN)) }
        catch (_: Exception) { active = false; publish("Falha ao solicitar desconexão; confira VPN do Android") }
    }
    override fun updateState(s: String?, message: String?, resId: Int, level: ConnectionStatus?, intent: Intent?) {
        if (!active) return
        when (level) {
            ConnectionStatus.LEVEL_CONNECTED -> publish("Conectado ao servidor de teste (estado do motor)")
            ConnectionStatus.LEVEL_AUTH_FAILED -> publish("Falha de autenticação no teste")
            ConnectionStatus.LEVEL_NOTCONNECTED -> { active = false; publish("Desconectado (estado do motor)") }
            ConnectionStatus.LEVEL_NONETWORK -> publish("Sem rede para o teste")
            ConnectionStatus.LEVEL_VPNPAUSED -> publish("Teste pausado")
            ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> publish("Teste requer entrada não suportada")
            ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET, ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED, ConnectionStatus.LEVEL_START -> publish("Conectando fixture de teste…")
            else -> publish("Estado do motor: ${s ?: "desconhecido"}")
        }
    }
    override fun setConnectedVPN(uuid: String?) { /* This callback reports the selected profile, NOT a connected tunnel. */ }
}
