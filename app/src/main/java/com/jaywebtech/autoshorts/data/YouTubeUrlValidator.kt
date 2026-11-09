package com.jaywebtech.autoshorts.data

import java.net.URI
import java.util.Locale

object YouTubeUrlValidator {
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    fun normalize(input: String): String? {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) {
            return null
        }

        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
        val pathSegments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        val videoId = when (host) {
            "youtu.be" -> pathSegments.firstOrNull()
            "youtube.com", "m.youtube.com", "music.youtube.com" -> when (pathSegments.firstOrNull()) {
                "watch" -> uri.rawQuery.orEmpty()
                    .split('&')
                    .firstOrNull { it.substringBefore('=') == "v" }
                    ?.substringAfter('=', "")
                "shorts", "embed", "live" -> pathSegments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return videoId
            ?.takeIf(videoIdPattern::matches)
            ?.let { "https://www.youtube.com/watch?v=$it" }
    }
}