package io.homeassistant.companion.android.onboarding.login

/**
 * Represents the UI state of the MSH login screen.
 */
internal sealed interface LoginUiState {

    /** Initial state, waiting for user input. */
    data object Idle : LoginUiState

    /** Firebase authentication and credential fetch in progress. */
    data object Loading : LoginUiState

    /**
     * Multiple servers found for this account — user must pick one.
     *
     * @property servers list of available server options with display name and ID
     */
    data class ServerSelection(val servers: List<ServerOption>) : LoginUiState

    /**
     * Login failed with an error message.
     *
     * @property message user-facing error description
     */
    data class Error(val message: String) : LoginUiState

    /**
     * Login succeeded and credentials are stored. Ready to navigate forward.
     *
     * @property url the external URL of the selected Home Assistant server
     */
    data class Success(val url: String) : LoginUiState
}

/**
 * A server available for selection when the user has multiple servers.
 *
 * @property id Firestore document ID of the server
 * @property name display name (homeName from Firestore, falls back to ID)
 */
internal data class ServerOption(
    val id: String,
    val name: String,
)
