package com.hortencioflix.tv.data

import org.json.JSONObject

/**
 * A fonte exata que estava a tocar em cada vídeo (link original do add-on, não o link temporário do debrid).
 * Ao retomar, é essa que se tenta primeiro — mesmo ficheiro, mesmas legendas sincronizadas, mesmo áudio.
 * Guarda as últimas 60, durante 30 dias.
 */
object LastSource {
    private const val KEY = "lastSrc"

    private fun all(): JSONObject = try { JSONObject(Prefs.str(KEY, "{}")) } catch (e: Exception) { JSONObject() }

    fun save(videoId: String, s: StreamItem) {
        if (videoId.isEmpty() || !s.url.startsWith("http")) return
        try {
            val o = all()
            o.remove(videoId)
            o.put(videoId, JSONObject().put("u", s.url).put("f", s.filename).put("a", s.addon).put("n", s.name)
                .put("t", s.title.take(300)).put("q", s.quality).put("at", System.currentTimeMillis())
                .put("h", JSONObject(s.headers as Map<*, *>)))
            val keys = o.keys().asSequence().toList()
            if (keys.size > 60) keys.take(keys.size - 60).forEach { o.remove(it) }
            Prefs.put(KEY, o.toString())
        } catch (e: Exception) {}
    }

    fun find(videoId: String): StreamItem? {
        val x = all().optJSONObject(videoId) ?: return null
        if (System.currentTimeMillis() - x.optLong("at") > 30L * 24 * 3600_000) return null
        val h = HashMap<String, String>()
        x.optJSONObject("h")?.let { o -> for (k in o.keys()) h[k] = o.optString(k) }
        return StreamItem(x.optString("a"), x.optString("n"), x.optString("t"), x.optString("u"), x.optString("f"), h, x.optInt("q"), true, false)
    }

    /** A mesma fonte: mesmo link, ou o mesmo ficheiro do mesmo add-on. */
    fun same(a: StreamItem, b: StreamItem): Boolean =
        a.url == b.url || (a.filename.isNotEmpty() && a.filename.equals(b.filename, true) && a.addon == b.addon)
}
