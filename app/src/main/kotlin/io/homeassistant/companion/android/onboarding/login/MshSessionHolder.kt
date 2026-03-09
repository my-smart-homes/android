package io.homeassistant.companion.android.onboarding.login

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the decrypted MSH server credentials during the onboarding session.
 *
 * Scoped as a singleton so the session survives the transition from the onboarding
 * activity to WebViewActivity. Data is set once after a successful Firebase login
 * and read by the connection screen for auto-login injection and by WebViewActivity
 * for WiFi auto-configuration.
 */
@Singleton
internal class MshSessionHolder @Inject constructor() {

    var session: MshSessionData? = null
        private set

    /**
     * Stores the authenticated session data after successful Firebase login.
     */
    fun setSession(data: MshSessionData) {
        session = data
    }

    /**
     * Clears the session data, typically after onboarding completes or on sign-out.
     */
    fun clear() {
        session = null
    }
}

/**
 * Immutable snapshot of the MSH server credentials for a single server.
 *
 * @property externalUrl The public URL of the Home Assistant instance
 * @property internalUrl The local network URL of the Home Assistant instance, if available
 * @property username The Home Assistant login username
 * @property password The decrypted Home Assistant login password
 */
internal data class MshSessionData(
    val externalUrl: String,
    val internalUrl: String?,
    val username: String,
    val password: String,
)
