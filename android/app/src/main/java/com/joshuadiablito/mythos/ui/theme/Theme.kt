package com.joshuadiablito.mythos.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mythos's palette, as on iOS: one cool accent (Aegean blue) for actions,
 * one warm note (apricot) for vocabulary, on a quiet paper background.
 */
data class MythosColors(
    val accent: Color,
    val warm: Color,
    val paper: Color,
    val card: Color,
    val ink: Color,
    val dark: Boolean,
) {
    val secondary: Color get() = ink.copy(alpha = 0.62f)
    val tertiary: Color get() = ink.copy(alpha = 0.38f)
    val quaternary: Color get() = ink.copy(alpha = 0.12f)
    val green: Color get() = hex(0x2E9E4F)
}

private val Light = MythosColors(
    accent = hex(0x2E5BD8), warm = hex(0xD9773A), paper = hex(0xF6F4EF), card = hex(0xFFFFFF), ink = hex(0x1C2333), dark = false,
)
private val Dark = MythosColors(
    accent = hex(0x4F7DF5), warm = hex(0xF2B27E), paper = hex(0x0D1117), card = hex(0x171C25), ink = hex(0xE8ECF4), dark = true,
)

val LocalMythosColors = staticCompositionLocalOf { Light }

fun hex(value: Long): Color = Color(0xFF000000 or value)

object Theme {
    val colors: MythosColors
        @Composable @ReadOnlyComposable get() = LocalMythosColors.current

    /** The story text face: a serif reads like a book. */
    fun storyFont(size: TextUnit = 21.sp) = TextStyle(fontFamily = FontFamily.Serif, fontSize = size)

    fun serif(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.Serif, fontSize = size, fontWeight = weight)
}

@Composable
fun MythosTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.paper, surface = c.card,
        onBackground = c.ink, onSurface = c.ink, surfaceContainer = c.card, surfaceContainerLow = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = hex(0x222936), secondaryContainer = c.accent.copy(alpha = 0.2f),
        onSecondaryContainer = c.ink, onSurfaceVariant = c.secondary, outlineVariant = c.quaternary,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.paper, surface = c.card,
        onBackground = c.ink, onSurface = c.ink, surfaceContainer = c.card, surfaceContainerLow = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = hex(0xECE9E2), secondaryContainer = c.accent.copy(alpha = 0.14f),
        onSecondaryContainer = c.ink, onSurfaceVariant = c.secondary, outlineVariant = c.quaternary,
    )
    CompositionLocalProvider(LocalMythosColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** A content card: solid, softly shadowed. */
@Composable
fun Modifier.card(padding: Dp = 20.dp, radius: Dp = 26.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
        .background(Theme.colors.card, shape)
        .padding(padding)
}

/** The app's background: paper, with a soft accent glow at the top and a warm one low down. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Theme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(c.paper)
            .drawBehind {
                val scale = density
                drawRect(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.15f, 0f), radius = 520f * scale))
                drawRect(Brush.radialGradient(listOf(c.warm.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width, size.height * 0.85f), radius = 460f * scale))
            },
        content = content,
    )
}
