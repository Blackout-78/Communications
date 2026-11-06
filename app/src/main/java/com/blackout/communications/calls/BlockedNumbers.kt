package com.blackout.communications.calls

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
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

    fun block(context: Context, number: String): Boolean = runCatching {
        context.contentResolver.insert(
            Table.CONTENT_URI,
            ContentValues().apply { put(Table.COLUMN_ORIGINAL_NUMBER, number) },
        ) != null
    }.getOrDefault(false)

    fun unblock(context: Context, number: String): Boolean = runCatching {
        BlockedNumberContract.unblock(context, number) > 0
    }.getOrDefault(false)

    fun isBlocked(context: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)
}
