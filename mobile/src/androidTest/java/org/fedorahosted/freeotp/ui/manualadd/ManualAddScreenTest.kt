package org.fedorahosted.freeotp.ui.manualadd

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualAddScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var added: Uri? = null
    private val snackbar = SnackbarHostState()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /*
     * The test clock is stopped so that waiting for idle cannot run past the snackbar's display
     * time; one frame is enough for the click handler to request the snackbar. The button is
     * scrolled into view first: with a text field focused, the keyboard shrinks the form.
     */
    private fun clickAddAndAssertMessage(message: Int) {
        compose.onNodeWithTag("add").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("add").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(string(message), snackbar.currentSnackbarData?.visuals?.message)
        compose.mainClock.autoAdvance = true
    }

    /*
     * The field is scrolled into view first (the keyboard may cover it), and the menu opens in a
     * popup window, which a slow emulator may not have attached by the time the click returns.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun choose(tag: String, option: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("$tag:$option"), timeoutMillis = 5_000)
        compose.onNodeWithTag("$tag:$option").performClick()
    }

    @Before
    fun setUp() {
        compose.setContent {
            FreeOTPTheme { ManualAddScreen(onAdd = { added = it }, onNavigateUp = {}, snackbar = snackbar) }
        }
    }

    @Test
    fun emptySecretShowsMessageAndAddsNothing() {
        clickAddAndAssertMessage(R.string.manual_empty_secret)
        assertNull(added)
    }

    @Test
    fun colonInIssuerShowsMessage() {
        compose.onNodeWithTag("secret").performTextReplacement("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        compose.onNodeWithTag("issuer").performTextReplacement("a:b")
        clickAddAndAssertMessage(R.string.manual_malformed_issuer_account)
        assertNull(added)
    }

    @Test
    fun filledFormProducesUri() {
        compose.onNodeWithTag("account").performTextReplacement("jdoe")
        compose.onNodeWithTag("issuer").performTextReplacement("Example")
        compose.onNodeWithTag("secret").performTextReplacement("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        compose.onNodeWithText(string(R.string.manual_button_totp_text)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.manual_button_digits_eight)).performScrollTo().performClick()
        choose("algorithm", "SHA1")
        choose("interval", "60")
        compose.onNodeWithTag("add").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(
            "otpauth://totp/Example%3Ajdoe?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                "&algorithm=SHA1&digits=8&period=60",
            added.toString(),
        )
    }
}
