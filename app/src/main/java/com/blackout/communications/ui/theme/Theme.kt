package com.blackout.communications.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.blackout.communications.data.Appearance
import com.blackout.communications.data.ColorSlot
import com.blackout.communications.data.ThemeMode

/** The current look. Changing it redraws every screen. */
object ThemeState {
    var appearance by mutableStateOf(Appearance())
    var activeTab by mutableStateOf("Contacts")
    var systemDark by mutableStateOf(true)
}

private data class Base(val bg: Color, val card: Color, val line: Color, val text: Color, val sub: Color, val their: Color)

private val DarkBase = Base(Color(0xFF0E0E10), Color(0xFF1C1C1F), Color(0xFF2A2A2E), Color(0xFFF2F2F2), Color(0xFFA0A0A8), Color(0xFF232327))
private val BlackBase = Base(Color(0xFF000000), Color(0xFF141416), Color(0xFF222226), Color(0xFFF2F2F2), Color(0xFF9A9AA2), Color(0xFF1C1C1F))
private val LightBase = Base(Color(0xFFF7F7F9), Color(0xFFFFFFFF), Color(0xFFE2E2E6), Color(0xFF141416), Color(0xFF5E5E66), Color(0xFFE9E9EE))

/** App colors. Your choices in Settings > Appearance override the theme's own colors. */
object AppColors {
    private val mode get() = ThemeState.appearance.theme
    val isLight: Boolean get() = mode == ThemeMode.Light || (mode == ThemeMode.System && !ThemeState.systemDark)
    private val base: Base get() = when {
        isLight -> LightBase
        mode == ThemeMode.Black -> BlackBase
        else -> DarkBase
    }
    private fun custom(slot: ColorSlot): Color? =
        ThemeState.appearance.paletteFor(ThemeState.activeTab).colors[slot]?.let { Color(it) }

    val Accent: Color get() = custom(ColorSlot.Accent) ?: Color(0xFF7C5CFF)
    val Background: Color get() = custom(ColorSlot.Background) ?: base.bg
    val Text: Color get() = custom(ColorSlot.Text) ?: base.text
    val Card: Color get() = custom(ColorSlot.Background)?.let { lerp(it, Text, 0.08f) } ?: base.card
    val Line: Color get() = custom(ColorSlot.Background)?.let { lerp(it, Text, 0.14f) } ?: base.line
    val SubText: Color get() = if (custom(ColorSlot.Text) != null || custom(ColorSlot.Background) != null) lerp(Background, Text, 0.65f) else base.sub
    val MyBubble: Color get() = custom(ColorSlot.MyBubble) ?: Accent
    val TheirBubble: Color get() = custom(ColorSlot.TheirBubble) ?: base.their

    val Favorite = Color(0xFFF5C542)

    // Call type colors
    val Missed = Color(0xFFFF5A5F)
    val Incoming = Color(0xFF22C55E)
    val Outgoing = Color(0xFFFF8A00)
    val Voicemail = Color(0xFF8A8A93)

    /** Background colors for letter circles when a contact has no picture. */
    val AvatarPalette = listOf(
        Color(0xFF1D4ED8), Color(0xFFB45309), Color(0xFF0F766E), Color(0xFF9D174D), Color(0xFF6D28D9),
        Color(0xFF15803D), Color(0xFFB91C1C), Color(0xFF0E7490), Color(0xFF7C2D12), Color(0xFF4338CA),
    )

    /** White or near-black, whichever reads better on a color. */
    fun onColor(c: Color): Color = if (c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f > 0.6f) Color(0xFF141416) else Color.White
}

@Composable
fun CommunicationsTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    SideEffect { ThemeState.systemDark = systemDark }
    val scheme = if (AppColors.isLight) {
        lightColorScheme(
            primary = AppColors.Accent, onPrimary = AppColors.onColor(AppColors.Accent),
            background = AppColors.Background, onBackground = AppColors.Text,
            surface = AppColors.Background, onSurface = AppColors.Text,
            surfaceVariant = AppColors.Card, onSurfaceVariant = AppColors.SubText,
            surfaceContainer = AppColors.Card, surfaceContainerHigh = AppColors.Card,
            outline = AppColors.Line, outlineVariant = AppColors.Line, error = AppColors.Missed,
        )
    } else {
        darkColorScheme(
            primary = AppColors.Accent, onPrimary = AppColors.onColor(AppColors.Accent),
            background = AppColors.Background, onBackground = AppColors.Text,
            surface = AppColors.Background, onSurface = AppColors.Text,
            surfaceVariant = AppColors.Card, onSurfaceVariant = AppColors.SubText,
            surfaceContainer = AppColors.Card, surfaceContainerHigh = AppColors.Card,
            outline = AppColors.Line, outlineVariant = AppColors.Line, error = AppColors.Missed,
        )
    }
    val density = LocalDensity.current
    val scale = ThemeState.appearance.textSize.scale
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
