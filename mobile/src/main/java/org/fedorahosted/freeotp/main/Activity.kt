package org.fedorahosted.freeotp.main

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
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
import org.fedorahosted.freeotp.ui.manualadd.ManualAddScreen
import org.fedorahosted.freeotp.ui.onboarding.Onboarding
import org.fedorahosted.freeotp.ui.onboarding.OnboardingScreen
import org.fedorahosted.freeotp.ui.password.NbvcxzEvaluator
import org.fedorahosted.freeotp.ui.password.PasswordScreen
import org.fedorahosted.freeotp.ui.password.PasswordViewModel
import org.fedorahosted.freeotp.ui.scan.hasCamera
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/**
 * The app's only activity, also started as `.PasswordActivity` (the launcher alias). Navigates
 * between onboarding, master password, token list and manual entry; handles `otpauth://` VIEW
 * intents; and carries out the [MainEvent]s of [MainViewModel] that need the activity:
 * clipboard, device credential, documents and dialogs.
 */
class Activity : ComponentActivity() {
    private val model: MainViewModel by viewModels { MainViewModel.factory(this) }
    private val dialogs: MainDialogsViewModel by viewModels()
    private var tokenBackup: TokenPersistence? = null

    /** Tokens from VIEW intents, added once the token list shows (after provisioning, so they
     *  are backed up). */
    private val pendingUris = mutableStateListOf<Uri>()
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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

        if (savedInstanceState == null) {
            intent?.data?.let { pendingUris += it }
        } else {
            // A recreated activity must not add the intent's token again, only unhandled ones.
            BundleCompat.getParcelableArrayList(savedInstanceState, PENDING_URIS, Uri::class.java)
                ?.let { pendingUris += it }
        }

        val start = if (tokenBackup?.isProvisioned == true) Route.TOKENS else Route.PASSWORD
        val firstRun = savedInstanceState == null && start == Route.PASSWORD && !Onboarding.isCompleted(this)

        setContent { FreeOTPTheme { Navigation(start, firstRun) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        pendingUris += uri
        // Back to the token list (a no-op before provisioning, when it isn't on the stack).
        navController?.popBackStack(Route.TOKENS, inclusive = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(PENDING_URIS, ArrayList(pendingUris))
    }

    @Composable
    private fun Navigation(start: String, firstRun: Boolean) {
        val nav = rememberNavController()
        navController = nav

        // Onboarding opens on top of the password screen, as before: Back skips it.
        var onboardingShown by rememberSaveable { mutableStateOf(!firstRun) }
        LaunchedEffect(Unit) {
            if (!onboardingShown) {
                onboardingShown = true
                nav.navigate(Route.ONBOARDING)
            }
        }

        NavHost(nav, startDestination = start) {
            composable(Route.ONBOARDING) {
                SystemBarIcons(onPrimary = false)
                OnboardingScreen(onFinished = {
                    Onboarding.setCompleted(this@Activity, true)
                    nav.popBackStack()
                })
            }
            composable(Route.PASSWORD) {
                SystemBarIcons(onPrimary = false)
                PasswordRoute(onProvisioned = {
                    nav.navigate(Route.TOKENS) { popUpTo(Route.PASSWORD) { inclusive = true } }
                })
            }
            composable(Route.TOKENS) {
                SystemBarIcons(onPrimary = true)
                MainContent(onManualAdd = { nav.navigate(Route.MANUAL_ADD) { launchSingleTop = true } })
            }
            composable(Route.MANUAL_ADD) {
                SystemBarIcons(onPrimary = false)
                ManualAddScreen(
                    onAdd = { uri ->
                        model.addToken(uri)
                        nav.popBackStack()
                    },
                    onNavigateUp = { nav.popBackStack() },
                )
            }
        }
    }

    /** Status bar icons that contrast with the top bar: the primary color or the surface. */
    @Composable
    private fun SystemBarIcons(onPrimary: Boolean) {
        val dark = isSystemInDarkTheme()
        LaunchedEffect(onPrimary, dark) {
            // primary is purple_500 (light icons) in the light theme and purple_200 (dark icons)
            // in the dark one; the surface is the other way round.
            val lightIcons = onPrimary != dark
            enableEdgeToEdge(
                statusBarStyle = if (lightIcons) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        }
    }

    @Composable
    private fun PasswordRoute(onProvisioned: () -> Unit) {
        val backup = tokenBackup
        val factory = remember {
            viewModelFactory {
                initializer {
                    PasswordViewModel(
                        evaluator = NbvcxzEvaluator { ConfigurationCompat.getLocales(resources.configuration)[0]!! },
                        provision = { password ->
                            checkNotNull(backup) { "Token storage unavailable" }.provision(password)
                        },
                    )
                }
            }
        }
        val password: PasswordViewModel = viewModel(factory = factory)
        val state by password.state.collectAsStateWithLifecycle()
        val currentOnProvisioned by rememberUpdatedState(onProvisioned)

        LaunchedEffect(state.provisioned) {
            if (state.provisioned) currentOnProvisioned()
        }

        PasswordScreen(
            state = state,
            onPasswordChange = password::onPasswordChange,
            onConfirmChange = password::onConfirmChange,
            onDone = password::onDone,
        )
    }

    @Composable
    private fun MainContent(onManualAdd: () -> Unit) {
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

        LaunchedEffect(pendingUris.size) {
            while (pendingUris.isNotEmpty()) model.addToken(pendingUris.removeAt(0))
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
                override fun onManualAdd() = onManualAdd()

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
        const val PENDING_URIS = "pendingUris"
    }

    private object Route {
        const val ONBOARDING = "onboarding"
        const val PASSWORD = "password"
        const val TOKENS = "tokens"
        const val MANUAL_ADD = "manualAdd"
    }
}
