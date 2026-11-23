package org.fedorahosted.freeotp.ui.share

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.main.share.Clipboard
import org.fedorahosted.freeotp.main.share.ShareTarget
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun clipboardTargetCopiesCodeAndCloses() {
        var dismissed = false
        compose.setContent {
            FreeOTPTheme { ShareSheet(code = "123456", onDismiss = { dismissed = true }) }
        }

        val clipboard = context.getString(R.string.share_clipboard_clipboard)
        compose.onNodeWithTag("share_target:$clipboard").performClick()
        compose.waitUntil(5_000) { dismissed }

        // On the UI thread: before API 28, ClipboardManager needs a Looper on the creating thread.
        val clip = compose.runOnIdle { context.getSystemService(ClipboardManager::class.java).primaryClip }
        assertEquals("123456", clip?.getItemAt(0)?.text?.toString())
    }

    /* Discoverables change their targets in place, e.g. "Scan for…" becomes "Scanning for…". */
    @Test
    fun targetChangesAreShown() {
        // On the UI thread, like in the app: its Clipboard target creates a ClipboardManager.
        val controller = compose.runOnIdle { ShareController(context, "123456") }
        val target = ShareTarget().apply {
            title = "Test target"
            subtitle = "Devices"
            image = R.drawable.ic_bluetooth
        }
        compose.setContent { FreeOTPTheme { ShareTargetList(controller) } }
        compose.runOnIdle { controller.onShareAppeared(Clipboard(context, controller), target, null) }
        compose.onNodeWithText("Test target").assertIsDisplayed()

        compose.runOnIdle {
            target.title = "Test target (changed)"
            target.onClickListener = null
        }
        compose.onNodeWithText("Test target (changed)").assertIsDisplayed()
        compose.onNodeWithText("Test target").assertDoesNotExist()
    }
}
