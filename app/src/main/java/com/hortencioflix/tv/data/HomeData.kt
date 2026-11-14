package com.hortencioflix.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Dados do Início partilhados pela TV e pelo telemóvel. Funções bloqueantes. */
object HomeData {
    fun localContinue(): List<MetaItem> {
        val arr = Prefs.progressList()
        val out = ArrayList<MetaItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = o.optLong("d")
            val prog = if (d > 0) (o.optLong("t").toFloat() / d).coerceIn(0f, 1f) else 0f
            val v = o.str("v")
            val id = o.str("m")
            if (id.isEmpty()) continue
            val info = listOf(epLabel(v), remaining(o.optLong("t"), d)).filter { it.isNotEmpty() }.joinToString(" · ")
            // entradas vindas da Roku podem chegar sem nome/tipo: nunca mostrar «null»
            val type = o.str("y").ifEmpty { if (v.count { it == ':' } >= 2) "series" else "movie" }
            out.add(MetaItem(id, type, o.str("n"), o.str("p"), info = info, videoId = v, progress = prog))
        }
        return out
    }

    private fun remaining(posSec: Long, durSec: Long): String {
        val left = (durSec - posSec) / 60
        return if (durSec <= 0) "" else if (left >= 60) "faltam ${left / 60} h ${left % 60} min" else "faltam ${maxOf(1, left)} min"
    }

    private fun epLabel(videoId: String): String {
        val p = videoId.split(":")
        return if (p.size >= 3) "T${p[1]} E${p[2]}" else ""
    }

    fun continueItems(): List<MetaItem> {
        val seen = HashSet<String>()
        val hidden = Prefs.array("hiddenContinue")
        val hid = (0 until hidden.length()).map { hidden.optString(it) }.toSet()
        val remote = Stremio.continueItems().map { if (it.info.isNotEmpty()) it else it.copy(info = epLabel(it.videoId)) }
        val byId = remote.associateBy { it.id }
        val local = localContinue().map { m ->
            if (m.name.isNotEmpty() && m.poster.isNotEmpty()) m
            else byId[m.id]?.let { r -> m.copy(name = m.name.ifEmpty { r.name }, poster = m.poster.ifEmpty { r.poster }) }?.takeIf { it.name.isNotEmpty() } ?: repair(m)
        }
        return (local + remote).filter { seen.add(it.id) && it.id !in hid }.take(20)
    }

    /**
     * Entrada sem nome ou capa (veio de outro aparelho incompleta): vai buscar ao catálogo e corrige o que
     * está guardado, para não voltar a pedir. Bloqueante — chamada só fora da thread principal.
     */
    private fun repair(m: MetaItem): MetaItem {
        if (!m.id.startsWith("tt")) return if (m.name.isEmpty()) m.copy(name = "Sem título") else m
        val meta = try { Http.getJson("${Addons.CINEMETA}/meta/${m.type}/${m.id}.json")?.optJSONObject("meta") } catch (e: Exception) { null }
        val name = m.name.ifEmpty { meta?.str("name").orEmpty() }
        val poster = m.poster.ifEmpty { meta?.str("poster").orEmpty().ifEmpty { "https://images.metahub.space/poster/medium/${m.id}/img" } }
        if (name.isEmpty()) return m.copy(name = "Sem título", poster = poster)
        try {
            val arr = Prefs.progressList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.str("m") == m.id) { o.put("n", name); o.put("p", poster); o.put("y", m.type) }
            }
            Prefs.putArray("progress", arr)
        } catch (e: Exception) {}
        return m.copy(name = name, poster = poster)
    }

    /** Retira um título de «Continuar a ver» (local e conta Stremio). */
    fun removeContinue(metaId: String) {
        Prefs.removeProgress(metaId)
        val a = Prefs.array("hiddenContinue")
        a.put(metaId)
        while (a.length() > 100) a.remove(0)
        Prefs.putArray("hiddenContinue", a)
        try { Stremio.clearProgress(metaId) } catch (e: Exception) {}
    }

    // ---------- Novos episódios das séries que acompanhas ----------

    /** Episódios saídos nos últimos 10 dias das séries da biblioteca / em curso (cache de 6 h). Bloqueante. */
    fun newEpisodes(): List<MetaItem> {
        val now = System.currentTimeMillis()
        val cachedAt = Prefs.str("newEpAt", "0").toLongOrNull() ?: 0L
        if (now - cachedAt < 6 * 3600_000L) {
            val a = Prefs.array("newEp")
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { metaFromJson(it) }
        }
        val series = (Stremio.libraryItems() + localContinue()).filter { it.type == "series" && it.id.startsWith("tt") }.distinctBy { it.id }.take(25)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val out = ArrayList<Pair<Long, MetaItem>>()
        for (sr in series) {
            val meta = Http.getJson("${Addons.CINEMETA}/meta/series/${sr.id}.json")?.optJSONObject("meta") ?: continue
            for (ep in Addons.episodes(meta)) {
                if (ep.season == 0) continue
                val t = try { fmt.parse(ep.released.take(10))?.time ?: 0L } catch (e: Exception) { 0L }
                if (t <= 0 || t > now + 86_400_000L || t < now - 10 * 86_400_000L) continue
                if (Prefs.isWatched(ep.id)) continue
                out.add(t to MetaItem(sr.id, "series", meta.optString("name", sr.name), meta.optString("poster", sr.poster),
                    info = "${ep.label} · novo", videoId = ep.id, progress = 0f))
            }
        }
        val list = out.sortedByDescending { it.first }.map { it.second }.distinctBy { it.id }.take(20)
        val a = JSONArray()
        for (m in list) a.put(metaToJson(m))
        Prefs.putArray("newEp", a)
        Prefs.put("newEpAt", now.toString())
        return list
    }

    // ---------- Cache do Início (abre logo com os dados da última vez) ----------

    private fun cacheFile() = File(Prefs.app.filesDir, if (Prefs.profileId == "main") "home_cache.json" else "home_cache_${Prefs.profileId}.json")

    private fun metaToJson(m: MetaItem) = JSONObject().put("i", m.id).put("t", m.type).put("n", m.name).put("p", m.poster)
        .put("b", m.background).put("d", m.description).put("f", m.info).put("v", m.videoId)

    private fun metaFromJson(o: JSONObject) = MetaItem(o.optString("i"), o.optString("t"), o.optString("n"), o.optString("p"),
        o.optString("b"), o.optString("d"), o.optString("f"), o.optString("v"))

    fun saveRows(rows: List<Pair<String, List<MetaItem>>>) {
        try {
            val a = JSONArray()
            for ((t, items) in rows) {
                val arr = JSONArray()
                for (m in items) arr.put(metaToJson(m))
                a.put(JSONObject().put("t", t).put("i", arr))
            }
            cacheFile().writeText(a.toString())
        } catch (e: Exception) {
        }
    }

    fun loadRows(): List<Pair<String, List<MetaItem>>> = try {
        val f = cacheFile()
        if (!f.exists()) emptyList() else {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
                val arr = o.optJSONArray("i") ?: JSONArray()
                o.optString("t") to (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { metaFromJson(it) }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Sincroniza a conta Stremio se a última sincronização tiver mais de 30 min. */
    fun syncIfNeeded(force: Boolean) {
        if (!Stremio.loggedIn) return
        val last = Prefs.str("lastSync", "0").toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        if (force || now - last > 30 * 60 * 1000L) {
            Stremio.sync()
            Prefs.put("lastSync", now.toString())
        }
    }
}
