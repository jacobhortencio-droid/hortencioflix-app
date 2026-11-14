package com.hortencioflix.tv.data

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * «A dar agora» para canais de contas Xtream, pedido só para os canais que estão no ecrã.
 * O guia completo (XMLTV) de uma conta com milhares de canais tem dezenas de MB e muitas vezes não chega
 * a carregar; a API da conta dá o programa atual de um canal em poucos bytes (get_short_epg).
 * Guarda 10 minutos; no máximo 3 pedidos ao mesmo tempo.
 */
object XtreamNow {
    data class Now(val title: String, val start: Long, val end: Long)

    private val rx = Regex("^(https?://[^/]+)/live/([^/]+)/([^/]+)/(\\d+)\\.[A-Za-z0-9]+$")
    private val cache = ConcurrentHashMap<String, Pair<Long, Now?>>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private const val TTL = 10 * 60_000L

    fun supports(url: String) = rx.matches(url)

    /** O que está guardado e ainda está a dar. */
    fun cached(url: String): Now? {
        val c = cache[url] ?: return null
        val n = c.second ?: return null
        return if (n.end > System.currentTimeMillis()) n else null
    }

    private fun fresh(url: String): Boolean {
        val c = cache[url] ?: return false
        val now = System.currentTimeMillis()
        return now - c.first < TTL && (c.second == null || c.second!!.end > now)
    }

    /** Pede o programa atual (se ainda não se sabe). [done] corre na thread principal quando houver novidade. */
    fun request(url: String, done: () -> Unit) {
        val m = rx.find(url) ?: return
        if (fresh(url) || !inFlight.add(url)) return
        pool.execute {
            var found: Now? = null
            try {
                val (server, user, pass, id) = m.destructured
                val api = "$server/player_api.php?username=${Addons.enc(user)}&password=${Addons.enc(pass)}&action=get_short_epg&stream_id=$id&limit=2"
                val txt = Http.getStringH(api, mapOf("User-Agent" to IptvLoader.uaFor(url)))
                val arr = if (txt != null && txt.trimStart().startsWith("{")) JSONObject(txt).optJSONArray("epg_listings") else null
                val now = System.currentTimeMillis()
                for (i in 0 until (arr?.length() ?: 0)) {
                    val o = arr!!.optJSONObject(i) ?: continue
                    val start = (o.str("start_timestamp").toLongOrNull() ?: 0L) * 1000
                    val end = (o.str("stop_timestamp").toLongOrNull() ?: 0L) * 1000
                    if (end <= now) continue
                    val title = try { String(android.util.Base64.decode(o.str("title"), android.util.Base64.DEFAULT), Charsets.UTF_8).trim() } catch (e: Exception) { o.str("title") }
                    if (title.isNotEmpty() && start <= now + 60_000) { found = Now(title, start, end); break }
                }
            } catch (e: Exception) {
            }
            cache[url] = System.currentTimeMillis() to found
            inFlight.remove(url)
            if (found != null) main.post(done)
        }
    }
}
