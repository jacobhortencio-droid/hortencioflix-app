package com.hortencioflix.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** Junta os dados do Android e da Roku (funções puras, testadas em SyncMergeTest). */
object SyncMerge {
    /** «Continuar a ver»: um por título, fica o mais recente; tira o que já foi acabado de ver. */
    fun progress(a: JSONArray, b: JSONArray, finished: Set<String>, limit: Int = 20): JSONArray {
        val best = LinkedHashMap<String, JSONObject>()
        for (arr in listOf(a, b)) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val m = o.optString("m")
            if (m.isEmpty() || m in finished) continue
            val cur = best[m]
            if (cur == null || o.optLong("a") > cur.optLong("a")) best[m] = o
        }
        val out = JSONArray()
        best.values.sortedByDescending { it.optLong("a") }.take(limit).forEach { out.put(it) }
        return out
    }

    /** Lista por «u» (listas IPTV, favoritos): primeiro as do Android, depois as que só a Roku tem. */
    fun byUrl(a: JSONArray, b: JSONArray, limit: Int): JSONArray {
        val seen = HashSet<String>()
        val out = JSONArray()
        for (arr in listOf(a, b)) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("u")
            if (u.isEmpty() || !seen.add(u) || out.length() >= limit) continue
            out.put(o)
        }
        return out
    }

    /** Roku guarda os episódios vistos como [{m:"tt1", e:"1:1-8,2:3"}]; o Android como "tt1:1:1". */
    fun rokuToIds(arr: JSONArray): Set<String> {
        val out = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val m = o.optString("m")
            if (m.isEmpty()) continue
            for (part in o.optString("e").split(',')) {
                val c = part.indexOf(':')
                if (c <= 0) continue
                val season = part.substring(0, c).toIntOrNull() ?: continue
                val range = part.substring(c + 1)
                val ab = range.split('-').mapNotNull { it.trim().toIntOrNull() }
                if (ab.isEmpty()) continue
                val from = ab[0]
                val to = ab.getOrElse(1) { from }
                if (to - from > 500) continue
                for (e in from..to) out.add("$m:$season:$e")
            }
        }
        return out
    }

    fun idsToRoku(ids: Collection<String>, limit: Int = 25): JSONArray {
        val bySeries = LinkedHashMap<String, MutableMap<Int, MutableSet<Int>>>()
        for (id in ids) {
            val p = id.split(':')
            if (p.size != 3) continue
            val s = p[1].toIntOrNull() ?: continue
            val e = p[2].toIntOrNull() ?: continue
            bySeries.getOrPut(p[0]) { sortedMapOf() }.getOrPut(s) { sortedSetOf() }.add(e)
        }
        val out = JSONArray()
        for ((m, seasons) in bySeries.entries.toList().takeLast(limit)) {
            val parts = ArrayList<String>()
            for ((s, eps) in seasons) {
                val list = eps.sorted()
                var i = 0
                while (i < list.size) {
                    val a = list[i]
                    var b = a
                    while (i + 1 < list.size && list[i + 1] == b + 1) { i++; b = list[i] }
                    parts.add(if (a == b) "$s:$a" else "$s:$a-$b")
                    i++
                }
            }
            out.put(JSONObject().put("m", m).put("e", parts.joinToString(",")))
        }
        return out
    }

    /** JSON só com caracteres ASCII (o servidor da Roku conta caracteres, não bytes). */
    fun asciiJson(o: JSONObject): String {
        val s = o.toString()
        val sb = StringBuilder(s.length + 32)
        for (ch in s) if (ch.code < 128) sb.append(ch) else sb.append(String.format("\\u%04x", ch.code))
        return sb.toString()
    }
}
