package com.simplelauncher.perf

import android.content.Context
import com.simplelauncher.BuildConfig

object PerformanceConfig {
    enum class PerfMode {
        LOW,
        HIGH,
        CUSTOM
    }

    interface Listener {
        fun onPerformanceChanged()
    }

    private val listeners = mutableSetOf<Listener>()

    private const val PREF_NAME = "launcher_perf"
    private const val KEY_MODE = "mode"
    private const val KEY_REDUCE_ANIMATION = "reduce_animation"
    private const val KEY_SIMPLE_BACKGROUND = "simple_background"
    private const val KEY_STRICT_REDRAW = "strict_redraw"
    private const val KEY_GLASSMORPHISM = "glassmorphism"
    private const val KEY_SPRING_ANIMATION = "spring_animation"
    private const val KEY_FOCUS_SCALE = "focus_scale"
    private const val KEY_ICON_SHADOW = "icon_shadow"
    private const val KEY_TTS = "tts_enabled"
    private const val KEY_WALLPAPER_ANIM = "wallpaper_anim"

    fun reduceAnimation(context: Context): Boolean {
        val defaultValue = BuildConfig.PERF_REDUCE_ANIMATION
        return prefs(context).getBoolean(KEY_REDUCE_ANIMATION, defaultValue)
    }

    fun simpleBackground(context: Context): Boolean {
        val defaultValue = BuildConfig.PERF_SIMPLE_BACKGROUND
        return prefs(context).getBoolean(KEY_SIMPLE_BACKGROUND, defaultValue)
    }

    fun strictRedraw(context: Context): Boolean {
        val defaultValue = BuildConfig.PERF_STRICT_REDRAW
        return prefs(context).getBoolean(KEY_STRICT_REDRAW, defaultValue)
    }

    fun glassmorphism(context: Context): Boolean {
        val defaultValue = BuildConfig.PERF_GLASSMORPHISM
        return prefs(context).getBoolean(KEY_GLASSMORPHISM, defaultValue)
    }

    fun springAnimation(context: Context): Boolean {
        val defaultValue = BuildConfig.PERF_SPRING_ANIMATION
        val reduced = reduceAnimation(context)
        return if (reduced) false else prefs(context).getBoolean(KEY_SPRING_ANIMATION, defaultValue)
    }

    fun focusScale(context: Context): Boolean {
        val defaultValue = BuildConfig.PERF_FOCUS_SCALE
        val reduced = reduceAnimation(context)
        return if (reduced) false else prefs(context).getBoolean(KEY_FOCUS_SCALE, defaultValue)
    }

    fun iconShadow(context: Context): Boolean {
        val reduced = reduceAnimation(context)
        return if (reduced) false else prefs(context).getBoolean(KEY_ICON_SHADOW, true)
    }

    fun ttsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TTS, true)
    }

    fun wallpaperAnimation(context: Context): Boolean {
        val reduced = reduceAnimation(context)
        return if (reduced) false else prefs(context).getBoolean(KEY_WALLPAPER_ANIM, true)
    }

    fun mode(context: Context): PerfMode {
        val raw = prefs(context).getString(KEY_MODE, PerfMode.LOW.name) ?: PerfMode.LOW.name
        return try {
            PerfMode.valueOf(raw)
        } catch (_: Throwable) {
            PerfMode.LOW
        }
    }

    fun setReduceAnimation(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REDUCE_ANIMATION, enabled).apply()
        notifyChanged()
    }

    fun setSimpleBackground(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SIMPLE_BACKGROUND, enabled).apply()
        notifyChanged()
    }

    fun setStrictRedraw(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRICT_REDRAW, enabled).apply()
        notifyChanged()
    }

    fun setGlassmorphism(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLASSMORPHISM, enabled).apply()
        notifyChanged()
    }

    fun setSpringAnimation(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPRING_ANIMATION, enabled).apply()
        notifyChanged()
    }

    fun setFocusScale(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOCUS_SCALE, enabled).apply()
        notifyChanged()
    }

    fun setIconShadow(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ICON_SHADOW, enabled).apply()
        notifyChanged()
    }

    fun setTtsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TTS, enabled).apply()
        notifyChanged()
    }

    fun setWallpaperAnimation(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WALLPAPER_ANIM, enabled).apply()
        notifyChanged()
    }

    fun enableLowEndMode(context: Context, enabled: Boolean) {
        val editor = prefs(context).edit()
        if (enabled) {
            editor.putBoolean(KEY_REDUCE_ANIMATION, true)
                .putBoolean(KEY_SIMPLE_BACKGROUND, true)
                .putBoolean(KEY_GLASSMORPHISM, false)
                .putBoolean(KEY_SPRING_ANIMATION, false)
                .putBoolean(KEY_FOCUS_SCALE, false)
                .putBoolean(KEY_ICON_SHADOW, false)
                .putBoolean(KEY_TTS, false)
                .putBoolean(KEY_WALLPAPER_ANIM, false)
                .putBoolean(KEY_STRICT_REDRAW, true)
                .putString(KEY_MODE, PerfMode.HIGH.name)
        } else {
            editor.putBoolean(KEY_REDUCE_ANIMATION, false)
                .putBoolean(KEY_SIMPLE_BACKGROUND, false)
                .putBoolean(KEY_GLASSMORPHISM, true)
                .putBoolean(KEY_SPRING_ANIMATION, true)
                .putBoolean(KEY_FOCUS_SCALE, true)
                .putBoolean(KEY_ICON_SHADOW, true)
                .putBoolean(KEY_TTS, true)
                .putBoolean(KEY_WALLPAPER_ANIM, true)
                .putBoolean(KEY_STRICT_REDRAW, true)
                .putString(KEY_MODE, PerfMode.LOW.name)
        }
        editor.apply()
        notifyChanged()
    }

    fun isLowEndMode(context: Context): Boolean {
        return mode(context) == PerfMode.HIGH
    }

    fun setMode(context: Context, mode: PerfMode) {
        when (mode) {
            PerfMode.LOW -> enableLowEndMode(context, false)
            PerfMode.HIGH -> enableLowEndMode(context, true)
            PerfMode.CUSTOM -> {
                prefs(context).edit().putString(KEY_MODE, PerfMode.CUSTOM.name).apply()
                notifyChanged()
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onPerformanceChanged() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
