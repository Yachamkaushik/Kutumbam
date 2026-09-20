package com.kutumbam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Palette lifted from the design mockups. */
object K {
    val Bg = Color(0xFFFAF7F2)
    val Teal = Color(0xFF0F6E63)
    val TealTint = Color(0xFFE7F3F1)
    val Ink = Color(0xFF22201B)
    val Muted = Color(0xFF6E6858)
    val Border = Color(0xFFE7E1D3)
    val Divider = Color(0xFFF1EDE3)
    val Card = Color(0xFFFFFFFF)
    val WarnBg = Color(0xFFFCEEDD)
    val WarnBorder = Color(0xFFF0C48A)
    val WarnIcon = Color(0xFFB45309)
    val WarnText = Color(0xFF8A4008)
    val Green = Color(0xFF15803D)
    val Ring = Color(0xFFD8D2C2)

    /** Fraunces in the mockup; the system serif keeps the APK free of bundled fonts. */
    val Display = FontFamily.Serif
}

@Composable
fun KutumbamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = K.Teal, onPrimary = Color.White, background = K.Bg, surface = K.Bg, onSurface = K.Ink,
            surfaceVariant = K.TealTint, outline = K.Border, error = K.WarnIcon,
        ),
        typography = Typography(bodyLarge = TextStyle(fontWeight = FontWeight.Normal)),
        content = content,
    )
}
