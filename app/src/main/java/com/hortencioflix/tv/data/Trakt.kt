package com.hortencioflix.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** Trakt: ligação por código, scrobble (progresso), lista para ver, recomendações e histórico. Bloqueante. */
object Trakt {
    private const val API = "https://api.trakt.tv"

    var clientId: String
        get() = Prefs.str("traktId")
        set(v) = Prefs.put("traktId", v.trim())
    var clientSecret: String
        get() = Prefs.str("traktSecret")
        set(v) = Prefs.put("traktSecret", v.trim())
    var token: String
        get() = Prefs.str("traktToken")
        set(v) = Prefs.put("traktToken", v)

    val linked: Boolean get() = token.isNotEmpty() && clientId.isNotEmpty()

    private fun headers(auth: Boolean = true): Map<String, String> {
        val h = HashMap<String, String>()
        h["trakt-api-version"] = "2"
        h["trakt-api-key"] = clientId
        h["Content-Type"] = "application/json"
        if (auth && token.isNotEmpty()) h["Authorization"] = "Bearer $token"
        return h
    }

    data class DeviceCode(val deviceCode: String, val userCode: String, val url: String, val interval: Int, val expiresIn: Int)

    fun deviceCode(): DeviceCode? {
        val r = Http.postAuth("$API/oauth/device/code", JSONObject().put("client_id", clientId), headers(false)) ?: return null
        val dc = r.optString("device_code")
        if (dc.isEmpty()) return null
        return DeviceCode(dc, r.optString("user_code"), r.optString("verification_url", "https://trakt.tv/activate"), r.optInt("interval", 5), r.optInt("expires_in", 600))
    }

    /** Devolve true quando o utilizador aprovou (e guarda o token). */
    fun poll(code: String): Boolean {
        val r = Http.postAuth("$API/oauth/device/token", JSONObject().put("code", code).put("client_id", clientId).put("client_secret", clientSecret), headers(false)) ?: return false
        val t = r.optString("access_token")
        if (t.isEmpty()) return false
        token = t
        Prefs.put("traktRefresh", r.optString("refresh_token"))
        return true
    }

    fun logout() { token = ""; Prefs.remove("traktRefresh") }

    private fun body(meta: MetaItem, videoId: String, progress: Float): JSONObject? {
        val imdb = meta.id.substringBefore(':')
        if (!imdb.startsWith("tt")) return null
        val o = JSONObject().put("progress", (progress * 100).coerceIn(0f, 100f).toDouble())
        val p = videoId.split(":")
        if (meta.type == "series" && p.size >= 3) {
            o.put("show", JSONObject().put("ids", JSONObject().put("imdb", imdb)))
            o.put("episode", JSONObject().put("season", p[1].toIntOrNull() ?: 0).put("number", p[2].toIntOrNull() ?: 0))
        } else {
            o.put("movie", JSONObject().put("ids", JSONObject().put("imdb", imdb)))
        }
        return o
    }

    /** action: start, pause ou stop (stop com ≥80% marca como visto no Trakt). */
    fun scrobble(action: String, meta: MetaItem, videoId: String, progress: Float) {
        if (!linked || !Prefs.traktScrobble) return
        val b = body(meta, videoId, progress) ?: return
        Http.postAuth("$API/scrobble/$action", b, headers())
    }

    private fun toMetas(arr: JSONArray?, key: String? = null): List<MetaItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<MetaItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = if (key != null) o.optJSONObject(key) else (o.optJSONObject("movie") ?: o.optJSONObject("show"))
            item ?: continue
            val type = if (key == "show" || o.has("show")) "series" else "movie"
            val imdb = item.optJSONObject("ids")?.optString("imdb") ?: continue
            if (!imdb.startsWith("tt")) continue
            out.add(MetaItem(imdb, type, item.optString("title"), "https://images.metahub.space/poster/medium/$imdb/img",
                info = item.optInt("year").takeIf { it > 0 }?.toString() ?: ""))
        }
        return out
    }

    fun watchlist(): List<MetaItem> {
        if (!linked) return emptyList()
        val arr = Http.getJsonArray("$API/sync/watchlist?sort=added,desc&limit=60", headers())
        return toMetas(arr).take(40)
    }

    fun recommendations(): List<MetaItem> {
        if (!linked) return emptyList()
        val m = toMetas(Http.getJsonArray("$API/recommendations/movies?limit=20", headers()), null)
        val s = toMetas(Http.getJsonArray("$API/recommendations/shows?limit=20", headers()), null)
        val out = ArrayList<MetaItem>()
        for (i in 0 until maxOf(m.size, s.size)) { m.getOrNull(i)?.let { out.add(it.copy(type = "movie")) }; s.getOrNull(i)?.let { out.add(it.copy(type = "series")) } }
        return out
    }

    /** Importa o histórico de episódios e filmes vistos do Trakt (marca-os como vistos aqui). */
    fun importWatched(): Int {
        if (!linked) return 0
        val ids = ArrayList<String>()
        Http.getJsonArray("$API/sync/watched/shows", headers())?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val imdb = o.optJSONObject("show")?.optJSONObject("ids")?.optString("imdb") ?: continue
                if (!imdb.startsWith("tt")) continue
                val seasons = o.optJSONArray("seasons") ?: continue
                for (s in 0 until seasons.length()) {
                    val se = seasons.optJSONObject(s) ?: continue
                    val n = se.optInt("number")
                    val eps = se.optJSONArray("episodes") ?: continue
                    for (e in 0 until eps.length()) ids.add("$imdb:$n:${eps.optJSONObject(e)?.optInt("number")}")
                }
            }
        }
        Http.getJsonArray("$API/sync/watched/movies", headers())?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.optJSONObject("movie")?.optJSONObject("ids")?.optString("imdb")?.takeIf { it.startsWith("tt") }?.let { ids.add(it) }
        }
        if (ids.isNotEmpty()) Prefs.setWatched(ids, true)
        return ids.size
    }
}
