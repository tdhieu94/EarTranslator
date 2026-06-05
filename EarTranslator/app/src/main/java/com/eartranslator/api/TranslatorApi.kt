package com.eartranslator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * MyMemory Translation API — hoàn toàn miễn phí, không cần API key
 * Giới hạn: 1000 request/ngày (đủ dùng bình thường)
 * Tài liệu: https://mymemory.translated.net/doc/spec.php
 */
object TranslatorApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Dịch văn bản
     * @param text Văn bản cần dịch
     * @param sourceLang Ngôn ngữ gốc (vd: "en", "zh", "ja") — "auto" để tự nhận diện
     * @param targetLang Ngôn ngữ đích (vd: "vi")
     * @return Bản dịch hoặc null nếu lỗi
     */
    suspend fun translate(
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "vi"
    ): TranslationResult = withContext(Dispatchers.IO) {

        // Bỏ qua văn bản quá ngắn (nhiễu, âm thanh không rõ)
        if (text.trim().length < 3) {
            return@withContext TranslationResult.Error("Văn bản quá ngắn")
        }

        try {
            val encodedText = URLEncoder.encode(text.trim(), "UTF-8")
            val langPair = if (sourceLang == "auto") "en|$targetLang" else "$sourceLang|$targetLang"
            val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$langPair"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "EarTranslator/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext TranslationResult.Error("Lỗi mạng: ${response.code}")
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)

            val responseStatus = json.optInt("responseStatus", 0)
            if (responseStatus != 200) {
                return@withContext TranslationResult.Error("API lỗi: $responseStatus")
            }

            val responseData = json.getJSONObject("responseData")
            val translated = responseData.getString("translatedText")

            // Phát hiện ngôn ngữ gốc từ response
            val detectedLang = json.optJSONArray("matches")
                ?.optJSONObject(0)
                ?.optString("source-segment-attributes")
                ?.let { parseLang(it) } ?: sourceLang

            TranslationResult.Success(
                originalText = text,
                translatedText = translated,
                detectedSourceLang = detectedLang,
                targetLang = targetLang
            )

        } catch (e: Exception) {
            TranslationResult.Error("Lỗi: ${e.message}")
        }
    }

    private fun parseLang(attr: String): String {
        // Trích xuất mã ngôn ngữ từ attributes
        return try {
            attr.substringAfter("lang\":\"").substringBefore("\"")
        } catch (e: Exception) {
            "en"
        }
    }
}

// Kết quả trả về (sealed class — an toàn hơn null)
sealed class TranslationResult {
    data class Success(
        val originalText: String,
        val translatedText: String,
        val detectedSourceLang: String,
        val targetLang: String
    ) : TranslationResult()

    data class Error(val message: String) : TranslationResult()
}

// Danh sách ngôn ngữ hỗ trợ
object SupportedLanguages {
    val list = listOf(
        Language("vi", "Tiếng Việt", "🇻🇳"),
        Language("en", "English", "🇺🇸"),
        Language("zh", "中文", "🇨🇳"),
        Language("ja", "日本語", "🇯🇵"),
        Language("ko", "한국어", "🇰🇷"),
        Language("fr", "Français", "🇫🇷"),
        Language("de", "Deutsch", "🇩🇪"),
        Language("es", "Español", "🇪🇸"),
        Language("th", "ภาษาไทย", "🇹🇭"),
        Language("ru", "Русский", "🇷🇺"),
    )
}

data class Language(val code: String, val name: String, val flag: String)
