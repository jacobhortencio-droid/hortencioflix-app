package com.hortencioflix.tv

import com.hortencioflix.tv.data.AudioLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioLangTest {
    @Test fun detectsDubs() {
        assertEquals("br", AudioLang.of("Filme.2024.1080p.WEB-DL.DUAL.5.1 Dublado"))
        assertEquals("br", AudioLang.of("Movie 2023 PT-BR 1080p"))
        assertEquals("pt", AudioLang.of("Filme 2022 PT-PT Dobrado 720p"))
        assertEquals("pt", AudioLang.of("Torrentio 1080p 🇵🇹"))
        assertEquals("por", AudioLang.of("Movie.2021.1080p.Portuguese.AAC"))
        assertNull(AudioLang.of("Movie.2021.1080p.English Legendado PT"))
        assertNull(AudioLang.of("Movie 2021 1080p English"))
    }
}
