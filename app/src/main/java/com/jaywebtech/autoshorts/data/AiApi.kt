package com.jaywebtech.autoshorts.data

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class NormalizedTranscript(
    val language: String,
    val durationMs: Long,
    val segments: List<TranscriptSegment>,
)

internal class AiApi(private val settings: AppSettings) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.MINUTES)
        .writeTimeout(20, TimeUnit.MINUTES)
        .build()

    fun transcribe(audio: File): NormalizedTranscript {
        val request = Request.Builder()
            .url("https://api.deepgram.com/v1/listen?model=nova-2&smart_format=true&diarize=true&punctuate=true")
            .header("Authorization", "Token ${settings.deepgramKey}")
            .post(audio.asRequestBody(audioContentType(audio).toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        check(response.isSuccessful) { "Deepgram returned ${response.code}: ${body.take(500)}" }
        val json = JSONObject(body)
        val alternative = json.getJSONObject("results")
            .getJSONArray("channels").getJSONObject(0)
            .getJSONArray("alternatives").getJSONObject(0)
        val rawWords = alternative.optJSONArray("words") ?: JSONArray()
        val words = (0 until rawWords.length()).mapNotNull { index ->
            val word = rawWords.optJSONObject(index) ?: return@mapNotNull null
            Word(
                text = word.optString("punctuated_word", word.optString("word")).trim(),
                startMs = (word.optDouble("start", 0.0) * 1000).toLong(),
                endMs = (word.optDouble("end", 0.0) * 1000).toLong(),
            ).takeIf { it.text.isNotBlank() }
        }
        val durationMs = (json.optJSONObject("metadata")?.optDouble("duration", 0.0)?.times(1000))?.toLong()
            ?: words.lastOrNull()?.endMs
            ?: 0L
        return NormalizedTranscript(
            language = json.optJSONObject("metadata")?.optString("language", "en") ?: "en",
            durationMs = durationMs,
            segments = groupWords(words),
        )
    }

    fun findCandidates(segments: List<TranscriptSegment>, durationMs: Long, projectId: String): List<CandidateEntity> {
        require(segments.isNotEmpty()) { "No spoken words were found in the selected media." }
        val transcript = segments.joinToString("\n") { "${it.startMs / 1000.0}: ${it.text}" }
        val prompt = """
            Find up to 12 compelling, self-contained vertical video clip moments from this transcript.
            Favor a clear hook, tension and payoff. Prefer 30 to 90 seconds, or use shorter clips only when the source itself is shorter.
            Times must be seconds within the source duration ${durationMs / 1000.0}. Return only JSON: {"candidates":[{"start":0,"end":40,"score":0.9,"hook":"...","rationale":"..."}]}
            Transcript:
            $transcript
        """.trimIndent()
        val responseText = when (settings.llmProvider) {
            LlmProvider.CLAUDE -> callClaude(prompt)
            LlmProvider.GEMINI -> callGemini(prompt)
            LlmProvider.DEEPSEEK, LlmProvider.OPENAI -> callChatCompletion(prompt)
        }
        val jsonText = responseText.substringAfter('{', "").let { "{$it" }.substringBeforeLast('}', "}") + "}"
        val raw = JSONObject(jsonText).optJSONArray("candidates") ?: JSONArray()
        val valid = (0 until raw.length()).mapNotNull { index ->
            val item = raw.optJSONObject(index) ?: return@mapNotNull null
            val start = (item.optDouble("start", -1.0) * 1000).toLong().coerceAtLeast(0L)
            val end = (item.optDouble("end", -1.0) * 1000).toLong().coerceAtMost(durationMs)
            val hook = item.optString("hook").trim()
            if (end <= start || hook.isBlank()) return@mapNotNull null
            CandidateEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                startMs = start,
                endMs = end,
                score = item.optDouble("score", 0.0).let { if (it > 1.0) it / 100.0 else it }.coerceIn(0.0, 1.0),
                hook = hook,
                rationale = item.optString("rationale", "Selected as a strong, self-contained moment."),
                rank = index + 1,
                selected = index < 5,
            )
        }.sortedByDescending(CandidateEntity::score).take(12)
        return valid.mapIndexed { index, candidate -> candidate.copy(rank = index + 1, selected = index < 5) }
    }

    private fun callChatCompletion(prompt: String): String {
        val provider = settings.llmProvider
        val base = when (provider) {
            LlmProvider.DEEPSEEK -> "https://api.deepseek.com/v1"
            else -> settings.openAiBaseUrl.ifBlank { "https://api.openai.com/v1" }
        }.trimEnd('/')
        val defaultModel = if (provider == LlmProvider.DEEPSEEK) "deepseek-chat" else "gpt-4o-mini"
        val payload = JSONObject().apply {
            put("model", settings.llmModel.ifBlank { defaultModel })
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("temperature", 0.2)
            put("response_format", JSONObject().put("type", "json_object"))
        }
        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer ${settings.llmKey}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val result = execute(request)
        return JSONObject(result).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    }

    private fun callClaude(prompt: String): String {
        val payload = JSONObject().apply {
            put("model", settings.llmModel.ifBlank { "claude-3-5-sonnet-latest" })
            put("max_tokens", 3000)
            put("temperature", 0.2)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", settings.llmKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return JSONObject(execute(request)).getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun callGemini(prompt: String): String {
        val model = settings.llmModel.ifBlank { "gemini-2.0-flash" }
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.2))
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.llmKey}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return JSONObject(execute(request)).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun execute(request: Request): String {
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        check(response.isSuccessful) { "AI provider returned ${response.code}: ${body.take(500)}" }
        return body
    }

    private fun audioContentType(audio: File): String = when (audio.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        else -> "audio/mp4"
    }

    private fun groupWords(words: List<Word>): List<TranscriptSegment> {
        val segments = mutableListOf<TranscriptSegment>()
        var start = 0L
        var end = 0L
        val text = StringBuilder()
        for (word in words) {
            val gap = word.startMs - end
            val sentenceEnd = text.lastOrNull() in listOf('.', '!', '?')
            if (text.isNotEmpty() && (gap > 900L || sentenceEnd)) {
                segments += TranscriptSegment(start, end, text.toString())
                text.clear()
            }
            if (text.isEmpty()) start = word.startMs else text.append(' ')
            text.append(word.text)
            end = word.endMs
        }
        if (text.isNotEmpty()) segments += TranscriptSegment(start, end, text.toString())
        return segments
    }

    private data class Word(val text: String, val startMs: Long, val endMs: Long)
}