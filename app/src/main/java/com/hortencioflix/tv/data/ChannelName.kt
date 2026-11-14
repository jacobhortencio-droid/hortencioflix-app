package com.hortencioflix.tv.data

/**
 * Nome limpo de um canal IPTV. As listas trazem etiquetas técnicas coladas ao nome
 * («|4K| TF1 HDR/UHD/4K», «PT - RTP 1 FHD HEVC»): aqui separam-se — o nome fica «TF1» / «RTP 1» e as
 * etiquetas passam a selos compactos (PT · FHD · HEVC). O país do prefixo fica como primeiro selo.
 */
object ChannelName {
    data class Clean(val name: String, val badges: List<String>, val region: String = "")

    private val I = RegexOption.IGNORE_CASE
    private const val B = "(?<![A-Za-z0-9])"   // início de palavra
    private const val E = "(?![A-Za-z0-9])"    // fim de palavra
    private val tokens = Regex("$B(4K\\+?|UHD|FHD|1080[pi]?|720p?|HDR(?:10\\+?)?|HEVC|H\\.?265|HD|SD|[56]0\\s?FPS|RAW)$E", I)
    private const val SEP = "/|-–·[](){}:"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Clean>()

    fun of(raw: String): Clean = cache[raw] ?: compute(raw).also { if (cache.size < 80_000) cache[raw] = it }

    private fun compute(full: String): Clean {
        // país no início («PT - », «FR: », «|UK| »): sai do nome e passa a selo
        var region = ""
        var raw = full
        ChannelFacets.splitPrefix(full)?.let { (code, rest) -> if (rest.length >= 2) { region = code; raw = rest } }
        val found = LinkedHashSet<String>()
        for (m in tokens.findAll(raw)) {
            val t = m.value.uppercase().replace(" ", "")
            found.add(when {
                t.startsWith("4K") || t == "UHD" -> "4K"
                t == "FHD" || t.startsWith("1080") -> "FHD"
                t.startsWith("720") || t == "HD" -> "HD"
                t.startsWith("HDR") -> "HDR"
                t == "HEVC" || t.startsWith("H") && t.endsWith("265") -> "HEVC"
                t.endsWith("FPS") -> t.take(2) + "fps"
                else -> t
            })
        }
        // tira as etiquetas e depois os separadores que ficaram soltos («| |», « / / », «( )»)
        var s = tokens.replace(raw, " ").split(Regex("\\s+"))
            .map { it.trim('/') }
            .filter { p -> p.isNotEmpty() && !p.all { it in SEP } }
            .joinToString(" ")
        if (s.length < 2) s = raw.trim()
        if (region.isEmpty()) ChannelFacets.splitPrefix(s)?.let { (code, rest) -> if (rest.length >= 2) { region = code; s = rest } }
        // um só selo de resolução (o melhor), depois os restantes
        val res = listOf("4K", "FHD", "HD", "SD").firstOrNull { it in found }
        val rest = found.filter { it !in setOf("4K", "FHD", "HD", "SD", "RAW") }
        return Clean(s, listOfNotNull(region.ifEmpty { null }, res) + rest, region)
    }
}
