package com.blackout.communications.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colors from the design preview. The accent becomes user-selectable in Stage 4 (Settings). */
object AppColors {
    val Background = Color(0xFF0E0E10)
    val Card = Color(0xFF1C1C1F)
    val Line = Color(0xFF2A2A2E)
    val Text = Color(0xFFF2F2F2)
    val SubText = Color(0xFFA0A0A8)
    val Accent = Color(0xFF7C5CFF)
    val Favorite = Color(0xFFF5C542)

    // Call type colors (used from Stage 2)
    val Missed = Color(0xFFFF5A5F)
    val Incoming = Color(0xFF22C55E)
    val Outgoing = Color(0xFFFF8A00)
    val Voicemail = Color(0xFF8A8A93)

    /** Background colors for letter circles when a contact has no picture. */
    val AvatarPalette = listOf(
        Color(0xFF1D4ED8), Color(0xFFB45309), Color(0xFF0F766E), Color(0xFF9D174D), Color(0xFF6D28D9),
        Color(0xFF15803D), Color(0xFFB91C1C), Color(0xFF0E7490), Color(0xFF7C2D12), Color(0xFF4338CA),
    )
}

@Composable
fun CommunicationsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            onPrimary = Color.White,
            background = AppColors.Background,
            onBackground = AppColors.Text,
            surface = AppColors.Background,
            onSurface = AppColors.Text,
            surfaceVariant = AppColors.Card,
            onSurfaceVariant = AppColors.SubText,
            surfaceContainer = AppColors.Card,
            surfaceContainerHigh = AppColors.Card,
            outline = AppColors.Line,
            outlineVariant = AppColors.Line,
            error = AppColors.Missed,
        ),
        content = content,
    )
}
