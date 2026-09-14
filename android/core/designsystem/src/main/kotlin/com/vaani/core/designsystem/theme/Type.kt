package com.vaani.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Vaani type scale — the single source of truth for text sizing. Screens
 * MUST style text through `MaterialTheme.typography.*` (or [OverlineStyle] /
 * [MonoStyle]); no hardcoded `fontSize` literals in feature code, so the scale
 * cannot drift from DESIGN_SYSTEM.md. Sizes are the design doc's @1080w px
 * values mapped to sp for a phone. Each slot bakes its dominant weight; a call
 * site that needs a different weight passes `fontWeight = …` alongside `style`.
 *
 * UI text uses the system sans (Inter to be swapped in later); technical values
 * (timestamps, ids, figures) use [VaaniMono].
 */
val VaaniMono: FontFamily = FontFamily.Monospace

val VaaniTypography = Typography(
    // Display — hero + top-level screen titles
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    // Headline — bars and prominent headings
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    // Title — card + row titles
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    // Body — content text
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    // Label — chips, captions, nav
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp),
)

/** Overline style: tracked, uppercase, muted — used for section headers. */
val OverlineStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.5.sp,
)

/** Monospace style for timestamps / technical values. */
val MonoStyle = TextStyle(
    fontFamily = VaaniMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)
