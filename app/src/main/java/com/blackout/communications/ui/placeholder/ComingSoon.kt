package com.blackout.communications.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.communications.ui.theme.AppColors

/** Shown on tabs that later stages fill in. */
@Composable
fun ComingSoon(icon: ImageVector, title: String, stage: String, details: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(48.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(stage, color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(details, color = AppColors.SubText, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
