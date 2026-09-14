package com.vaani.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * SHARP CORNERS — the defining visual rule. Every M3 shape slot is a
 * zero-radius corner so nothing in the app ever rounds. (M3 Shapes requires
 * CornerBasedShape; RoundedCornerShape(0.dp) is a rectangle.)
 */
val VaaniShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

val LocalVaaniColors = staticCompositionLocalOf { LightVaaniColors }

/** Access the Vaani semantic palette from any composable: `VaaniTheme.colors`. */
object VaaniTheme {
    val colors: VaaniColors
        @Composable @ReadOnlyComposable get() = LocalVaaniColors.current
}

@Composable
fun VaaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val vaaniColors = if (darkTheme) DarkVaaniColors else LightVaaniColors

    // Bridge to M3 so stock M3 components also pick up the right roles.
    val m3Colors = if (darkTheme) {
        darkColorScheme(
            primary = vaaniColors.coffee,
            onPrimary = vaaniColors.onCoffee,
            secondary = vaaniColors.slate,
            background = vaaniColors.background,
            onBackground = vaaniColors.ink,
            surface = vaaniColors.surface,
            onSurface = vaaniColors.ink,
            error = vaaniColors.danger,
            outline = vaaniColors.hairline,
        )
    } else {
        lightColorScheme(
            primary = vaaniColors.coffee,
            onPrimary = vaaniColors.onCoffee,
            secondary = vaaniColors.slate,
            background = vaaniColors.background,
            onBackground = vaaniColors.ink,
            surface = vaaniColors.surface,
            onSurface = vaaniColors.ink,
            error = vaaniColors.danger,
            outline = vaaniColors.hairline,
        )
    }

    CompositionLocalProvider(LocalVaaniColors provides vaaniColors) {
        MaterialTheme(
            colorScheme = m3Colors,
            typography = VaaniTypography,
            shapes = VaaniShapes,
            content = content,
        )
    }
}
