package com.blackout.communications.ui.contacts

import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.data.ContactWithNumbers
import com.blackout.communications.data.mainNumber
import com.blackout.communications.data.title
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.components.SquareSearchBar
import com.blackout.communications.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.util.Locale

private val Letters = ('A'..'Z').toList()

@Composable
fun ContactsScreen(
    vm: ContactsViewModel,
    onOpenProfile: (Long) -> Unit,
    onAddContact: () -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val state by vm.listState.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val group by vm.groupFilter.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    // Index in the LazyColumn where the non-favorite list starts.
    val headerItems = if (state.favorites.isNotEmpty()) 1 + state.favorites.size + 1 else 0

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SquareSearchBar(
                query = query,
                onQueryChange = { vm.query.value = it },
                placeholder = "Search contacts",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            ) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = AppColors.SubText)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label, fontWeight = if (order == sort) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { vm.sort.value = order; sortMenu = false },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { filterMenu = true }) {
                        Icon(Icons.Filled.FilterList, "Filter by group", tint = if (group != null) AppColors.Accent else AppColors.SubText)
                    }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        DropdownMenuItem(text = { Text("All contacts") }, onClick = { vm.groupFilter.value = null; filterMenu = false })
                        state.groups.forEach { g ->
                            DropdownMenuItem(text = { Text(g) }, onClick = { vm.groupFilter.value = g; filterMenu = false })
                        }
                        if (state.groups.isEmpty()) {
                            DropdownMenuItem(text = { Text("No groups yet", color = AppColors.SubText) }, onClick = { filterMenu = false }, enabled = false)
                        }
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "More options", tint = AppColors.SubText)
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text("Import") }, onClick = { moreMenu = false; onOpenBackup() })
                        DropdownMenuItem(text = { Text("Export") }, onClick = { moreMenu = false; onOpenBackup() })
                    }
                }
            }

            if (state.isEmpty) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No contacts yet.\nTap + to add one.", color = AppColors.SubText, fontSize = 16.sp)
                }
            } else {
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 36.dp, bottom = 100.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.favorites.isNotEmpty()) {
                            item {
                                Text(
                                    "FAVORITES",
                                    color = AppColors.SubText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.7.sp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(state.favorites, key = { "fav-${it.contact.id}" }) { c ->
                                ContactRow(c, showStar = true, onOpenProfile = onOpenProfile)
                            }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                        items(state.others, key = { it.contact.id }) { c ->
                            ContactRow(c, showStar = false, onOpenProfile = onOpenProfile)
                        }
                    }
                    AlphabetBar(
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 8.dp),
                        onLetter = { letter ->
                            val target = state.others.indexOfFirst {
                                val key = ContactsViewModel.sortKey(it, sort).uppercase(Locale.getDefault())
                                key.isNotEmpty() && key[0] >= letter
                            }
                            val index = if (target < 0) headerItems + state.others.size - 1 else headerItems + target
                            if (index >= 0) scope.launch { listState.scrollToItem(index.coerceAtLeast(0)) }
                        },
                    )
                }
            }
        }

        // Dialpad button (bottom middle) and Add contact button (bottom right).
        FloatingActionButton(
            onClick = onOpenDialpad,
            shape = CircleShape,
            containerColor = AppColors.Accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) { Icon(Icons.Filled.Dialpad, "Open dialpad") }
        FloatingActionButton(
            onClick = onAddContact,
            shape = RoundedCornerShape(16.dp),
            containerColor = AppColors.Accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
        ) { Icon(Icons.Filled.Add, "Add contact") }
    }
}

/** One contact: tap the picture to open the profile. Tapping the rest of the row does nothing. */
@Composable
private fun ContactRow(c: ContactWithNumbers, showStar: Boolean, onOpenProfile: (Long) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Avatar(
                name = c.title,
                photoPath = c.contact.photoPath,
                size = 44.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenProfile(c.contact.id) }
                    .semantics { contentDescription = "Open ${c.title} profile" },
            )
            Column(Modifier.weight(1f)) {
                Text(c.title, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                c.mainNumber?.let { n ->
                    Text("${n.label} · ${formatNumber(n.number)}", color = AppColors.SubText, fontSize = 13.sp, maxLines = 1)
                }
            }
            if (showStar) Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = AppColors.Favorite, modifier = Modifier.size(16.dp))
        }
        HorizontalDivider(color = AppColors.Line, thickness = 1.dp)
    }
}

/** A–Z bar on the right edge. Tap or slide along it to jump to a letter. */
@Composable
private fun AlphabetBar(modifier: Modifier, onLetter: (Char) -> Unit) {
    var height by remember { mutableStateOf(1) }
    val currentOnLetter by rememberUpdatedState(onLetter)
    fun letterAt(y: Float): Char = Letters[((y / height) * Letters.size).toInt().coerceIn(0, Letters.size - 1)]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(20.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { currentOnLetter(letterAt(it.y)) } }
            .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> currentOnLetter(letterAt(change.position.y)) } },
    ) {
        Letters.forEach { Text(it.toString(), color = AppColors.AccentInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp) }
    }
}

fun formatNumber(number: String): String =
    PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
