package com.hortencioflix.tv

import com.hortencioflix.tv.data.SourceFilters
import com.hortencioflix.tv.data.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFiltersTest {
    private fun s(name: String, q: Int = 2160, cached: Boolean = true) =
        StreamItem("Torrentio", "[TB+] Torrentio", name, "http://x/$name", name, emptyMap(), q, cached, false)

    private val dvP5 = s("Movie.2023.2160p.WEB-DL.DV.P5.DDP5.1.Atmos 💾 15 GB")
    private val dvHdr = s("Movie.2023.2160p.BluRay.REMUX.DV.HDR.TrueHD.7.1.Atmos 💾 70 GB")
    private val hdr10p = s("Movie.2023.2160p.AMZN.WEB-DL.HDR10+.DDP5.1 💾 18 GB")
    private val sdr1080 = s("Movie.2023.1080p.BluRay.x264.Dublado 💾 9 GB", q = 1080)
    private val cam = s("Movie 2023 HDCAM 720p", q = 720)

    @Test fun orWithinGroupAndAcrossGroups() {
        val f = mapOf("img" to setOf("dv", "hdr10p"), "q" to setOf("4K"))
        assertTrue(SourceFilters.passes(dvP5, f))
        assertTrue(SourceFilters.passes(hdr10p, f))
        assertFalse(SourceFilters.passes(sdr1080, f))
        assertTrue(SourceFilters.passes(sdr1080, f + ("q" to setOf("1080p")), except = "img"))
    }

    @Test fun facets() {
        assertTrue(SourceFilters.facet(dvHdr, "img", "anyhdr"))
        assertTrue(SourceFilters.facet(dvHdr, "src", "remux"))
        assertTrue(SourceFilters.facet(dvHdr, "aud", "lossless"))
        assertFalse(SourceFilters.facet(hdr10p, "aud", "lossless"))
        assertTrue(SourceFilters.facet(hdr10p, "src", "web"))
        assertTrue(SourceFilters.facet(sdr1080, "img", "sdr"))
        assertTrue(SourceFilters.facet(sdr1080, "lang", "br"))
        assertFalse(SourceFilters.facet(cam, "nocam", "1"))
        assertTrue(SourceFilters.facet(sdr1080, "nocam", "1"))
        assertEquals(70.0, SourceFilters.sizeGb(dvHdr.title), 0.01)
    }

    @Test fun pinnedFirstKeepsReadyBeforeDownloads() {
        val notReadyDv = s("Movie.2160p.DV.HDR.x265", cached = false)
        val out = SourceFilters.pinnedFirst(listOf(sdr1080, notReadyDv, dvHdr), mapOf("img" to setOf("dv")))
        assertEquals(listOf(dvHdr, sdr1080, notReadyDv), out)
    }

    @Test fun encodeDecode() {
        val f = mapOf("img" to setOf("dv", "hdr10p"), "q" to setOf("4K"))
        assertEquals(f, SourceFilters.decode(SourceFilters.encode(f)))
        assertEquals("4K  ·  Dolby Vision ou HDR10+", SourceFilters.describe(linkedMapOf("q" to setOf("4K"), "img" to linkedSetOf("dv", "hdr10p"))))
    }
}
