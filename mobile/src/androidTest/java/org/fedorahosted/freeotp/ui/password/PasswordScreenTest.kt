package org.fedorahosted.freeotp.ui.password

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class PasswordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /* Each evaluation waits until the test completes it, so ordering can be controlled. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Strength>>()
    private val evaluator = PasswordEvaluator { pending.getOrPut(it) { CompletableDeferred() }.await() }
    private val provisioned = mutableListOf<String>()
    private lateinit var model: PasswordViewModel

    private fun complete(password: String, strength: Strength) {
        compose.waitUntil(timeoutMillis = 10_000) { pending.containsKey(password) }
        pending.getValue(password).complete(strength)
        compose.waitForIdle()
    }

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun setUp() {
        model = PasswordViewModel(evaluator) { provisioned += it }
        compose.setContent {
            FreeOTPTheme {
                val state by model.state.collectAsStateWithLifecycle()
                PasswordScreen(state, model::onPasswordChange, model::onConfirmChange, model::onDone)
            }
        }
    }

    @Test
    fun weakPasswordShowsWarningButCanStillBeConfirmed() {
        compose.onNodeWithTag("password").performTextReplacement("password")
        compose.onNodeWithTag("confirm").assertDoesNotExist()
        complete("password", Strength(safe = false, warning = "Too common"))

        compose.onNodeWithText("Too common").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performTextReplacement("password")
        compose.onNodeWithTag("done").assertIsEnabled()
    }

    @Test
    fun mismatchShowsErrorAndDisablesDone() {
        compose.onNodeWithTag("password").performTextReplacement("Correct-Horse-1")
        complete("Correct-Horse-1", Strength(safe = true, warning = null))

        compose.onNodeWithTag("confirm").performTextReplacement("Correct-Horse-2")
        compose.onNodeWithText(string(R.string.password_match)).assertIsDisplayed()
        compose.onNodeWithTag("done").assertIsNotEnabled()
    }

    @Test
    fun staleEvaluationIsIgnored() {
        compose.onNodeWithTag("password").performTextReplacement("a")
        compose.onNodeWithTag("password").performTextReplacement("Correct-Horse-1")
        complete("a", Strength(safe = false, warning = "Too short"))

        compose.onNodeWithText("Too short").assertDoesNotExist()
        compose.onNodeWithTag("confirm").assertDoesNotExist()
    }

    @Test
    fun changingPasswordResetsConfirmation() {
        compose.onNodeWithTag("password").performTextReplacement("Correct-Horse-1")
        complete("Correct-Horse-1", Strength(safe = true, warning = null))
        compose.onNodeWithTag("confirm").performTextReplacement("Correct-Horse-1")

        compose.onNodeWithTag("password").performTextReplacement("Correct-Horse-12")
        compose.onNodeWithTag("confirm").assertDoesNotExist()
        compose.onNodeWithTag("done").assertIsNotEnabled()
        assertEquals("", model.state.value.confirm)
    }

    @Test
    fun doneProvisionsThePassword() {
        compose.onNodeWithTag("password").performTextReplacement("Correct-Horse-1")
        complete("Correct-Horse-1", Strength(safe = true, warning = null))
        compose.onNodeWithTag("confirm").performTextReplacement("Correct-Horse-1")
        // The keyboard is open after typing, and on a small screen it can cover the button.
        compose.onNodeWithTag("done").performScrollTo().assertIsEnabled().performClick()
        assertTrue("Done click was not delivered", model.state.value.let { it.provisioning || it.provisioned })

        // Provisioning runs on a background dispatcher, which the test clock doesn't wait for.
        compose.waitUntil(timeoutMillis = 10_000) { model.state.value.provisioned }
        assertEquals(listOf("Correct-Horse-1"), provisioned)
        assertTrue(!model.state.value.doneEnabled)
    }
}
