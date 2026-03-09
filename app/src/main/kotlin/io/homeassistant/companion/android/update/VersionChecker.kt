package io.homeassistant.companion.android.update

import io.homeassistant.companion.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

private const val VERSION_URL = "https://my-smart-homes.github.io/app-landing/version.json"

/**
 * Checks for app updates by comparing the current version code against a remote version manifest.
 */
@Singleton
internal class VersionChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Checks whether a newer version of the app is available.
     *
     * @return true if an update is available, false otherwise
     */
    suspend fun isUpdateAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$VERSION_URL?rand=${System.currentTimeMillis()}")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext false
                val latestVersionCode = JSONObject(body).getInt("latest_version_code")
                latestVersionCode > BuildConfig.VERSION_CODE
            } else {
                Timber.e("Version check failed with code: ${response.code}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for app update")
            false
        }
    }
}
