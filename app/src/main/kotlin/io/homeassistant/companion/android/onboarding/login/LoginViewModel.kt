package io.homeassistant.companion.android.onboarding.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.update.VersionChecker
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the MSH Firebase login screen.
 *
 * Handles user authentication via [LoginRepository], multi-server selection,
 * and stores the resulting session data in [MshSessionHolder] for downstream use
 * by the connection and WebView screens.
 */
@HiltViewModel
internal class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val sessionHolder: MshSessionHolder,
    private val versionChecker: VersionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var pendingServers: List<PendingServer> = emptyList()

    init {
        viewModelScope.launch {
            if (versionChecker.isUpdateAvailable()) {
                _showUpdateDialog.update { true }
            }
        }
    }

    /**
     * Dismisses the update dialog.
     */
    fun onDismissUpdateDialog() {
        _showUpdateDialog.update { false }
    }

    /**
     * Initiates Firebase authentication and credential fetch.
     *
     * @param email the user's Firebase account email
     * @param password the user's Firebase account password
     */
    fun onLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { LoginUiState.Error("Email and password cannot be empty") }
            return
        }

        _uiState.update { LoginUiState.Loading }

        viewModelScope.launch {
            val result = loginRepository.login(email = email, password = password)
            handleLoginResult(result)
        }
    }

    /**
     * Selects a server from the multi-server list and resolves its credentials.
     *
     * @param index the index of the chosen server in the pending servers list
     */
    fun onServerSelected(index: Int) {
        val server = pendingServers.getOrNull(index) ?: return
        _uiState.update { LoginUiState.Loading }

        viewModelScope.launch {
            val result = loginRepository.resolveServer(
                serverId = server.id,
                encryptedPassword = server.encryptedPassword,
            )
            handleLoginResult(result)
        }
    }

    /**
     * Resets the UI state back to idle so the user can retry.
     */
    fun onDismissError() {
        _uiState.update { LoginUiState.Idle }
    }

    private fun handleLoginResult(result: LoginResult) {
        when (result) {
            is LoginResult.Success -> {
                sessionHolder.setSession(result.sessionData)
                Timber.d("Login successful, external URL: ${result.sessionData.externalUrl}")
                _uiState.update { LoginUiState.Success(url = result.sessionData.externalUrl) }
            }
            is LoginResult.MultipleServers -> {
                pendingServers = result.servers
                _uiState.update {
                    LoginUiState.ServerSelection(
                        servers = result.servers.map { ServerOption(id = it.id, name = it.name) },
                    )
                }
            }
            is LoginResult.Error -> {
                Timber.w("Login failed: ${result.message}")
                _uiState.update { LoginUiState.Error(message = result.message) }
            }
        }
    }
}
