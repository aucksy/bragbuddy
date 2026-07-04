package com.bragbuddy.app.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the device currently has a **validated** internet connection (Phase 7 · offline
 * queue / calm error states). Registered once for the app's lifetime — a singleton is never
 * unregistered, which is fine for a process-wide monitor.
 *
 * "Online" means the default network reports both INTERNET and VALIDATED capabilities (a captive
 * portal or dead Wi-Fi counts as offline). Consumers: [com.bragbuddy.app.data.entry.OfflineRecovery]
 * (drain the voice-note queue + retry FAILED entries on reconnect) and the calm offline copy in the
 * capture sheet / Inbox / Summary. Everything stays best-effort — a wrong reading only changes copy
 * or delays a retry; the never-lose-an-entry spine doesn't depend on it.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _isOnline = MutableStateFlow(false)

    /** True while the default network is validated. Starts from a synchronous best-effort read. */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** True once the network callback registered successfully. When false, [isOnline] is only the
     *  one-shot launch seed and may be stuck — treat it as advisory (change copy, never gate an
     *  action on it; let the real network call fail instead). */
    var callbackRegistered: Boolean = false
        private set

    init {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        // Seed from the current state so an app started online doesn't briefly read as offline.
        _isOnline.value = runCatching {
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)

        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _isOnline.value =
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }

                override fun onUnavailable() {
                    _isOnline.value = false
                }
            }) ?: error("no ConnectivityManager")
            callbackRegistered = true
        }
        // If registration fails (exotic OEM restriction), the seed value stands and every consumer
        // degrades gracefully — recovery still runs on each launch.
    }
}
