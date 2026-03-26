package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.login.MshSessionHolder
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import timber.log.Timber

/**
 * Represents the navigation events that can occur during the connection process.
 */
internal sealed interface ConnectionNavigationEvent {
    /**
     * Emitted when authentication is successful and the code is available
     *
     * @property url The URL of the Home Assistant instance
     * @param authCode The authorization code returned by Home Assistant
     * @param requiredMTLS The authentication required the use of mTLS
     */
    data class Authenticated(val url: String, val authCode: String, val requiredMTLS: Boolean) :
        ConnectionNavigationEvent

    /**
     * Emitted when a link is not from the server that we are connecting to,
     * allowing it to be opened in an external browser.
     *
     * We don't want to open the URL within the application if it's not for the same host.
     * Otherwise the user might be able to leave the onboarding and not being able to come back.
     * A good exemple is clicking on the `help` or `forget password` button on the login page.
     *
     * @property url The [Uri] of the link to open.
     */
    data class OpenExternalLink(val url: Uri) : ConnectionNavigationEvent
}

private const val AUTH_CALLBACK_SCHEME = "homeassistant"
private const val AUTH_CALLBACK_HOST = "auth-callback"
private const val AUTH_CALLBACK = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST"

@HiltViewModel
internal class ConnectionViewModel @VisibleForTesting constructor(
    private val rawUrl: String,
    private val webViewClientFactory: HAWebViewClientFactory,
    private val connectivityCheckRepository: ConnectivityCheckRepository,
    private val mshSessionHolder: MshSessionHolder?,
) : ViewModel(),
    FrontendConnectionErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        webViewClientFactory: HAWebViewClientFactory,
        connectivityCheckRepository: ConnectivityCheckRepository,
        mshSessionHolder: MshSessionHolder,
    ) : this(savedStateHandle.toRoute<ConnectionRoute>().url, webViewClientFactory, connectivityCheckRepository, mshSessionHolder)

    private val rawUri: Uri by lazy { rawUrl.toUri() }

    private val _navigationEventsFlow = MutableSharedFlow<ConnectionNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    private val _urlFlow = MutableStateFlow<String?>(null)
    override val urlFlow = _urlFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(true)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    private val _errorFlow = MutableStateFlow<FrontendConnectionError?>(null)
    override val errorFlow = _errorFlow.asStateFlow()

    private val _connectivityCheckState = MutableStateFlow(ConnectivityCheckState())
    override val connectivityCheckState = _connectivityCheckState.asStateFlow()

    private var connectivityCheckJob: Job? = null

    /**
     * Runs connectivity checks against the server URL.
     * Results are emitted to [connectivityCheckState].
     */
    override fun runConnectivityChecks() {
        connectivityCheckJob?.cancel()
        connectivityCheckJob = viewModelScope.launch {
            connectivityCheckRepository.runChecks(rawUrl).collect { state ->
                _connectivityCheckState.value = state
            }
        }
    }

    private val _autoLoginJsFlow = MutableStateFlow<String?>(null)
    val autoLoginJsFlow = _autoLoginJsFlow.asStateFlow()

    val webViewClient: HAWebViewClient = webViewClientFactory.create(
        currentUrlFlow = urlFlow,
        onFrontendError = ::onError,
        onUrlIntercepted = ::interceptRedirectIfRequired,
        onPageFinished = {
            _isLoadingFlow.update { false }
            emitAutoLoginScriptIfNeeded()
        },
    )

    init {
        viewModelScope.launch {
            buildAuthUrl(rawUrl)
        }
    }

    private suspend fun buildAuthUrl(base: String) {
        Timber.d("Building auth url based on $base")
        try {
            val authUrl = with(base.toHttpUrl()) {
                HttpUrl.Builder()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .addPathSegments("auth/authorize")
                    .addEncodedQueryParameter("response_type", "code")
                    .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                    .addEncodedQueryParameter("redirect_uri", AUTH_CALLBACK)
                    .build()
                    .toString()
            }
            Timber.d("Auth url is: $authUrl")
            _urlFlow.emit(authUrl)
        } catch (e: Exception) {
            Timber.e(e, "Unable to build authentication URL")
            onError(
                FrontendConnectionError.UnreachableError(
                    message = commonR.string.connection_screen_malformed_url,
                    errorDetails = e.localizedMessage ?: e.message,
                    rawErrorType = e::class.toString(),
                ),
            )
        }
    }

    private fun interceptRedirectIfRequired(url: Uri, isTLSClientAuthNeeded: Boolean): Boolean {
        return if (url.isOpaque) {
            false // Not intercepted: opaque is not handled by app
        } else if (url.scheme == AUTH_CALLBACK_SCHEME && url.host == AUTH_CALLBACK_HOST) {
            val code = url.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                viewModelScope.launch {
                    _navigationEventsFlow.emit(
                        ConnectionNavigationEvent.Authenticated(
                            url = rawUrl,
                            authCode = code,
                            requiredMTLS = isTLSClientAuthNeeded,
                        ),
                    )
                }
                true // Intercepted: Authentication successful
            } else {
                Timber.w("Auth code is missing from the auth callback")
                false // Not intercepted: Auth code missing
            }
        } else if (url.host != rawUri.host) {
            Timber.d("$url is not from the server, opening it on external browser.")
            viewModelScope.launch {
                _navigationEventsFlow.emit(ConnectionNavigationEvent.OpenExternalLink(url))
            }
            true // Intercepted: External link
        } else {
            false // Default: Not intercepted
        }
    }

    /**
     * Called when the system WebView fails to initialize.
     *
     * Transitions to an error state with a [FrontendConnectionError.UnrecoverableError.WebViewCreationError]
     * so the error screen is displayed with guidance to update the system WebView.
     */
    fun onWebViewCreationFailed(throwable: Throwable) {
        onError(
            FrontendConnectionError.UnrecoverableError.WebViewCreationError(
                message = commonR.string.webview_creation_failed,
                throwable = throwable,
            ),
        )
    }

    private fun onError(error: FrontendConnectionError) {
        _errorFlow.update { error }
        // Automatically run connectivity checks when an error occurs
        runConnectivityChecks()
    }

    private fun emitAutoLoginScriptIfNeeded() {
        val session = mshSessionHolder?.session ?: return
        Timber.d("MSH session found, preparing auto-login injection")
        _autoLoginJsFlow.value = buildAutoLoginScript(
            username = session.username,
            password = session.password,
        )
    }

    private fun buildAutoLoginScript(username: String, password: String): String {
        val escapedUsername = JSONObject.quote(username)
        val escapedPassword = JSONObject.quote(password)
        return """
            (function() {
                var found = false;
                function createLoadingOverlay() {
                    var overlay = document.createElement('div');
                    overlay.id = 'mshLoadingOverlay';
                    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:white;z-index:9999;display:flex;flex-direction:column;justify-content:center;align-items:center;';
                    overlay.innerHTML = '<div style="border:8px solid #f3f3f3;border-top:8px solid #3498db;border-radius:50%;width:50px;height:50px;animation:mshSpin 1s linear infinite;"></div><p style="font-size:24px;font-family:Arial,sans-serif;color:black;">Signing in...</p>';
                    var style = document.createElement('style');
                    style.innerHTML = '@keyframes mshSpin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }';
                    document.head.appendChild(style);
                    document.body.appendChild(overlay);
                }
                function showErrorInOverlay() {
                    var overlay = document.getElementById('mshLoadingOverlay');
                    if (overlay) {
                        overlay.innerHTML = '<p style="font-size:24px;font-family:Arial,sans-serif;color:red;text-align:center;">Invalid username or password.</p>';
                    }
                }
                function checkForErrorAlert() {
                    var interval = setInterval(function() {
                        if (document.querySelector('ha-alert[alert-type="error"]')) {
                            clearInterval(interval);
                            showErrorInOverlay();
                        }
                    }, 1000);
                }
                function checkInputElement() {
                    if (found) return;
                    var inputElement = document.querySelector('input[name="username"]');
                    if (inputElement) {
                        found = true;
                        doSignIn();
                    }
                }
                function doSignIn() {
                    var usernameInput = document.querySelector('input[name="username"]');
                    var passwordInput = document.querySelector('input[name="password"]');
                    var loginButton = document.querySelector('mwc-button');
                    usernameInput.value = $escapedUsername;
                    usernameInput.dispatchEvent(new Event('input', { bubbles: true }));
                    passwordInput.value = $escapedPassword;
                    passwordInput.dispatchEvent(new Event('input', { bubbles: true }));
                    setTimeout(function() {
                        if (loginButton) {
                            loginButton.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true }));
                        }
                        checkForErrorAlert();
                    }, 100);
                }
                setInterval(checkInputElement, 1000);
                // createLoadingOverlay(); // temporarily disabled for debugging
            })();
        """.trimIndent()
    }
}
