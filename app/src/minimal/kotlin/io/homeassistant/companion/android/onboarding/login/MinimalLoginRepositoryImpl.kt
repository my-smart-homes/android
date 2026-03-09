package io.homeassistant.companion.android.onboarding.login

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal-flavor stub of [LoginRepository] that always returns an error.
 *
 * Firebase Auth and Firestore are not available in the minimal (FOSS) flavor,
 * so login is not supported.
 */
@Singleton
internal class MinimalLoginRepositoryImpl @Inject constructor() : LoginRepository {

    override suspend fun login(email: String, password: String): LoginResult =
        LoginResult.Error("Login requires the full version of the app with Google Play Services")

    override suspend fun resolveServer(serverId: String, encryptedPassword: String): LoginResult =
        LoginResult.Error("Login requires the full version of the app with Google Play Services")
}
