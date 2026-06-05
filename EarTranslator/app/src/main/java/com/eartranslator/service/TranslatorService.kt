package com.eartranslator.service

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eartranslator.R
import com.eartranslator.api.SupportedLanguages
import com.eartranslator.api.TranslationResult
import com.eartranslator.api.TranslatorApi
import com.eartranslator.ui.MainActivity
import com.eartranslator.utils.SpeechManager
import com.eartranslator.utils.TtsManager
import kotlinx.coroutines.*

/**
 * Service chạy nền — trái tim của ứng dụng
 * Quản lý toàn bộ luồng: Nghe → Nhận dạng → Dịch → Đọc
 */
class TranslatorService : Service() {

    companion object {
        const val CHANNEL_ID = "ear_translator_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
    }

    // Binder cho MainActivity giao tiếp với Service
    inner class TranslatorBinder : Binder() {
        fun getService(): TranslatorService = this@TranslatorService
    }

    private val binder = TranslatorBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var speechManager: SpeechManager? = null
    private var ttsManager: TtsManager? = null
    private var mediaProjection: MediaProjection? = null

    // State
    var isTranslating = false
        private set
    var sourceLangCode = "en"
    var targetLangCode = "vi"
    var autoSpeak = true
    var ttsSpeed = 1.0f

    // Callbacks để cập nhật UI
    var onTranslationReady: ((original: String, translated: String) -> Unit)? = null
    var onListeningState: ((Boolean) -> Unit)? = null
    var onSpeakingState: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ttsManager = TtsManager(this) { speaking ->
            onSpeakingState?.invoke(speaking)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                startForeground(NOTIFICATION_ID, buildNotification("Đang khởi động..."))
                setupMediaProjection(resultCode, data)
            }
            ACTION_STOP -> stopTranslation()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ─── Khởi tạo MediaProjection ───────────────────────────────────────────

    private fun setupMediaProjection(resultCode: Int, data: Intent?) {
        if (resultCode == -1 || data == null) {
            // Không có MediaProjection → chỉ dùng mic (vẫn hoạt động cho giọng nói trực tiếp)
            startRecognition()
            return
        }

        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            startRecognition()
        } catch (e: Exception) {
            startRecognition() // Fallback sang mic thường
        }
    }

    // ─── Bắt đầu nhận dạng giọng nói ───────────────────────────────────────

    private fun startRecognition() {
        isTranslating = true

        speechManager = SpeechManager(
            context = this,
            onTextRecognized = { text ->
                updateNotification("Nhận dạng: $text")
                handleRecognizedText(text)
            },
            onError = { error ->
                onError?.invoke(error)
            },
            onListeningStateChanged = { listening ->
                onListeningState?.invoke(listening)
                if (listening) updateNotification("Đang lắng nghe...")
            }
        )

        speechManager?.startListening(sourceLangCode)
        updateNotification("Đang lắng nghe...")
    }

    // ─── Xử lý văn bản đã nhận dạng → dịch ────────────────────────────────

    private fun handleRecognizedText(text: String) {
        serviceScope.launch {
            updateNotification("Đang dịch...")

            when (val result = TranslatorApi.translate(text, sourceLangCode, targetLangCode)) {
                is TranslationResult.Success -> {
                    val translated = result.translatedText

                    // Cập nhật UI
                    onTranslationReady?.invoke(text, translated)

                    // Phát âm thanh qua tai nghe
                    if (autoSpeak) {
                        ttsManager?.setSpeed(ttsSpeed)
                        ttsManager?.speak(translated, targetLangCode)
                    }

                    updateNotification("✓ $translated")
                }
                is TranslationResult.Error -> {
                    onError?.invoke(result.message)
                    updateNotification("Đang lắng nghe...")
                }
            }
        }
    }

    // ─── Điều khiển ─────────────────────────────────────────────────────────

    fun startTranslation() {
        if (!isTranslating) startRecognition()
    }

    fun stopTranslation() {
        isTranslating = false
        speechManager?.stopListening()
        speechManager = null
        ttsManager?.stop()
        onListeningState?.invoke(false)
        updateNotification("Đã dừng")
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    fun setSourceLanguage(code: String) {
        sourceLangCode = code
        if (isTranslating) {
            speechManager?.stopListening()
            speechManager?.startListening(code)
        }
    }

    fun speakLastTranslation(text: String) {
        ttsManager?.speak(text, targetLangCode)
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EarTranslator",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Dịch thuật đang chạy nền"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TranslatorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎧 EarTranslator")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Dừng", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechManager?.stopListening()
        ttsManager?.destroy()
        mediaProjection?.stop()
    }
}
