package com.blackout.communications.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.data.mainNumber
import com.blackout.communications.data.title
import com.blackout.communications.ui.calls.ScreenHeader
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.components.SquareSearchBar
import com.blackout.communications.ui.contacts.ContactsViewModel
import com.blackout.communications.ui.contacts.formatNumber
import com.blackout.communications.ui.theme.AppColors

/** Pick a contact, or type a number, to start a conversation. */
@Composable
fun NewMessageScreen(contactsVm: ContactsViewModel, onBack: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list by contactsVm.listState.collectAsStateWithLifecycle()
    val all = (list.favorites + list.others).filter { it.numbers.isNotEmpty() }
    val needle = query.trim().lowercase()
    val digits = query.filter { it.isDigit() }
    val matches = all.filter { c ->
        needle.isEmpty() || c.title.lowercase().contains(needle) || (digits.isNotEmpty() && c.numbers.any { it.number.filter(Char::isDigit).contains(digits) })
    }.sortedBy { it.title.lowercase() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("New message", onBack)
        SquareSearchBar(query, { query = it }, "Name or number", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (digits.length >= 3) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { onPick(query.trim()) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = AppColors.Accent)
                        Text("Send to ${formatNumber(query.trim())}", fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
                    }
                    HorizontalDivider(color = AppColors.Line)
                }
            }
            items(matches, key = { it.contact.id }) { c ->
                val n = c.mainNumber ?: return@items
                Row(Modifier.fillMaxWidth().clickable { onPick(n.number) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.title, c.contact.photoPath, 44.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(c.title, fontSize = 16.sp)
                        Text("${n.label} · ${formatNumber(n.number)}", fontSize = 13.sp, color = AppColors.SubText)
                    }
                }
                HorizontalDivider(color = AppColors.Line)
            }
        }
    }
}
