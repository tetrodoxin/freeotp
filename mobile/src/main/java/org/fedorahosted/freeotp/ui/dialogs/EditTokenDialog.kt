package org.fedorahosted.freeotp.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/** Edits a token's account and issuer; the icon is shown for orientation only. */
@Composable
fun EditTokenDialog(
    dialog: MainDialog.EditToken,
    onSave: (account: String, issuer: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var account by rememberSaveable(dialog) { mutableStateOf(dialog.account) }
    var issuer by rememberSaveable(dialog) { mutableStateOf(dialog.issuer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { TokenImage(dialog) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    placeholder = { Text(stringResource(R.string.manual_account_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_account"),
                )
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    placeholder = { Text(stringResource(R.string.manual_issuer_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_issuer"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(account, issuer) }, Modifier.testTag("edit_save")) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun TokenImage(dialog: MainDialog.EditToken) {
    val modifier = Modifier
        .size(72.dp)
        .background(Color(dialog.color))
        .padding(8.dp)
    val fallback = painterResource(dialog.imageRes)
    if (dialog.imageUrl.isNullOrEmpty()) {
        Image(fallback, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(
            model = dialog.imageUrl,
            contentDescription = null,
            error = fallback,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun EditTokenDialogPreview() {
    FreeOTPTheme {
        EditTokenDialog(
            dialog = MainDialog.EditToken("1", "jdoe@example.com", "Example", R.drawable.ic_freeotp, null, 0xFF2196F3.toInt()),
            onSave = { _, _ -> },
            onDismiss = {},
        )
    }
}
