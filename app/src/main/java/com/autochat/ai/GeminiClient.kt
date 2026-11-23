package com.autochat.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatTurn(
    val isFromPartner: Boolean, // true = đối phương (bên trái), false = mình (bên phải)
    val text: String,
    val topY: Int
)

object GeminiClient {

    suspend fun generateReply(
        apiKey: String,
        model: String,
        personaPrompt: String,
        partnerName: String,
        conversation: List<ChatTurn>,
        latestPartnerMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Chưa nhập Gemini API Key trong ứng dụng AutoChat-AI"))
        }

        val cleanModel = model.trim().ifEmpty { "gemini-2.5-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        try {
            val historyTranscript = buildString {
                appendLine("Lịch sử đoạn hội thoại đang hiển thị trên màn hình (từ trên xuống dưới):")
                conversation.takeLast(14).forEach { turn ->
                    val speaker = if (turn.isFromPartner) "[Đối phương - ${partnerName.ifBlank { "Người chat" }}]" else "[Tôi - Chủ máy]"
                    appendLine("$speaker: ${turn.text}")
                }
                appendLine()
                appendLine("Tin nhắn mới nhất của đối phương cần trả lời ngay: \"$latestPartnerMessage\"")
                appendLine("Hãy viết DUY NHẤT nội dung tin nhắn trả lời (không đặt trong ngoặc kép, không giải thích, không thêm tiền tố '[Tôi]:').")
            }

            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", personaPrompt)
                    }))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", historyTranscript)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 250)
                })
            }

            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                return@withContext Result.failure(RuntimeException("Lỗi Gemini ($code): ${responseBody.take(180)}"))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(RuntimeException("Gemini không trả về câu trả lời."))
            }

            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawReply = parts?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""

            val cleanedReply = rawReply
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace(Regex("^\\[?Tôi\\]?\\s*:\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            if (cleanedReply.isBlank()) {
                Result.failure(RuntimeException("Câu trả lời rỗng"))
            } else {
                Result.success(cleanedReply)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
