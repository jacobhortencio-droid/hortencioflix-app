package com.hortencioflix.tv.data

/**
 * Tipo de conteúdo e país de um canal IPTV, adivinhados pelo nome do grupo e do canal.
 * As listas grandes (milhares de canais, centenas de grupos do fornecedor) ficam navegáveis por
 * «Desporto», «Filmes», «Notícias»… e por país, em vez de só pela arrumação do fornecedor.
 */
object ChannelFacets {
    enum class Kind(val label: String) {
        SPORT("Desporto"), MOVIES("Filmes"), SERIES("Séries"), NEWS("Notícias"), KIDS("Infantil"),
        DOCS("Documentários"), MUSIC("Música e rádio"), ENT("Entretenimento"), RELIGION("Religião"),
        ADULT("Adultos"), OTHER("Outros")
    }

    /** [starts] = início de palavra; [words] = palavra inteira; [any] = em qualquer sítio. */
    private class Rule(val kind: Kind, val starts: List<String>, val words: List<String> = emptyList(), val any: List<String> = emptyList())

    // A ordem conta: «Discovery Kids» é infantil antes de ser documentário, «Fox Sports» é desporto antes de ser séries.
    private val rules = listOf(
        Rule(Kind.ADULT, listOf("adult", "xxx", "porn", "erotic")),
        Rule(Kind.KIDS, listOf("kid", "infantil", "infantis", "crianc", "cartoon", "disney", "nick", "panda", "baby", "boomerang",
            "gloob", "tooncast", "anime", "junior", "desenho", "enfant", "jeunesse", "zig zag", "kinder", "bambini", "ragazzi",
            "ninos", "cocuk", "dzieci", "bajki", "gulli", "tiji", "piwi", "canal j", "kika", "boing", "cbeebies", "cbbc", "pogo",
            "minimax", "jim jam", "jimjam", "biggs"), listOf("jr"), listOf("toon")),
        Rule(Kind.SPORT, listOf("sport", "desport", "esport", "deport", "futebol", "football", "futbol", "soccer", "champions",
            "premier league", "la liga", "laliga", "dazn", "eleven", "benfica", "bein", "espn", "premiere", "combate", "golf",
            "tennis", "tenis", "motogp", "wwe", "fight", "supersport", "ppv", "formula", "eurosport", "liga ", "boxe", "boxing",
            "racing", "nba", "nfl", "ufc", "calcio", "fussball", "bundesliga", "ligue", "serie a ", "rugby", "cricket", "hockey",
            "basket", "padel", "wrestling", "evenement", "event", "stadium", "setanta", "tyc", "willow", "viaplay", "olymp",
            "match", "alkass", "equipe", "foot", "telefoot", "moto"), listOf("f1", "nhl", "mlb", "btv", "mma", "tsn", "ssc", "gol"),
            listOf("sport")),
        Rule(Kind.NEWS, listOf("notici", "informac", "jornal", "bloomberg", "cnbc", "al jazeera", "aljazeera", "info", "actualit",
            "nachricht", "haber", "24h", "cnews", "msnbc", "tagesschau", "france 24", "franceinfo", "bbc world", "i24",
            "al arabiya", "alarabiya", "tg24", "tgcom", "wiadomosci", "tvn24", "noticiero", "telediario", "24 horas"),
            listOf("cnn", "rtp3", "rtp 3", "tvi24", "cmtv", "bfm", "lci", "dw", "ntv", "n24", "welt"), listOf("news")),
        Rule(Kind.RELIGION, listOf("relig", "gospel", "igrej", "church", "catol", "cathol", "evangel", "islam", "quran", "kuran",
            "christian", "cancao nova", "aparecida", "rede vida", "kuriakos", "biblia", "bible", "ewtn", "daystar", "noursat",
            "makkah", "peace tv", "jesus", "god tv"), listOf("tbn", "kto", "dini", "iurd")),
        Rule(Kind.MUSIC, listOf("musique", "musik", "muzik", "muzyk", "mtv", "vh1", "trace", "radio", "hits", "mezzo", "clubbing",
            "kizomba", "karaoke", "stingray", "jazz", "classica"), listOf("nrj", "mcm", "fm", "kiss", "rock", "rap", "dance"),
            listOf("music")),
        Rule(Kind.DOCS, listOf("docu", "doku", "discovery", "national geo", "nat geo", "natgeo", "history", "historia", "histoire",
            "odisseia", "odisea", "odyssey", "animal planet", "animaux", "travel", "voyage", "viajes", "cienc", "science",
            "cultura", "natur", "bbc earth", "crime", "investiga", "viajar", "planete", "planeta", "ushuaia", "caca e pesca",
            "decouverte", "belgesel", "explorer", "viasat", "smithsonian", "curiosity", "savoir", "chasse"),
            listOf("arte", "geo", "wild")),
        Rule(Kind.MOVIES, listOf("filme", "movie", "cinema", "film", "telecine", "hollywood", "megapix", "box office", "vod",
            "kino", "pelicula", "paramount", "action", "thriller", "horror", "terror", "western", "starz", "showtime", "epix",
            "hallmark", "blockbuster"), listOf("hbo", "amc", "tcm", "tnt", "ocs", "mgm"), listOf("cine")),
        Rule(Kind.SERIES, listOf("serie", "novela", "telenovela", "sitcom", "24 7", "universal", "warner", "sony", "syfy",
            "star channel", "star life", "dizi", "drama", "soap", "sky atlantic", "tv show", "shows", "serial"), listOf("axn", "fox")),
        Rule(Kind.ENT, listOf("entreten", "entertain", "variedad", "variety", "general", "geral", "lifestyle", "culinar", "cuisine",
            "cooking", "cocina", "food", "reality", "comed", "humor", "abertos", "nacionais", "nacional", "national", "generalist",
            "tv aberta", "divertissement", "unterhaltung", "intrattenimento", "fashion", "hgtv", "local", "regional", "terrestr",
            "ulusal", "genel", "free to air"),
            listOf("tlc", "sic", "tvi", "rtp", "globo", "record", "sbt", "band", "zap", "tf1", "m6", "w9", "tmc", "c8", "rtl", "zdf",
                "ard", "itv", "rai", "tve", "nbc", "cbs", "abc", "bbc", "antena", "cuatro", "telecinco", "canal+", "fta"))
    )

    private val accents = Regex("\\p{Mn}+")
    private val notWord = Regex("[^a-z0-9+]+")

    private fun norm(s: String): String =
        " " + notWord.replace(accents.replace(java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD), ""), " ").trim() + " "

    private val kindCache = java.util.concurrent.ConcurrentHashMap<String, Kind>()

    private fun classify(text: String): Kind {
        if (text.isEmpty()) return Kind.OTHER
        kindCache[text]?.let { return it }
        val raw = text.lowercase()
        val t = norm(text)
        var found = Kind.OTHER
        if (raw.contains("+18") || raw.contains("18+")) found = Kind.ADULT
        else for (r in rules) {
            if (r.starts.any { t.contains(" $it") } || r.words.any { t.contains(" $it ") } || r.any.any { t.contains(it) }) { found = r.kind; break }
        }
        if (kindCache.size < 60_000) kindCache[text] = found
        return found
    }

    /** Tipo de conteúdo: pelo grupo do fornecedor («PT | DESPORTO») e, quando o grupo é vago, pelo nome do canal. */
    fun kind(ch: Channel): Kind {
        val g = classify(ch.group)
        // um grupo «geral» não diz muito: se o nome do canal for mais específico (notícias, desporto…), vale o nome
        if (g == Kind.OTHER || g == Kind.ENT) {
            val n = classify(ch.name)
            if (n != Kind.OTHER) return n
        }
        return g
    }

    // ---------- país / língua ----------

    class Region(val key: String, val label: String)

    private val regionList = listOf(
        Region("PT", "Portugal") to listOf("PT", "POR", "PRT"), Region("BR", "Brasil") to listOf("BR", "BRA"),
        Region("AO", "Angola") to listOf("AO", "ANG"), Region("MZ", "Moçambique") to listOf("MZ", "MOZ"),
        Region("CV", "Cabo Verde") to listOf("CV"), Region("ES", "Espanha") to listOf("ES", "ESP", "SP"),
        Region("FR", "França") to listOf("FR", "FRA"), Region("UK", "Reino Unido") to listOf("UK", "GB", "ENG", "GBR"),
        Region("US", "EUA") to listOf("US", "USA"), Region("DE", "Alemanha") to listOf("DE", "GER", "DEU"),
        Region("IT", "Itália") to listOf("IT", "ITA"), Region("NL", "Países Baixos") to listOf("NL", "NED", "NLD"),
        Region("BE", "Bélgica") to listOf("BE", "BEL"), Region("CH", "Suíça") to listOf("CH", "SUI"),
        Region("TR", "Turquia") to listOf("TR", "TUR"), Region("IN", "Índia") to listOf("IN", "IND"),
        Region("PL", "Polónia") to listOf("PL", "POL"), Region("RO", "Roménia") to listOf("RO", "ROM", "ROU"),
        Region("AL", "Albânia") to listOf("AL", "ALB"), Region("GR", "Grécia") to listOf("GR", "GRE"),
        Region("CA", "Canadá") to listOf("CA", "CAN"), Region("MX", "México") to listOf("MX", "MEX"),
        Region("AR", "Argentina") to listOf("ARG"), Region("CO", "Colômbia") to listOf("CO", "COL"),
        Region("ZA", "África do Sul") to listOf("ZA", "RSA"), Region("AF", "África") to listOf("AF", "AFR"),
        Region("LAT", "América Latina") to listOf("LAT", "LATAM", "LATINO"), Region("ARA", "Árabe") to listOf("ARA", "ARAB"),
        Region("RU", "Rússia") to listOf("RU", "RUS"), Region("SE", "Suécia") to listOf("SE", "SWE"),
        Region("DK", "Dinamarca") to listOf("DK", "DEN"), Region("NO", "Noruega") to listOf("NO", "NOR"),
        Region("IE", "Irlanda") to listOf("IE", "IRL"), Region("AT", "Áustria") to listOf("AT", "AUT"),
        Region("PK", "Paquistão") to listOf("PK", "PAK"), Region("IL", "Israel") to listOf("IL", "ISR"),
        Region("AU", "Austrália") to listOf("AU", "AUS")
    )
    private val byCode: Map<String, Region> = HashMap<String, Region>().apply { for ((r, codes) in regionList) for (c in codes) put(c, r) }
    private val byKey: Map<String, Region> = regionList.associate { it.first.key to it.first }

    // nomes por extenso que aparecem nos grupos («PORTUGAL», «CANAIS DO BRASIL»)
    private val byWord = listOf(
        "portugal" to "PT", "portugues" to "PT", "brasil" to "BR", "brazil" to "BR", "angola" to "AO", "mocambique" to "MZ",
        "cabo verde" to "CV", "espanha" to "ES", "espana" to "ES", "spain" to "ES", "franca" to "FR", "france" to "FR",
        "reino unido" to "UK", "united kingdom" to "UK", "estados unidos" to "US", "alemanha" to "DE", "germany" to "DE",
        "deutschland" to "DE", "italia" to "IT", "italy" to "IT", "africa" to "AF", "arabic" to "ARA", "arabe" to "ARA",
        "turkey" to "TR", "turquia" to "TR", "india" to "IN", "latino" to "LAT"
    )

    /** «|PT| », «PT - », «[PT] », «PT: », «PT | » no início. */
    private val prefix = Regex("^[\\s|\\[(\\-:•*#┃]*([A-Za-z]{2,6})\\s*[|\\]):\\-–•┃]+\\s*")

    fun regionLabel(key: String): String = byKey[key]?.label ?: key

    /** Código do país no início do texto e o resto sem ele, ou null. */
    fun splitPrefix(text: String): Pair<String, String>? {
        val m = prefix.find(text) ?: return null
        val r = byCode[m.groupValues[1].uppercase()] ?: return null
        return r.key to text.substring(m.range.last + 1).trim()
    }

    private val regionCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun regionOfGroup(group: String): String {
        if (group.isEmpty()) return ""
        regionCache[group]?.let { return it }
        var key = splitPrefix(group)?.first ?: ""
        if (key.isEmpty()) {
            val t = norm(group)
            key = byWord.firstOrNull { t.contains(" ${it.first}") }?.second ?: ""
        }
        if (regionCache.size < 20_000) regionCache[group] = key
        return key
    }

    /** País do canal ("" = desconhecido): pelo grupo, senão pelo prefixo do nome. */
    fun region(ch: Channel): String = regionOfGroup(ch.group).ifEmpty { splitPrefix(ch.name)?.first ?: "" }

    // ---------- nome do grupo para mostrar ----------

    private val small = setOf("de", "da", "do", "dos", "das", "e", "em", "o", "a", "os", "as", "la", "el", "le", "les", "et", "y",
        "of", "and", "the", "du", "des", "en", "para", "com")

    /** «|PT| INTERNACIONAL» → «PT · Internacional»; os grupos escritos em maiúsculas deixam de gritar. */
    fun groupLabel(raw: String): String {
        var code = ""
        var s = raw.trim()
        splitPrefix(s)?.let { code = it.first; s = it.second }
        s = s.trim(' ', '|', '-', ':', '•', '*', '#', '┃', '[', ']')
        if (s.isNotEmpty() && s.none { it.isLowerCase() } && s.any { it.isLetter() }) {
            s = s.split(' ').filter { it.isNotEmpty() }.mapIndexed { i, w ->
                val lw = w.lowercase()
                when {
                    lw in small && i > 0 -> lw
                    w.length <= 3 && lw !in small -> w            // PT, UK, HD, 4K, TV, VIP
                    else -> lw.replaceFirstChar { it.uppercase() }
                }
            }.joinToString(" ")
        }
        return when {
            code.isNotEmpty() && s.isNotEmpty() -> "$code · $s"
            code.isNotEmpty() -> regionLabel(code)
            s.isNotEmpty() -> s
            else -> raw.trim()
        }
    }
}
