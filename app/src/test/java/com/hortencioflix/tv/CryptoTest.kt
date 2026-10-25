package com.hortencioflix.tv

import com.hortencioflix.tv.data.BackupCrypto
import com.hortencioflix.tv.data.Hex
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoTest {
    @Test fun hexRoundTrip() {
        val b = byteArrayOf(0, 1, 15, 16, 127, -128, -1)
        assertEquals("00010f107f80ff", Hex.enc(b))
        assertArrayEquals(b, Hex.dec(Hex.enc(b)))
    }

    @Test fun backupRoundTripAndWrongPassword() {
        val json = """{"app":"hortencioflix","prefs":{"torboxKey":"abc123"}}"""
        val wrapped = JSONObject(BackupCrypto.encrypt(json, "segredo"))
        assertEquals("hortencioflix", wrapped.getString("app"))
        assertNotNull(wrapped.opt("enc"))
        assertEquals(json, BackupCrypto.decrypt(wrapped, "segredo"))
        assertNull(BackupCrypto.decrypt(wrapped, "errada"))
    }
}
