package br.com.vegas.independente

import android.app.Application

class VegasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A worker cannot survive process death. Never preserve a phantom in-progress request.
        CatalogRepository.recoverInterrupted(this)
        // Do not silently resume a fixture after process death; stale config may contain a test key.
        de.blinkt.openvpn.core.ProfileManager.setConntectedVpnProfileDisconnected(this)
        val staleConfig = java.io.File(cacheDir, "android.conf")
        if (staleConfig.exists() && !staleConfig.delete())
            android.util.Log.e("VegasVPN", "Unable to remove stale private VPN configuration")
        // A legacy temporary profile may be user-owned; do not delete it blindly.
        FixtureVpn.init()
        GateVpn.init()
    }
}
