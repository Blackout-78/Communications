package com.blackout.communications.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ContactDao {

    @Transaction
    @Query("SELECT * FROM contacts")
    abstract fun observeAll(): Flow<List<ContactWithNumbers>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id")
    abstract fun observe(id: Long): Flow<ContactWithNumbers?>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id")
    abstract suspend fun get(id: Long): ContactWithNumbers?

    @Transaction
    @Query("SELECT * FROM contacts")
    abstract suspend fun getAll(): List<ContactWithNumbers>

    @Insert
    abstract suspend fun insertContact(contact: Contact): Long

    @Update
    abstract suspend fun updateContact(contact: Contact)

    @Delete
    abstract suspend fun deleteContact(contact: Contact)

    @Insert
    abstract suspend fun insertNumbers(numbers: List<PhoneNumber>)

    @Query("DELETE FROM phone_numbers WHERE contactId = :contactId")
    abstract suspend fun deleteNumbers(contactId: Long)

    @Query("UPDATE contacts SET speedDial = NULL WHERE speedDial = :key")
    abstract suspend fun clearSpeedDialKey(key: Int)

    @Query("UPDATE contacts SET speedDial = :key WHERE id = :contactId")
    abstract suspend fun setSpeedDialKey(contactId: Long, key: Int?)

    @Query("SELECT ringtonePath FROM contacts WHERE ringtonePath IS NOT NULL")
    abstract suspend fun ringtonePathsInUse(): List<String>

    /** Inserts or updates a contact together with its phone numbers, as one step. Returns the contact's id. */
    @Transaction
    open suspend fun save(contact: Contact, numbers: List<PhoneNumber>): Long {
        val id = if (contact.id == 0L) {
            insertContact(contact)
        } else {
            updateContact(contact)
            deleteNumbers(contact.id)
            contact.id
        }
        val cleaned = numbers.filter { it.number.isNotBlank() }
        val hasMain = cleaned.any { it.isMain }
        insertNumbers(
            cleaned.mapIndexed { index, n ->
                n.copy(id = 0, contactId = id, isMain = if (hasMain) n.isMain else index == 0)
            },
        )
        return id
    }

    /** Puts a contact on a speed dial key. Whoever had that key before loses it. */
    @Transaction
    open suspend fun assignSpeedDial(contactId: Long, key: Int?) {
        if (key != null) clearSpeedDialKey(key)
        setSpeedDialKey(contactId, key)
    }
}
