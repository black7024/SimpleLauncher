package com.simplelauncher.ui.theme

import android.content.Context
import android.graphics.Color

object ThemeConfig {
    private const val PREF_NAME = "launcher_theme"
    private const val KEY_THEME_COLOR = "theme_color"

    private const val DEFAULT_THEME_COLOR = 0xFF7FA6FF.toInt()

    fun getThemeColor(context: Context): Int {
        return prefs(context).getInt(KEY_THEME_COLOR, DEFAULT_THEME_COLOR)
    }

    fun setThemeColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_THEME_COLOR, color).apply()
    }

    fun parseArgbHex(input: String): Int? {
        val clean = input.trim().removePrefix("#")
        if (clean.length != 8 && clean.length != 6) {
            return null
        }
        val normalized = if (clean.length == 6) "FF$clean" else clean
        return try {
            Color.parseColor("#$normalized")
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun asArgbHex(color: Int): String {
        val argb = color.toLong() and 0xFFFFFFFFL
        return String.format("#%08X", argb)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
