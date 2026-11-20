package org.fedorahosted.freeotp.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var finished = 0

    private fun string(id: Int) = compose.activity.getString(id)

    @Before
    fun setUp() {
        compose.setContent { FreeOTPTheme { OnboardingScreen(onFinished = { finished++ }) } }
    }

    @Test
    fun swipeShowsKeystorePage() {
        compose.onNodeWithText(string(R.string.app_welcome)).assertIsDisplayed()
        compose.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText(string(R.string.android_keystore_title)).assertIsDisplayed()
    }

    @Test
    fun indicatorJumpsToLastPageAndGetStartedFinishes() {
        compose.onNodeWithTag("onboarding_page_2").performClick()
        compose.onNodeWithText(string(R.string.backup_restore_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.get_started)).performScrollTo().performClick()
        assertEquals(1, finished)
    }

    @Test
    fun backGoesToPreviousPage() {
        compose.onNodeWithTag("onboarding_page_1").performClick()
        compose.onNodeWithText(string(R.string.android_keystore_title)).assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(string(R.string.app_welcome)).assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
        assertEquals(0, finished)
    }
}
