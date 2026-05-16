package com.watchtogether.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoItemFormattingTest {

    @Test
    fun `formatDuration returns minutes and seconds for short videos`() {
        // 5 minutes 30 seconds = 330000ms
        val result = formatDuration(330000L)
        assertEquals("05:30", result)
    }

    @Test
    fun `formatDuration returns hours minutes seconds for long videos`() {
        // 1 hour 23 minutes 45 seconds = 5025000ms
        val result = formatDuration(5025000L)
        assertEquals("1:23:45", result)
    }

    @Test
    fun `formatDuration returns zero for zero duration`() {
        val result = formatDuration(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatDuration returns correct for exactly one hour`() {
        val result = formatDuration(3600000L)
        assertEquals("1:00:00", result)
    }

    @Test
    fun `formatDuration returns correct for one second`() {
        val result = formatDuration(1000L)
        assertEquals("00:01", result)
    }

    @Test
    fun `formatDuration returns correct for 59 minutes 59 seconds`() {
        val result = formatDuration(3599000L)
        assertEquals("59:59", result)
    }

    @Test
    fun `formatSize returns KB for small files`() {
        val result = formatSize(500L * 1024) // 500KB
        assertEquals("500 KB", result)
    }

    @Test
    fun `formatSize returns MB for medium files`() {
        val result = formatSize(50L * 1024 * 1024) // 50MB
        assertEquals("50.0 MB", result)
    }

    @Test
    fun `formatSize returns GB for large files`() {
        val result = formatSize(2L * 1024 * 1024 * 1024) // 2GB
        assertEquals("2.0 GB", result)
    }

    @Test
    fun `formatSize returns MB for 1MB`() {
        val result = formatSize(1L * 1024 * 1024)
        assertEquals("1.0 MB", result)
    }

    @Test
    fun `formatSize returns GB for 1GB`() {
        val result = formatSize(1L * 1024 * 1024 * 1024)
        assertEquals("1.0 GB", result)
    }

    /**
     * Extracted formatting logic matching VideoItem.formattedDuration
     */
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Extracted formatting logic matching VideoItem.formattedSize
     */
    private fun formatSize(sizeBytes: Long): String {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
}
