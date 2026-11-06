package com.blackout.communications.data

import android.content.Context
import org.json.JSONObject

enum class ThemeMode(val label: String) { Dark("Dark"), Black("Black"), Light("Light"), System("Follow phone") }
enum class TextSize(val label: String, val scale: Float) { Small("Small", 0.9f), Normal("Normal", 1f), Large("Large", 1.12f), ExtraLarge("Extra large", 1.25f) }
enum class StartTab(val label: String) { Contacts("Contacts"), Chats("Chats"), Calls("Calls"), LastUsed("Last used") }
enum class ColorSlot(val label: String) { Accent("Accent"), Background("Background"), Text("Text"), MyBubble("Your message bubbles"), TheirBubble("Their message bubbles") }

/** Custom colors as ARGB ints. Missing = use the theme's own color. */
data class Palette(val colors: Map<ColorSlot, Int> = emptyMap()) {
    fun with(slot: ColorSlot, argb: Int?) = Palette(if (argb == null) colors - slot else colors + (slot to argb))
}

data class Appearance(
    val theme: ThemeMode = ThemeMode.Dark,
    val sameForAllTabs: Boolean = true,
    /** Palette for the whole app, or per tab ("Contacts", "Chats", "Calls", "Settings") when sameForAllTabs is off. */
    val palette: Palette = Palette(),
    val tabPalettes: Map<String, Palette> = emptyMap(),
    val picturePerTab: Boolean = false,
    /** Background picture paths: key "all" or a tab name. */
    val pictures: Map<String, String> = emptyMap(),
    val dim: Float = 0.5f,
    val textSize: TextSize = TextSize.Normal,
    val startTab: StartTab = StartTab.Contacts,
    val lastTab: String = "Contacts",
    val iconColor: Int = 0xFF377DC0.toInt(),
) {
    fun paletteFor(tab: String): Palette = if (sameForAllTabs) palette else tabPalettes[tab] ?: palette
    fun pictureFor(tab: String): String? = if (picturePerTab) pictures[tab] else pictures["all"]
}

/** Look-and-feel settings, saved on the phone only. */
class AppearanceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(): Appearance = runCatching { decode(JSONObject(prefs.getString("json", "{}")!!)) }.getOrDefault(Appearance())

    fun save(a: Appearance) = prefs.edit().putString("json", encode(a).toString()).apply()

    private fun encodePalette(p: Palette) = JSONObject().apply { p.colors.forEach { (k, v) -> put(k.name, v) } }
    private fun decodePalette(o: JSONObject?) = Palette(
        o?.keys()?.asSequence()?.mapNotNull { k -> runCatching { ColorSlot.valueOf(k) to o.getInt(k) }.getOrNull() }?.toMap().orEmpty(),
    )

    private fun encode(a: Appearance) = JSONObject().apply {
        put("theme", a.theme.name)
        put("same", a.sameForAllTabs)
        put("palette", encodePalette(a.palette))
        put("tabPalettes", JSONObject().apply { a.tabPalettes.forEach { (k, v) -> put(k, encodePalette(v)) } })
        put("picturePerTab", a.picturePerTab)
        put("pictures", JSONObject().apply { a.pictures.forEach { (k, v) -> put(k, v) } })
        put("dim", a.dim.toDouble())
        put("textSize", a.textSize.name)
        put("startTab", a.startTab.name)
        put("lastTab", a.lastTab)
        put("iconColor", a.iconColor)
    }

    private fun decode(o: JSONObject): Appearance {
        val d = Appearance()
        val tabs = o.optJSONObject("tabPalettes")
        val pics = o.optJSONObject("pictures")
        return Appearance(
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(d.theme),
            sameForAllTabs = o.optBoolean("same", true),
            palette = decodePalette(o.optJSONObject("palette")),
            tabPalettes = tabs?.keys()?.asSequence()?.associateWith { decodePalette(tabs.optJSONObject(it)) }.orEmpty(),
            picturePerTab = o.optBoolean("picturePerTab", false),
            pictures = pics?.keys()?.asSequence()?.associateWith { pics.getString(it) }.orEmpty(),
            dim = o.optDouble("dim", 0.5).toFloat(),
            textSize = runCatching { TextSize.valueOf(o.getString("textSize")) }.getOrDefault(d.textSize),
            startTab = runCatching { StartTab.valueOf(o.getString("startTab")) }.getOrDefault(d.startTab),
            lastTab = o.optString("lastTab", "Contacts"),
            iconColor = o.optInt("iconColor", d.iconColor),
        )
    }
}
