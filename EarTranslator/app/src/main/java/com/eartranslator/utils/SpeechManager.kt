package com.eartranslator.utils

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Quản lý nhận dạng giọng nói từ âm thanh hệ thống
 * Dùng Android SpeechRecognizer (miễn phí, tích hợp sẵn)
 */
class SpeechManager(
    private val context: Context,
    private val onTextRecognized: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var sourceLanguage = "en-US"
    private var shouldContinue = false

    /**
     * Bắt đầu lắng nghe liên tục
     * @param langCode Mã ngôn ngữ nguồn (vd: "en", "zh", "ja")
     */
    fun startListening(langCode: String = "en") {
        shouldContinue = true
        sourceLanguage = langToLocale(langCode)
        createRecognizer()
        startSingleRecognition()
    }

    fun stopListening() {
        shouldContinue = false
        isListening = false
        onListeningStateChanged(false)
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListeningStateChanged(true)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                if (text.isNotEmpty()) {
                    onTextRecognized(text)
                }

                // Tự động tiếp tục lắng nghe
                if (shouldContinue) {
                    handler.postDelayed({ startSingleRecognition() }, 300)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Không dùng partial — đợi kết quả cuối cùng cho chính xác hơn
            }

            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "no_match" // Không nghe thấy gì — bình thường
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "Lỗi mạng, kiểm tra kết nối"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                    else -> "Lỗi $error"
                }

                // Lỗi nhỏ (no_match, timeout) → tự restart, không thông báo
                if (msg == "no_match" || msg == "timeout" || msg == "busy") {
                    if (shouldContinue) {
                        handler.postDelayed({ startSingleRecognition() }, 500)
                    }
                } else {
                    onError(msg)
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startSingleRecognition() {
        if (!shouldContinue) return

        // Tạo recognizer mới mỗi lần (tránh lỗi busy)
        createRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sourceLanguage)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        handler.post {
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                if (shouldContinue) {
                    handler.postDelayed({ startSingleRecognition() }, 1000)
                }
            }
        }
    }

    // Chuyển mã ngôn ngữ ngắn sang Locale đầy đủ
    private fun langToLocale(code: String): String = when (code) {
        "vi" -> "vi-VN"
        "en" -> "en-US"
        "zh" -> "zh-CN"
        "ja" -> "ja-JP"
        "ko" -> "ko-KR"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "es" -> "es-ES"
        "th" -> "th-TH"
        "ru" -> "ru-RU"
        else -> "$code-${code.uppercase()}"
    }
}
