package com.hortencioflix.tv.data

/** Procura legendas para conteúdos que não vêm de um add-on (listas IPTV, Xtream, TorBox): descobre o título no Cinemeta e pede as legendas. */
object SubFinder {
    data class Found(val imdb: String, val type: String, val title: String, val poster: String, val subs: List<SubtitleItem>)

    private val cache = HashMap<String, Found>()

    /**
     * [raw] é o nome como aparece na lista (ex.: «Grimcutty (2022)» ou «Serie.S01E02.1080p»).
     * [forceType]/[season]/[episode] sobrepõem o que se deduz do nome.
     */
    suspend fun find(raw: String, forceType: String? = null, season: Int? = null, episode: Int? = null): Found? {
        val p = FileName.parse(raw)
        val title = (p?.title ?: raw).trim()
        if (title.length < 2) return null
        val s = season ?: p?.season
        val e = episode ?: p?.episode
        val type = forceType ?: if (s != null) "series" else "movie"
        val key = "$type|${Epg.norm(title)}|${p?.year ?: 0}|${s ?: 0}|${e ?: 0}"
        cache[key]?.let { return it }
        val found = Addons.metas(Http.getJson("${Addons.CINEMETA}/catalog/$type/top/search=${Addons.enc(title)}.json"), 10)
        val norm = Epg.norm(title)
        val best = found.firstOrNull { f -> p?.year != null && f.info.startsWith(p.year.toString()) && Epg.norm(f.name) == norm }
            ?: found.firstOrNull { f -> Epg.norm(f.name) == norm }
            ?: found.firstOrNull { f -> p?.year != null && f.info.startsWith(p.year.toString()) }
            ?: found.firstOrNull()
            ?: return null
        val vid = if (type == "series" && s != null && e != null) "${best.id}:$s:$e" else best.id
        val subs = try { Addons.subtitles(type, vid) } catch (x: Exception) { emptyList() }
        val out = Found(best.id, type, best.name, best.poster, subs)
        if (subs.isNotEmpty()) cache[key] = out
        return out
    }
}
