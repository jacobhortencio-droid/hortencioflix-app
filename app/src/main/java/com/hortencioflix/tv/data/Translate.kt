package com.hortencioflix.tv.data

import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Título e sinopse em português (os add-ons de catálogo dão quase sempre em inglês).
 * Ordem: TMDB pt-PT → TMDB pt-BR (se houver chave TMDB) → traduções do Trakt (PT primeiro, depois BR).
 * Fica em memória e numa pequena cache guardada (últimos 300 títulos).
 */
object Translate {
    data class Pt(val name: String, val desc: String, val lang: String)

    private val mem = ConcurrentHashMap<String, Pt>()
    private const val CACHE = "ptCache"

    fun available(): Boolean = Prefs.str("tmdbKey").isNotEmpty() || Trakt.clientId.isNotEmpty()

    fun cached(id: String): Pt? {
        val k = id.substringBefore(':')
        mem[k]?.let { return it }
        return try {
            val o = org.json.JSONObject(Prefs.str(CACHE).ifEmpty { "{}" }).optJSONObject(k) ?: return null
            Pt(o.optString("n"), o.optString("d"), o.optString("l")).also { mem[k] = it }
        } catch (e: Exception) { null }
    }

    private fun store(id: String, p: Pt) {
        mem[id] = p
        try {
            val o = org.json.JSONObject(Prefs.str(CACHE).ifEmpty { "{}" })
            o.put(id, org.json.JSONObject().put("n", p.name).put("d", p.desc.take(1200)).put("l", p.lang))
            // limite: tira os mais antigos
            val keys = o.keys().asSequence().toList()
            if (keys.size > 300) keys.take(keys.size - 300).forEach { o.remove(it) }
            Prefs.put(CACHE, o.toString())
        } catch (e: Exception) {}
    }

    /** Bloqueante (chamar fora da thread principal). null = não há tradução. */
    fun pt(type: String, id: String): Pt? {
        val imdb = id.substringBefore(':')
        if (!imdb.startsWith("tt")) return null
        cached(imdb)?.let { return it.takeIf { p -> p.desc.isNotEmpty() || p.name.isNotEmpty() } }
        val series = type == "series" || type == "anime"
        val tmdb = Prefs.str("tmdbKey")
        if (tmdb.isNotEmpty()) for (lang in listOf("pt-PT", "pt-BR")) {
            val d = Http.getJson("https://api.themoviedb.org/3/find/$imdb?api_key=$tmdb&external_source=imdb_id&language=$lang") ?: continue
            val r = (if (series) d.optJSONArray("tv_results") else d.optJSONArray("movie_results"))?.optJSONObject(0)
                ?: d.optJSONArray("movie_results")?.optJSONObject(0) ?: d.optJSONArray("tv_results")?.optJSONObject(0)
            val ov = r?.optString("overview").orEmpty()
            if (ov.isNotEmpty()) {
                val p = Pt(r!!.optString("title").ifEmpty { r.optString("name") }, ov, lang)
                store(imdb, p); return p
            }
        }
        val cid = Trakt.clientId
        if (cid.isNotEmpty()) {
            val h = mapOf("trakt-api-version" to "2", "trakt-api-key" to cid)
            val arr: JSONArray? = Http.getJsonArray("https://api.trakt.tv/${if (series) "shows" else "movies"}/$imdb/translations/pt", h)
            if (arr != null) {
                var best: org.json.JSONObject? = null
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    if (t.optString("overview").isEmpty()) continue
                    if (t.optString("country").equals("pt", true)) best = t
                    if (best == null) best = t
                }
                best?.let {
                    val p = Pt(it.optString("title"), it.optString("overview"), "pt-" + it.optString("country").uppercase())
                    store(imdb, p); return p
                }
            }
        }
        // sem tradução: lembra-se para não voltar a pedir
        store(imdb, Pt("", "", ""))
        return null
    }
}
