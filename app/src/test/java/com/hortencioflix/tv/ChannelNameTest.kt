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

    @Test fun keepsPlusAndMovesCountryToBadge() {
        assertEquals("CANAL+ BOX OFFICE", c("|4K| CANAL+ BOX OFFICE UHD/4K").name)
        assertEquals("RTP 1", c("PT| RTP 1 FHD").name)
        assertEquals(listOf("PT", "FHD"), c("PT| RTP 1 FHD").badges)
        assertEquals("TF1", c("FR: TF1 HD HEVC").name)
        assertEquals(listOf("FR", "HD", "HEVC"), c("FR: TF1 HD HEVC").badges)
        assertEquals("ZAP VIVA", c("PT - ZAP VIVA UHD").name)
        assertEquals(listOf("PT", "4K"), c("PT - ZAP VIVA UHD").badges)
        assertEquals("TV Record", c("TV - Record").name)             // «TV» não é um país
    }

    private fun ch(name: String, group: String) = com.hortencioflix.tv.data.Channel(name, "", "http://x/$name", group)

    @Test fun contentType() {
        val K = com.hortencioflix.tv.data.ChannelFacets.Kind
        fun k(name: String, group: String = "") = com.hortencioflix.tv.data.ChannelFacets.kind(ch(name, group))
        assertEquals(K.SPORT, k("SPORT TV 1", "|PT| DESPORTO"))
        assertEquals(K.SPORT, k("Fox Sports 2"))
        assertEquals(K.SPORT, k("Sky Sports News"))
        assertEquals(K.NEWS, k("SIC Notícias", "PORTUGAL"))
        assertEquals(K.NEWS, k("CMTV"))                              // «mtv» dentro de CMTV não é música
        assertEquals(K.MUSIC, k("MTV Live"))
        assertEquals(K.KIDS, k("Discovery Kids"))
        assertEquals(K.DOCS, k("Discovery Channel"))
        assertEquals(K.MOVIES, k("TVCine Top"))
        assertEquals(K.MOVIES, k("Qualquer", "PT | FILMES E SÉRIES"))
        assertEquals(K.SERIES, k("AXN White"))
        assertEquals(K.ENT, k("PT - ZAP VIVA UHD", "|PT| INTERNACIONAL"))
        assertEquals(K.ADULT, k("Canal", "ADULTOS +18"))
        assertEquals(K.OTHER, k("Canal 123"))
    }

    @Test fun regionAndGroupLabel() {
        val F = com.hortencioflix.tv.data.ChannelFacets
        assertEquals("PT", F.region(ch("RTP 1", "|PT| INTERNACIONAL")))
        assertEquals("BR", F.region(ch("BR: Globo SP", "Canais")))
        assertEquals("PT", F.region(ch("RTP 1", "PORTUGAL")))
        assertEquals("", F.region(ch("HD - Canal", "Variedades")))
        assertEquals("PT · Internacional", F.groupLabel("|PT| INTERNACIONAL"))
        assertEquals("Canais de Desporto", F.groupLabel("CANAIS DE DESPORTO"))
        assertEquals("Filmes 4K", F.groupLabel("FILMES 4K"))
        assertEquals("Séries e Novelas", F.groupLabel("Séries e Novelas"))
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
