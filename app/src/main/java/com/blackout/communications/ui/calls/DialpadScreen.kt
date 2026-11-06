package com.blackout.communications.ui.calls

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.calls.Phone
import com.blackout.communications.data.mainNumber
import com.blackout.communications.ui.contacts.ContactsViewModel
import com.blackout.communications.ui.contacts.formatNumber
import com.blackout.communications.ui.theme.AppColors

private val Keys = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

/** Dialpad. Hold 2–9 for speed dial, 1 for voicemail, 0 for +. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(contactsVm: ContactsViewModel, initial: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf(initial) }
    val owners by contactsVm.speedDialOwners.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (number.isEmpty()) "Enter a number" else formatNumber(number),
                fontSize = if (number.length > 14) 28.sp else 34.sp,
                color = if (number.isEmpty()) AppColors.SubText else AppColors.Text,
                textAlign = TextAlign.Center,
            )
            Text("Hold a number to speed dial", color = AppColors.SubText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { (digit, letters) ->
                        val key = digit.digitToIntOrNull()
                        val owner = if (key != null && key in 2..9) owners[key] else null
                        val firstName = owner?.contact?.firstName?.ifBlank { owner.contact.lastName }
                        Column(
                            Modifier.size(84.dp).clip(CircleShape).background(AppColors.Card)
                                .combinedClickable(
                                    onClick = { number += digit },
                                    onLongClick = {
                                        when {
                                            digit == '0' -> number += "+"
                                            digit == '1' -> Phone.callVoicemail(context)
                                            owner != null -> owner.mainNumber?.let { Phone.call(context, it.number) }
                                            key != null && key in 2..9 ->
                                                Toast.makeText(context, "Nobody is on speed dial $key. Set it in a contact's profile.", Toast.LENGTH_SHORT).show()
                                            else -> number += digit
                                        }
                                    },
                                )
                                .semantics { contentDescription = "$digit" + (firstName?.let { ", hold to call $it" } ?: "") },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(digit.toString(), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                            when {
                                digit == '1' -> Icon(Icons.Outlined.Voicemail, null, tint = AppColors.SubText, modifier = Modifier.size(14.dp))
                                letters.isNotEmpty() -> Text(letters, fontSize = 10.sp, color = AppColors.SubText, letterSpacing = 1.sp)
                            }
                            Text(firstName ?: "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent, maxLines = 1)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(56.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF16A34A))
                    .combinedClickable(onClick = { Phone.call(context, number) })
                    .semantics { contentDescription = "Call" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .combinedClickable(
                        onClick = { number = number.dropLast(1) },
                        onLongClick = { number = "" },
                    )
                    .semantics { contentDescription = "Delete digit. Hold to clear." },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = AppColors.SubText) }
        }
        Spacer(Modifier.height(4.dp))
    }
}
