package com.anotherlauncher.data

import android.content.Intent
import android.provider.Settings
import java.text.Normalizer
import java.util.Locale

data class SettingShortcut(val title: String, val action: String, val keywords: List<String>) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return false
        val t = title.lowercase(Locale.ROOT)
        if (t.contains(q) || q.contains(t)) return true
        val queryWords = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return keywords.any { kw ->
            val k = kw.lowercase(Locale.ROOT)
            k.startsWith(q) || q.startsWith(k) || queryWords.any { w -> k.startsWith(w) || w.startsWith(k) }
        }
    }

    fun intent(): Intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object AppSearch {
    private val marks = "\\p{M}+".toRegex()
    private val whitespace = "\\s+".toRegex()
    private val camelOrWord = Regex("(?=[A-Z])|[^\\p{L}\\p{N}]+").toPattern()
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(marks, "").lowercase(Locale.ROOT).trim()

    /**
     * Stable ranking:
     * 0 = exact name
     * 1 = name prefix
     * 2 = word prefixes
     * 3 = name substring
     * 4 = initials / acronym prefix (e.g. "yt" -> "YouTube", "ps" -> "Play Store")
     * 5 = ordered subsequence (e.g. "spfy" -> "Spotify")
     * 6 = package match
     */
    fun rank(label: String, packageName: String, query: String): Int? {
        val term = normalize(query)
        if (term.isBlank()) return 0
        val name = normalize(label)
        val words = name.split(whitespace).filter { it.isNotEmpty() }
        val tokens = term.split(whitespace).filter { it.isNotEmpty() }
        // Split on both whitespace and CamelCase boundaries to extract natural initials (e.g. YouTube -> Y, T)
        val camelWords = label.split(Regex("(?<=[a-z])(?=[A-Z])|[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val initials = normalize(camelWords.mapNotNull { it.firstOrNull() }.joinToString(""))
        return when {
            name == term -> 0
            name.startsWith(term) -> 1
            tokens.isNotEmpty() && tokens.all { token -> words.any { it.startsWith(token) } } -> 2
            tokens.isNotEmpty() && tokens.all(name::contains) -> 3
            initials.isNotEmpty() && initials.startsWith(term) -> 4
            isSubsequence(term, name) -> 5
            term.length >= 3 && normalize(packageName).contains(term) -> 6
            else -> null
        }
    }

    fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.length < 2) return false
        var i = 0
        for (c in haystack) {
            if (i < needle.length && c == needle[i]) i++
        }
        return i == needle.length
    }

    /** Evaluates simple arithmetic like "12*(3+4)/2" or "50*1.08". Returns null when the query isn't math. */
    fun evaluateMath(input: String): String? {
        val text = input.replace('×', '*').replace('÷', '/').replace(" ", "")
        if (text.length < 3 || !text.any { it in "+-*/^%" } || !text.all { it.isDigit() || it in "+-*/^%.()" }) return null
        if (Regex("""^\d+(-\d+)+$""").matches(text)) return null // Phone numbers and dates aren't calculations
        return runCatching {
            val parser = MathParser(text)
            val value = parser.expression()
            if (!parser.done || value.isNaN() || value.isInfinite()) null
            else if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
            else String.format(Locale.ROOT, "%.6f", value).trimEnd('0').trimEnd('.')
        }.getOrNull()
    }

    val settingShortcuts = listOf(
        SettingShortcut("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "internet", "network")),
        SettingShortcut("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "pair", "headphones")),
        SettingShortcut("Mobile network", Settings.ACTION_NETWORK_OPERATOR_SETTINGS, listOf("cellular", "mobile", "data", "sim")),
        SettingShortcut("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight")),
        SettingShortcut("Display & brightness", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark")),
        SettingShortcut("Sounds & vibration", Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration")),
        SettingShortcut("Notifications", if (android.os.Build.VERSION.SDK_INT >= 33) Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS, listOf("notification", "alerts")),
        SettingShortcut("Battery", Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "power", "charging")),
        SettingShortcut("Apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "uninstall")),
        SettingShortcut("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, listOf("default", "home app", "browser", "assistant")),
        SettingShortcut("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space")),
        SettingShortcut("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
        SettingShortcut("Security & privacy", Settings.ACTION_SECURITY_SETTINGS, listOf("security", "privacy", "lock", "password", "fingerprint")),
        SettingShortcut("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility")),
        SettingShortcut("Date & time", Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock")),
        SettingShortcut("Language & keyboard", Settings.ACTION_LOCALE_SETTINGS, listOf("language", "keyboard")),
        SettingShortcut("Do Not Disturb", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, listOf("dnd", "do not disturb", "focus")),
        SettingShortcut("NFC & payments", Settings.ACTION_NFC_SETTINGS, listOf("nfc", "pay", "wallet", "contactless")),
        SettingShortcut("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging")),
        SettingShortcut("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "phone", "software", "version")),
        SettingShortcut("Settings", Settings.ACTION_SETTINGS, listOf("settings", "preferences"))
    )

    fun findSettings(query: String): List<SettingShortcut> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return settingShortcuts.filter { it.matches(q) }
    }
}

internal class MathParser(private val text: String) {
    private var i = 0
    val done get() = i == text.length
    private fun peek() = text.getOrNull(i)

    fun expression(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') {
            val op = text[i++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = power()
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            val op = text[i++]
            val r = power()
            v = when (op) {
                '*' -> v * r
                '/' -> v / r
                else -> v % r
            }
        }
        return v
    }

    private fun power(): Double {
        val b = unary()
        return if (peek() == '^') {
            i++
            Math.pow(b, power())
        } else b
    }

    private fun unary(): Double {
        if (peek() == '-') {
            i++
            return -unary()
        }
        if (peek() == '+') {
            i++
            return unary()
        }
        if (peek() == '(') {
            i++
            val v = expression()
            require(peek() == ')')
            i++
            return v
        }
        val start = i
        while (peek()?.let { it.isDigit() || it == '.' } == true) i++
        require(start < i)
        return text.substring(start, i).toDouble()
    }
}
