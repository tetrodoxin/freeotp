package org.fedorahosted.freeotp.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class MainDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    private val viewModel = MainDialogsViewModel()
    private val passwords = mutableListOf<String>()
    private var edited: Pair<String, String>? = null

    private val listener = object : MainDialogListener {
        override fun onEditTokenSave(account: String, issuer: String) {
            edited = account to issuer
        }

        override fun onRestorePassword(password: String) {
            passwords += password
        }
    }

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun setUp() {
        compose.setContent { FreeOTPTheme { MainDialogHost(viewModel, listener) } }
    }

    @Test
    fun editTokenSavesChangedLabel() {
        viewModel.showEditToken("jdoe", "Example", R.drawable.ic_freeotp, null, 0xFF000000.toInt())

        compose.onNodeWithTag("edit_account").assertTextContains("jdoe")
        compose.onNodeWithTag("edit_issuer").performTextReplacement("Example Inc")
        compose.onNodeWithTag("edit_save").performClick()

        assertEquals("jdoe" to "Example Inc", edited)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun editTokenCancelKeepsLabel() {
        viewModel.showEditToken("jdoe", "Example", R.drawable.ic_freeotp, "https://invalid.example/x.png", 0)

        compose.onNodeWithTag("edit_issuer").performTextReplacement("Changed")
        compose.onNodeWithText(string(R.string.cancel)).performClick()

        assertNull(edited)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun restoreRequiresPassword() {
        viewModel.showRestore()

        compose.onNodeWithTag("restore_ok").performClick()
        compose.onNodeWithText(string(R.string.main_restore_password_required)).assertIsDisplayed()
        assertEquals(emptyList<String>(), passwords)

        compose.onNodeWithTag("restore_password").performTextInput("x")
        compose.onNodeWithText(string(R.string.main_restore_password_required)).assertDoesNotExist()
    }

    @Test
    fun wrongRestorePasswordShowsErrorAndKeepsDialog() {
        viewModel.showRestore()

        compose.onNodeWithTag("restore_password").performTextInput("wrong")
        compose.onNodeWithTag("restore_ok").performClick()
        assertEquals(listOf("wrong"), passwords)

        viewModel.restoreFailed()
        compose.onNodeWithText(string(R.string.main_restore_bad_password)).assertIsDisplayed()

        compose.onNodeWithTag("restore_password").performTextReplacement("right")
        compose.onNodeWithText(string(R.string.main_restore_bad_password)).assertDoesNotExist()
        compose.onNodeWithTag("restore_ok").performClick()
        assertEquals(listOf("wrong", "right"), passwords)
    }

    @Test
    fun cancellingRestoreAsksForConfirmation() {
        viewModel.showRestore()

        compose.onNodeWithTag("restore_cancel").performClick()
        assertEquals(MainDialog.RestoreCancel, viewModel.dialog.value)

        compose.onNodeWithTag("restore_go_back").performClick()
        assertEquals(MainDialog.Restore(), viewModel.dialog.value)
        compose.onNodeWithTag("restore_password").assertIsDisplayed()

        compose.onNodeWithTag("restore_cancel").performClick()
        compose.onNodeWithTag("restore_proceed").performClick()
        assertNull(viewModel.dialog.value)
        assertEquals(emptyList<String>(), passwords)
    }
}
