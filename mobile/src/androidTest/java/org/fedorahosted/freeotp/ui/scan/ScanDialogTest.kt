package org.fedorahosted.freeotp.ui.scan

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dialog's flow with a fake camera; decoding itself is covered by [QrCodesTest]. */
@RunWith(AndroidJUnit4::class)
class ScanDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val camera: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    private val scanned = mutableListOf<String>()
    private var dismissed = 0

    private fun show(fake: (onDecoded: (String) -> Unit, onError: () -> Unit) -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FreeOTPTheme {
                ScanDialog(
                    onScanned = { scanned += it },
                    onDismiss = { dismissed++ },
                    camera = { onDecoded, onError, _ -> LaunchedEffect(Unit) { fake(onDecoded, onError) } },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun recognizedCodeIsShownThenReportedOnce() {
        show { onDecoded, _ ->
            onDecoded("otpauth://totp/A:b?secret=GEZDGNBVGY3TQOJQ")
            onDecoded("otpauth://totp/Other:c?secret=GEZDGNBVGY3TQOJQ")
        }

        compose.onNodeWithTag("scan_result").assertIsDisplayed()
        assertEquals(emptyList<String>(), scanned)

        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()

        assertEquals(listOf("otpauth://totp/A:b?secret=GEZDGNBVGY3TQOJQ"), scanned)
        assertEquals(0, dismissed)
    }

    @Test
    fun cameraErrorIsShown() {
        show { _, onError -> onError() }
        compose.mainClock.advanceTimeByFrame()

        val error = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.error_camera_open)
        compose.onNodeWithText(error).assertIsDisplayed()
        assertEquals(emptyList<String>(), scanned)
    }
}
