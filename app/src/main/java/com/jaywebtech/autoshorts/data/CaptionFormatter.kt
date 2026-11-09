package com.jaywebtech.autoshorts.data

object CaptionFormatter {
    fun format(segments: List<TranscriptSegment>, clipStartMs: Long, clipEndMs: Long): String {
        return segments.mapNotNull { segment ->
            val start = maxOf(clipStartMs, segment.startMs)
            val end = minOf(clipEndMs, segment.endMs)
            if (end <= start || segment.text.isBlank()) return@mapNotNull null
            start - clipStartMs to (end - clipStartMs) to segment.text.trim()
        }.mapIndexed { index, cue ->
            val start = cue.first.first
            val end = cue.first.second
            "${index + 1}\n${formatTime(start)} --> ${formatTime(end)}\n${cue.second}\n"
        }.joinToString("\n")
    }

    private fun formatTime(timeMs: Long): String {
        val total = timeMs.coerceAtLeast(0)
        val hours = total / 3_600_000
        val minutes = (total % 3_600_000) / 60_000
        val seconds = (total % 60_000) / 1_000
        val millis = total % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}