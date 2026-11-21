package org.fedorahosted.freeotp.main

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.fedorahosted.freeotp.ManualAdd
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.TokenPersistence
import org.fedorahosted.freeotp.ui.dialogs.MainDialog
import org.fedorahosted.freeotp.ui.dialogs.MainDialogHost
import org.fedorahosted.freeotp.ui.dialogs.MainDialogListener
import org.fedorahosted.freeotp.ui.dialogs.MainDialogsViewModel
import org.fedorahosted.freeotp.ui.main.MainActions
import org.fedorahosted.freeotp.ui.main.MainEvent
import org.fedorahosted.freeotp.ui.main.MainScreen
import org.fedorahosted.freeotp.ui.main.MainViewModel
import org.fedorahosted.freeotp.ui.scan.hasCamera
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/**
 * The token list. Handles `otpauth://` VIEW intents and carries out the [MainEvent]s of
 * [MainViewModel] that need the activity: clipboard, device credential, documents and dialogs.
 */
class Activity : ComponentActivity() {
    private val model: MainViewModel by viewModels { MainViewModel.factory(this) }
    private val dialogs: MainDialogsViewModel by viewModels()
    private var tokenBackup: TokenPersistence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The top bar is purple_500 (light icons) in the light theme and purple_200 (dark icons)
        // in the dark one.
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (night) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            else SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Don't let other apps screenshot token codes...
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        tokenBackup = try {
            TokenPersistence(applicationContext)
        } catch (e: Exception) {
            Log.e(LOGTAG, "Exception", e)
            null
        }

        // Set by TokenBackupAgent after Android restored the backup prefs.
        val backups = getSharedPreferences(BACKUP, MODE_PRIVATE)
        if (backups.getBoolean(RESTORED, false)) {
            backups.edit { remove(RESTORED) }
            dialogs.showRestore()
        }

        // Only on the first start: a recreated activity has already added the token.
        if (savedInstanceState == null) intent?.data?.let { model.addToken(it) }

        setContent { FreeOTPTheme { MainContent() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { model.addToken(it) }
    }

    @Composable
    private fun MainContent() {
        val state by model.state.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val grid = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        val hasCamera = remember { hasCamera(applicationContext) }

        fun notify(message: String) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }

        fun notifyToggle(@StringRes message: Int, enabled: Boolean) = notify(
            getString(message, getString(if (enabled) R.string.state_enabled_toast else R.string.state_disabled_toast))
        )

        // The token whose code waits for the device credential; saved in case the process dies.
        var pendingAuth by rememberSaveable { mutableStateOf<String?>(null) }
        val authenticate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val id = pendingAuth
            pendingAuth = null
            if (it.resultCode == RESULT_OK && id != null) model.activate(id)
        }
        val manualAdd = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) it.data?.data?.let { uri -> model.addToken(uri) }
        }
        val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) {
            uri ->
            val backup = tokenBackup
            if (uri != null && backup != null) {
                backup.copyBackupToExternal(uri)
                notify(getString(R.string.backup_toast))
            }
        }
        val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val backup = tokenBackup
            if (uri != null && backup != null) {
                backup.restoreBackupFromExternal(uri)
                dialogs.showRestore()
            }
        }

        LaunchedEffect(Unit) {
            model.events.collect { event ->
                when (event) {
                    is MainEvent.CopyCode -> copyToClipboard(event.code)
                    is MainEvent.AuthenticationRequired -> {
                        val token = event.token
                        val intent = getSystemService<KeyguardManager>()?.createConfirmDeviceCredentialIntent(
                            token.issuer ?: getString(R.string.unknown_issuer), token.label,
                        )
                        if (intent != null) {
                            pendingAuth = token.id
                            authenticate.launch(intent)
                        }
                    }
                    MainEvent.KeyInvalidated -> dialogs.show(MainDialog.KeyInvalidated)
                    is MainEvent.UnsafeUri -> dialogs.show(MainDialog.UnsafeUri(event.uri))
                    MainEvent.InvalidUri -> dialogs.show(MainDialog.InvalidUri)
                    is MainEvent.AddFailed ->
                        dialogs.show(if (event.lockRequired) MainDialog.LockRequired else MainDialog.AddError)
                    is MainEvent.ScrollTo -> grid.animateScrollToItem(event.index)
                    MainEvent.RestoreDone -> dialogs.dismiss()
                    MainEvent.RestoreBadPassword -> dialogs.restoreFailed()
                }
            }
        }

        val actions = remember {
            object : MainActions {
                override fun onActivate(id: String) = model.activate(id)
                override fun onToggleSelection(id: String) = model.toggleSelection(id)
                override fun onClearSelection() = model.clearSelection()
                override fun onMoveUp() = model.moveSelectionUp()
                override fun onMoveDown() = model.moveSelectionDown()
                override fun onDelete() = dialogs.show(MainDialog.ConfirmDelete)
                override fun onShare(code: String) = dialogs.showShare(code)
                override fun onAbout() = dialogs.show(MainDialog.About)
                override fun onAddRandom() = model.addRandomToken()
                override fun onBackup() = saveBackup.launch("externalBackup")
                override fun onRestore() = openBackup.launch(arrayOf("*/*"))
                override fun onManualAdd() = manualAdd.launch(Intent(this@Activity, ManualAdd::class.java))

                override fun onEdit() {
                    val token = model.state.value.firstSelected ?: return
                    dialogs.showEditToken(
                        token.id, token.label.orEmpty(), token.issuer.orEmpty(),
                        token.imageRes, token.imageUrl, token.color,
                    )
                }

                override fun onToggleAutoCopy() {
                    model.toggleAutoCopy()
                    notifyToggle(R.string.auto_copy_clipboard_toast, model.state.value.autoCopy)
                }

                override fun onToggleSortByMru() {
                    model.toggleSortByMru()
                    notifyToggle(R.string.sort_by_recent_use_toast, model.state.value.sortByMru)
                }

                override fun onScan() = dialogs.show(MainDialog.Scan)
            }
        }

        val listener = remember {
            object : MainDialogListener {
                override fun onEditTokenSave(tokenId: String, account: String, issuer: String) =
                    model.setLabel(tokenId, account, issuer)

                override fun onRestorePassword(password: String) = model.restore(password)
                override fun onDeleteConfirmed() = model.deleteSelected()
                override fun onAddUnsafe(uri: Uri) = model.addToken(uri, safe = false)
                override fun onEnableLockScreen() = startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
                override fun onScanned(uri: String) = model.addToken(uri.toUri())
            }
        }

        MainScreen(state, actions, hasCamera, snackbar, grid)
        MainDialogHost(dialogs, listener, restoring = state.restoring)
    }

    private fun copyToClipboard(code: String) {
        val clip = ClipData.newPlainText("code", code)
        // Keeps the code out of the clipboard preview and keyboard suggestions (API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
    }

    private companion object {
        const val LOGTAG = "Activity"
        const val BACKUP = "tokenBackup"
        const val RESTORED = "restoreComplete"
    }
}
