package com.hortencioflix.tv

import com.hortencioflix.tv.data.FileName
import com.hortencioflix.tv.data.Iptv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ParsersTest {
    @Test fun seriesFileName() {
        val p = FileName.parse("MobLand.S02E03.2160p.HDR10Plus.DV.WEBRip.6CH.x265.HEVC-PSA")
        assertNotNull(p)
        assertEquals("MobLand", p!!.title)
        assertEquals(2, p.season)
        assertEquals(3, p.episode)
        assertEquals("4K", p.quality)
    }

    @Test fun movieWithSitePrefix() {
        val p = FileName.parse("www.UIndex.org    -    Best.of.the.Best.2026.iTA-ENG.WEBDL.2160p.HEVC.HDR.x265-CYBER.mkv")
        assertNotNull(p)
        assertEquals("Best of the Best", p!!.title)
        assertEquals(2026, p.year)
        assertNull(p.season)
    }

    @Test fun movieMulti() {
        val p = FileName.parse("Coyote.vs.Acme.2026.MULTI.VFQ.2160p.WEB.DV.HDR.H265-BOUBA.mkv")
        assertEquals("Coyote vs Acme", p!!.title)
        assertEquals(2026, p.year)
    }

    @Test fun m3uParsing() {
        val text = """#EXTM3U url-tvg="http://epg.example/guide.xml.gz"
#EXTINF:-1 tvg-id="rtp1.pt" tvg-logo="http://l/rtp1.png" group-title="Generalistas",RTP 1
http://s/rtp1.m3u8
#EXTINF:-1 tvg-id="sic.pt" group-title="Generalistas",SIC
http://s/sic.m3u8
#EXTINF:-1 group-title="Notícias",CNN Portugal
http://s/cnn.m3u8
"""
        val groups = Iptv.parse(text)
        assertEquals("http://epg.example/guide.xml.gz", Iptv.lastEpgUrl)
        assertEquals("Todos", groups[0].title)
        assertEquals(3, groups[0].channels.size)
        val gen = groups.first { it.title == "Generalistas" }
        assertEquals(2, gen.channels.size)
        assertEquals("rtp1.pt", gen.channels[0].tvgId)
    }

    @Test fun xtreamEpgUrl() {
        assertEquals("http://srv:8080/xmltv.php?username=u&password=p",
            Iptv.xtreamEpg("http://srv:8080/get.php?username=u&password=p&type=m3u_plus&output=ts"))
    }
}
