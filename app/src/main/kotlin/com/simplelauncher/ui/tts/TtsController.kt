package com.simplelauncher.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.simplelauncher.perf.PerformanceConfig
import java.util.Locale

class TtsController(private val context: Context) : TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var supported = true

    fun init() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            supported = false
            return
        }
        val tts = textToSpeech ?: return
        val result = tts.setLanguage(Locale.CHINA)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        supported = ready
    }

    fun isSupported(): Boolean = supported

    fun speak(text: String) {
        if (!supported || !ready) return
        if (!PerformanceConfig.ttsEnabled(context)) return
        val tts = textToSpeech ?: return
        tts.stop()
        @Suppress("DEPRECATION")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
