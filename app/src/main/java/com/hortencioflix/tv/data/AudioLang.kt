package com.hortencioflix.tv.data

/**
 * Língua do ÁUDIO de uma fonte (pelo nome/descrição): dobrado em português do Brasil, de Portugal,
 * ou português sem variante clara. Legendas («legendado», «subs») não contam como áudio.
 */
object AudioLang {
    const val BR = "br"
    const val PT = "pt"
    const val POR = "por"   // português, variante desconhecida

    private val br = Regex("dublad|pt-?br|\\bbr[ .-]?dub|\\bnacional\\b|brazil|brasil|portugu[eê]s \\(br|🇧🇷|\\bpob\\b", RegexOption.IGNORE_CASE)
    private val pt = Regex("pt-?pt\\b|\\bportugal\\b|dobrad|europeu|european portuguese|🇵🇹", RegexOption.IGNORE_CASE)
    private val por = Regex("portugu|\\bpor\\b|\\bpt\\b", RegexOption.IGNORE_CASE)
    // a palavra de legenda e a língua logo a seguir («Legendado PT», «Subs: Português»)
    private val subsOnly = Regex("(legendad\\w*|\\bleg\\b|subtitles?|\\bsubs?\\b)[ .:_-]*(pt(-?br|-?pt)?|por\\w*|portugu\\w*)?", RegexOption.IGNORE_CASE)

    fun of(text: String): String? {
        val hasBr = br.containsMatchIn(text)
        val hasPt = pt.containsMatchIn(text)
        // «🇵🇹» sozinho num add-on brasileiro é muitas vezes só «português»: com sinais BR, fica BR
        if (hasBr) return BR
        if (hasPt) return PT
        // «português» que só aparece ligado a legendas («legendado», «subs pt») não é áudio dobrado
        if (por.containsMatchIn(subsOnly.replace(text, " "))) return POR
        return null
    }

    fun of(s: StreamItem): String? = of("${s.name} ${s.title} ${s.filename}")

    /** Corresponde à preferência «br» / «pt» (o português sem variante serve para as duas). */
    fun matches(lang: String?, pref: String): Boolean = when (pref) {
        BR -> lang == BR || lang == POR
        PT -> lang == PT || lang == POR
        "any" -> lang != null          // qualquer áudio em português
        else -> false
    }
}
