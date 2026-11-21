package org.fedorahosted.freeotp.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fedorahosted.freeotp.Code
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.Token
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(MainUiState(loading = false))

    private val actions = object : MainActions {
        override fun onActivate(id: String) { calls += "activate $id" }
        override fun onToggleSelection(id: String) { calls += "toggle $id" }
        override fun onClearSelection() { calls += "clear" }
        override fun onMoveUp() { calls += "up" }
        override fun onMoveDown() { calls += "down" }
        override fun onEdit() { calls += "edit" }
        override fun onDelete() { calls += "delete" }
        override fun onShare(code: String) { calls += "share $code" }
        override fun onBackup() { calls += "backup" }
        override fun onRestore() { calls += "restore" }
        override fun onToggleAutoCopy() { calls += "autoCopy" }
        override fun onToggleSortByMru() { calls += "sortByMru" }
        override fun onAbout() { calls += "about" }
        override fun onScan() { calls += "scan" }
        override fun onManualAdd() { calls += "manual" }
        override fun onAddRandom() { calls += "random" }
    }

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun token(id: String, issuer: String?, label: String = "alice", locked: Boolean = false) =
        TokenItem(id, issuer, label, Token.Type.TOTP, locked, null, R.drawable.ic_totp, 0xFF795548.toInt())

    private fun show(hasCamera: Boolean = true) {
        compose.setContent { FreeOTPTheme { MainScreen(state, actions, hasCamera) } }
    }

    private val tokens = listOf(token("a", "GitHub"), token("b", null, "bob"), token("c", "Example"))

    @Test
    fun emptyListShowsWelcome() {
        show()
        compose.onNodeWithText(string(R.string.main_welcome)).assertIsDisplayed()
    }

    @Test
    fun tokensShowIssuerAndAccount() {
        state = state.copy(tokens = tokens)
        show()

        compose.onNodeWithText("GitHub").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.unknown_issuer)).assertIsDisplayed()
        compose.onNodeWithText("bob").assertIsDisplayed()
    }

    @Test
    fun tapActivatesAndLongPressSelects() {
        state = state.copy(tokens = tokens)
        show()

        compose.onNodeWithTag("token_a").performClick()
        compose.onNodeWithTag("token_b").performTouchInput { longClick() }

        assertEquals(listOf("activate a", "toggle b"), calls)
    }

    @Test
    fun activeTokenShowsGroupedCodeAndSharesRawCode() {
        state = state.copy(tokens = tokens, codes = mapOf("a" to Code("123456", 30)))
        show()

        compose.onNodeWithTag("token_code", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("123 456", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("token_share", useUnmergedTree = true).performClick()

        assertEquals(listOf("share 123456"), calls)
    }

    @Test
    fun selectionModeShowsCountAndActions() {
        state = state.copy(tokens = tokens, selection = setOf("a", "c"))
        show()

        compose.onNodeWithTag("selection_count").assertTextEquals("2")
        compose.onNodeWithTag("token_a").assertIsSelected()
        // "a" is first and "c" last, so neither direction is possible.
        compose.onNodeWithContentDescription(string(R.string.move_up)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.move_down)).assertIsNotEnabled()
        compose.onNodeWithTag("fab").assertDoesNotExist()

        compose.onNodeWithContentDescription(string(R.string.edit_token)).performClick()
        compose.onNodeWithContentDescription(string(R.string.delete)).performClick()
        // In selection mode a tap toggles instead of showing the code.
        compose.onNodeWithTag("token_b").performClick()
        compose.onNodeWithContentDescription(string(R.string.main_clear_selection)).performClick()

        assertEquals(listOf("edit", "delete", "toggle b", "clear"), calls)
    }

    @Test
    fun moveButtonsFollowSelection() {
        state = state.copy(tokens = tokens, selection = setOf("b"))
        show()

        compose.onNodeWithContentDescription(string(R.string.move_up)).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(string(R.string.move_down)).assertIsEnabled().performClick()

        assertEquals(listOf("up", "down"), calls)
    }

    @Test
    fun overflowMenuShowsSettingsAndCallsActions() {
        state = state.copy(tokens = tokens, autoCopy = true)
        show()

        for ((label, call) in listOf(
            R.string.backup_title to "backup",
            R.string.restore_title to "restore",
            R.string.auto_copy_clipboard to "autoCopy",
            R.string.sort_by_recent_use to "sortByMru",
            R.string.about to "about",
        )) {
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithText(string(label)).performClick()
            assertEquals(call, calls.last())
        }
    }

    @Test
    fun addButtonExpandsToScanAndManualEntry() {
        show()

        compose.onNodeWithTag("fab_manual").assertDoesNotExist()
        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("fab_scan").performClick()
        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("fab_manual").performClick()

        assertEquals(listOf("scan", "manual"), calls)
        compose.onNodeWithTag("fab_manual").assertDoesNotExist()
    }

    @Test
    fun withoutCameraOnlyManualEntry() {
        show(hasCamera = false)

        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("fab_manual").assertIsDisplayed()
        compose.onNodeWithTag("fab_scan").assertDoesNotExist()
    }

    @Test
    fun threeLongPressesArmRandomTokenMode() {
        show()

        repeat(3) { compose.onNodeWithTag("fab").performTouchInput { longClick() } }
        compose.onNodeWithTag("fab").performClick()

        assertEquals(listOf("random"), calls)
        compose.onNodeWithTag("fab_manual").assertDoesNotExist()
    }
}
