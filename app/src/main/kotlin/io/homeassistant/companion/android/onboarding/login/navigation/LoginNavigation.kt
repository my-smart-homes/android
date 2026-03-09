package io.homeassistant.companion.android.onboarding.login.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.login.LoginScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object LoginRoute

internal fun NavController.navigateToLogin(navOptions: NavOptions? = null) {
    navigate(LoginRoute, navOptions = navOptions)
}

internal fun NavGraphBuilder.loginScreen(
    onLoginSuccess: (url: String) -> Unit,
) {
    composable<LoginRoute> {
        LoginScreen(
            viewModel = hiltViewModel(),
            onLoginSuccess = onLoginSuccess,
        )
    }
}
