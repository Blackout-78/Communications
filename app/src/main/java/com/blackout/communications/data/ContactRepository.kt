package com.blackout.communications.data

import android.net.Uri
import com.blackout.communications.calls.Phone
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val dao: ContactDao,
    private val files: FileStore,
) {
    fun observeAll(): Flow<List<ContactWithNumbers>> = dao.observeAll()

    fun observe(id: Long): Flow<ContactWithNumbers?> = dao.observe(id)

    suspend fun get(id: Long): ContactWithNumbers? = dao.get(id)

    /** Finds the contact who owns a phone number, for caller ID and call history. */
    suspend fun findByNumber(number: String?): ContactWithNumbers? {
        val key = Phone.matchKey(number)
        if (key.length < 3) return null
        return dao.getAll().firstOrNull { c -> c.numbers.any { Phone.matchKey(it.number) == key } }
    }

    suspend fun save(contact: Contact, numbers: List<PhoneNumber>, previousPhoto: String?): Long {
        val id = dao.save(contact, numbers)
        if (previousPhoto != null && previousPhoto != contact.photoPath) files.deletePhoto(previousPhoto)
        return id
    }

    suspend fun delete(id: Long) {
        val existing = dao.get(id) ?: return
        dao.deleteContact(existing.contact)
        files.deletePhoto(existing.contact.photoPath)
        cleanUpRingtones()
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        val existing = dao.get(id) ?: return
        dao.updateContact(existing.contact.copy(favorite = favorite))
    }

    suspend fun setRingtone(id: Long, uri: Uri?) {
        val existing = dao.get(id) ?: return
        val saved = uri?.let { files.saveRingtone(it) }
        if (uri != null && saved == null) return
        dao.updateContact(existing.contact.copy(ringtonePath = saved?.first, ringtoneName = saved?.second))
        cleanUpRingtones()
    }

    /** key = 2..9, or null to remove the contact from speed dial. */
    suspend fun setSpeedDial(id: Long, key: Int?) {
        require(key == null || key in 2..9)
        dao.assignSpeedDial(id, key)
    }

    suspend fun savePhoto(uri: Uri): String? = files.savePhoto(uri)

    fun discardPhoto(path: String?) = files.deletePhoto(path)

    private suspend fun cleanUpRingtones() {
        files.deleteUnusedRingtones(dao.ringtonePathsInUse())
    }
}
