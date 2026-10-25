package com.hortencioflix.tv.data

/**
 * Gama de imagem de uma fonte, lida do nome: Dolby Vision (e se tem base HDR10), HDR10+, HDR10, HLG ou SDR.
 *
 * Regras que importam na prática:
 * - Dolby Vision com HDR10 por baixo («DV HDR», perfil 7 ou 8) toca em qualquer ecrã HDR: sem DV vê-se em HDR10.
 * - Dolby Vision «só DV» (perfil 5, típico dos WEB-DL) num ecrã sem DV fica com cores roxas/verdes.
 * - HDR10+ tem sempre HDR10 por baixo.
 */
object VideoRange {
    private val I = RegexOption.IGNORE_CASE
    private val dv = Regex("\\bdv\\b|dovi|dolby[ .-]?vision|\\bdv[ .-]?hdr", I)
    private val dvProfile = Regex("(?:\\bdv|dovi|dolby[ .-]?vision)[ .-]?(?:profile[ .-]?|p)([4578])(?:\\.\\d)?\\b", I)
    private val hdr10p = Regex("hdr10\\+|hdr10plus|hdr10p\\b|hdr\\+", I)
    private val hlg = Regex("\\bhlg\\b", I)
    // «HDR» genérico conta como HDR10 (é o que quase sempre é); um Blu-ray UHD é sempre HDR
    private val hdr10 = Regex("\\bhdr(?:10)?\\b|\\bhdr[ .-]|\\buhd[ .-]?blu-?ray\\b|\\bpq\\b|bt\\.?2020|\\bhdr10\\b", I)

    /** Tipo principal (o melhor que a fonte tem): "dv", "hdr10p", "hdr10", "hlg" ou "sdr". */
    fun kind(t: String): String = when {
        isDv(t) -> "dv"
        hdr10p.containsMatchIn(t) -> "hdr10p"
        hdr10.containsMatchIn(t) -> "hdr10"
        hlg.containsMatchIn(t) -> "hlg"
        else -> "sdr"
    }

    fun isDv(t: String) = dv.containsMatchIn(t)
    fun isHdr10p(t: String) = hdr10p.containsMatchIn(t)
    fun isHlg(t: String) = hlg.containsMatchIn(t)

    /** Perfil Dolby Vision indicado no nome (5, 7, 8) ou 0 se não disser. */
    fun dvProfile(t: String): Int = dvProfile.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Dolby Vision com HDR10 por baixo (vê-se bem em qualquer ecrã HDR). */
    fun dvWithHdr(t: String): Boolean {
        if (!isDv(t)) return false
        val p = dvProfile(t)
        if (p == 7 || p == 8) return true
        if (p == 5) return false
        return hdr10p.containsMatchIn(t) || hdr10.containsMatchIn(t)
    }

    /** Dolby Vision sem base HDR10 (perfil 5): só fica bem num ecrã com Dolby Vision. */
    fun dvOnly(t: String) = isDv(t) && !dvWithHdr(t)

    /** Qualquer HDR que um ecrã HDR10 mostra bem (HDR10, HDR10+, HLG, DV com base HDR10). */
    fun isHdr(t: String) = hdr10.containsMatchIn(t) || hdr10p.containsMatchIn(t) || hlg.containsMatchIn(t) || dvWithHdr(t)
    fun isSdr(t: String) = !isDv(t) && !isHdr(t)

    fun text(s: StreamItem) = "${s.name} ${s.title} ${s.filename}"

    /** Etiqueta curta para a lista. */
    fun label(t: String): String? = when {
        isDv(t) && dvOnly(t) -> "Dolby Vision (só DV)"
        isDv(t) && isHdr10p(t) -> "DV + HDR10+"
        isDv(t) -> "DV + HDR10"
        isHdr10p(t) -> "HDR10+"
        hdr10.containsMatchIn(t) -> "HDR10"
        isHlg(t) -> "HLG"
        else -> null
    }

    /**
     * O ecrã mostra bem esta imagem? null = sim (ou não se sabe); senão, o aviso.
     * Usado para avisar nos filtros e na lista em vez de deixar escolher às cegas.
     */
    fun screenWarning(t: String): String? {
        if (!DeviceCaps.known) return null
        return when {
            dvOnly(t) && !DeviceCaps.dv -> "este ecrã não tem Dolby Vision: cores erradas"
            (isDv(t) || isHdr(t)) && !DeviceCaps.hdr && !DeviceCaps.dv -> "ecrã sem HDR: cores mais desbotadas"
            else -> null
        }
    }

    /**
     * Preferência de imagem: "auto" = o melhor que o ecrã mostra (Dolby Vision > HDR > SDR);
     * num ecrã sem HDR, as cópias SDR passam à frente (as HDR ficariam com cores desbotadas).
     */
    fun wanted(pref: String): String = when (pref) {
        "dv", "hdr", "sdr" -> pref
        "off" -> ""
        else -> when { DeviceCaps.known && DeviceCaps.dv -> "dv"; DeviceCaps.known && DeviceCaps.hdr -> "hdr"; DeviceCaps.known -> "sdr"; else -> "" }
    }

    fun matches(t: String, want: String): Boolean = when (want) {
        "dv" -> isDv(t) && (DeviceCaps.dv || !DeviceCaps.known || dvWithHdr(t))
        "hdr" -> isHdr(t) || (isDv(t) && DeviceCaps.dv)
        "sdr" -> isSdr(t)
        else -> false
    }

    /** Reordena só dentro de cada qualidade (não troca 4K por 1080p): as que têm a imagem desejada sobem. */
    fun prefer(list: List<StreamItem>, want: String): List<StreamItem> {
        if (want.isEmpty() || list.size < 2) return list
        val out = ArrayList<StreamItem>(list.size)
        var i = 0
        while (i < list.size) {
            var j = i
            while (j < list.size && list[j].quality == list[i].quality) j++
            val run = list.subList(i, j)
            val (a, b) = run.partition { matches(text(it), want) }
            out.addAll(a); out.addAll(b)
            i = j
        }
        return out
    }
}
