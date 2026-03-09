package io.homeassistant.companion.android.onboarding.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.update.UpdateDialog
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.compose.HAPreviews

private val ICON_SIZE = 80.dp
private val MSH_BLUE = Color(0xFF3EBDF1)

@Composable
internal fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (url: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess((uiState as LoginUiState.Success).url)
        }
    }

    LoginScreen(
        uiState = uiState,
        onLogin = viewModel::onLogin,
        onServerSelected = viewModel::onServerSelected,
        onDismissError = viewModel::onDismissError,
    )

    if (showUpdateDialog) {
        UpdateDialog(onDismiss = viewModel::onDismissUpdateDialog)
    }
}

@Composable
internal fun LoginScreen(
    uiState: LoginUiState,
    onLogin: (email: String, password: String) -> Unit,
    onServerSelected: (index: Int) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val isLoading = uiState is LoginUiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LoginHeader()

        Text(
            text = stringResource(commonR.string.login),
            style = HATextStyle.Headline,
            color = LocalHAColorScheme.current.colorTextPrimary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = HADimens.SPACE2),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = HADimens.SPACE4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            HATextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(commonR.string.msh_login_email_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                enabled = !isLoading,
            )

            HATextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(commonR.string.msh_login_password_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (!isLoading) onLogin(email, password) },
                ),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading,
            )

            Spacer(modifier = Modifier.height(HADimens.SPACE2))

            if (isLoading) {
                HALoading(modifier = Modifier.size(48.dp))
            } else {
                HAAccentButton(
                    text = stringResource(commonR.string.msh_login_button),
                    onClick = { onLogin(email, password) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(HADimens.SPACE4))

            HAPlainButton(
                text = stringResource(commonR.string.msh_need_help),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mysmarthomes.us/"))
                    context.startActivity(intent)
                },
            )
        }
    }

    if (uiState is LoginUiState.Error) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(commonR.string.msh_login_error_title)) },
            text = { Text(uiState.message) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(stringResource(commonR.string.confirm_positive))
                }
            },
        )
    }

    if (uiState is LoginUiState.ServerSelection) {
        ServerSelectionDialog(
            servers = uiState.servers,
            onServerSelected = onServerSelected,
        )
    }
}

@Composable
private fun LoginHeader() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = 0.35f),
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(x = 0f, y = 0f)
                lineTo(x = w, y = 0f)
                lineTo(x = w, y = h * 0.99f)
                cubicTo(
                    x1 = w * 0.7f, y1 = h * 0.75f,
                    x2 = w * 0.3f, y2 = h * 0.75f,
                    x3 = 0f, y3 = h,
                )
                close()
            }
            drawPath(path = path, color = MSH_BLUE)
        }

        Image(
            painter = painterResource(R.drawable.my_smart_home_icon),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

@Composable
private fun ServerSelectionDialog(
    servers: List<ServerOption>,
    onServerSelected: (index: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(commonR.string.msh_server_selection_title)) },
        text = {
            Column {
                servers.forEachIndexed { index, server ->
                    TextButton(
                        onClick = { onServerSelected(index) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = server.name,
                                style = HATextStyle.Body,
                            )
                            Text(
                                text = server.id,
                                style = HATextStyle.Body,
                                color = LocalHAColorScheme.current.colorTextSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@HAPreviews
@Composable
private fun LoginScreenIdlePreview() {
    HAThemeForPreview {
        LoginScreen(
            uiState = LoginUiState.Idle,
            onLogin = { _, _ -> },
            onServerSelected = {},
            onDismissError = {},
        )
    }
}

@HAPreviews
@Composable
private fun LoginScreenLoadingPreview() {
    HAThemeForPreview {
        LoginScreen(
            uiState = LoginUiState.Loading,
            onLogin = { _, _ -> },
            onServerSelected = {},
            onDismissError = {},
        )
    }
}
