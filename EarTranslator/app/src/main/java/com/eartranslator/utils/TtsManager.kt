package com.eartranslator.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Quản lý Text-to-Speech — đọc bản dịch qua tai nghe
 * Tự động phát qua thiết bị audio đang kết nối (tai nghe Bluetooth)
 */
class TtsManager(
    private val context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var pendingLang: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeakingChanged(true)
                    }
                    override fun onDone(utteranceId: String?) {
                        onSpeakingChanged(false)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onSpeakingChanged(false)
                    }
                })

                // Phát text đang chờ (nếu có)
                pendingText?.let { text ->
                    pendingLang?.let { lang ->
                        speak(text, lang)
                        pendingText = null
                        pendingLang = null
                    }
                }
            }
        }
    }

    /**
     * Đọc văn bản qua loa / tai nghe
     * @param text Văn bản cần đọc
     * @param langCode Mã ngôn ngữ (vd: "vi", "en")
     */
    fun speak(text: String, langCode: String = "vi") {
        if (!isReady) {
            // TTS chưa sẵn sàng, lưu lại để phát sau
            pendingText = text
            pendingLang = langCode
            return
        }

        val locale = codeToLocale(langCode)
        val result = tts?.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback sang tiếng Anh nếu ngôn ngữ không được hỗ trợ
            tts?.setLanguage(Locale.ENGLISH)
        }

        tts?.stop() // Dừng câu đang đọc
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH, // Không xếp hàng — đọc ngay
            null,
            "utterance_${System.currentTimeMillis()}"
        )
    }

    fun stop() {
        tts?.stop()
        onSpeakingChanged(false)
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun codeToLocale(code: String): Locale = when (code) {
        "vi" -> Locale("vi", "VN")
        "en" -> Locale.ENGLISH
        "zh" -> Locale.CHINESE
        "ja" -> Locale.JAPANESE
        "ko" -> Locale.KOREAN
        "fr" -> Locale.FRENCH
        "de" -> Locale.GERMAN
        "es" -> Locale("es", "ES")
        "th" -> Locale("th", "TH")
        "ru" -> Locale("ru", "RU")
        else -> Locale.ENGLISH
    }
}
