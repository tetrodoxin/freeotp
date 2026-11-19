package org.fedorahosted.freeotp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import org.fedorahosted.freeotp.R

/**
 * Material 3 theme for the Compose UI.
 *
 * Colors come from `res/values/colors.xml` so the Compose and View UIs share one palette while
 * both exist. Dynamic (wallpaper) color is intentionally not used, to keep the brand purple.
 */
@Composable
fun FreeOTPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme() else lightScheme(),
        content = content,
    )
}

@Composable
private fun lightScheme(): ColorScheme {
    val primary = colorResource(R.color.purple_500)
    val onSurface = colorResource(R.color.freeotp_dark_primary)
    val surface = colorResource(R.color.freeotp_white_primary)
    return lightColorScheme(
        primary = primary,
        onPrimary = colorResource(R.color.white),
        primaryContainer = colorResource(R.color.purple_200),
        onPrimaryContainer = colorResource(R.color.purple_700),
        secondary = primary,
        onSecondary = colorResource(R.color.white),
        secondaryContainer = colorResource(R.color.freeotp_grey_primary),
        onSecondaryContainer = onSurface,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        onSurfaceVariant = colorResource(R.color.freeotp_grey_dark_primary),
    )
}

@Composable
private fun darkScheme(): ColorScheme {
    val primary = colorResource(R.color.purple_200)
    val surface = colorResource(R.color.dark_grey)
    val onSurface = colorResource(R.color.freeotp_white_primary)
    return darkColorScheme(
        primary = primary,
        onPrimary = colorResource(R.color.black),
        primaryContainer = colorResource(R.color.purple_700),
        onPrimaryContainer = colorResource(R.color.purple_200),
        secondary = primary,
        onSecondary = colorResource(R.color.black),
        secondaryContainer = colorResource(R.color.freeotp_grey_dark_primary),
        onSecondaryContainer = onSurface,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        onSurfaceVariant = colorResource(R.color.freeotp_grey_primary),
    )
}
