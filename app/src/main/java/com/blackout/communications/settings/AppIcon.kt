package com.blackout.communications.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.blackout.communications.MainActivity
import com.blackout.communications.R
import com.blackout.communications.data.IconStyle

/**
 * The app icon: a style (A, B or C) and two colors you pick.
 * - Home-screen shortcut: drawn in exactly your two colors, updated in place when you change them.
 * - App drawer icon: Android only allows ready-made icons, so it switches to the closest of 24 designs.
 */
object AppIcon {
    private const val SHORTCUT_ID = "colored_icon"
    const val DEFAULT_MAIN = 0xFF377DC0.toInt()
    const val DEFAULT_SECOND = 0xFFFFFFFF.toInt()

    private data class Preset(val style: IconStyle, val main: Int, val second: Int, val alias: String)

    private fun presets(context: Context): List<Preset> =
        context.resources.getStringArray(R.array.icon_presets).map { line ->
            val (s, m, c, alias) = line.split("|")
            Preset(IconStyle.valueOf(s), android.graphics.Color.parseColor(m), android.graphics.Color.parseColor(c), alias)
        }

    /**
     * Sets the app drawer icon to the closest ready-made design (357 to choose from).
     * Only the old and new icons are switched, so the change is quick.
     */
    private val TAB_ALIASES = listOf(".OpenContacts", ".OpenChats", ".OpenCalls")

    fun prefs(context: Context) = context.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    /** True when the user has chosen three icons (Contacts, Messages, Call Log). */
    fun threeIcons(context: Context) = prefs(context).getBoolean("three", false)

    /** Has the first-run question (one icon or three) been answered? */
    fun choiceMade(context: Context) = prefs(context).getBoolean("choiceMade", false)

    /** Switches between one icon and three icons in the app drawer. */
    fun setThreeIcons(context: Context, three: Boolean, style: IconStyle, main: Int, second: Int) {
        prefs(context).edit().putBoolean("three", three).putBoolean("choiceMade", true).apply()
        val pm = context.packageManager
        fun set(alias: String, on: Boolean) = runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(context, context.packageName + alias),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (three) {
            TAB_ALIASES.forEach { set(it, true) }
            set(prefs(context).getString("alias", ".Home")!!, false)
        } else {
            prefs(context).edit().putString("alias", "none").apply() // forces the single icon back on
            applyDrawerIcon(context, style, main, second)
            TAB_ALIASES.forEach { set(it, false) }
        }
    }

    fun applyDrawerIcon(context: Context, style: IconStyle, main: Int, second: Int) {
        if (threeIcons(context)) return
        val isDefault = style == IconStyle.A && main == DEFAULT_MAIN && second == DEFAULT_SECOND
        val wanted = if (isDefault) ".Home" else presets(context).filter { it.style == style }
            .minBy { distance(it.main, main) * 2 + distance(it.second, second) }.alias
        val prefs = prefs(context)
        val current = prefs.getString("alias", ".Home")!!
        if (current == wanted && isEnabled(context, wanted)) return
        val pm = context.packageManager
        // Turn on the new one first so the app never has zero launcher icons.
        runCatching { pm.setComponentEnabledSetting(ComponentName(context, context.packageName + wanted), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP) }
        if (current != wanted && current != "none") {
            runCatching { pm.setComponentEnabledSetting(ComponentName(context, context.packageName + current), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
        }
        prefs.edit().putString("alias", wanted).apply()
    }

    private fun isEnabled(context: Context, alias: String): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, context.packageName + alias))
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (alias == ".Home" && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    /** Adds the home-screen shortcut in your exact colors, or updates it if it's already there. */
    fun pinOrUpdateShortcut(context: Context, style: IconStyle, main: Int, second: Int, ask: Boolean): Boolean {
        val info = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel("Communications")
            .setIcon(IconCompat.createWithAdaptiveBitmap(render(context, style, main, second)))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        val pinned = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).any { it.id == SHORTCUT_ID }
        }.getOrDefault(false)
        if (pinned) return runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(info)) }.getOrDefault(false)
        if (!ask || !ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** Draws the icon at adaptive-icon size (the launcher crops it to its own shape). */
    fun render(context: Context, style: IconStyle, main: Int, second: Int, size: Int = 432): Bitmap {
        val bg = if (style == IconStyle.A) second else main
        val fg = if (style == IconStyle.A) main else second
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)
        fun layer(res: Int, tint: Int) {
            ContextCompat.getDrawable(context, res)?.mutate()?.apply {
                setBounds(0, 0, size, size); setTint(tint); draw(canvas)
            }
        }
        if (style == IconStyle.C) {
            canvas.save()
            canvas.scale(1.35f, 1.35f, size / 2f, size / 2f)
        } else {
            layer(R.drawable.icon_layer_ring, fg)
        }
        layer(R.drawable.icon_layer_book, fg)
        layer(R.drawable.icon_layer_window, bg)
        layer(R.drawable.icon_layer_handset, fg)
        if (style == IconStyle.C) canvas.restore()
        return bmp
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr * 2 + dg * dg * 4 + db * db * 3
    }
}
