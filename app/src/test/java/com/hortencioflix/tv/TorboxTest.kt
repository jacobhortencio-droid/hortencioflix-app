package com.hortencioflix.tv

import com.hortencioflix.tv.data.TorboxApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorboxTest {
    @Test fun hashFromTorrentioLink() {
        val h = "0123456789ABCDEF0123456789abcdef01234567"
        assertEquals(h.lowercase(), TorboxApi.hashOf("https://torrentio.strem.fun/resolve/torbox/KEY/$h/null/0/Movie.mkv"))
        assertNull(TorboxApi.hashOf("https://cdn.example.com/video.mp4"))
        assertNull(TorboxApi.hashOf("https://x/" + "a".repeat(41)))
    }
}
