package com.suidvck.any.features.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {

    @Test
    fun extractsPlainSearchMarker() {
        assertEquals(
            "ข่าว AI วันนี้",
            WebSearch.extractQuery("SEARCH: ข่าว AI วันนี้")
        )
    }

    @Test
    fun extractsBacktickedMarker() {
        assertEquals(
            "weather bangkok",
            WebSearch.extractQuery("```\nSEARCH: weather bangkok\n```")
        )
    }

    @Test
    fun ignoresOrdinaryReplies() {
        assertNull(WebSearch.extractQuery("สวัสดีครับ มีอะไรให้ช่วยไหม"))
        assertNull(WebSearch.extractQuery("SEARCH:"))
    }

    @Test
    fun buildsUntrustedContextWithSources() {
        val context = WebSearch.buildContext(
            "test query",
            listOf(
                SearchResult("Example", "https://example.com", "A snippet"),
                SearchResult("Second", "https://example.org", "")
            )
        )
        assertTrue(context.contains("test query"))
        assertTrue(context.contains("https://example.com"))
        assertTrue(context.contains("A snippet"))
        assertTrue(context.contains("untrusted web content"))
    }
}
