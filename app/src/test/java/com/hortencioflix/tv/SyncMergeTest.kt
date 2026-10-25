package com.hortencioflix.tv

import com.hortencioflix.tv.data.SyncMerge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun p(m: String, a: Long) = JSONObject().put("m", m).put("a", a)

    @Test fun progressKeepsNewestAndDropsFinished() {
        val a = JSONArray().put(p("tt1", 10)).put(p("tt2", 50))
        val b = JSONArray().put(p("tt1", 90)).put(p("tt3", 20))
        val out = SyncMerge.progress(a, b, setOf("tt2"))
        assertEquals(2, out.length())
        assertEquals("tt1", out.getJSONObject(0).getString("m"))
        assertEquals(90L, out.getJSONObject(0).getLong("a"))
        assertEquals("tt3", out.getJSONObject(1).getString("m"))
    }

    @Test fun watchedRoundTrip() {
        val ids = listOf("tt9:1:1", "tt9:1:2", "tt9:1:3", "tt9:2:5", "tt8:1:4")
        val roku = SyncMerge.idsToRoku(ids)
        val back = SyncMerge.rokuToIds(roku)
        assertEquals(ids.toSet(), back)
        val e = (0 until roku.length()).map { roku.getJSONObject(it) }.first { it.getString("m") == "tt9" }.getString("e")
        assertEquals("1:1-3,2:5", e)
    }

    @Test fun listsByUrlAndAscii() {
        val a = JSONArray().put(JSONObject().put("n", "A").put("u", "x"))
        val b = JSONArray().put(JSONObject().put("n", "B").put("u", "x")).put(JSONObject().put("n", "C").put("u", "y"))
        val out = SyncMerge.byUrl(a, b, 10)
        assertEquals(2, out.length())
        assertEquals("A", out.getJSONObject(0).getString("n"))
        val s = SyncMerge.asciiJson(JSONObject().put("n", "Ação"))
        assertTrue(s.contains("\\u00e7"))
        assertFalse(s.any { it.code > 127 })
    }
}
