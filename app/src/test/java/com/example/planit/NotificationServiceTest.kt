package com.example.planit

import com.example.planit.notification.NotificationService
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationServiceTest {

    @Test
    fun testResolveIconEmoji_standardKeywords() {
        assertEquals("💻", NotificationService.resolveIconEmoji("code"))
        assertEquals("💻", NotificationService.resolveIconEmoji("laptop"))
        assertEquals("💻", NotificationService.resolveIconEmoji("dev"))

        assertEquals("⚡", NotificationService.resolveIconEmoji("fitness"))
        assertEquals("⚡", NotificationService.resolveIconEmoji("gym"))
        assertEquals("⚡", NotificationService.resolveIconEmoji("workout"))

        assertEquals("📖", NotificationService.resolveIconEmoji("book"))
        assertEquals("📖", NotificationService.resolveIconEmoji("read"))
        assertEquals("📖", NotificationService.resolveIconEmoji("study"))

        assertEquals("👥", NotificationService.resolveIconEmoji("meeting"))
        assertEquals("☕", NotificationService.resolveIconEmoji("coffee"))
        assertEquals("🎨", NotificationService.resolveIconEmoji("design"))
        assertEquals("✍️", NotificationService.resolveIconEmoji("write"))
        assertEquals("🎵", NotificationService.resolveIconEmoji("music"))
        assertEquals("🏃", NotificationService.resolveIconEmoji("run"))
    }

    @Test
    fun testResolveIconEmoji_rawEmojisAndFallbacks() {
        // Direct emoji strings
        assertEquals("🔥", NotificationService.resolveIconEmoji("🔥"))
        assertEquals("✨", NotificationService.resolveIconEmoji("✨"))
        // Unknown keyword falls back to ✦
        assertEquals("✦", NotificationService.resolveIconEmoji("unknown_category"))
    }
}
