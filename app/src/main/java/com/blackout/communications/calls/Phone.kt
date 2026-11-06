package com.blackout.communications.calls

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Small helpers for placing calls and matching phone numbers. */
object Phone {

    fun isDefaultPhoneApp(context: Context): Boolean {
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_DIALER) && roles.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    fun requestPhoneRoleIntent(context: Context): Intent? =
        context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_DIALER)

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Calls a number. Uses Communications itself once it is the phone app, otherwise opens the current phone app. */
    fun call(context: Context, number: String) {
        if (number.isBlank()) return
        place(context, Uri.fromParts("tel", number, null))
    }

    fun callVoicemail(context: Context) = place(context, Uri.fromParts("voicemail", "", null))

    private fun place(context: Context, uri: Uri) {
        if (isDefaultPhoneApp(context) && has(context, Manifest.permission.CALL_PHONE)) {
            try {
                context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
                return
            } catch (e: SecurityException) {
                // Fall through to the dial screen below.
            }
        }
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Can't place calls on this device", Toast.LENGTH_SHORT).show()
        }
    }

    /** Last 10 digits, so "+1 (480) 555-0188" and "4805550188" match. */
    fun matchKey(number: String?): String {
        val digits = number.orEmpty().filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /** Country name for international numbers (those starting with + other than +1). Worked out on the phone. */
    fun country(number: String?): String? {
        val n = number?.trim() ?: return null
        val digits = when {
            n.startsWith("+") -> n.drop(1).filter { it.isDigit() }
            n.startsWith("011") -> n.drop(3).filter { it.isDigit() }
            else -> return null
        }
        if (digits.startsWith("1")) return null
        for (len in 3 downTo 1) {
            COUNTRY_CODES[digits.take(len)]?.let { return it }
        }
        return null
    }

    private val COUNTRY_CODES = mapOf(
        "7" to "Russia", "20" to "Egypt", "27" to "South Africa", "30" to "Greece", "31" to "Netherlands",
        "32" to "Belgium", "33" to "France", "34" to "Spain", "36" to "Hungary", "39" to "Italy",
        "40" to "Romania", "41" to "Switzerland", "43" to "Austria", "44" to "United Kingdom", "45" to "Denmark",
        "46" to "Sweden", "47" to "Norway", "48" to "Poland", "49" to "Germany", "51" to "Peru",
        "52" to "Mexico", "53" to "Cuba", "54" to "Argentina", "55" to "Brazil", "56" to "Chile",
        "57" to "Colombia", "58" to "Venezuela", "60" to "Malaysia", "61" to "Australia", "62" to "Indonesia",
        "63" to "Philippines", "64" to "New Zealand", "65" to "Singapore", "66" to "Thailand", "81" to "Japan",
        "82" to "South Korea", "84" to "Vietnam", "86" to "China", "90" to "Turkey", "91" to "India",
        "92" to "Pakistan", "93" to "Afghanistan", "94" to "Sri Lanka", "95" to "Myanmar", "98" to "Iran",
        "211" to "South Sudan", "212" to "Morocco", "213" to "Algeria", "216" to "Tunisia", "218" to "Libya",
        "220" to "Gambia", "221" to "Senegal", "233" to "Ghana", "234" to "Nigeria", "237" to "Cameroon",
        "249" to "Sudan", "251" to "Ethiopia", "254" to "Kenya", "255" to "Tanzania", "256" to "Uganda",
        "260" to "Zambia", "263" to "Zimbabwe", "351" to "Portugal", "353" to "Ireland", "354" to "Iceland",
        "355" to "Albania", "357" to "Cyprus", "358" to "Finland", "359" to "Bulgaria", "370" to "Lithuania",
        "371" to "Latvia", "372" to "Estonia", "373" to "Moldova", "374" to "Armenia", "375" to "Belarus",
        "380" to "Ukraine", "381" to "Serbia", "382" to "Montenegro", "383" to "Kosovo", "385" to "Croatia",
        "386" to "Slovenia", "387" to "Bosnia and Herzegovina", "389" to "North Macedonia", "420" to "Czechia",
        "421" to "Slovakia", "502" to "Guatemala", "503" to "El Salvador", "504" to "Honduras", "505" to "Nicaragua",
        "506" to "Costa Rica", "507" to "Panama", "509" to "Haiti", "591" to "Bolivia", "593" to "Ecuador",
        "595" to "Paraguay", "598" to "Uruguay", "852" to "Hong Kong", "855" to "Cambodia", "880" to "Bangladesh",
        "886" to "Taiwan", "960" to "Maldives", "961" to "Lebanon", "962" to "Jordan", "963" to "Syria",
        "964" to "Iraq", "965" to "Kuwait", "966" to "Saudi Arabia", "967" to "Yemen", "968" to "Oman",
        "970" to "Palestine", "971" to "United Arab Emirates", "972" to "Israel", "973" to "Bahrain",
        "974" to "Qatar", "976" to "Mongolia", "977" to "Nepal", "992" to "Tajikistan", "993" to "Turkmenistan",
        "994" to "Azerbaijan", "995" to "Georgia", "996" to "Kyrgyzstan", "998" to "Uzbekistan",
    )
}
