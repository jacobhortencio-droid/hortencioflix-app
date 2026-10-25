package com.hortencioflix.tv.data

/** Escolha de fontes: serviço debrid primeiro, troca de qualidade e alternativas quando uma fonte falha. */
object SourcePicker {
    private val debridTag = Regex("\\[(TB|RD|AD|PM|DL|OC|ED|PP)\\s*[+\\-]?", RegexOption.IGNORE_CASE)

    /** A fonte vem de um serviço debrid (ex.: «[TB+] Torrentio»)? */
    fun isDebrid(label: String) = debridTag.containsMatchIn(label)

    /** 0 = melhor. Com debrid: debrid em cache → link direto → resto. Sem debrid: link direto primeiro. */
    fun rank(s: StreamItem, hasDebrid: Boolean): Int {
        val deb = isDebrid(s.name)
        return when {
            hasDebrid && deb && s.cached -> 0
            !deb -> if (hasDebrid) 1 else 0
            else -> if (hasDebrid) 2 else 3
        }
    }

    /** Ordena por grupo (rank) e, dentro do grupo, da maior para a menor qualidade (estável). */
    fun order(list: List<StreamItem>, hasDebrid: Boolean): List<StreamItem> =
        list.sortedWith(compareBy({ rank(it, hasDebrid) }, { -it.quality }))

    /**
     * Índice da melhor fonte para a qualidade pedida (0 = a melhor de todas).
     * Sem a qualidade exata usa a mais próxima abaixo e, se não houver, a mais próxima acima.
     * A lista já vem ordenada, por isso a primeira de cada qualidade é a melhor.
     */
    fun indexFor(streams: List<StreamItem>, target: Int, skip: Set<Int> = emptySet()): Int? {
        val all = streams.indices.filter { it !in skip }
        // Só fontes prontas (em cache); as «download» ficam para escolha manual.
        val cand = all.filter { streams[it].cached }.ifEmpty { all }
        if (cand.isEmpty()) return null
        if (target == 0) return cand.first()
        cand.firstOrNull { streams[it].quality == target }?.let { return it }
        val below = cand.filter { streams[it].quality in 1 until target }.maxOfOrNull { streams[it].quality }
        if (below != null) return cand.first { streams[it].quality == below }
        val above = cand.filter { streams[it].quality > target }.minOfOrNull { streams[it].quality }
        if (above != null) return cand.first { streams[it].quality == above }
        return cand.first()
    }

    /** Quando uma fonte falha: tenta outra com a mesma qualidade e só depois as restantes. */
    fun nextAfterFail(streams: List<StreamItem>, failed: Set<Int>, current: Int): Int? {
        // Nunca salta sozinha para uma fonte que ainda não está no debrid (dá o vídeo «downloading to debrid»).
        val rest = streams.indices.filter { it !in failed && it != current && streams[it].cached }
        if (rest.isEmpty()) return null
        // Um add-on que já falhou 2 vezes neste vídeo (ex.: links «⚡ prontos» que dão o vídeo de espera)
        // fica para o fim: não se percorrem as fontes dele uma a uma.
        val tried = failed + current
        val bad = tried.mapNotNull { streams.getOrNull(it)?.addon }.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
        val pool = rest.filter { streams[it].addon !in bad }.ifEmpty { rest }
        val q = streams.getOrNull(current)?.quality ?: 0
        return pool.firstOrNull { streams[it].quality == q } ?: pool.first()
    }

    /** Há pelo menos uma fonte pronta a tocar já? */
    fun hasReady(streams: List<StreamItem>) = streams.any { it.cached }

    /**
     * O Torrentio, quando o torrent ainda não está no serviço debrid, devolve um vídeo curto
     * («TORRENT DOWNLOADING TO DEBRID»). Uma fonte debrid com menos de 2 minutos num filme/episódio é isso.
     */
    fun looksLikePlaceholder(s: StreamItem?, durationMs: Long, type: String): Boolean =
        s != null && isDebrid(s.name) && durationMs in 1..120_000 && (type == "movie" || type == "series")

    fun qualityLabel(q: Int) = when { q >= 2160 -> "4K"; q > 0 -> "${q}p"; else -> "?" }
}
