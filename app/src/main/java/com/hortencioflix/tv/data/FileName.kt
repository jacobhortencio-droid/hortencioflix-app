package com.hortencioflix.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Reconhece títulos a partir de nomes de ficheiros de torrents (ex.: «MobLand.S02E03.2160p…»). */
object FileName {
    data class Parsed(val title: String, val year: Int?, val season: Int?, val episode: Int?, val quality: String)

    private val I = RegexOption.IGNORE_CASE
    private val sitePrefix = Regex("^\\s*(www\\.)?[\\w-]+\\.(org|com|net|to|se|cc|me|in|xyz|info|io|tv|ws|lol|nu)\\s*[-–:]\\s*", I)
    private val bracket = Regex("^\\s*\\[[^\\]]*\\]\\s*")
    private val ext = Regex("\\.(mkv|mp4|avi|ts|m4v|mov|wmv|webm)$", I)
    private val se = Regex("\\bS(\\d{1,2})\\s?E(\\d{1,3})\\b", I)
    private val sx = Regex("\\b(\\d{1,2})x(\\d{2,3})\\b", I)
    private val year = Regex("\\b(19[3-9]\\d|20[0-4]\\d)\\b")
    private val junk = Regex("\\b(2160p|1080p|720p|480p|4k|uhd|web[- ]?dl|webrip|web|bluray|blu[- ]ray|brrip|bdrip|hdtv|dvdrip|multi|remux|hdr|x264|x265|h264|h265|hevc|proper|repack|complete|season)\\b", I)

    fun parse(raw: String): Parsed? {
        var s = raw.trim().substringAfterLast('/')
        s = sitePrefix.replace(s, "")
        s = bracket.replace(s, "")
        s = ext.replace(s, "")
        val norm = s.replace('.', ' ').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        val m1 = se.find(norm) ?: sx.find(norm)
        val y = year.find(norm)
        val cuts = listOfNotNull(m1?.range?.first, y?.range?.first, junk.find(norm)?.range?.first).filter { it > 0 }
        val cut = cuts.minOrNull()
        var title = if (cut != null) norm.substring(0, cut) else norm
        title = title.replace(Regex("[\\-–(\\[\\s]+$"), "").trim()
        if (title.length < 2) return null
        val q = when (Addons.quality(norm)) { 2160 -> "4K"; 1440 -> "1440p"; 1080 -> "1080p"; 720 -> "720p"; 480 -> "480p"; else -> "" }
        val yr = y?.groupValues?.get(1)?.toIntOrNull()
        return Parsed(title, yr, m1?.groupValues?.get(1)?.toIntOrNull(), m1?.groupValues?.get(2)?.toIntOrNull(), q)
    }

    /** Substitui itens com nomes de ficheiros pelo título real (capa, tipo e episódio). */
    suspend fun enrich(items: List<MetaItem>): List<MetaItem> = coroutineScope {
        items.take(30).map { m ->
            async(Dispatchers.IO) {
                val p = parse(m.name) ?: return@async m
                val isSeries = p.season != null
                val type = if (isSeries) "series" else "movie"
                val found = Addons.metas(Http.getJson("${Addons.CINEMETA}/catalog/$type/top/search=${Addons.enc(p.title)}.json"), 10)
                val best = found.firstOrNull { f -> p.year != null && f.info.startsWith(p.year.toString()) } ?: found.firstOrNull()
                val epLabel = if (isSeries) "T${p.season} E${p.episode}" else ""
                if (best == null) {
                    m.copy(name = listOf(p.title, epLabel).filter { it.isNotEmpty() }.joinToString(" · "), info = p.quality)
                } else {
                    MetaItem(
                        id = best.id, type = type,
                        name = best.name + (if (isSeries) " · $epLabel" else ""),
                        poster = best.poster, background = best.background, description = best.description,
                        info = listOf(p.quality, best.info).filter { it.isNotEmpty() }.joinToString("   ·   "),
                        videoId = if (isSeries) "${best.id}:${p.season}:${p.episode}" else best.id
                    )
                }
            }
        }.awaitAll().distinctBy { it.videoId.ifEmpty { it.id } }
    }
}
