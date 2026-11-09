package com.jaywebtech.autoshorts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionFormatterTest {
    @Test
    fun clipsAndOffsetsCaptionsToExportRange() {
        val result = CaptionFormatter.format(
            segments = listOf(
                TranscriptSegment(1_000, 2_000, "First line"),
                TranscriptSegment(2_500, 4_000, "Second line"),
                TranscriptSegment(5_000, 6_000, "Outside clip"),
            ),
            clipStartMs = 1_500,
            clipEndMs = 3_500,
        )

        assertEquals(
            "1\n00:00:00,000 --> 00:00:00,500\nFirst line\n\n" +
                "2\n00:00:01,000 --> 00:00:02,000\nSecond line\n",
            result,
        )
    }

    @Test
    fun omitsEmptyAndNonOverlappingCaptions() {
        val result = CaptionFormatter.format(
            segments = listOf(
                TranscriptSegment(0, 100, "Earlier"),
                TranscriptSegment(200, 300, "  "),
            ),
            clipStartMs = 100,
            clipEndMs = 200,
        )

        assertEquals("", result)
    }
}