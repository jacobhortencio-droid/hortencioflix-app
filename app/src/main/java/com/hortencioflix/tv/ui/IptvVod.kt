package com.hortencioflix.tv.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.Channel
import com.hortencioflix.tv.data.FileName
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Session
import com.hortencioflix.tv.data.StreamItem
import com.hortencioflix.tv.data.SubFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Filmes e séries que vêm de uma lista IPTV: abrem como filme (com retoma, barra de tempo) e procuram legendas por conta própria. */
object IptvVod {
    private val videoExt = Regex("\\.(mp4|mkv|avi|mov|m4v|wmv|webm)(\\?.*)?$", RegexOption.IGNORE_CASE)

    fun isVod(ch: Channel): Boolean {
        if (ch.url.startsWith("stremio:")) return false
        val u = ch.url
        return u.contains("/movie/") || u.contains("/series/") || videoExt.containsMatchIn(u)
    }

    private fun idFor(url: String) = "iptv:" + Integer.toHexString(url.hashCode())

    /** Procura legendas (máx. 7 s) e abre o leitor. [busy] mostra/esconde um indicador no ecrã de origem. */
    fun open(a: FragmentActivity, ch: Channel, busy: (Boolean) -> Unit = {}) {
        busy(true)
        a.lifecycleScope.launch {
            val p = FileName.parse(ch.name)
            val hint = when {
                ch.url.contains("/series/") -> "series"
                ch.url.contains("/movie/") -> "movie"
                else -> null
            }
            val found = withContext(Dispatchers.IO) {
                try { withTimeoutOrNull(7000) { SubFinder.find(ch.name, hint) } } catch (e: Exception) { null }
            }
            busy(false)
            val id = idFor(ch.url)
            val type = found?.type ?: hint ?: if (p?.season != null) "series" else "movie"
            val epLabel = if (type == "series" && p?.season != null) "  ·  T${p.season} E${p.episode}" else ""
            val meta = MetaItem(id, type, (found?.title ?: p?.title ?: ch.name) + epLabel, found?.poster?.ifEmpty { ch.logo } ?: ch.logo)
            Prefs.put("iptvVod:$id", JSONObject().put("url", ch.url).put("name", ch.name).put("logo", ch.logo).put("group", ch.group).toString())
            val stream = StreamItem("IPTV", "", ch.name, ch.url, ch.name, emptyMap(), Addons.quality(ch.name), true, false)
            val subs = found?.subs ?: emptyList()
            Session.vod(meta, id, listOf(stream), 0, subs, emptyList(), Prefs.resumeFor(id, id))
            if (found != null && subs.isEmpty()) a.toast("Não encontrei legendas para «${found.title}».")
            PlayerActivity.start(a)
        }
    }

    /** «Continuar a ver» de um filme IPTV: volta a abrir a mesma ligação. Devolve false se já não existe. */
    fun resume(a: FragmentActivity, m: MetaItem): Boolean {
        val raw = Prefs.str("iptvVod:${m.id}")
        if (raw.isEmpty()) return false
        val o = try { JSONObject(raw) } catch (e: Exception) { return false }
        open(a, Channel(o.optString("name"), o.optString("logo"), o.optString("url"), o.optString("group")))
        return true
    }
}
