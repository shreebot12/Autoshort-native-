package com.jaywebtech.autoshorts.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class LlmProvider(val label: String) {
    DEEPSEEK("DeepSeek"),
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    OPENAI("OpenAI compatible"),
}

class AppSettings(context: Context) {
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "autoshorts_secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var deepgramKey: String
        get() = preferences.getString(KEY_DEEPGRAM, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_DEEPGRAM, value.trim()).apply()

    var llmProvider: LlmProvider
        get() = LlmProvider.entries.firstOrNull {
            it.name == preferences.getString(KEY_PROVIDER, LlmProvider.DEEPSEEK.name)
        } ?: LlmProvider.DEEPSEEK
        set(value) = preferences.edit().putString(KEY_PROVIDER, value.name).apply()

    var llmKey: String
        get() = keyFor(llmProvider)
        set(value) = preferences.edit().putString(providerKey(llmProvider), value.trim()).apply()

    fun keyFor(provider: LlmProvider): String = preferences.getString(providerKey(provider), "").orEmpty()

    var llmModel: String
        get() = preferences.getString(KEY_MODEL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_MODEL, value.trim()).apply()

    var openAiBaseUrl: String
        get() = preferences.getString(KEY_BASE_URL, "https://api.openai.com/v1").orEmpty()
        set(value) = preferences.edit().putString(KEY_BASE_URL, value.trim()).apply()

    companion object {
        private const val KEY_DEEPGRAM = "deepgram_key"
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_BASE_URL = "openai_base_url"

        private fun providerKey(provider: LlmProvider) = "llm_key_${provider.name.lowercase()}"
    }
}