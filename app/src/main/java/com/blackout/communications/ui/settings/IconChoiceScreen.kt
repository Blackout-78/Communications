package com.blackout.communications.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.communications.R
import com.blackout.communications.settings.AppIcon
import com.blackout.communications.ui.theme.AppColors
import com.blackout.communications.ui.theme.ThemeState

/** Asked the first time the app opens: one icon, or three (Contacts, Messages, Call Log). */
@Composable
fun IconChoiceScreen(onChoose: (three: Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to Communications", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
        Text(
            "How do you want it on your phone? You can change this later in Settings → App icon.",
            fontSize = 15.sp, color = AppColors.SubText, textAlign = TextAlign.Center,
        )
        IconModeCard(
            title = "One icon",
            body = "A single Communications icon. Contacts, Messages and Call Log are tabs inside it.",
            onClick = { onChoose(false) },
        ) { SingleIconPreview(64.dp) }
        IconModeCard(
            title = "Three icons",
            body = "Separate Contacts, Messages and Call Log icons. Each one opens this app on its tab.",
            onClick = { onChoose(true) },
        ) { ThreeIconsPreview(52.dp) }
    }
}

@Composable
fun IconModeCard(title: String, body: String, selected: Boolean = false, onClick: () -> Unit, preview: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.Card)
            .border(2.dp, if (selected) AppColors.Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        preview()
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(body, fontSize = 14.sp, color = AppColors.SubText, textAlign = TextAlign.Center)
    }
}

@Composable
fun SingleIconPreview(size: Dp) {
    val context = LocalContext.current
    val a = ThemeState.appearance
    val img = remember(a.iconStyle, a.iconColor, a.iconColor2) { AppIcon.render(context, a.iconStyle, a.iconColor, a.iconColor2, 256).asImageBitmap() }
    Image(img, "One Communications icon", modifier = Modifier.size(size).clip(CircleShape))
}

@Composable
fun ThreeIconsPreview(size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            R.drawable.ic_tab_contacts_fg to "Contacts",
            R.drawable.ic_tab_messages_fg to "Messages",
            R.drawable.ic_tab_calls_fg to "Call Log",
        ).forEach { (res, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(size).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    // The drawing has padding around it (adaptive icon), so show it a bit larger than the circle.
                    Image(painterResource(res), null, modifier = Modifier.size(size * 1.5f))
                }
                Text(label, fontSize = 12.sp, color = AppColors.SubText, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
