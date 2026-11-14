package com.hortencioflix.tv

import com.hortencioflix.tv.data.ChannelName
import com.hortencioflix.tv.data.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNameTest {
    private fun c(s: String) = ChannelName.of(s)

    @Test fun pipesAndSlashes() {
        assertEquals("TF1", c("|4K| TF1 HDR/UHD/4K").name)
        assertEquals(listOf("4K", "HDR"), c("|4K| TF1 HDR/UHD/4K").badges)
        assertEquals("NESN", c("|4K| NESN UHD/4K+").name)
        assertEquals("EVENEMENT SPORT", c("|4K| EVENEMENT SPORT HDR/UHD/4K").name)
    }

    @Test fun keepsPlusAndCountry() {
        assertEquals("CANAL+ BOX OFFICE", c("|4K| CANAL+ BOX OFFICE UHD/4K").name)
        assertEquals("PT| RTP 1", c("PT| RTP 1 FHD").name)
        assertEquals(listOf("FHD"), c("PT| RTP 1 FHD").badges)
        assertEquals("FR: TF1", c("FR: TF1 HD HEVC").name)
        assertEquals(listOf("HD", "HEVC"), c("FR: TF1 HD HEVC").badges)
    }

    @Test fun doesNotEatWords() {
        assertEquals("HDTV Portugal", c("HDTV Portugal").name)      // «HD» dentro de palavra fica
        assertEquals("SIC", c("SIC").name)
        assertEquals(emptyList<String>(), c("SIC").badges)
        assertEquals("4K", c("4K").name)                            // se só sobra a etiqueta, fica o nome original
    }

    @Test fun text() {
        assertEquals("h•••••@gmail.com", Text.maskEmail("hortencio.x@gmail.com"))
        assertEquals("conta a•••••@b.pt ok", Text.maskEmails("conta abc@b.pt ok"))
        assertEquals("1 perfil", Text.plural(1, "perfil", "perfis"))
        assertEquals("3 perfis", Text.plural(3, "perfil", "perfis"))
        assertEquals("20 000", Text.num(20000))
    }
}
