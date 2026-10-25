package com.hortencioflix.tv

import com.hortencioflix.tv.data.SourcePicker
import com.hortencioflix.tv.data.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePickerTest {
    private fun s(name: String, q: Int, cached: Boolean = true) = StreamItem("add", name, "", "http://x/$name$q", "", emptyMap(), q, cached, false)

    @Test fun debridRecognised() {
        assertTrue(SourcePicker.isDebrid("[TB+] Torrentio\n1080p"))
        assertTrue(SourcePicker.isDebrid("[RD+] Torrentio"))
        assertFalse(SourcePicker.isDebrid("Direto HD"))
    }

    @Test fun withDebridCachedFirst() {
        val l = listOf(s("Direto", 1080), s("[TB+] T", 720), s("[TB download] T", 2160, false))
        val o = SourcePicker.order(l, true)
        assertEquals("[TB+] T", o[0].name)
        assertEquals("Direto", o[1].name)
        assertEquals("[TB download] T", o[2].name)
    }

    @Test fun withoutDebridDirectFirst() {
        val l = listOf(s("[TB+] T", 2160), s("Direto", 720))
        assertEquals("Direto", SourcePicker.order(l, false)[0].name)
    }

    @Test fun qualityTargets() {
        val l = listOf(s("a", 2160), s("b", 1080), s("c", 1080), s("d", 720))
        assertEquals(0, SourcePicker.indexFor(l, 0))
        assertEquals(1, SourcePicker.indexFor(l, 1080))
        assertEquals(3, SourcePicker.indexFor(l, 720))
        assertEquals(3, SourcePicker.indexFor(l, 480))      // nada abaixo: o mais próximo acima
        assertEquals(1, SourcePicker.indexFor(l, 1440))     // mais próximo abaixo
        assertNull(SourcePicker.indexFor(emptyList(), 720))
    }

    @Test fun failoverPrefersSameQuality() {
        val l = listOf(s("a", 1080), s("b", 720), s("c", 1080))
        assertEquals(2, SourcePicker.nextAfterFail(l, setOf(0), 0))
        assertEquals(1, SourcePicker.nextAfterFail(l, setOf(0, 2), 2))
        assertNull(SourcePicker.nextAfterFail(l, setOf(0, 1, 2), 2))
    }

    @Test fun neverAutoPicksDownloading() {
        val l = listOf(s("[TB+] a", 1080), s("[TB download] b", 1080, false), s("[TB download] c", 720, false))
        assertNull(SourcePicker.nextAfterFail(l, setOf(0), 0))
        assertEquals(0, SourcePicker.indexFor(l, 720))
    }

    @Test fun placeholderDetected() {
        val d = s("[TB download] T", 1080, false)
        assertTrue(SourcePicker.looksLikePlaceholder(d, 15_000, "series"))
        assertFalse(SourcePicker.looksLikePlaceholder(d, 40 * 60_000L, "series"))
        assertFalse(SourcePicker.looksLikePlaceholder(s("Direto", 1080), 15_000, "movie"))
        assertFalse(SourcePicker.looksLikePlaceholder(d, 15_000, "tv"))
    }
}
