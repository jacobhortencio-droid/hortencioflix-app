package com.hortencioflix.tv

import com.hortencioflix.tv.data.VideoRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRangeTest {
    @Test fun ranges() {
        assertTrue(VideoRange.isDv("Movie.2023.2160p.DV.HDR.HEVC"))
        assertEquals("DV + HDR10", VideoRange.label("Movie 2160p DoVi HDR10"))
        assertEquals("Dolby Vision (só DV)", VideoRange.label("Movie 2160p Dolby Vision"))
        assertEquals("DV + HDR10+", VideoRange.label("Movie.2160p.DV.HDR10+.HEVC"))
        assertEquals("HDR10+", VideoRange.label("Movie 2160p HDR10+"))
        assertEquals("HDR10", VideoRange.label("Movie 2160p HDR"))
        assertEquals("HLG", VideoRange.label("Jogo 2160p HLG"))
        assertTrue(VideoRange.isSdr("Movie 1080p WEB-DL"))
        assertFalse(VideoRange.isDv("Movie DVDRip"))
    }

    @Test fun hdripIsNotHdr() {
        assertNull(VideoRange.label("Movie.2019.720p.HDRip.x264"))
        assertTrue(VideoRange.isSdr("Movie.2019.HDRip.XviD"))
        assertTrue(VideoRange.isSdr("Movie 2019 HDTV"))
    }

    @Test fun dvProfiles() {
        assertEquals(5, VideoRange.dvProfile("Movie 2160p DV P5 WEB-DL"))
        assertTrue(VideoRange.dvOnly("Movie 2160p DV P5 WEB-DL"))
        assertTrue(VideoRange.dvWithHdr("Movie 2160p DoVi P8 WEB-DL"))
        assertTrue(VideoRange.dvWithHdr("Movie.2160p.BluRay.REMUX.DV.Profile.7"))
        assertEquals("dv", VideoRange.kind("Movie 2160p DV HDR"))
        assertEquals("hdr10p", VideoRange.kind("Movie 2160p HDR10Plus"))
        assertEquals("hdr10", VideoRange.kind("Movie 2160p UHD BluRay"))
    }
}
