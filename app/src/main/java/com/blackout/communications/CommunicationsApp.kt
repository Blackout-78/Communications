package com.blackout.communications

import android.app.Application
import com.blackout.communications.chats.ChatPrefs
import com.blackout.communications.data.AppDatabase
import com.blackout.communications.data.Appearance
import com.blackout.communications.data.AppearanceSettings
import com.blackout.communications.backup.BackupReminder
import com.blackout.communications.ui.theme.ThemeState
import com.blackout.communications.data.CallSettings
import com.blackout.communications.data.ContactRepository
import com.blackout.communications.data.FileStore

class CommunicationsApp : Application() {
    val appearanceSettings: AppearanceSettings by lazy { AppearanceSettings(this) }

    override fun onCreate() {
        super.onCreate()
        ThemeState.appearance = appearanceSettings.load()
        BackupReminder.schedule(this)
    }

    /** Changes the look and saves it. */
    fun updateAppearance(change: (Appearance) -> Appearance) {
        val next = change(ThemeState.appearance)
        ThemeState.appearance = next
        appearanceSettings.save(next)
    }

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val fileStore: FileStore by lazy { FileStore(this) }
    val contacts: ContactRepository by lazy { ContactRepository(database.contactDao(), fileStore) }
    val callSettings: CallSettings by lazy { CallSettings(this) }
    val chatPrefs: ChatPrefs by lazy { ChatPrefs(this) }
}
