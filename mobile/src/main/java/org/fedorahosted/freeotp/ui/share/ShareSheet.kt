package org.fedorahosted.freeotp.ui.share

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.fedorahosted.freeotp.main.share.Clipboard
import org.fedorahosted.freeotp.main.share.Discoverable
import org.fedorahosted.freeotp.main.share.Jelling
import org.fedorahosted.freeotp.main.share.ShareTarget

/**
 * Immutable copy of a [ShareTarget] for display. The targets themselves are mutable and keep
 * their identity, so rows built from them directly would be skipped on recomposition.
 */
data class ShareTargetUi(
    val target: ShareTarget,
    val title: String,
    val subtitle: String,
    @DrawableRes val image: Int,
    /** False while the target is not usable yet (shown with a progress indicator). */
    val ready: Boolean,
    val enabled: Boolean,
)

/**
 * Collects the share targets of the [Discoverable]s and drives their permission, enablement and
 * discovery steps. The Android requests themselves go through the callbacks set by [ShareSheet].
 */
class ShareController(context: Context, private val code: String) : Discoverable.DiscoveryCallback {
    private val targets = mutableStateListOf<ShareTarget>()
    private var revision by mutableIntStateOf(0)

    var requestPermissions: (Array<String>) -> Unit = {}
    var requestEnable: (Intent) -> Unit = {}
    var onShared: () -> Unit = {}

    private val discoverables: List<Discoverable> = listOf(Clipboard(context, this), Jelling(context, this))

    /** Targets in display order; reading it makes the caller observe target changes. */
    val sortedTargets: List<ShareTargetUi>
        get() {
            @Suppress("UNUSED_EXPRESSION") revision
            return targets.sortedWith(ShareTarget.ORDER).map {
                val ready = it.onClickListener != null
                ShareTargetUi(it, it.title.orEmpty(), it.subtitle.orEmpty(), it.image, ready, ready && it.enabled)
            }
        }

    /**
     * Starts discovery on every permitted discoverable. Ones that need enabling first (Bluetooth
     * turned off) are only asked to if [askToEnable], so a refused request is not repeated each
     * time the screen comes back.
     */
    fun start(askToEnable: Boolean) {
        for (d in discoverables) {
            if (!d.permitted() || d.isDiscovering) continue
            val enable = d.enablement()
            if (enable == null) d.startDiscovery()
            else if (askToEnable) requestEnable(enable)
        }
    }

    fun stop() {
        discoverables.filter { it.isDiscovering }.forEach { it.stopDiscovery() }
    }

    override fun onShareAppeared(discoverable: Discoverable, target: ShareTarget, shareable: Discoverable.Shareable?) {
        target.onClickListener = if (shareable == null) {
            ShareTarget.OnClickListener { requestPermissions(discoverable.permissions()) }
        } else {
            ShareTarget.OnClickListener {
                it.onClickListener = null
                targets.forEach { t -> t.enabled = false }
                shareable.share(code) { onShared() }
            }
        }
        target.onChange = { revision++ }
        if (targets.none { it === target }) targets.add(target)
    }

    override fun onShareDisappeared(discoverable: Discoverable, target: ShareTarget) {
        target.onChange = null
        targets.removeAll { it === target }
    }
}

/** Bottom sheet for sharing [code]: copy to the clipboard or send to a Jelling Bluetooth device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val controller = remember(code) { ShareController(context, code) }

    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.start(askToEnable = false)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        controller.start(askToEnable = true)
    }
    controller.requestEnable = { enableLauncher.launch(it) }
    controller.requestPermissions = { permissionLauncher.launch(it) }
    controller.onShared = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnDismiss() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    controller.start(askToEnable = first)
                    first = false
                }
                Lifecycle.Event.ON_STOP -> controller.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ShareTargetList(controller)
    }
}

@Composable
internal fun ShareTargetList(controller: ShareController) {
    LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp).testTag("share_targets")) {
        items(controller.sortedTargets) { ShareTargetRow(it) }
    }
}

@Composable
private fun ShareTargetRow(item: ShareTargetUi) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = { Text(item.subtitle) },
        leadingContent = {
            if (!item.ready) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(painterResource(item.image), contentDescription = null, Modifier.size(24.dp))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .alpha(if (item.enabled) 1f else 0.38f)
            .clickable(enabled = item.enabled) { item.target.onClickListener?.onClick(item.target) }
            .testTag("share_target:${item.subtitle}"),
    )
}
