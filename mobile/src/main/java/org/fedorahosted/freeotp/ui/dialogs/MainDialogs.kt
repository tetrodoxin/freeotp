package org.fedorahosted.freeotp.ui.dialogs

import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.scan.ScanDialog
import org.fedorahosted.freeotp.ui.share.ShareSheet

/** The dialog currently shown over the main screen, if any. */
sealed interface MainDialog {
    data class EditToken(
        val tokenId: String,
        val account: String,
        val issuer: String,
        @DrawableRes val imageRes: Int,
        val imageUrl: String?,
        @ColorInt val color: Int,
    ) : MainDialog

    /** [failures] counts wrong passwords, so each one shows the error again. */
    data class Restore(val failures: Int = 0) : MainDialog

    data object RestoreCancel : MainDialog

    data class Share(val code: String) : MainDialog

    data object ConfirmDelete : MainDialog

    /** The token URI fails the safety checks; it can still be added on request. */
    data class UnsafeUri(val uri: Uri) : MainDialog

    data object InvalidUri : MainDialog

    /** The keystore refused the token's key; as before, this asks for a screen lock. */
    data object LockRequired : MainDialog

    data object AddError : MainDialog

    data object KeyInvalidated : MainDialog

    data object About : MainDialog

    data object Scan : MainDialog
}

/** Callbacks of the main screen's dialogs that act on tokens or leave the app. */
interface MainDialogListener {
    fun onEditTokenSave(tokenId: String, account: String, issuer: String) {}
    fun onRestorePassword(password: String) {}
    fun onDeleteConfirmed() {}
    fun onAddUnsafe(uri: Uri) {}
    fun onEnableLockScreen() {}
    fun onScanned(uri: String) {}
}

/**
 * Holds the open dialog so it survives configuration changes. The restore dialogs move between
 * each other here; everything that touches tokens goes through [MainDialogListener].
 */
class MainDialogsViewModel : ViewModel() {
    private val _dialog = MutableStateFlow<MainDialog?>(null)
    val dialog: StateFlow<MainDialog?> = _dialog.asStateFlow()

    fun show(dialog: MainDialog) {
        _dialog.value = dialog
    }

    fun showEditToken(
        tokenId: String,
        account: String,
        issuer: String,
        @DrawableRes imageRes: Int,
        imageUrl: String?,
        @ColorInt color: Int,
    ) = show(MainDialog.EditToken(tokenId, account, issuer, imageRes, imageUrl, color))

    fun showRestore() = show(MainDialog.Restore())

    fun showRestoreCancel() = show(MainDialog.RestoreCancel)

    fun showShare(code: String) = show(MainDialog.Share(code))

    /** The restore password was wrong: keep the dialog open and show the error. */
    fun restoreFailed() {
        _dialog.update { if (it is MainDialog.Restore) it.copy(failures = it.failures + 1) else it }
    }

    fun dismiss() {
        _dialog.value = null
    }
}

/** Renders the open dialog. [restoring] disables the restore dialog while PBKDF2 runs. */
@Composable
fun MainDialogHost(
    viewModel: MainDialogsViewModel,
    listener: MainDialogListener,
    restoring: Boolean = false,
) {
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val dismiss = viewModel::dismiss
    when (val d = dialog) {
        null -> {}
        is MainDialog.EditToken -> EditTokenDialog(
            dialog = d,
            onSave = { account, issuer ->
                dismiss()
                listener.onEditTokenSave(d.tokenId, account, issuer)
            },
            onDismiss = dismiss,
        )
        is MainDialog.Restore -> RestoreDialog(
            failures = d.failures,
            busy = restoring,
            onRestore = listener::onRestorePassword,
            onCancel = viewModel::showRestoreCancel,
        )
        MainDialog.RestoreCancel -> RestoreCancelDialog(
            onGoBack = viewModel::showRestore,
            onProceed = dismiss,
        )
        is MainDialog.Share -> ShareSheet(code = d.code, onDismiss = dismiss)
        MainDialog.ConfirmDelete -> MessageDialog(
            title = R.string.main_deletion_title,
            message = R.string.main_deletion_message,
            confirm = R.string.delete,
            onConfirm = {
                dismiss()
                listener.onDeleteConfirmed()
            },
            onDismiss = dismiss,
        )
        is MainDialog.UnsafeUri -> MessageDialog(
            title = R.string.main_unsafe_title,
            message = R.string.main_unsafe_message,
            confirm = R.string.add_anyway,
            onConfirm = {
                dismiss()
                listener.onAddUnsafe(d.uri)
            },
            onDismiss = dismiss,
        )
        MainDialog.InvalidUri -> MessageDialog(R.string.main_invalid_title, R.string.main_invalid_message, onDismiss = dismiss)
        MainDialog.AddError -> MessageDialog(R.string.main_error_title, R.string.main_error_message, onDismiss = dismiss)
        MainDialog.KeyInvalidated ->
            MessageDialog(R.string.main_invalidated_title, R.string.main_invalidated_message, onDismiss = dismiss)
        MainDialog.LockRequired -> MessageDialog(
            title = R.string.main_lock_title,
            message = R.string.main_lock_message,
            confirm = R.string.enable_lock_screen,
            onConfirm = {
                dismiss()
                listener.onEnableLockScreen()
            },
            onDismiss = dismiss,
        )
        MainDialog.About -> AboutDialog(onDismiss = dismiss)
        MainDialog.Scan -> ScanDialog(
            onScanned = {
                dismiss()
                listener.onScanned(it)
            },
            onDismiss = dismiss,
        )
    }
}
