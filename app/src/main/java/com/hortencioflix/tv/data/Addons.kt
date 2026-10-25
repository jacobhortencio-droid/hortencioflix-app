package com.hortencioflix.tv.data

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Cliente do protocolo de add-ons do Stremio. */
object Addons {
    const val CINEMETA = "https://v3-cinemeta.strem.io"
    const val OPENSUBS = "https://opensubtitles-v3.strem.io"

    private val manifests = ConcurrentHashMap<String, JSONObject>()

    fun normalize(u: String): String {
        var s = u.trim()
        if (s.startsWith("stremio://", ignoreCase = true)) s = "https://" + s.substring(10)
        if (s.endsWith("/manifest.json", ignoreCase = true)) s = s.substring(0, s.length - 14)
        return s.trimEnd('/')
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun pathId(id: String): String =
        if (id.any { it == ' ' || it == '/' || it == '?' || it == '#' || it == '%' || it == '&' }) enc(id) else id

    /** Add-ons em uso: conta Stremio + locais + Torrentio/TorBox automático. */
    fun list(): List<String> {
        val out = LinkedHashSet<String>()
        out.addAll(Prefs.stremioAddons())
        out.addAll(Prefs.localAddons())
        autoTorrentio(out)?.let { out.add(it) }
        val off = Prefs.disabledAddons()
        return out.filter { it !in off }
    }

    /** Todos os add-ons, incluindo os desativados (para o gestor). */
    fun allInstalled(): List<String> {
        val out = LinkedHashSet<String>()
        out.addAll(Prefs.stremioAddons())
        out.addAll(Prefs.localAddons())
        autoTorrentio(out)?.let { out.add(it) }
        return out.toList()
    }

    /** Torrentio configurado automaticamente com o serviço de debrid escolhido (TorBox, Real-Debrid…). */
    private fun autoTorrentio(existing: Collection<String>): String? {
        val svc = Prefs.debridService
        val key = if (svc == "torbox") Prefs.torboxKey else Prefs.debridKey
        if (key.isEmpty()) return null
        if (existing.any { it.contains("torrentio", true) && it.contains("$svc=", true) }) return null
        return "https://torrentio.strem.fun/$svc=$key"
    }

    fun cachedManifest(base: String): JSONObject? = manifests[base]

    fun catalogKey(base: String, type: String, id: String) = "$base|$type|$id"

    fun putManifest(base: String, m: JSONObject) { manifests[base] = m }

    suspend fun manifest(base: String): JSONObject? {
        manifests[base]?.let { return it }
        val m = withContext(Dispatchers.IO) { Http.getJson("$base/manifest.json") }
        if (m != null) manifests[base] = m
        return m
    }

    private suspend fun ensureManifests(bases: List<String>) = coroutineScope {
        bases.filter { !manifests.containsKey(it) }.map { b -> async { manifest(b) } }.awaitAll()
    }

    private fun matches(arr: JSONArray?, v: String, prefix: Boolean): Boolean {
        if (arr == null || arr.length() == 0) return true
        for (i in 0 until arr.length()) {
            val p = arr.optString(i)
            if (prefix && v.startsWith(p)) return true
            if (!prefix && v == p) return true
        }
        return false
    }

    fun supports(man: JSONObject?, resource: String, type: String, id: String): Boolean {
        if (man == null) return false
        val res = man.optJSONArray("resources") ?: return false
        for (i in 0 until res.length()) {
            val r = res.opt(i)
            var name: String? = null
            var types: JSONArray? = man.optJSONArray("types")
            var prefixes: JSONArray? = man.optJSONArray("idPrefixes")
            if (r is String) {
                name = r
            } else if (r is JSONObject) {
                name = r.optString("name")
                r.optJSONArray("types")?.let { types = it }
                r.optJSONArray("idPrefixes")?.let { prefixes = it }
            }
            if (name == resource) return matches(types, type, false) && matches(prefixes, id, true)
        }
        return false
    }

    private suspend fun supporting(resource: String, type: String, id: String): List<String> {
        val bases = list()
        ensureManifests(bases)
        return bases.filter { supports(manifests[it], resource, type, id) }
    }

    private fun needsExtra(cat: JSONObject): Boolean {
        val req = cat.optJSONArray("extraRequired")
        if (req != null && req.length() > 0) return true
        val extra = cat.optJSONArray("extra") ?: return false
        for (i in 0 until extra.length()) {
            if (extra.optJSONObject(i)?.optBoolean("isRequired") == true) return true
        }
        return false
    }

    private fun supportsSearch(cat: JSONObject): Boolean {
        val extra = cat.optJSONArray("extra")
        if (extra != null) for (i in 0 until extra.length()) if (extra.optJSONObject(i)?.optString("name") == "search") return true
        val sup = cat.optJSONArray("extraSupported")
        if (sup != null) for (i in 0 until sup.length()) if (sup.optString(i) == "search") return true
        return false
    }

    fun typeLabel(t: String): String = when (t) {
        "movie" -> "Filmes"
        "series" -> "Séries"
        "tv" -> "TV"
        "channel" -> "Canais"
        "other" -> "Outros"
        else -> t
    }

    fun metas(data: JSONObject?, limit: Int = 40): List<MetaItem> {
        val arr = data?.optJSONArray("metas") ?: return emptyList()
        val out = ArrayList<MetaItem>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id")
            val type = m.optString("type")
            if (id.isEmpty() || type.isEmpty()) continue
            val parts = ArrayList<String>()
            m.optString("releaseInfo").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
            m.optString("imdbRating").takeIf { it.isNotEmpty() }?.let { parts.add("IMDb $it") }
            val g = m.optJSONArray("genres")
            if (g != null && g.length() > 0) parts.add((0 until minOf(3, g.length())).joinToString(", ") { g.optString(it) })
            out.add(
                MetaItem(
                    id = id, type = type, name = m.optString("name"), poster = m.optString("poster"),
                    background = m.optString("background"), description = m.optString("description"),
                    info = parts.joinToString("   ·   ")
                )
            )
        }
        return out
    }

    /** Referência a um catálogo (para «Ver tudo» e Explorar). */
    data class CatalogRef(
        val base: String, val type: String, val id: String, val name: String, val addonName: String,
        val genres: List<String>, val supportsSkip: Boolean, val supportsGenre: Boolean,
        val fixedGenre: String? = null
    ) : java.io.Serializable

    val catalogRefs = ConcurrentHashMap<String, CatalogRef>()

    private fun refOf(base: String, man: JSONObject, c: JSONObject): CatalogRef {
        var skip = false
        var genre = false
        val genres = ArrayList<String>()
        c.optJSONArray("extra")?.let { ex ->
            for (i in 0 until ex.length()) {
                val e = ex.optJSONObject(i) ?: continue
                when (e.optString("name")) {
                    "skip" -> skip = true
                    "genre" -> {
                        genre = true
                        e.optJSONArray("options")?.let { o -> for (j in 0 until o.length()) genres.add(o.optString(j)) }
                    }
                }
            }
        }
        c.optJSONArray("extraSupported")?.let { sup ->
            for (i in 0 until sup.length()) { if (sup.optString(i) == "skip") skip = true; if (sup.optString(i) == "genre") genre = true }
        }
        c.optJSONArray("genres")?.let { o -> if (genres.isEmpty()) for (j in 0 until o.length()) genres.add(o.optString(j)) }
        return CatalogRef(base, c.optString("type"), c.optString("id"), c.optString("name", c.optString("id")), man.optString("name"), genres, skip, genre)
    }

    /** Catálogos de filmes/séries de todos os add-ons (para Explorar). */
    suspend fun browseCatalogs(type: String): List<CatalogRef> {
        val bases = list()
        ensureManifests(bases)
        val out = ArrayList<CatalogRef>()
        for (b in bases) {
            val man = manifests[b] ?: continue
            val cats = man.optJSONArray("catalogs") ?: continue
            for (i in 0 until cats.length()) {
                val c = cats.optJSONObject(i) ?: continue
                if (c.optString("type") != type || needsExtra(c)) continue
                out.add(refOf(b, man, c))
            }
        }
        return out
    }

    /** Uma página de um catálogo (com género e/ou salto opcionais). */
    suspend fun catalogPage(ref: CatalogRef, genre: String?, skip: Int): List<MetaItem> = withContext(Dispatchers.IO) {
        val extras = ArrayList<String>()
        if (!genre.isNullOrEmpty()) extras.add("genre=${enc(genre)}")
        if (skip > 0) extras.add("skip=$skip")
        val url = "${ref.base}/catalog/${ref.type}/${pathId(ref.id)}" + (if (extras.isEmpty()) "" else "/" + extras.joinToString("&")) + ".json"
        metas(Http.getJson(url), 200)
    }

    /** Tipos que pertencem à TV ao vivo e não ao Início. */
    fun isTvType(t: String): Boolean = t == "tv" || t == "channel" || t == "events"

    /** Filas do Início: um catálogo por fila (sem canais de TV; «Outros» no fim). */
    suspend fun homeRows(): List<Pair<String, List<MetaItem>>> = coroutineScope {
        if (Prefs.isKids) {
            // Perfil infantil: só animação e família (Cinemeta)
            val man = manifest(CINEMETA) ?: JSONObject()
            val kids = listOf(
                Triple("Animação · Filmes", "movie", "Animation"), Triple("Família · Filmes", "movie", "Family"),
                Triple("Animação · Séries", "series", "Animation"), Triple("Família · Séries", "series", "Family")
            )
            return@coroutineScope kids.map { (title, type, g) ->
                catalogRefs[title] = CatalogRef(CINEMETA, type, "top", title, man.optString("name", "Cinemeta"), listOf(g), true, false, g)
                async(Dispatchers.IO) { title to metas(Http.getJson("$CINEMETA/catalog/$type/top/genre=$g.json")) }
            }.awaitAll().filter { it.second.isNotEmpty() }
        }
        val bases = list()
        ensureManifests(bases)
        val main = ArrayList<Pair<String, String>>()
        val other = ArrayList<Pair<String, String>>()
        var first = true
        for (b in bases) {
            val man = manifests[b] ?: continue
            val cats = man.optJSONArray("catalogs") ?: continue
            for (i in 0 until cats.length()) {
                if (main.size + other.size >= 30) break
                val c = cats.optJSONObject(i) ?: continue
                if (needsExtra(c)) continue
                val type = c.optString("type")
                val cid = c.optString("id")
                if (type.isEmpty() || cid.isEmpty() || isTvType(type)) continue
                if (Prefs.isCatalogHidden(catalogKey(b, type, cid))) continue
                var title = c.optString("name", cid).ifEmpty { cid } + " · " + typeLabel(type)
                if (!first) title = man.optString("name", "") + " — " + title
                val job = title to "$b/catalog/$type/${pathId(cid)}.json"
                catalogRefs[title] = refOf(b, man, c)
                if (type == "movie" || type == "series") main.add(job) else other.add(job)
            }
            first = false
        }
        val res = (main + other).map { (title, url) ->
            async(Dispatchers.IO) { title to metas(Http.getJson(url)) }
        }.awaitAll().filter { it.second.isNotEmpty() }
        // Ficheiros do TorBox (nomes de torrents): reconhecer títulos ou esconder.
        res.mapNotNull { (title, items) ->
            val isFiles = title.contains("torbox", true) && main.none { it.first == title }
            if (!isFiles) title to items
            else if (!Prefs.showTorboxRow) null
            else "TorBox · os meus ficheiros" to FileName.enrich(items)
        }
    }

    /** Canais dos add-ons de TV do Stremio: um grupo por catálogo. */
    suspend fun tvGroups(): List<ChannelGroup> = coroutineScope {
        val bases = list()
        ensureManifests(bases)
        val jobs = ArrayList<Pair<String, String>>()
        for (b in bases) {
            val man = manifests[b] ?: continue
            val cats = man.optJSONArray("catalogs") ?: continue
            for (i in 0 until cats.length()) {
                val c = cats.optJSONObject(i) ?: continue
                val type = c.optString("type")
                val cid = c.optString("id")
                if (!isTvType(type) || cid.isEmpty() || needsExtra(c)) continue
                val title = man.optString("name").ifEmpty { b } + " · " + c.optString("name", cid).ifEmpty { cid }
                jobs.add(title to "$b/catalog/$type/${pathId(cid)}.json")
            }
        }
        jobs.map { (title, url) ->
            async(Dispatchers.IO) {
                val arr = Http.getJson(url)?.optJSONArray("metas")
                val chans = ArrayList<Channel>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id")
                    val type = m.optString("type")
                    if (id.isEmpty() || type.isEmpty()) continue
                    val logo = m.optString("logo").ifEmpty { m.optString("poster") }
                    chans.add(Channel(m.optString("name").ifEmpty { id }, logo, "stremio:$type:$id", title))
                }
                ChannelGroup(title, chans)
            }
        }.awaitAll().filter { it.channels.isNotEmpty() }
    }

    fun hasTvAddons(): Boolean = list().any { b ->
        val cats = manifests[b]?.optJSONArray("catalogs") ?: return@any false
        (0 until cats.length()).any { isTvType(cats.optJSONObject(it)?.optString("type") ?: "") }
    }

    suspend fun search(query: String): List<Pair<String, List<MetaItem>>> = coroutineScope {
        val bases = list()
        ensureManifests(bases)
        val jobs = ArrayList<Pair<String, String>>()
        for (b in bases) {
            val man = manifests[b] ?: continue
            val cats = man.optJSONArray("catalogs") ?: continue
            for (i in 0 until cats.length()) {
                val c = cats.optJSONObject(i) ?: continue
                if (!supportsSearch(c)) continue
                val type = c.optString("type")
                if (isTvType(type)) continue
                jobs.add("${typeLabel(type)} · ${man.optString("name")}" to "$b/catalog/$type/${pathId(c.optString("id"))}/search=${enc(query)}.json")
            }
        }
        jobs.map { (title, url) -> async(Dispatchers.IO) { title to metas(Http.getJson(url)) } }
            .awaitAll().filter { it.second.isNotEmpty() }
    }

    suspend fun meta(type: String, id: String): JSONObject? = coroutineScope {
        val bases = supporting("meta", type, id)
        val results = bases.map { b -> async(Dispatchers.IO) { Http.getJson("$b/meta/$type/${pathId(id)}.json")?.optJSONObject("meta") } }.awaitAll()
        val m = results.firstOrNull { it != null }
        // lembra se é animação, para a preferência de áudio dobrado nos desenhos animados
        m?.optJSONArray("genres")?.let { g ->
            val anim = (0 until g.length()).any { g.optString(it).lowercase() in setOf("animation", "anime", "animação") }
            animatedIds[id.substringBefore(':')] = anim
        }
        m
    }

    /** Títulos que são animação (preenchido ao abrir a página do título). */
    val animatedIds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Preferência de áudio dobrado para este título: a da animação, se for desenho animado; senão a geral. */
    fun dubPrefFor(id: String): String {
        val anim = animatedIds[id.substringBefore(':')] == true
        val a = Prefs.str("animDub", "any")
        return if (anim && a != "off") a else Prefs.str("dubPref")
    }

    fun quality(s: String): Int {
        val t = s.lowercase()
        return when {
            "2160p" in t || "4k" in t || "uhd" in t -> 2160
            "1440p" in t -> 1440
            "1080p" in t -> 1080
            "720p" in t -> 720
            "480p" in t || "576p" in t -> 480
            else -> 0
        }
    }

    /** Dolby Vision sem HDR, AV1 e 3D falham em muitas TVs. */
    fun risky(s: String): Boolean {
        val t = s.lowercase()
        if ("av1" in t || " 3d" in t || ".3d." in t) return true
        val dv = " dv" in t || ".dv." in t || "dovi" in t || "dolby vision" in t || "dolbyvision" in t
        return dv && "hdr" !in t
    }

    // ---------- Cache e pré-busca de fontes/legendas (abrir um título sem esperar) ----------

    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class Entry<T>(val at: Long, val job: Deferred<T>)
    private val streamCache = ConcurrentHashMap<String, Entry<List<StreamItem>>>()
    private val subCache = ConcurrentHashMap<String, Entry<List<SubtitleItem>>>()
    private const val CACHE_MS = 10 * 60_000L

    private suspend fun <T> cached(map: ConcurrentHashMap<String, Entry<List<T>>>, key: String, fresh: Boolean, load: suspend () -> List<T>): List<T> {
        val now = SystemClock.elapsedRealtime()
        val e = map[key]
        if (!fresh && e != null && now - e.at < CACHE_MS) {
            val r = runCatching { e.job.await() }.getOrNull()
            if (!r.isNullOrEmpty()) return r
        }
        // Corre fora de quem pediu: se o ecrã fechar, a busca continua e fica em cache.
        val job = bg.async { load() }
        map[key] = Entry(now, job)
        return job.await()
    }

    /** Fontes (com cache de 10 min, partilhada entre o ecrã de detalhes e o leitor). */
    suspend fun streams(type: String, id: String, ignoreMax: Boolean = false, fresh: Boolean = false): List<StreamItem> {
        val key = "$type|$id|$ignoreMax|${Prefs.maxQuality}|${Prefs.cachedOnly}|${Prefs.hideRisky}|${Prefs.hasDebrid()}|${Prefs.dataSaver && Prefs.onMeteredNetwork()}|${dubPrefFor(id)}|${Prefs.str("rangePref", "auto")}|${Prefs.str("pinFilters")}"
        return cached(streamCache, key, fresh) { fetchStreams(type, id, ignoreMax) }
    }

    suspend fun subtitles(type: String, id: String): List<SubtitleItem> =
        cached(subCache, "$type|$id|${Prefs.subLang}", false) { fetchSubtitles(type, id) }

    /** Começa já a procurar fontes e legendas em segundo plano. */
    fun prefetch(type: String, id: String) {
        if (id.isEmpty()) return
        bg.launch {
            // Só a lista de fontes e as legendas: pedir já o link ao debrid (1.9) gastava pedidos do
            // TorBox por cada título aberto e fazia as fontes falharem («CONTAINER_MALFORMED»).
            runCatching { streams(type, id) }
            runCatching { subtitles(type, id) }
        }
    }

    private suspend fun fetchStreams(type: String, id: String, ignoreMax: Boolean): List<StreamItem> = coroutineScope {
        val bases = supporting("stream", type, id)
        // Fontes curadas: um só pedido ao teu servidor (junta, tira repetidos, confirma no TorBox).
        // Se falhar ou vier vazio, pede diretamente a cada add-on como antes.
        // Os add-ons que o servidor não conseguiu contactar (alguns bloqueiam servidores na nuvem) são pedidos
        // diretamente por este aparelho e juntam-se ao resultado.
        // Os que falharam lá da última vez (24 h) já nem vão ao servidor: são pedidos aqui, ao mesmo tempo.
        val cur = curatorUrl()
        val results: List<Pair<String, JSONObject?>> = if (cur == null || bases.isEmpty()) {
            bases.map { b -> async(Dispatchers.IO) { b to Http.getJson("$b/stream/$type/${pathId(id)}.json") } }.awaitAll()
        } else {
            val skip = curatorSkip()
            val viaServer = bases.filter { it !in skip }
            val early = bases.filter { it in skip }.map { b -> async(Dispatchers.IO) { b to Http.getJson("$b/stream/$type/${pathId(id)}.json") } }
            val curated = if (viaServer.isNotEmpty()) async(Dispatchers.IO) { curatedStreams(cur, viaServer, type, id) }.await() else null
            val late = (curated?.second ?: viaServer).map { b -> async(Dispatchers.IO) { b to Http.getJson("$b/stream/$type/${pathId(id)}.json") } }
            if (curated != null) rememberSkip(curated.second)
            (if (curated != null && (curated.first.optJSONArray("streams")?.length() ?: 0) > 0) listOf(CURATOR to curated.first) else emptyList()) +
                early.awaitAll() + late.awaitAll()
        }
        val good = ArrayList<StreamItem>()
        val riskyList = ArrayList<StreamItem>()
        val later = ArrayList<StreamItem>()
        var maxQ = Prefs.maxQuality
        if (Prefs.dataSaver && Prefs.onMeteredNetwork()) maxQ = if (maxQ == 0) 720 else minOf(maxQ, 720)
        if (ignoreMax) maxQ = 0
        for ((b, d) in results) {
            val arr = d?.optJSONArray("streams") ?: continue
            val baseName = manifests[b]?.optString("name")?.ifEmpty { b } ?: b
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val addonName = s.optString("addon").ifEmpty { baseName }
                val url = s.optString("url")
                if (!url.startsWith("http", true)) continue
                val bh = s.optJSONObject("behaviorHints")
                val filename = bh?.optString("filename") ?: ""
                val headers = HashMap<String, String>()
                bh?.optJSONObject("proxyHeaders")?.optJSONObject("request")?.let { h ->
                    for (k in h.keys()) headers[k] = h.optString(k)
                }
                val label = s.optString("name")
                val detail = s.optString("title").ifEmpty { s.optString("description") }
                val all = "$label $detail $filename"
                val q = quality(all)
                val uncached = label.lowercase().contains("download]")
                // incompatível com este aparelho (ex.: áudio E-AC3 num telemóvel sem esse descodificador) → para o fim
                val rk = risky(all) || DeviceCaps.problem(all, q) != null
                val item = StreamItem(addonName, label, detail, url, filename, headers, q, !uncached, rk)
                when {
                    maxQ > 0 && q > maxQ -> {}
                    Prefs.cachedOnly && uncached -> {}
                    Prefs.hideRisky && rk -> {}
                    uncached -> later.add(item)
                    rk -> riskyList.add(item)
                    else -> good.add(item)
                }
            }
        }
        val deb = Prefs.hasDebrid()
        val ordered = SourcePicker.order(good, deb) + SourcePicker.order(riskyList, deb) + SourcePicker.order(later, deb)
        val pref = Prefs.str("src:" + id.substringBefore(':'))
        // A fonte lembrada só passa à frente entre as que já estão prontas (nunca uma «download»).
        // O mesmo ficheiro vindo de vários add-ons (ou repetido) aparece só uma vez — fica o melhor colocado.
        val unique = ordered.distinctBy { s ->
            val f = s.filename.lowercase().trim()
            if (f.length > 8) f + "|" + s.cached else s.url
        }
        val (ready0, notReady) = unique.partition { it.cached }
        // O que já falhou neste aparelho vai para o fim; o que já tocou bem sobe (sem mudar a ordem do resto).
        val dub = dubPrefFor(id)
        val ready = ready0.sortedWith(compareBy(
            { val sc = SourceStats.score(it); if (sc <= -3) 1 else 0 },
            // Preferência de áudio dobrado (PT-BR / PT-PT): essas fontes passam à frente
            { if (dub.isNotEmpty() && AudioLang.matches(AudioLang.of(it), dub)) 0 else 1 }
        )).let { VideoRange.prefer(it, VideoRange.wanted(Prefs.str("rangePref", "auto"))) }
            // Filtros fixados pelo utilizador (ex.: «só Dolby Vision»): essas fontes vão para a frente
            .let { SourceFilters.pinnedFirst(it) }
        if (pref.isEmpty()) ready + notReady else {
            val pin = SourceFilters.pinned()
            // a fonte lembrada só passa à frente se também cumprir os filtros fixados
            val (a, b) = ready.partition { sourceSignature(it) == pref && (pin.isEmpty() || SourceFilters.passes(it, pin)) }
            a + b + notReady
        }
    }

    // ---------- Fontes curadas (servidor próprio, ver pasta curador/) ----------

    private const val CURATOR = "curador"

    /** Endereço do servidor de fontes curadas, se estiver ligado. */
    fun curatorUrl(): String? {
        val u = Prefs.str("curatorUrl").trim().trimEnd('/')
        return if (u.startsWith("http", true) && Prefs.str("curatorOn", "1") == "1") u else null
    }

    /** Configuração no endereço: add-ons de fontes e (se o debrid for o TorBox) a chave. */
    fun curatorConfig(bases: List<String>): String {
        val o = JSONObject().put("a", JSONArray(bases))
        if (Prefs.debridService == "torbox" && Prefs.torboxKey.isNotEmpty()) o.put("k", Prefs.torboxKey)
        return android.util.Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    /** Add-ons que o servidor não conseguiu contactar recentemente (bloqueiam servidores, lentos): pedem-se diretamente. */
    private fun curatorSkip(): Set<String> {
        val o = try { JSONObject(Prefs.str("curSkip").ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        val now = System.currentTimeMillis()
        return o.keys().asSequence().filter { o.optLong(it) > now }.toSet()
    }

    private fun rememberSkip(failed: List<String>) {
        if (failed.isEmpty()) return
        val o = try { JSONObject(Prefs.str("curSkip").ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        val until = System.currentTimeMillis() + 24 * 3600_000L
        for (b in failed) o.put(b, until)
        Prefs.put("curSkip", o.toString())
    }

    fun clearCuratorSkip() = Prefs.remove("curSkip")

    /** Add-ons que dão fontes para este título (para o teste do servidor). */
    suspend fun streamBases(type: String, id: String): List<String> = supporting("stream", type, id)

    /** Resposta do servidor e os add-ons a pedir diretamente (os que falharam lá); null = pedir todos diretamente. */
    private fun curatedStreams(cur: String, bases: List<String>, type: String, id: String): Pair<JSONObject, List<String>>? {
        val t0 = SystemClock.elapsedRealtime()
        val d = Http.getJson("$cur/${curatorConfig(bases)}/stream/$type/${pathId(id)}.json") ?: run {
            PlayLog.add("Fontes curadas sem resposta — a pedir a cada add-on")
            return null
        }
        val n = d.optJSONArray("streams")?.length() ?: 0
        val st = d.optJSONObject("hf")?.optJSONArray("addons")
        val failed = if (st != null && st.length() == bases.size) bases.filterIndexed { i, _ -> st.optJSONObject(i)?.optBoolean("ok") != true }
            else if (n == 0) bases else emptyList()
        PlayLog.add("Fontes curadas: $n em ${SystemClock.elapsedRealtime() - t0} ms" + if (failed.isNotEmpty()) " · ${failed.size} add-on(s) pedidos diretamente" else "")
        return d to failed
    }

    /** Assinatura de uma fonte (add-on + qualidade + grupo de lançamento) para a lembrar. */
    fun sourceSignature(s: StreamItem): String {
        val name = s.filename.ifEmpty { s.title.lineSequence().firstOrNull() ?: "" }
        val group = Regex("-([A-Za-z0-9]+)(?:\\.[a-z0-9]{2,4})?\\s*$").find(name.trim())?.groupValues?.get(1) ?: ""
        return "${s.addon}|${s.quality}|$group"
    }

    fun rememberSource(metaId: String, s: StreamItem) = Prefs.put("src:" + metaId.substringBefore(':'), sourceSignature(s))

    fun langCodes(pref: String): List<String> = when (pref) {
        "por" -> listOf("por", "pob", "pt", "pt-br", "pt-pt")
        "eng" -> listOf("eng", "en")
        "spa" -> listOf("spa", "es")
        "fre" -> listOf("fre", "fra", "fr")
        else -> emptyList()
    }

    fun langName(c: String): String = when (c.lowercase()) {
        "por", "pt", "pt-pt" -> "Português"
        "pob", "pt-br" -> "Português (BR)"
        "eng", "en" -> "Inglês"
        "spa", "es" -> "Espanhol"
        "fre", "fra", "fr" -> "Francês"
        "ita" -> "Italiano"
        else -> c
    }

    private suspend fun fetchSubtitles(type: String, id: String): List<SubtitleItem> = coroutineScope {
        var bases = supporting("subtitles", type, id)
        if (bases.isEmpty()) bases = listOf(OPENSUBS)
        val all = bases.map { b -> async(Dispatchers.IO) { Http.getJson("$b/subtitles/$type/${pathId(id)}.json") } }.awaitAll()
        val subs = ArrayList<Pair<String, String>>()
        for (d in all) {
            val arr = d?.optJSONArray("subtitles") ?: continue
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val u = s.optString("url")
                val l = s.optString("lang")
                if (u.isNotEmpty() && l.isNotEmpty()) subs.add(u to l)
            }
        }
        val wanted = (langCodes(Prefs.subLang) + listOf("eng")).distinct()
        val out = ArrayList<SubtitleItem>()
        for (w in wanted) {
            var n = 0
            for ((u, l) in subs) {
                if (out.size < 8 && n < 3 && l.equals(w, true)) {
                    n++
                    out.add(SubtitleItem(u, l, "${langName(l)} $n"))
                }
            }
        }
        out
    }

    /** Todas as legendas disponíveis (para escolher manualmente no leitor). */
    suspend fun allSubtitles(type: String, id: String): List<SubtitleItem> = coroutineScope {
        var bases = supporting("subtitles", type, id)
        if (bases.isEmpty()) bases = listOf(OPENSUBS)
        val all = bases.map { b -> async(Dispatchers.IO) { Http.getJson("$b/subtitles/$type/${pathId(id)}.json") } }.awaitAll()
        val out = ArrayList<SubtitleItem>()
        val count = HashMap<String, Int>()
        val pref = langCodes(Prefs.subLang)
        for (d in all) {
            val arr = d?.optJSONArray("subtitles") ?: continue
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val u = s.optString("url")
                val l = s.optString("lang")
                if (u.isEmpty() || l.isEmpty()) continue
                val n = (count[l] ?: 0) + 1
                count[l] = n
                out.add(SubtitleItem(u, l, "${langName(l)} $n"))
            }
        }
        out.sortedWith(compareBy({ if (pref.any { p -> it.lang.equals(p, true) }) 0 else if (it.lang.startsWith("en", true)) 1 else 2 }, { it.lang })).take(120)
    }

    /** Títulos semelhantes: mesmo género no Cinemeta. */
    suspend fun similar(type: String, id: String, genres: List<String>): List<MetaItem> = withContext(Dispatchers.IO) {
        val g = genres.firstOrNull() ?: return@withContext emptyList()
        val t = if (type == "series") "series" else "movie"
        metas(Http.getJson("$CINEMETA/catalog/$t/top/genre=${enc(g)}.json"), 40).filter { it.id != id }.shuffled().take(15)
    }

    /**
     * «A seguir» no fim de um filme: títulos do mesmo género (os que partilham os dois géneros principais
     * primeiro), sem os que já viste. Com Trakt ligado, junta as recomendações da conta.
     */
    suspend fun recommendFor(type: String, id: String): List<MetaItem> = withContext(Dispatchers.IO) {
        val f = try { meta(type, id) } catch (e: Exception) { null }
        val g = f?.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val t = if (type == "series") "series" else "movie"
        val lists = g.take(2).map { genre -> metas(Http.getJson("$CINEMETA/catalog/$t/top/genre=${enc(genre)}.json"), 60) }
        val first = lists.getOrNull(0) ?: emptyList()
        val second = lists.getOrNull(1)?.map { it.id }?.toSet() ?: emptySet()
        val watched = Prefs.array("watched").let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
        val trakt = if (Trakt.linked) try { Trakt.recommendations().filter { it.type == t } } catch (e: Exception) { emptyList() } else emptyList()
        val both = first.filter { it.id in second }
        val rest = first.filter { it.id !in second }.shuffled()
        (both.shuffled() + trakt.take(4) + rest)
            .filter { it.id != id && it.id !in watched }
            .distinctBy { it.id }
            .take(8)
    }

    /** Pesquisa com resultados juntos (sem repetidos), filmes e séries. */
    suspend fun searchMerged(query: String): List<MetaItem> {
        val rows = search(query)
        val seen = HashSet<String>()
        val movies = ArrayList<MetaItem>()
        val series = ArrayList<MetaItem>()
        val other = ArrayList<MetaItem>()
        for ((_, items) in rows) for (m in items) {
            if (!seen.add(m.id)) continue
            when (m.type) { "movie" -> movies.add(m); "series" -> series.add(m); else -> other.add(m) }
        }
        // intercalar filmes e séries para os melhores resultados de ambos aparecerem no topo
        val out = ArrayList<MetaItem>()
        val n = maxOf(movies.size, series.size)
        for (i in 0 until n) { movies.getOrNull(i)?.let { out.add(it) }; series.getOrNull(i)?.let { out.add(it) } }
        out.addAll(other)
        return out
    }

    /** Episódios de uma série a partir do "meta.videos". */
    fun episodes(meta: JSONObject?): List<Episode> {
        val vids = meta?.optJSONArray("videos") ?: return emptyList()
        val out = ArrayList<Episode>()
        for (i in 0 until vids.length()) {
            val v = vids.optJSONObject(i) ?: continue
            val id = v.optString("id")
            if (id.isEmpty()) continue
            val s = v.optInt("season", 0)
            val e = if (v.has("episode")) v.optInt("episode", 0) else v.optInt("number", 0)
            val name = v.optString("name").ifEmpty { v.optString("title") }
            val ov = v.optString("overview").ifEmpty { v.optString("description") }
            val rel = v.optString("released").ifEmpty { v.optString("firstAired") }
            out.add(Episode(id, s, e, name, v.optString("thumbnail"), ov, rel))
        }
        return out.sortedWith(compareBy({ if (it.season == 0) 9999 else it.season }, { it.episode }))
    }
}
