package com.blackout.communications

import android.app.Application
import com.blackout.communications.data.AppDatabase
import com.blackout.communications.data.ContactRepository
import com.blackout.communications.data.FileStore

class CommunicationsApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val fileStore: FileStore by lazy { FileStore(this) }
    val contacts: ContactRepository by lazy { ContactRepository(database.contactDao(), fileStore) }
}
