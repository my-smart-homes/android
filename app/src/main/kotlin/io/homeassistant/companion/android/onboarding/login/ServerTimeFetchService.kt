package io.homeassistant.companion.android.onboarding.login

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

private const val SERVER_TIME_URL = "https://getservertime-jrskleaqea-uc.a.run.app"

/**
 * Fetches the current server time from the MSH time endpoint.
 *
 * Used to validate subscription expiration against a trusted server clock
 * rather than the device's local time.
 */
@Singleton
internal class ServerTimeFetchService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Fetches the current server time as a Unix timestamp in milliseconds.
     *
     * @return epoch milliseconds from the server, or null if the request fails
     */
    suspend fun fetchServerTime(): Long? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(SERVER_TIME_URL).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val timeString = JSONObject(body).getString("time")
                OffsetDateTime.parse(timeString, DateTimeFormatter.ISO_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            } else {
                Timber.e("Server time fetch failed with code: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch server time")
            null
        }
    }
}
