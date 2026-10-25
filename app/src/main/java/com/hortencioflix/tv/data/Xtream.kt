package com.hortencioflix.tv.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Conta Xtream Codes: filmes (VOD) e séries além dos canais. Funções bloqueantes. */
class Xtream(val server: String, val user: String, val pass: String) {
    companion object {
        /** Extrai servidor/utilizador/palavra-passe de um link get.php. */
        fun fromListUrl(url: String): Xtream? {
            if (!url.contains("get.php", true)) return null
            val u = Uri.parse(url)
            val user = u.getQueryParameter("username") ?: return null
            val pass = u.getQueryParameter("password") ?: return null
            val server = url.substringBefore("/get.php")
            return Xtream(server, user, pass)
        }
    }

    private fun api(action: String, extra: String = ""): String =
        "$server/player_api.php?username=${Addons.enc(user)}&password=${Addons.enc(pass)}&action=$action$extra"

    data class Category(val id: String, val name: String)

    private fun categories(action: String): List<Category> {
        val arr = Http.getJsonArray(api(action), emptyMap()) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Category(it.optString("category_id"), it.optString("category_name")) }
    }

    fun vodCategories() = categories("get_vod_categories")
    fun seriesCategories() = categories("get_series_categories")

    fun vod(categoryId: String?): List<MetaItem> {
        val arr = Http.getJsonArray(api("get_vod_streams", if (categoryId != null) "&category_id=$categoryId" else ""), emptyMap()) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.take(1500).map { o ->
            val ext = o.optString("container_extension").ifEmpty { "mp4" }
            val rating = o.optString("rating").takeIf { it.isNotEmpty() && it != "0" }
            MetaItem("xtv:${o.optString("stream_id")}:$ext", "movie", o.optString("name"), o.optString("stream_icon"),
                info = if (rating != null) "IMDb $rating" else "")
        }
    }

    fun series(categoryId: String?): List<MetaItem> {
        val arr = Http.getJsonArray(api("get_series", if (categoryId != null) "&category_id=$categoryId" else ""), emptyMap()) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.take(1500).map { o ->
            val rating = o.optString("rating").takeIf { it.isNotEmpty() && it != "0" }
            MetaItem("xts:${o.optString("series_id")}", "series", o.optString("name"), o.optString("cover"),
                background = o.optJSONArray("backdrop_path")?.optString(0) ?: "", description = o.optString("plot"),
                info = if (rating != null) "IMDb $rating" else "")
        }
    }

    /** Episódios de uma série (ids «xte:<id>:<ext>»). */
    fun episodes(seriesId: String): List<Episode> {
        val d = Http.getJsonAuth(api("get_series_info", "&series_id=$seriesId"), emptyMap()) ?: return emptyList()
        val eps = d.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        for (k in eps.keys()) {
            val arr: JSONArray = eps.optJSONArray(k) ?: continue
            for (i in 0 until arr.length()) {
                val e: JSONObject = arr.optJSONObject(i) ?: continue
                val info = e.optJSONObject("info")
                val ext = e.optString("container_extension").ifEmpty { "mp4" }
                out.add(Episode("xte:${e.optString("id")}:$ext", e.optInt("season", k.toIntOrNull() ?: 1), e.optInt("episode_num"),
                    e.optString("title"), info?.optString("movie_image") ?: "", info?.optString("plot") ?: "", info?.optString("releasedate") ?: ""))
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    fun streamFor(id: String): StreamItem? {
        val p = id.split(":")
        if (p.size < 3) return null
        val url = when (p[0]) {
            "xtv" -> "$server/movie/$user/$pass/${p[1]}.${p[2]}"
            "xte" -> "$server/series/$user/$pass/${p[1]}.${p[2]}"
            else -> return null
        }
        return StreamItem("Xtream", "", "", url, "", emptyMap(), 0, true, false)
    }
}
