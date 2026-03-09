package io.homeassistant.companion.android.onboarding.login

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.network.WifiHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Automatically adds the current WiFi SSID to server internal network lists
 * and updates the internal URL from the MSH session.
 *
 * This ensures that after onboarding, the app automatically recognizes the
 * user's home WiFi and uses the internal URL for direct local communication.
 */
@Singleton
internal class MshAutoWifiManager @Inject constructor(
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper,
    private val sessionHolder: MshSessionHolder,
) {

    /**
     * Adds the current WiFi SSID and updates the internal URL for all registered servers.
     *
     * Skips silently if WiFi is not connected or if no internal URL is available in the session.
     */
    suspend fun autoAddCurrentWifiToAllServers() = withContext(Dispatchers.IO) {
        val internalUrl = sessionHolder.session?.internalUrl
        if (internalUrl.isNullOrBlank()) {
            Timber.d("No internal URL in session, skipping WiFi auto-add")
            return@withContext
        }

        if (!wifiHelper.isUsingWifi()) {
            Timber.d("Not connected to WiFi, skipping auto-add")
            return@withContext
        }

        val currentSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\"")
        if (currentSsid.isNullOrBlank()) {
            Timber.d("No valid SSID found")
            return@withContext
        }

        try {
            for (server in serverManager.servers()) {
                addSsidToServer(serverId = server.id, ssid = currentSsid)
                updateInternalUrl(serverId = server.id, internalUrl = internalUrl)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during WiFi auto-add for all servers")
        }
    }

    private suspend fun addSsidToServer(serverId: Int, ssid: String) {
        val server = serverManager.getServer(serverId) ?: return
        if (ssid in server.connection.internalSsids) return

        serverManager.updateServer(
            server.copy(
                connection = server.connection.copy(
                    internalSsids = (server.connection.internalSsids + ssid).sorted(),
                ),
            ),
        )
        Timber.d("Added SSID $ssid to server $serverId")
    }

    private suspend fun updateInternalUrl(serverId: Int, internalUrl: String) {
        val server = serverManager.getServer(serverId) ?: return
        serverManager.updateServer(
            server.copy(
                connection = server.connection.copy(internalUrl = internalUrl),
            ),
        )
        Timber.d("Updated internal URL for server $serverId")
    }
}
