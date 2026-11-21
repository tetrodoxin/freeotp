package org.fedorahosted.freeotp.ui.dialogs

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.pm.PackageInfoCompat
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/**
 * A title, a message and OK; with [confirm], a Cancel button and the confirm button instead.
 */
@Composable
fun MessageDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes confirm: Int? = null,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            if (confirm == null) {
                TextButton(onClick = onDismiss, Modifier.testTag("dialog_ok")) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(onClick = onConfirm, Modifier.testTag("dialog_confirm")) { Text(stringResource(confirm)) }
            }
        },
        dismissButton = confirm?.let {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        },
    )
}

/** Version, copyright and license with working links. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val html = stringResource(R.string.main_about_message)
    val linkColor = MaterialTheme.colorScheme.primary
    val message = remember(html, linkColor) {
        AnnotatedString.fromHtml(
            html,
            linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.main_about_title, info.versionName ?: "", PackageInfoCompat.getLongVersionCode(info)))
        },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Preview
@Composable
private fun MessageDialogPreview() {
    FreeOTPTheme {
        MessageDialog(R.string.main_unsafe_title, R.string.main_unsafe_message, R.string.add_anyway, onDismiss = {})
    }
}
