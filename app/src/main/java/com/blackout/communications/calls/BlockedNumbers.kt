package com.blackout.communications.calls

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import com.blackout.communications.CommunicationsApp
import android.provider.BlockedNumberContract.BlockedNumbers as Table

/**
 * The phone's own blocked list. Android stops calls and texts from these numbers
 * before they reach any app. Only the phone app or texting app may change it.
 */
object BlockedNumbers {

    fun canUse(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false) &&
            (Phone.isDefaultPhoneApp(context) || android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName)

    fun list(context: Context): List<Pair<Long, String>> = runCatching {
        context.contentResolver.query(
            Table.CONTENT_URI,
            arrayOf(Table.COLUMN_ID, Table.COLUMN_ORIGINAL_NUMBER),
            null, null, Table.COLUMN_ORIGINAL_NUMBER,
        )?.use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Numbers you blocked on purpose, here. "Never block my contacts" leaves these alone. */
    private fun manual(context: Context) = context.getSharedPreferences("manual_blocks", Context.MODE_PRIVATE)

    fun block(context: Context, number: String): Boolean = runCatching {
        context.contentResolver.insert(
            Table.CONTENT_URI,
            ContentValues().apply { put(Table.COLUMN_ORIGINAL_NUMBER, number) },
        ) != null
    }.getOrDefault(false).also { if (it) manual(context).edit().putBoolean(Phone.matchKey(number), true).apply() }

    fun unblock(context: Context, number: String): Boolean = runCatching {
        BlockedNumberContract.unblock(context, number) > 0
    }.getOrDefault(false).also { if (it) manual(context).edit().remove(Phone.matchKey(number)).apply() }

    /**
     * "Never block my contacts": takes contacts off Android's blocked list, unless you blocked
     * them here on purpose. Blocks that already existed before this feature count as on purpose.
     * Returns how many numbers were cleared.
     */
    suspend fun clearBlockedContacts(app: CommunicationsApp): Int {
        if (!app.callSettings.current.protectContacts || !canUse(app)) return 0
        val manual = manual(app)
        val blocked = list(app)
        if (!manual.getBoolean("seeded", false)) {
            manual.edit().apply { blocked.forEach { putBoolean(Phone.matchKey(it.second), true) }; putBoolean("seeded", true) }.apply()
            return 0
        }
        var cleared = 0
        for ((_, number) in blocked) {
            if (manual.getBoolean(Phone.matchKey(number), false)) continue
            if (app.contacts.findByNumber(number) != null && unblock(app, number)) cleared++
        }
        return cleared
    }

    fun isBlocked(context: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)
}
