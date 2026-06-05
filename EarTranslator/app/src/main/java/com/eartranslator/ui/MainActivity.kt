package com.eartranslator.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.eartranslator.R
import com.eartranslator.api.SupportedLanguages
import com.eartranslator.databinding.ActivityMainBinding
import com.eartranslator.service.TranslatorService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var translatorService: TranslatorService? = null
    private var isBound = false
    private var lastTranslatedText = ""

    // Danh sách ngôn ngữ nguồn (không bao gồm tiếng Việt vì đó là đích)
    private val sourceLangs = SupportedLanguages.list.filter { it.code != "vi" }
    private var selectedSourceIndex = 0 // English mặc định

    // ─── Xin quyền ──────────────────────────────────────────────────────────

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            requestMediaProjection()
        } else {
            showToast("Cần cấp quyền micro để sử dụng!")
        }
    }

    private val requestMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        startTranslatorService(result.resultCode, result.data)
    }

    // ─── Service Connection ──────────────────────────────────────────────────

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TranslatorService.TranslatorBinder
            translatorService = binder.getService()
            isBound = true
            setupServiceCallbacks()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            translatorService = null
            isBound = false
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageSelector()
        setupButtons()
        setupSpeedSlider()
        updateSourceLangDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    // ─── Setup UI ────────────────────────────────────────────────────────────

    private fun setupLanguageSelector() {
        // Nút chọn ngôn ngữ nguồn (vuốt trái/phải hoặc nhấn)
        binding.btnPrevLang.setOnClickListener {
            selectedSourceIndex = (selectedSourceIndex - 1 + sourceLangs.size) % sourceLangs.size
            updateSourceLangDisplay()
            applySourceLangChange()
        }

        binding.btnNextLang.setOnClickListener {
            selectedSourceIndex = (selectedSourceIndex + 1) % sourceLangs.size
            updateSourceLangDisplay()
            applySourceLangChange()
        }
    }

    private fun setupButtons() {
        // Nút bật/tắt dịch
        binding.btnToggle.setOnClickListener {
            if (translatorService?.isTranslating == true) {
                stopTranslation()
            } else {
                checkPermissionsAndStart()
            }
        }

        // Nút đọc lại bản dịch cuối
        binding.btnRepeat.setOnClickListener {
            if (lastTranslatedText.isNotEmpty()) {
                translatorService?.speakLastTranslation(lastTranslatedText)
            } else {
                showToast("Chưa có bản dịch nào")
            }
        }

        // Toggle tự động đọc
        binding.switchAutoSpeak.setOnCheckedChangeListener { _, isChecked ->
            translatorService?.autoSpeak = isChecked
        }
    }

    private fun setupSpeedSlider() {
        binding.sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                translatorService?.ttsSpeed = value
                binding.tvSpeedValue.text = "${String.format("%.1f", value)}x"
            }
        }
    }

    private fun updateSourceLangDisplay() {
        val lang = sourceLangs[selectedSourceIndex]
        binding.tvSourceLang.text = "${lang.flag} ${lang.name}"
    }

    private fun applySourceLangChange() {
        translatorService?.setSourceLanguage(sourceLangs[selectedSourceIndex].code)
    }

    // ─── Callbacks từ Service → cập nhật UI ─────────────────────────────────

    private fun setupServiceCallbacks() {
        val service = translatorService ?: return

        service.onTranslationReady = { original, translated ->
            runOnUiThread {
                lastTranslatedText = translated
                binding.tvOriginalText.text = original
                binding.tvTranslatedText.text = translated
                addToHistory(original, translated)
            }
        }

        service.onListeningState = { listening ->
            runOnUiThread {
                binding.tvStatus.text = if (listening) "🎤 Đang lắng nghe..." else "⏳ Đang xử lý..."
                binding.btnToggle.text = if (service.isTranslating) "⏹ Dừng dịch" else "▶ Bắt đầu dịch"
                binding.micWave.isActivated = listening
            }
        }

        service.onSpeakingState = { speaking ->
            runOnUiThread {
                binding.tvStatus.text = if (speaking) "🔊 Đang đọc bản dịch..." else "🎤 Đang lắng nghe..."
            }
        }

        service.onError = { error ->
            runOnUiThread {
                if (!error.contains("no_match") && !error.contains("timeout")) {
                    binding.tvStatus.text = "⚠ $error"
                }
            }
        }
    }

    // ─── Quyền & Service ─────────────────────────────────────────────────────

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            requestMediaProjection()
        } else {
            requestPermissions.launch(notGranted.toTypedArray())
        }
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestMediaProjection.launch(manager.createScreenCaptureIntent())
    }

    private fun startTranslatorService(resultCode: Int, data: Intent?) {
        val intent = Intent(this, TranslatorService::class.java).apply {
            action = TranslatorService.ACTION_START
            putExtra(TranslatorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(TranslatorService.EXTRA_DATA, data)
        }
        startForegroundService(intent)

        // Bind để giao tiếp 2 chiều
        bindService(
            Intent(this, TranslatorService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        binding.btnToggle.text = "⏹ Dừng dịch"
        binding.tvStatus.text = "🎤 Đang lắng nghe..."
    }

    private fun stopTranslation() {
        translatorService?.stopTranslation()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        binding.btnToggle.text = "▶ Bắt đầu dịch"
        binding.tvStatus.text = "Nhấn bắt đầu để dịch"
    }

    // ─── Lịch sử dịch ───────────────────────────────────────────────────────

    private val historyList = mutableListOf<Pair<String, String>>()

    private fun addToHistory(original: String, translated: String) {
        historyList.add(0, Pair(original, translated))
        if (historyList.size > 20) historyList.removeLast()

        // Cập nhật view lịch sử (đơn giản, dùng TextView)
        val historyText = historyList.take(5).joinToString("\n\n") { (orig, trans) ->
            "• $orig\n  → $trans"
        }
        binding.tvHistory.text = historyText
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
