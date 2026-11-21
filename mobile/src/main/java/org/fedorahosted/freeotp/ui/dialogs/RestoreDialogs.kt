package org.fedorahosted.freeotp.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.components.PasswordField
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/* Both restore dialogs can only be left through their buttons, as before the migration. */
private val NotCancelable = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

/**
 * Asks for the backup password. [failures] increases with each wrong password and shows the error.
 * While [busy] (the restore runs), the buttons are disabled and a progress bar shows.
 * The password is deliberately kept out of saved instance state.
 */
@Composable
fun RestoreDialog(failures: Int, onRestore: (String) -> Unit, onCancel: () -> Unit, busy: Boolean = false) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(failures) {
        if (failures > 0) {
            error = R.string.main_restore_bad_password
            focus.requestFocus()
        }
    }

    val submit = {
        if (busy) Unit
        else if (password.isEmpty()) error = R.string.main_restore_password_required
        else onRestore(password)
    }

    AlertDialog(
        onDismissRequest = {},
        properties = NotCancelable,
        title = { Text(stringResource(R.string.main_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.main_restore_message))
                PasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = stringResource(R.string.password),
                    error = error?.let { stringResource(it) },
                    imeAction = ImeAction.Go,
                    onIme = submit,
                    modifier = Modifier
                        .focusRequester(focus)
                        .testTag("restore_password"),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("restore_progress"))
            }
        },
        confirmButton = {
            TextButton(onClick = submit, Modifier.testTag("restore_ok"), enabled = !busy) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, Modifier.testTag("restore_cancel"), enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Confirms skipping the restore. */
@Composable
fun RestoreCancelDialog(onGoBack: () -> Unit, onProceed: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = NotCancelable,
        title = { Text(stringResource(R.string.main_restore_cancel_title)) },
        text = { Text(stringResource(R.string.main_restore_cancel_message)) },
        confirmButton = {
            TextButton(onClick = onGoBack, Modifier.testTag("restore_go_back")) {
                Text(stringResource(R.string.main_restore_go_back))
            }
        },
        dismissButton = {
            TextButton(onClick = onProceed, Modifier.testTag("restore_proceed")) {
                Text(stringResource(R.string.main_restore_proceed))
            }
        },
    )
}

@Preview
@Composable
private fun RestoreDialogPreview() {
    FreeOTPTheme { RestoreDialog(failures = 1, onRestore = {}, onCancel = {}) }
}

@Preview
@Composable
private fun RestoreCancelDialogPreview() {
    FreeOTPTheme { RestoreCancelDialog(onGoBack = {}, onProceed = {}) }
}
