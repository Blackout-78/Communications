package com.blackout.communications.ui.contacts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.data.Contact
import com.blackout.communications.data.ContactWithNumbers
import com.blackout.communications.data.PhoneNumber
import com.blackout.communications.data.displayName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) { FirstName("First name"), LastName("Last name"), Recent("Recently added") }

data class ContactListState(
    val favorites: List<ContactWithNumbers> = emptyList(),
    val others: List<ContactWithNumbers> = emptyList(),
    val groups: List<String> = emptyList(),
    val isEmpty: Boolean = true,
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as CommunicationsApp).contacts

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.FirstName)
    val groupFilter = MutableStateFlow<String?>(null)

    private val all: StateFlow<List<ContactWithNumbers>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listState: StateFlow<ContactListState> =
        combine(all, query, sort, groupFilter) { contacts, q, order, group ->
            val needle = q.trim().lowercase()
            val digits = q.filter { it.isDigit() }
            val filtered = contacts.filter { c ->
                (group == null || c.contact.groupName.equals(group, ignoreCase = true)) &&
                    (needle.isEmpty() ||
                        c.contact.displayName.lowercase().contains(needle) ||
                        (digits.isNotEmpty() && c.numbers.any { it.number.filter(Char::isDigit).contains(digits) }))
            }
            val sorted = when (order) {
                SortOrder.FirstName -> filtered.sortedBy { sortKey(it, order) }
                SortOrder.LastName -> filtered.sortedBy { sortKey(it, order) }
                SortOrder.Recent -> filtered.sortedByDescending { it.contact.createdAt }
            }
            ContactListState(
                favorites = sorted.filter { it.contact.favorite },
                others = sorted.filterNot { it.contact.favorite },
                groups = contacts.map { it.contact.groupName.trim() }.filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
                isEmpty = contacts.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactListState())

    /** Which contact is on each speed dial key, for the key picker. */
    val speedDialOwners: StateFlow<Map<Int, ContactWithNumbers>> =
        all.map { list -> list.mapNotNull { c -> c.contact.speedDial?.let { it to c } }.toMap() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun contact(id: Long): Flow<ContactWithNumbers?> = repo.observe(id)

    fun save(contact: Contact, numbers: List<PhoneNumber>, previousPhoto: String?, onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(repo.save(contact, numbers, previousPhoto)) }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    fun setFavorite(id: Long, favorite: Boolean) = viewModelScope.launch { repo.setFavorite(id, favorite) }

    fun setRingtone(id: Long, uri: Uri?) = viewModelScope.launch { repo.setRingtone(id, uri) }

    fun setSpeedDial(id: Long, key: Int?) = viewModelScope.launch { repo.setSpeedDial(id, key) }

    fun pickPhoto(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(repo.savePhoto(uri)) }
    }

    fun discardPhoto(path: String?) = repo.discardPhoto(path)

    companion object {
        /** The text used for alphabetical order and the A–Z bar. */
        fun sortKey(c: ContactWithNumbers, order: SortOrder): String {
            val first = c.contact.firstName.trim()
            val last = c.contact.lastName.trim()
            val key = when (order) {
                SortOrder.LastName -> listOf(last, first).filter { it.isNotEmpty() }.joinToString(" ")
                else -> listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
            }
            return key.ifEmpty { c.numbers.firstOrNull()?.number ?: "" }.lowercase()
        }
    }
}
