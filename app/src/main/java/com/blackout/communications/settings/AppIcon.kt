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

/** The four icon drawings: the single app icon, and Contacts, Messages and Call Log for three-icon mode. */
enum class IconGlyph(val key: String, val label: String, val defaultAlias: String, val tab: String?) {
    Main("main", "Communications", ".Home", null),
    Contacts("contacts", "Contacts", ".OpenContacts", "Contacts"),
    Messages("messages", "Messages", ".OpenChats", "Chats"),
    Calls("calls", "Call Log", ".OpenCalls", "Calls"),
}

/**
 * App icons: one icon or three, each in a style (A, B or C) and two colors you pick.
 * - Home-screen shortcuts are drawn in exactly your colors.
 * - App drawer icons: Android only allows ready-made icons, so each switches to the closest of 357 designs.
 */
object AppIcon {
    const val DEFAULT_MAIN = 0xFF377DC0.toInt()
    const val DEFAULT_SECOND = 0xFFFFFFFF.toInt()
    val TAB_GLYPHS = listOf(IconGlyph.Contacts, IconGlyph.Messages, IconGlyph.Calls)

    private data class Preset(val glyph: String, val style: IconStyle, val main: Int, val second: Int, val alias: String)

    private var cache: List<Preset>? = null
    private fun presets(context: Context): List<Preset> = cache ?: context.resources.getStringArray(R.array.icon_presets).map { line ->
        val (g, s, m, c, alias) = line.split("|")
        Preset(g, IconStyle.valueOf(s), android.graphics.Color.parseColor(m), android.graphics.Color.parseColor(c), alias)
    }.also { cache = it }

    fun prefs(context: Context) = context.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    /** True when the user has chosen three icons (Contacts, Messages, Call Log). */
    fun threeIcons(context: Context) = prefs(context).getBoolean("three", false)

    /** Has the first-run question (one icon or three) been answered? */
    fun choiceMade(context: Context) = prefs(context).getBoolean("choiceMade", false)

    fun setThreeIcons(context: Context, three: Boolean, style: IconStyle, main: Int, second: Int) {
        prefs(context).edit().putBoolean("three", three).putBoolean("choiceMade", true).apply()
        applyDrawerIcons(context, style, main, second)
    }

    /** Shows the right icons in the app drawer, each as close as possible to your style and colors. */
    fun applyDrawerIcons(context: Context, style: IconStyle, main: Int, second: Int) {
        val three = threeIcons(context)
        val isDefault = style == IconStyle.A && main == DEFAULT_MAIN && second == DEFAULT_SECOND
        val pm = context.packageManager
        val p = prefs(context)
        IconGlyph.entries.forEach { g ->
            val active = if (three) g != IconGlyph.Main else g == IconGlyph.Main
            val wanted = when {
                !active -> null
                isDefault -> g.defaultAlias
                else -> presets(context).filter { it.glyph == g.key && it.style == style }
                    .minBy { distance(it.main, main) * 2 + distance(it.second, second) }.alias
            }
            // The main icon starts enabled; the others start disabled.
            val current = p.getString("alias_${g.key}", if (g == IconGlyph.Main) g.defaultAlias else null)
            if (wanted == current && (wanted == null || isEnabled(context, wanted, g))) return@forEach
            // Turn the new icon on before the old one off, so the app is never missing from the drawer.
            if (wanted != null) set(pm, context, wanted, true)
            if (current != null && current != wanted) set(pm, context, current, false)
            // Earlier versions only had the plain tab icons; turn those off when a styled one replaces them.
            if (current == null && g != IconGlyph.Main && wanted != g.defaultAlias) set(pm, context, g.defaultAlias, false)
            p.edit().putString("alias_${g.key}", wanted).apply()
        }
    }

    private fun set(pm: PackageManager, context: Context, alias: String, on: Boolean) = runCatching {
        pm.setComponentEnabledSetting(
            ComponentName(context, context.packageName + alias),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun isEnabled(context: Context, alias: String, g: IconGlyph): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, context.packageName + alias))
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (alias == IconGlyph.Main.defaultAlias && g == IconGlyph.Main && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    /** Adds a home-screen shortcut in your exact colors, or updates it if it's already there. */
    fun pinOrUpdateShortcut(context: Context, glyph: IconGlyph, style: IconStyle, main: Int, second: Int, ask: Boolean): Boolean {
        val intent = Intent(context, MainActivity::class.java).setAction(if (glyph.tab == null) Intent.ACTION_MAIN else MainActivity.ACTION_OPEN_TAB)
        glyph.tab?.let { intent.putExtra(MainActivity.EXTRA_TAB, it) }
        val info = ShortcutInfoCompat.Builder(context, "icon_${glyph.key}")
            .setShortLabel(glyph.label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(render(context, glyph, style, main, second)))
            .setIntent(intent)
            .build()
        val pinned = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).any { it.id == info.id }
        }.getOrDefault(false)
        if (pinned) return runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(info)) }.getOrDefault(false)
        if (!ask || !ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** Updates every home-screen shortcut you've already added, after a style or color change. */
    fun updatePinnedShortcuts(context: Context, style: IconStyle, main: Int, second: Int) {
        IconGlyph.entries.forEach { pinOrUpdateShortcut(context, it, style, main, second, ask = false) }
    }

    /** Draws an icon at adaptive-icon size (the launcher crops it to its own shape). */
    fun render(context: Context, glyph: IconGlyph, style: IconStyle, main: Int, second: Int, size: Int = 432): Bitmap {
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
        when (glyph) {
            IconGlyph.Main -> { layer(R.drawable.icon_layer_book, fg); layer(R.drawable.icon_layer_window, bg); layer(R.drawable.icon_layer_handset, fg) }
            IconGlyph.Contacts -> { layer(R.drawable.icon_layer_book, fg); layer(R.drawable.icon_layer_window, bg); layer(R.drawable.icon_layer_person, fg) }
            IconGlyph.Messages -> { layer(R.drawable.icon_layer_bubble, fg); layer(R.drawable.icon_layer_dots, bg) }
            IconGlyph.Calls -> layer(R.drawable.icon_layer_bighandset, fg)
        }
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
