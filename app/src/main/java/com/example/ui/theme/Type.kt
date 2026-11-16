package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val JakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

// Optical sizing: tight negative tracking on display numerals, clean 0 tracking on body,
// slightly positive tracking on small micro-labels for dark-mode legibility.
val AlfredTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.1).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.75).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.05.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.45.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.55.sp,
    ),
)
