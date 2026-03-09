package io.homeassistant.companion.android.onboarding.login

/**
 * Handles MSH Firebase authentication, Firestore credential fetching, and subscription validation.
 *
 * Implementations are flavor-specific: the full flavor uses Firebase Auth and Firestore,
 * while the minimal flavor returns an error since Firebase is unavailable.
 */
internal interface LoginRepository {

    /**
     * Authenticates with Firebase and fetches the user's server credentials from Firestore.
     *
     * On success with a single server, returns [LoginResult.Success] with the session data.
     * On success with multiple servers, returns [LoginResult.MultipleServers] so the user can pick.
     * On any failure, returns [LoginResult.Error].
     *
     * @param email the user's Firebase account email
     * @param password the user's Firebase account password
     */
    suspend fun login(email: String, password: String): LoginResult

    /**
     * Resolves credentials for a specific server after the user picks from multiple options.
     *
     * @param serverId Firestore document ID of the chosen server
     * @param encryptedPassword the encrypted password from the serverPasswords subcollection
     */
    suspend fun resolveServer(serverId: String, encryptedPassword: String): LoginResult
}

/**
 * Result of a login or server resolution attempt.
 */
internal sealed interface LoginResult {

    /**
     * Login succeeded and credentials are ready for a single server.
     */
    data class Success(val sessionData: MshSessionData) : LoginResult

    /**
     * The user has multiple servers and must choose one.
     *
     * @property servers list of server options (id + name) plus their encrypted passwords
     */
    data class MultipleServers(val servers: List<PendingServer>) : LoginResult

    /**
     * Login or server resolution failed.
     *
     * @property message user-facing error description
     */
    data class Error(val message: String) : LoginResult
}

/**
 * A server pending user selection, before credentials are fully resolved.
 *
 * @property id Firestore document ID
 * @property name display name from servers collection
 * @property encryptedPassword the encrypted password from the serverPasswords subcollection
 */
internal data class PendingServer(
    val id: String,
    val name: String,
    val encryptedPassword: String,
)
