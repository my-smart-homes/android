package io.homeassistant.companion.android.update

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.R as commonR

private const val UPDATE_URL = "https://my-smart-homes.github.io/app-landing/"

/**
 * Shows a dialog informing the user that a new version of the app is available,
 * with options to download the update or dismiss.
 */
@Composable
internal fun UpdateDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(commonR.string.msh_update_available)) },
        text = { Text(text = stringResource(commonR.string.msh_update_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, UPDATE_URL.toUri())
                    context.startActivity(browserIntent)
                    onDismiss()
                },
            ) {
                Text(text = stringResource(commonR.string.msh_update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(commonR.string.msh_update_later))
            }
        },
    )
}
