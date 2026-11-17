package br.com.vegas.independente

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.SessionVpnStatus
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.GateStartRegistry

/** Private debug experiment; lifecycle and network behavior remain unvalidated on Android. */
object GateVpn : SessionVpnStatus.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val lifecycle = GateSession()
    private val observers = mutableSetOf<() -> Unit>()
    private var pending: VpnGateCatalog.Server? = null
    private var candidateHash: String? = null
    private var approvedHash: String? = null
    private var app: Context? = null
    private var sessionNonce: String? = null
    private var sessionProfile: VpnProfile? = null
    private var timer: Runnable? = null
    var state = "Não conectada — teste experimental não validado"; private set
    private fun requireMain() { check(Looper.myLooper() == Looper.getMainLooper()) }
    fun init() { requireMain(); SessionVpnStatus.addListener(this) }
    fun add(listener: () -> Unit) { requireMain(); observers.add(listener) }
    fun remove(listener: () -> Unit) { requireMain(); observers.remove(listener) }
    private fun publish(s: String) { state=s; observers.toList().forEach { it() } }
    fun isActive() = lifecycle.phase() != GateSession.Phase.IDLE
    fun canLaunch() = BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 26
    fun eligible(row: VpnGateCatalog.Server): Boolean = BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 26 &&
        !FixtureVpn.isActive() && !isActive() && CatalogRepository.results()?.any { it === row } == true &&
        ExperimentalGatePolicy.validate(BuildConfig.DEBUG, row.encodedProfile, row.address) != null
    fun prepare(row: VpnGateCatalog.Server): String {
        requireMain(); check(eligible(row))
        val hash = ExperimentalGatePolicy.hash(row.encodedProfile)
        pending=row; candidateHash=hash; approvedHash=null
        return hash
    }
    fun approveCandidate() {
        requireMain()
        val row = pending ?: error("Seleção perdida")
        val hash = candidateHash ?: error("Perfil não preparado")
        check(eligible(row) && ExperimentalGatePolicy.consentMatches(hash, row.encodedProfile))
        approvedHash=hash
    }
    fun cancelConsent() { requireMain(); pending=null; candidateHash=null; approvedHash=null }
    fun requestAndroidConsent(ctx: Context): Intent? {
        requireMain(); check(pending != null && approvedHash != null)
        return VpnService.prepare(ctx)
    }
    fun start(ctx: Context) {
        requireMain()
        val row = pending ?: error("Seleção perdida")
        val hash = approvedHash ?: error("Consentimento ausente")
        check(canLaunch()) { "VPN Gate experimental disponível apenas em debug no Android 8 ou superior" }
        check(eligible(row) && ExperimentalGatePolicy.consentMatches(hash, row.encodedProfile))
        check(VpnService.prepare(ctx) == null)
        val v = ExperimentalGatePolicy.validate(BuildConfig.DEBUG, row.encodedProfile, row.address) ?: error("Perfil alterado")
        val p = VpnProfile("Vegas VPN Gate experimental")
        p.mAuthenticationType=VpnProfile.TYPE_CERTIFICATES
        p.mCaFilename=VpnProfile.INLINE_TAG+v.caPem
        p.mClientCertFilename=VpnProfile.INLINE_TAG+v.certPem
        p.mClientKeyFilename=VpnProfile.INLINE_TAG+v.keyPem
        p.mConnections[0].mServerName=v.ip; p.mConnections[0].mServerPort=v.port
        p.mConnections[0].mUseUdp=v.protocol == "udp"
        p.mCheckRemoteCN=true; p.mRemoteCN=ExperimentalGatePolicy.NAME
        p.mX509AuthType=VpnProfile.X509_VERIFY_TLSREMOTE_RDN; p.mExpectTLSCert=true
        p.mUseLzo=false; p.mUseCustomConfig=false; p.mConnections[0].mUseCustomConfig=false
        p.mCipher="AES-128-CBC"; p.mVegasRestrictedDataCipher=true; p.mAuth="SHA1"
        p.mBlockUnusedAddressFamilies=true
        val id = p.uuidString
        val nonce = GateStartRegistry.INSTANCE.reserve(id) ?: error("Reserva VPN indisponível")
        if (!lifecycle.start(id, nonce)) {
            GateStartRegistry.INSTANCE.release(id, nonce) // no dispatch attempted
            error("Sessão VPN indisponível")
        }
        sessionNonce = nonce
        sessionProfile = p
        app=ctx.applicationContext
        cancelConsent()
        var dispatchMayHaveOccurred = false
        try {
            ProfileManager.setEphemeralProfile(ctx, p)
            publish("Iniciando conexão experimental…")
            timer=Runnable { if (lifecycle.matches(id, nonce) && lifecycle.phase() != GateSession.Phase.CONNECTED) disconnect("Tempo de conexão esgotado; solicitando desconexão") }
            main.postDelayed(timer!!, 45_000)
            dispatchMayHaveOccurred = true
            VPNLaunchHelper.startOpenVpn(p, ctx.applicationContext, nonce)
        } catch (e: Exception) {
            if (dispatchMayHaveOccurred) {
                // Dispatch can have happened before an exception: never infer process exit.
                disconnect("Falha ao iniciar; desconexão não confirmada")
            } else {
                // No service dispatch was attempted: release only this profile.
                ProfileManager.clearEphemeralProfile(p)
                GateStartRegistry.INSTANCE.release(id, nonce)
                sessionProfile=null
                timer?.let(main::removeCallbacks); timer=null; app=null; sessionNonce=null
                lifecycle.event(id, nonce, GateSession.Event.START_ABORTED)
                publish("Falha antes de iniciar o serviço VPN")
            }
        }
    }
    private fun requestStop(id: String) {
        timer?.let(main::removeCallbacks); timer=null
        val ctx = app ?: return
        if (lifecycle.id() != id) return
        val nonce = sessionNonce ?: return
        try {
            if (!GateStartRegistry.INSTANCE.cancel(id, nonce)) {
                publish("Sessão não reconhecida; desconexão não confirmada")
                return
            }
        } catch (_: RuntimeException) {
            // Cancellation is recorded before callback invocation. Still try the intent.
            publish("Falha parcial ao solicitar desconexão; não confirmada")
        }
        try { ctx.startService(Intent(ctx, OpenVPNService::class.java).setAction(OpenVPNService.DISCONNECT_VPN).putExtra(OpenVPNService.EXTRA_VEGAS_SESSION, id).putExtra(OpenVPNService.EXTRA_NOTIFICATION_NONCE, nonce)) }
        catch (_: Exception) { publish("Falha ao solicitar desconexão; confira VPN do Android") }
    }
    fun disconnect(message: String = "Solicitando desconexão…") {
        requireMain(); cancelConsent()
        val id = lifecycle.id() ?: return
        val nonce = lifecycle.nonce() ?: return
        if (!lifecycle.stop(id, nonce) && lifecycle.phase() != GateSession.Phase.STOPPING) return
        publish(message); requestStop(id)
    }
    override fun onSessionEvent(sessionId: String, nonce: String, event: SessionVpnStatus.Event) {
        val e = GateSession.Event.valueOf(event.name)
        lifecycle.noteTerminal(sessionId, nonce, e)
        main.post {
            when (lifecycle.event(sessionId, nonce, e)) {
                GateSession.Effect.IGNORE -> {}
                GateSession.Effect.FINISHED -> { timer?.let(main::removeCallbacks); timer=null; app=null; sessionProfile?.let(ProfileManager::clearEphemeralProfile); sessionProfile=null; GateStartRegistry.INSTANCE.release(sessionId, nonce); sessionNonce=null; publish(if (e == GateSession.Event.START_ABORTED) "Início VPN cancelado ou indisponível" else "Processo VPN encerrado") }
                GateSession.Effect.REQUEST_STOP -> { publish("Falha ou permissão revogada; solicitando desconexão"); requestStop(sessionId) }
                GateSession.Effect.UPDATE -> when (e) {
                    GateSession.Event.CONNECTED -> { timer?.let(main::removeCallbacks); timer=null; publish("Conectado (estado do motor)") }
                    GateSession.Event.NONETWORK -> publish("Sem rede")
                    GateSession.Event.PAUSED -> publish("Pausa solicitada (não confirmada pelo motor)")
                    else -> publish("Conectando…")
                }
            }
        }
    }
}
