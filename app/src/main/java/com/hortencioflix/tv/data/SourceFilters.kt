package com.hortencioflix.tv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Filtros das fontes, partilhados pelo ecrã de detalhes, pelo «Ver» e pelo leitor.
 *
 * Lógica: dentro do mesmo grupo os valores somam-se (OU — «4K ou 1080p», «Dolby Vision ou HDR10+»);
 * entre grupos têm de bater todos (E — «4K» e «Dolby Vision» e «PT-BR»).
 * Os filtros fixados aplicam-se a todos os títulos; se um título não tiver nenhuma fonte assim,
 * mostram-se todas (nunca fica sem nada para ver).
 */
object SourceFilters {
    private val I = RegexOption.IGNORE_CASE

    // Origem do ficheiro
    private val rxRemux = Regex("\\bremux\\b|\\bbdremux\\b", I)
    private val rxBluray = Regex("blu-?ray|\\bbd-?rip\\b|\\bbr-?rip\\b|\\bbdmux\\b|\\buhd[ .-]?bd\\b", I)
    private val rxWeb = Regex("web[ .-]?dl|web-?rip|\\bweb\\b|\\bamzn\\b|\\bnf\\b|\\bdsnp\\b|\\batvp\\b|\\bhmax\\b|\\bpcok\\b|\\bhulu\\b", I)
    private val rxHdtv = Regex("\\bhdtv\\b|\\bpdtv\\b|\\btv-?rip\\b", I)
    private val rxCam = Regex("\\bcam(?:-?rip)?\\b|\\bhd-?cam\\b|\\bhd-?ts\\b|telesync|\\btelecine\\b|\\bhd-?tc\\b|\\bscreener\\b|\\bdvd-?scr\\b|[ \\[(]ts[ \\])]|[ \\[(]tc[ \\])]", I)

    // Áudio
    private val rxAtmos = Regex("atmos", I)
    private val rxLossless = Regex("truehd|dts[ .-]?hd|dts[ .-]?x\\b|dts[ .-]?ma\\b|\\bflac\\b|\\blpcm\\b|\\bpcm\\b", I)
    private val rxSurround = Regex("[57]\\.1|\\b6ch\\b|\\b8ch\\b|ddp?[ .]?[57]|dd\\+[ .]?[57]|e-?ac-?3|atmos|truehd|dts", I)

    /** Origem principal: "remux", "bluray", "web", "hdtv", "cam" ou "". */
    fun source(t: String): String = when {
        rxCam.containsMatchIn(t) -> "cam"
        rxRemux.containsMatchIn(t) -> "remux"
        rxBluray.containsMatchIn(t) -> "bluray"
        rxWeb.containsMatchIn(t) -> "web"
        rxHdtv.containsMatchIn(t) -> "hdtv"
        else -> ""
    }

    fun qualityLabel(q: Int) = when (q) { 2160 -> "4K"; 1440 -> "1440p"; 1080 -> "1080p"; 720 -> "720p"; 480 -> "SD"; else -> "?" }

    /** Tamanho em GB a partir do nome (0 se não disser). */
    fun sizeGb(t: String): Double {
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s?(GB|MB|GiB|MiB|TB)", I).find(t) ?: return 0.0
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0.0
        return when (m.groupValues[2].uppercase()) { "MB", "MIB" -> v / 1024; "TB" -> v * 1024; else -> v }
    }

    /** A fonte cumpre o valor `v` do grupo `group`? */
    fun facet(s: StreamItem, group: String, v: String): Boolean {
        val t = VideoRange.text(s)
        return when (group) {
            "q" -> qualityLabel(s.quality) == v
            "img" -> when (v) {
                "anyhdr" -> VideoRange.isHdr(t) || (VideoRange.isDv(t) && (DeviceCaps.dv || !DeviceCaps.known))
                "dv" -> VideoRange.isDv(t)
                else -> VideoRange.kind(t) == v          // hdr10p, hdr10, hlg, sdr
            }
            "src" -> source(t) == v
            "nocam" -> source(t) != "cam"
            "lang" -> AudioLang.matches(AudioLang.of(s), v)
            "aud" -> when (v) {
                "atmos" -> rxAtmos.containsMatchIn(t)
                "lossless" -> rxLossless.containsMatchIn(t)
                "51" -> rxSurround.containsMatchIn(t)
                else -> true
            }
            "cached" -> s.cached
            "compat" -> DeviceCaps.problem(t, s.quality) == null && VideoRange.screenWarning(t)?.contains("cores erradas") != true
            else -> true
        }
    }

    /** Passa em todos os grupos (opcionalmente ignorando um grupo, para contar as opções desse grupo). */
    fun passes(s: StreamItem, filters: Map<String, Set<String>>, except: String? = null): Boolean =
        filters.all { (g, vs) -> g == except || vs.isEmpty() || vs.any { facet(s, g, it) } }

    // ---------- Filtros fixados (para todos os títulos) ----------

    fun pinned(): Map<String, Set<String>> = decode(Prefs.str("pinFilters"))

    fun setPinned(f: Map<String, Set<String>>) = Prefs.put("pinFilters", encode(f))

    fun encode(f: Map<String, Set<String>>): String {
        val o = JSONObject()
        for ((g, vs) in f) if (vs.isNotEmpty()) o.put(g, JSONArray(vs.toList()))
        return if (o.length() == 0) "" else o.toString()
    }

    fun decode(s: String): Map<String, Set<String>> {
        if (s.isBlank()) return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = LinkedHashMap<String, Set<String>>()
            for (k in o.keys()) {
                val a = o.optJSONArray(k) ?: continue
                val vs = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet()
                if (vs.isNotEmpty()) out[k] = vs
            }
            out
        } catch (e: Exception) { emptyMap() }
    }

    /**
     * Com filtros fixados, as fontes que os cumprem vão para a frente (as outras ficam atrás, como reserva).
     * Assim o «Ver» e o leitor escolhem logo uma que cumpre, mas nunca ficam sem alternativa.
     * Só reordena as fontes prontas (em cache): uma fonte por descarregar nunca passa à frente de uma pronta.
     */
    fun pinnedFirst(list: List<StreamItem>, f: Map<String, Set<String>> = pinned()): List<StreamItem> {
        if (f.isEmpty() || list.size < 2) return list
        val (ready, rest) = list.partition { it.cached }
        val (a, b) = ready.partition { passes(it, f) }
        return a + b + rest
    }

    // ---------- Nomes para mostrar ----------

    fun valueLabel(group: String, v: String): String = when (group) {
        "q" -> if (v == "?") "Outras" else v
        "img" -> when (v) { "anyhdr" -> "Qualquer HDR"; "dv" -> "Dolby Vision"; "hdr10p" -> "HDR10+"; "hdr10" -> "HDR10"; "hlg" -> "HLG"; "sdr" -> "SDR"; else -> v }
        "src" -> when (v) { "remux" -> "Remux"; "bluray" -> "Blu-ray"; "web" -> "WEB"; "hdtv" -> "TV"; else -> v }
        "nocam" -> "Sem CAM/TS"
        "lang" -> when (v) { "br" -> "🇧🇷 Dublado PT-BR"; "pt" -> "🇵🇹 Dobrado PT-PT"; else -> v }
        "aud" -> when (v) { "atmos" -> "Atmos"; "lossless" -> "Som sem perdas"; "51" -> "Som 5.1+"; else -> v }
        "cached" -> "Em cache"
        "compat" -> "Compatíveis"
        else -> v
    }

    /** «4K · Dolby Vision ou HDR10+ · PT-BR». */
    fun describe(f: Map<String, Set<String>>): String =
        f.filterValues { it.isNotEmpty() }.entries.joinToString("  ·  ") { (g, vs) -> vs.joinToString(" ou ") { valueLabel(g, it) } }
}
