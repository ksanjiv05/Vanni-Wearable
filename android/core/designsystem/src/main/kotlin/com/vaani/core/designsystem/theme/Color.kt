package com.vaani.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Vaani palette — charcoal · coffee · cream · slate (DESIGN_SYSTEM.md).
 * Raw tokens; mapped to semantic roles in [VaaniColors] / theme.kt.
 */
object VaaniPalette {
    // Light (cream) roles
    val Background = Color(0xFFF3EADB)
    val Surface = Color(0xFFFBF6EC)
    val Sunken = Color(0xFFECE0CD)
    val Ink = Color(0xFF1B1A18)
    val Secondary = Color(0xFF4A4038)
    val Muted = Color(0xFF8A7E70)
    val Hairline = Color(0xFFD8CAB4)

    // Brand
    val Coffee = Color(0xFF835F41)
    val CoffeeHi = Color(0xFFA17C5B)
    val CoffeeLo = Color(0xFF654833)
    val Slate = Color(0xFF63707D)

    // Semantics
    val Success = Color(0xFF7A8A6B) // sage
    val Warning = Color(0xFFC9A15B) // honey
    val Danger = Color(0xFFB5705E)  // clay

    // Dark theme surfaces
    val DarkBackground = Color(0xFF151412)
    val DarkSurface = Color(0xFF1B1A18)
    val DarkRaised = Color(0xFF211F1D)
    val Cream = Color(0xFFF3EADB)
}

/**
 * Semantic color set. Light + dark share brand + semantic colors and only
 * swap neutral surfaces/text, exactly per DESIGN_SYSTEM.md.
 */
data class VaaniColors(
    val background: Color,
    val surface: Color,
    val sunken: Color,
    val ink: Color,
    val secondary: Color,
    val muted: Color,
    val hairline: Color,
    val coffee: Color,
    val coffeeHi: Color,
    val coffeeLo: Color,
    val slate: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val onCoffee: Color,
    val isDark: Boolean,
)

val LightVaaniColors = VaaniColors(
    background = VaaniPalette.Background,
    surface = VaaniPalette.Surface,
    sunken = VaaniPalette.Sunken,
    ink = VaaniPalette.Ink,
    secondary = VaaniPalette.Secondary,
    muted = VaaniPalette.Muted,
    hairline = VaaniPalette.Hairline,
    coffee = VaaniPalette.Coffee,
    coffeeHi = VaaniPalette.CoffeeHi,
    coffeeLo = VaaniPalette.CoffeeLo,
    slate = VaaniPalette.Slate,
    success = VaaniPalette.Success,
    warning = VaaniPalette.Warning,
    danger = VaaniPalette.Danger,
    onCoffee = VaaniPalette.Cream,
    isDark = false,
)

val DarkVaaniColors = VaaniColors(
    background = VaaniPalette.DarkBackground,
    surface = VaaniPalette.DarkSurface,
    sunken = VaaniPalette.DarkRaised,
    ink = VaaniPalette.Cream,
    secondary = Color(0xFFC7BBA9),
    muted = VaaniPalette.Muted,
    hairline = Color(0xFF39352F),
    coffee = VaaniPalette.Coffee,
    coffeeHi = VaaniPalette.CoffeeHi,
    coffeeLo = VaaniPalette.CoffeeLo,
    slate = VaaniPalette.Slate,
    success = VaaniPalette.Success,
    warning = VaaniPalette.Warning,
    danger = VaaniPalette.Danger,
    onCoffee = VaaniPalette.Cream,
    isDark = true,
)
