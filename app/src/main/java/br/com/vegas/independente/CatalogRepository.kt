package br.com.vegas.independente

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Single-process repository. State is about the directory only; it is never a VPN status. */
object CatalogRepository {
    private const val PREFS = "catalog_status_v1"
    private const val KEY = "state"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    // Only retained in this process; never persisted or represented as a fresh result after restart.
    private var rows: List<VpnGateCatalog.Server>? = null
    private var policyEligible: Set<VpnGateCatalog.Server> = emptySet()
    private var completedAt: Long = 0
    fun passedPolicy(row: VpnGateCatalog.Server) = policyEligible.contains(row)

    fun state(context: Context): CatalogState.Status = CatalogState.parse(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    fun results(): List<VpnGateCatalog.Server>? = rows
    fun resultTime(): Long = completedAt
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    // Called on the main thread. Application startup recovers an abandoned request.
    fun recoverInterrupted(context: Context) {
        if (state(context) == CatalogState.Status.LOADING) transition(context, CatalogState.Status.INTERRUPTED)
    }
    private fun transition(context: Context, next: CatalogState.Status): Boolean {
        val app = context.applicationContext
        if (!CatalogState.allows(state(app), next)) return false
        // commit updates the in-memory preferences even if disk persistence fails. Continue the
        // operation and redraw instead of leaving a phantom loading state without a worker.
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.name).commit()
        VegasWidget.updateAll(app)
        listeners.toList().forEach { it() }
        return true
    }
    fun refresh(context: Context): Boolean {
        val app = context.applicationContext
        if (state(app) == CatalogState.Status.LOADING) return false
        rows = null
        policyEligible = emptySet()
        completedAt = 0
        if (!transition(app, CatalogState.Status.LOADING)) return false
        worker.execute {
            try {
                val result = VpnGateCatalog.fetch(BuildConfig.DEBUG)
                // RSA/X.509 checks are expensive; calculate on the catalog worker, never per UI redraw.
                val compatible = result.filter { ExperimentalGatePolicy.validate(BuildConfig.DEBUG, it.encodedProfile, it.address) != null }.toSet()
                val time = System.currentTimeMillis()
                main.post {
                    rows = result
                    policyEligible = compatible
                    completedAt = time
                    transition(app, CatalogState.Status.SUCCEEDED)
                }
            } catch (_: Exception) {
                main.post { transition(app, CatalogState.Status.FAILED) }
            }
        }
        return true
    }
}
