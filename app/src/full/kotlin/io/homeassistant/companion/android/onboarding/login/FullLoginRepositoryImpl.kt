package io.homeassistant.companion.android.onboarding.login

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Full-flavor implementation of [LoginRepository] using Firebase Auth and Firestore.
 *
 * Authenticates the user, fetches encrypted server credentials from Firestore,
 * validates the subscription via server time, and decrypts the password.
 */
@Singleton
internal class FullLoginRepositoryImpl @Inject constructor(
    private val serverTimeFetchService: ServerTimeFetchService,
) : LoginRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    override suspend fun login(email: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val userId = auth.currentUser?.uid
                    ?: return@withContext LoginResult.Error("Authentication failed: no user ID")

                Timber.d("Firebase sign-in successful for userId=$userId")
                fetchCredentials(userId)
            } catch (e: Exception) {
                Timber.e(e, "Firebase authentication failed")
                auth.signOut()
                LoginResult.Error("Authentication failed: ${e.message}")
            }
        }

    override suspend fun resolveServer(
        serverId: String,
        encryptedPassword: String,
    ): LoginResult = withContext(Dispatchers.IO) {
        try {
            val serverDoc = firestore.collection("servers").document(serverId).get().await()
            if (!serverDoc.exists()) {
                return@withContext LoginResult.Error("Server not found: $serverId")
            }

            val externalUrl = normalizeUrl(
                url = serverDoc.getString("externalUrl"),
                defaultScheme = "https",
            )
            val internalUrl = normalizeUrl(
                url = serverDoc.getString("internalUrl"),
                defaultScheme = "http",
            )
            val username = auth.currentUser?.email ?: ""

            val decryptedPassword = CryptoUtil.decrypt(encryptedPassword)
                ?: return@withContext LoginResult.Error("Failed to decrypt server credentials")

            if (externalUrl == null) {
                return@withContext LoginResult.Error("Server has no external URL configured")
            }

            LoginResult.Success(
                MshSessionData(
                    externalUrl = externalUrl,
                    internalUrl = internalUrl,
                    username = username,
                    password = decryptedPassword,
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve server $serverId")
            LoginResult.Error("Failed to load server details: ${e.message}")
        }
    }

    private suspend fun fetchCredentials(userId: String): LoginResult {
        val userDoc = firestore.collection("users").document(userId).get().await()
        if (!userDoc.exists()) {
            auth.signOut()
            return LoginResult.Error("No account data found")
        }

        val expirationTimestamp = userDoc.getTimestamp("subscription.expiresAt")
            ?.toDate()?.time

        val serverTime = serverTimeFetchService.fetchServerTime()
            ?: return LoginResult.Error("Failed to verify subscription: could not reach time server")

        if (expirationTimestamp == null || expirationTimestamp < serverTime) {
            auth.signOut()
            return LoginResult.Error("Your subscription has expired")
        }

        val serverPasswordsDocs = userDoc.reference
            .collection("serverPasswords")
            .get()
            .await()
            .documents

        if (serverPasswordsDocs.isEmpty()) {
            return LoginResult.Error("No servers found for this account")
        }

        if (serverPasswordsDocs.size > 1) {
            return resolveMultipleServers(serverPasswordsDocs)
        }

        val singleDoc = serverPasswordsDocs.first()
        val encryptedPassword = singleDoc.getString("encryptedPass")
            ?: return LoginResult.Error("No password found for server")

        return resolveServer(
            serverId = singleDoc.id,
            encryptedPassword = encryptedPassword,
        )
    }

    private suspend fun resolveMultipleServers(
        serverPasswordsDocs: List<com.google.firebase.firestore.DocumentSnapshot>,
    ): LoginResult {
        val pendingServers = serverPasswordsDocs.mapNotNull { doc ->
            val encryptedPass = doc.getString("encryptedPass") ?: return@mapNotNull null
            val serverId = doc.id

            val name = try {
                val serverDoc = firestore.collection("servers")
                    .document(serverId)
                    .get()
                    .await()
                serverDoc.getString("homeName") ?: serverId
            } catch (e: Exception) {
                Timber.w(e, "Could not fetch server name for $serverId")
                serverId
            }

            PendingServer(
                id = serverId,
                name = name,
                encryptedPassword = encryptedPass,
            )
        }

        if (pendingServers.isEmpty()) {
            return LoginResult.Error("No valid server credentials found")
        }

        return LoginResult.MultipleServers(pendingServers)
    }

    private fun normalizeUrl(url: String?, defaultScheme: String): String? {
        if (url.isNullOrBlank()) return null
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "$defaultScheme://$url"
        }
    }
}
