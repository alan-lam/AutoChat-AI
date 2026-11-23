package com.autochat.ai

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREF_NAME = "autochat_ai_prefs"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
    private const val KEY_PERSONA = "persona_prompt"
    private const val KEY_TARGET_NAME = "target_chat_name"
    private const val KEY_POLL_SEC = "poll_interval_sec"
    private const val KEY_DEBOUNCE_SEC = "debounce_sec"
    private const val KEY_SAFE_GUARD = "safe_guard_sensitive"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = ""
        set(_) {}

    fun getApiKey(ctx: Context): String =
        prefs(ctx).getString(KEY_API_KEY, "")?.trim() ?: ""

    fun setApiKey(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_API_KEY, value.trim()).apply()

    fun getModel(ctx: Context): String =
        prefs(ctx).getString(KEY_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"

    fun setModel(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_MODEL, value.trim().ifEmpty { "gemini-2.5-flash" }).apply()

    fun getPersona(ctx: Context): String =
        prefs(ctx).getString(
            KEY_PERSONA,
            "Bạn đang vào vai chủ nhân chiếc điện thoại này nhắn tin với vợ/người yêu. " +
                "Xưng 'anh', gọi đối phương là 'em' hoặc 'vợ'. " +
                "Trả lời cực kỳ tự nhiên như người thật đang chat Messenger/Zalo: câu ngắn gọn (1-2 câu), tình cảm, ấm áp, đúng trọng tâm câu hỏi của đối phương. " +
                "Tuyệt đối KHÔNG viết văn mẫu dài dòng, KHÔNG bao giờ nhận là AI."
        ) ?: ""

    fun setPersona(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_PERSONA, value.trim()).apply()

    fun getTargetName(ctx: Context): String =
        prefs(ctx).getString(KEY_TARGET_NAME, "")?.trim() ?: ""

    fun setTargetName(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_TARGET_NAME, value.trim()).apply()

    fun getDebounceMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_DEBOUNCE_SEC, 2500L).coerceIn(1000L, 10000L)

    fun setDebounceMs(ctx: Context, value: Long) =
        prefs(ctx).edit().putLong(KEY_DEBOUNCE_SEC, value.coerceIn(1000L, 10000L)).apply()

    fun isSafeGuardEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SAFE_GUARD, true)

    fun setSafeGuardEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SAFE_GUARD, enabled).apply()
}
