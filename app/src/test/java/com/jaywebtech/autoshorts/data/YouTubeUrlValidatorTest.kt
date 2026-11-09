package com.jaywebtech.autoshorts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlValidatorTest {
    @Test
    fun normalizesSupportedVideoUrlForms() {
        val expected = "https://www.youtube.com/watch?v=abcdefghijk"

        assertEquals(expected, YouTubeUrlValidator.normalize("https://www.youtube.com/watch?v=abcdefghijk&t=20s"))
        assertEquals(expected, YouTubeUrlValidator.normalize("https://youtu.be/abcdefghijk?feature=shared"))
        assertEquals(expected, YouTubeUrlValidator.normalize("https://youtube.com/shorts/abcdefghijk"))
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousUrls() {
        assertNull(YouTubeUrlValidator.normalize(""))
        assertNull(YouTubeUrlValidator.normalize("http://youtube.com/watch?v=abcdefghijk"))
        assertNull(YouTubeUrlValidator.normalize("https://youtube.com.evil.example/watch?v=abcdefghijk"))
        assertNull(YouTubeUrlValidator.normalize("https://youtube.com/watch?v=short"))
    }
}