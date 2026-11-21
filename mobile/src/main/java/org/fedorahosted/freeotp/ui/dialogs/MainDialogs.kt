package org.fedorahosted.freeotp.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/** The dialog currently shown over the (still View-based) token list, if any. */
sealed interface MainDialog {
    data class EditToken(
        val account: String,
        val issuer: String,
        @DrawableRes val imageRes: Int,
        val imageUrl: String?,
        @ColorInt val color: Int,
    ) : MainDialog

    /** [failures] counts wrong passwords, so each one shows the error again. */
    data class Restore(val failures: Int = 0) : MainDialog

    data object RestoreCancel : MainDialog
}

/** Callbacks of the main screen's dialogs, implemented by `main.Activity`. */
interface MainDialogListener {
    fun onEditTokenSave(account: String, issuer: String)
    fun onRestorePassword(password: String)
}

/**
 * Holds the open dialog so it survives configuration changes. The restore dialogs move between
 * each other here; everything that touches tokens goes through [MainDialogListener].
 */
class MainDialogsViewModel : ViewModel() {
    private val _dialog = MutableStateFlow<MainDialog?>(null)
    val dialog: StateFlow<MainDialog?> = _dialog.asStateFlow()

    fun showEditToken(account: String, issuer: String, @DrawableRes imageRes: Int, imageUrl: String?, @ColorInt color: Int) {
        _dialog.value = MainDialog.EditToken(account, issuer, imageRes, imageUrl, color)
    }

    fun showRestore() {
        _dialog.value = MainDialog.Restore()
    }

    fun showRestoreCancel() {
        _dialog.value = MainDialog.RestoreCancel
    }

    /** The restore password was wrong: keep the dialog open and show the error. */
    fun restoreFailed() {
        _dialog.update { if (it is MainDialog.Restore) it.copy(failures = it.failures + 1) else it }
    }

    fun dismiss() {
        _dialog.value = null
    }

    companion object {
        /**
         * Adds the Compose host for the dialogs to [activity]'s content and returns the
         * ViewModel that controls them.
         */
        @JvmStatic
        fun install(activity: ComponentActivity, listener: MainDialogListener): MainDialogsViewModel {
            val viewModel = ViewModelProvider(activity)[MainDialogsViewModel::class.java]
            activity.addContentView(
                ComposeView(activity).apply {
                    setContent { FreeOTPTheme { MainDialogHost(viewModel, listener) } }
                },
                android.view.ViewGroup.LayoutParams(0, 0),
            )
            return viewModel
        }
    }
}

@Composable
fun MainDialogHost(viewModel: MainDialogsViewModel, listener: MainDialogListener) {
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    when (val d = dialog) {
        null -> {}
        is MainDialog.EditToken -> EditTokenDialog(
            dialog = d,
            onSave = { account, issuer ->
                viewModel.dismiss()
                listener.onEditTokenSave(account, issuer)
            },
            onDismiss = viewModel::dismiss,
        )
        is MainDialog.Restore -> RestoreDialog(
            failures = d.failures,
            onRestore = listener::onRestorePassword,
            onCancel = viewModel::showRestoreCancel,
        )
        MainDialog.RestoreCancel -> RestoreCancelDialog(
            onGoBack = viewModel::showRestore,
            onProceed = viewModel::dismiss,
        )
    }
}
