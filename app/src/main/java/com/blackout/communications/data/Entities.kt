package com.blackout.communications.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** One person in the private contact list. Stored only inside this app. */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    /** Absolute path to a photo copied into the app's private folder, or null. */
    val photoPath: String? = null,
    val groupName: String = "",
    val favorite: Boolean = false,
    /** Absolute path to a song copied into the app's private ringtones folder, or null for the default ringtone. */
    val ringtonePath: String? = null,
    /** The song's original file name, shown on the profile. */
    val ringtoneName: String? = null,
    /** Dialpad key 2–9 that calls this contact when held, or null. */
    val speedDial: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class PhoneNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long = 0,
    /** Mobile, Home, Work or Other. */
    val label: String = "Mobile",
    val number: String = "",
    /** The main number is the one shown in the list and used by speed dial. */
    val isMain: Boolean = false,
)

data class ContactWithNumbers(
    @Embedded val contact: Contact,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val numbers: List<PhoneNumber>,
)

// Helpers kept outside the Room classes so the database only sees real columns.

val Contact.displayName: String
    get() = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")

val ContactWithNumbers.mainNumber: PhoneNumber?
    get() = numbers.firstOrNull { it.isMain } ?: numbers.firstOrNull()

/** The name to show, falling back to the main number for unnamed contacts. */
val ContactWithNumbers.title: String
    get() = contact.displayName.ifEmpty { mainNumber?.number ?: "" }
