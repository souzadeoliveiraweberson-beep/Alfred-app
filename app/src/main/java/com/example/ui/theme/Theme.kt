package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val GothamDarkColorScheme = darkColorScheme(
    primary = GothamPureWhite,
    onPrimary = GothamVoid,
    primaryContainer = GothamSurfaceElevated,
    onPrimaryContainer = GothamPureWhite,
    secondary = GothamSilver,
    onSecondary = GothamVoid,
    secondaryContainer = GothamSurface,
    onSecondaryContainer = GothamPearl,
    tertiary = GothamPearl,
    onTertiary = GothamVoid,
    background = GothamVoid,
    onBackground = GothamPureWhite,
    surface = GothamObsidian,
    onSurface = GothamPureWhite,
    surfaceVariant = GothamSurface,
    onSurfaceVariant = GothamSilver,
    outline = GothamHairlineStrong,
    outlineVariant = GothamHairline,
    error = GothamPureWhite,
    onError = GothamVoid,
)

val AlfredShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AlfredTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = GothamDarkColorScheme,
        typography = AlfredTypography,
        shapes = AlfredShapes,
        content = content,
    )
}
