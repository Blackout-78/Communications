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

/**
 * App icon color.
 * - Home-screen shortcut: drawn in exactly the color you pick, updated in place when you change it.
 * - App drawer icon: Android only allows ready-made icons, so it switches to the closest of 36 colors.
 */
object AppIcon {
    private const val SHORTCUT_ID = "colored_icon"
    const val DEFAULT_COLOR = 0xFF377DC0.toInt()

    fun presets(context: Context): IntArray =
        context.resources.getStringArray(R.array.icon_presets).map { android.graphics.Color.parseColor(it) }.toIntArray()

    /** Sets the app drawer icon to the preset closest to [color]. Returns the preset used. */
    fun applyDrawerIcon(context: Context, color: Int): Int {
        val presets = presets(context)
        val best = if (color == DEFAULT_COLOR) -1 else presets.indices.minBy { distance(presets[it], color) }
        val pm = context.packageManager
        val wanted = if (best < 0) ".Launcher" else ".IconC%02d".format(best)
        val names = listOf(".Launcher") + presets.indices.map { ".IconC%02d".format(it) }
        // Turn on the new one first so the app never has zero launcher icons.
        names.sortedBy { if (it == wanted) 0 else 1 }.forEach { name ->
            val state = if (name == wanted) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching {
                pm.setComponentEnabledSetting(ComponentName(context, context.packageName + name), state, PackageManager.DONT_KILL_APP)
            }
        }
        return if (best < 0) DEFAULT_COLOR else presets[best]
    }

    /** Adds the home-screen shortcut in the exact color, or updates it if it's already there. */
    fun pinOrUpdateShortcut(context: Context, color: Int, ask: Boolean): Boolean {
        val info = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel("Communications")
            .setIcon(IconCompat.createWithAdaptiveBitmap(render(context, color)))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        val pinned = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).any { it.id == SHORTCUT_ID }
        }.getOrDefault(false)
        if (pinned) return runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(info)) }.getOrDefault(false)
        if (!ask || !ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** Draws the icon (white background, colored phone book) at adaptive-icon size. */
    fun render(context: Context, color: Int, size: Int = 432): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        listOf(R.drawable.icon_layer_body to color, R.drawable.icon_layer_window to android.graphics.Color.WHITE, R.drawable.icon_layer_handset to color)
            .forEach { (res, tint) ->
                ContextCompat.getDrawable(context, res)?.mutate()?.apply {
                    setBounds(0, 0, size, size)
                    setTint(tint)
                    draw(canvas)
                }
            }
        return bmp
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr * 2 + dg * dg * 4 + db * db * 3
    }
}
