package org.fedorahosted.freeotp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fedorahosted.freeotp.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeOTPThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun color(id: Int): Int =
        ContextCompat.getColor(InstrumentationRegistry.getInstrumentation().targetContext, id)

    private fun primaryAndSurface(darkTheme: Boolean): Pair<Color, Color> {
        lateinit var result: Pair<Color, Color>
        compose.setContent {
            FreeOTPTheme(darkTheme = darkTheme) {
                result = MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.surface
            }
        }
        compose.waitForIdle()
        return result
    }

    @Test
    fun lightThemeUsesBrandColors() {
        val (primary, surface) = primaryAndSurface(darkTheme = false)
        assertEquals(color(R.color.purple_500), primary.toArgb())
        assertEquals(color(R.color.freeotp_white_primary), surface.toArgb())
    }

    @Test
    fun darkThemeUsesBrandColors() {
        val (primary, surface) = primaryAndSurface(darkTheme = true)
        assertEquals(color(R.color.purple_200), primary.toArgb())
        assertEquals(color(R.color.dark_grey), surface.toArgb())
    }
}
