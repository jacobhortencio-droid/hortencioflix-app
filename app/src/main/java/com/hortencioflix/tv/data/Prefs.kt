package com.hortencioflix.tv.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Definições guardadas no aparelho. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var psp: SharedPreferences
    lateinit var app: Context
        private set

    /** Chaves que pertencem a cada perfil (o resto — contas, add-ons, listas — é partilhado). */
    private val PERSONAL = setOf("progress", "watched", "iptvFav", "recentCh", "recentSearch", "hiddenContinue",
        "audioLang", "newEp", "newEpAt", "notifiedEp", "lastChannel")

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = ctx.getSharedPreferences("hortencioflix", Context.MODE_PRIVATE)
        psp = profileSp(profileId)
        // Cifra chaves e tokens que ainda estejam em texto simples (migração única).
        for (k in SECRET) {
            val raw = sp.getString(k, "") ?: ""
            if (raw.isNotEmpty() && !Secrets.isEncrypted(raw)) put(k, raw)
        }
    }

    /** Chaves e tokens: guardados cifrados (ver Secrets). */
    private val SECRET = setOf("stremioKey", "torboxKey", "debridKey", "ghToken", "traktToken", "traktSecret", "traktRefresh", "pin", "rokuPass")

    private fun profileSp(id: String): SharedPreferences =
        if (id == "main") sp else app.getSharedPreferences("hortencioflix_p_$id", Context.MODE_PRIVATE)

    private fun spFor(key: String): SharedPreferences = if (key in PERSONAL) psp else sp

    // ---------- Perfis ----------

    data class Profile(val id: String, val name: String, val kids: Boolean)

    val profileId: String get() = sp.getString("profile", "main") ?: "main"

    fun profiles(): List<Profile> {
        val a = try { JSONArray(sp.getString("profiles", "[]")) } catch (e: Exception) { JSONArray() }
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Profile(it.optString("id"), it.optString("n"), it.optBoolean("k")) }
        return if (list.none { it.id == "main" }) listOf(Profile("main", "Principal", false)) + list else list
    }

    private fun saveProfiles(list: List<Profile>) {
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("id", p.id).put("n", p.name).put("k", p.kids))
        sp.edit().putString("profiles", a.toString()).apply()
    }

    fun currentProfile(): Profile = profiles().firstOrNull { it.id == profileId } ?: Profile("main", "Principal", false)

    val isKids: Boolean get() = currentProfile().kids

    fun addProfile(name: String, kids: Boolean): Profile {
        val p = Profile("p" + System.currentTimeMillis().toString(36), name, kids)
        saveProfiles(profiles() + p)
        return p
    }

    fun removeProfile(id: String) {
        if (id == "main") return
        saveProfiles(profiles().filter { it.id != id })
        try { app.deleteSharedPreferences("hortencioflix_p_$id") } catch (e: Throwable) {}
        if (profileId == id) switchProfile("main")
    }

    fun switchProfile(id: String) {
        sp.edit().putString("profile", id).apply()
        psp = profileSp(id)
        watchedCache = null
    }

    private fun flag(key: String, def: Boolean) = str(key, if (def) "1" else "0") == "1"
    private fun setFlag(key: String, v: Boolean) = put(key, if (v) "1" else "0")

    var showTorboxRow: Boolean
        get() = flag("showTorboxRow", true)
        set(v) = setFlag("showTorboxRow", v)

    /** Tamanho das legendas: 0 pequeno, 1 normal, 2 grande, 3 muito grande */
    var subSize: Int
        get() = str("subSize", "1").toIntOrNull() ?: 1
        set(v) = put("subSize", v.toString())

    /** Cor: 0 branco, 1 amarelo */
    var subColor: Int
        get() = str("subColor", "0").toIntOrNull() ?: 0
        set(v) = put("subColor", v.toString())

    /** Fundo: 0 contorno, 1 semitransparente, 2 preto */
    var subBg: Int
        get() = str("subBg", "0").toIntOrNull() ?: 0
        set(v) = put("subBg", v.toString())

    /** Posição: 0 em baixo, 1 mais acima */
    var subPos: Int
        get() = str("subPos", "0").toIntOrNull() ?: 0
        set(v) = put("subPos", v.toString())

    var askResume: Boolean
        get() = flag("askResume", true)
        set(v) = setFlag("askResume", v)

    var autoNext: Boolean
        get() = flag("autoNext", true)
        set(v) = setFlag("autoNext", v)

    /** Guia EPG global (XMLTV) — opcional */
    var epgUrl: String
        get() = str("epgUrl")
        set(v) = put("epgUrl", v.trim())

    fun recentSearches(): List<String> {
        val a = array("recentSearch")
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
    }

    fun addRecentSearch(q: String) {
        val list = (listOf(q) + recentSearches().filter { !it.equals(q, true) }).take(10)
        putArray("recentSearch", JSONArray(list))
    }

    fun clearRecentSearches() = remove("recentSearch")

    // ---------- Episódios / filmes vistos ----------

    private var watchedCache: HashSet<String>? = null

    private fun watchedSet(): HashSet<String> {
        watchedCache?.let { return it }
        val a = array("watched")
        val set = HashSet<String>()
        for (i in 0 until a.length()) set.add(a.optString(i))
        watchedCache = set
        return set
    }

    fun isWatched(videoId: String) = watchedSet().contains(videoId)

    fun setWatched(ids: Collection<String>, watched: Boolean) {
        val set = watchedSet()
        if (watched) set.addAll(ids) else set.removeAll(ids.toSet())
        val list = set.toList().takeLast(4000)
        putArray("watched", JSONArray(list))
    }

    // ---------- Add-ons e catálogos ----------

    fun disabledAddons(): Set<String> {
        val a = array("addonsOff")
        return (0 until a.length()).map { a.optString(it) }.toSet()
    }

    fun setAddonEnabled(base: String, on: Boolean) {
        val s = disabledAddons().toMutableSet()
        if (on) s.remove(base) else s.add(base)
        putArray("addonsOff", JSONArray(s.toList()))
    }

    fun isCatalogHidden(key: String): Boolean {
        val a = array("catsOff")
        for (i in 0 until a.length()) if (a.optString(i) == key) return true
        return false
    }

    fun setCatalogHidden(key: String, hidden: Boolean) {
        val a = array("catsOff")
        val set = (0 until a.length()).map { a.optString(it) }.toMutableSet()
        if (hidden) set.add(key) else set.remove(key)
        putArray("catsOff", JSONArray(set.toList()))
    }

    // ---------- Poupança de dados ----------

    var dataSaver: Boolean
        get() = flag("dataSaver", false)
        set(v) = setFlag("dataSaver", v)

    fun onMeteredNetwork(): Boolean = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    } catch (e: Exception) { false }

    /** «Wi-Fi», «Dados móveis», «Cabo» ou «Sem ligação». */
    fun networkKind(): String = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "Sem ligação"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cabo"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
            else -> "Outra"
        }
    } catch (e: Exception) { "Desconhecida" }

    // ---------- PIN ----------

    var pin: String
        get() = str("pin")
        set(v) = put("pin", v)

    // ---------- Cópia de segurança ----------

    /** withSecrets = false: cópia sem chaves nem contas (pode ser partilhada sem risco). */
    fun exportAll(withSecrets: Boolean = true): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (v !is String || k == "library" || k == "playLog" || k == "srcStats") continue
            if (k in SECRET || k == "traktId") { if (withSecrets) o.put(k, str(k)) } else o.put(k, v)
        }
        return JSONObject().put("app", "hortencioflix").put("v", 1).put("prefs", o)
    }

    fun importAll(root: JSONObject): Int {
        val o = root.optJSONObject("prefs") ?: return 0
        val e = sp.edit()
        var n = 0
        for (k in o.keys()) { if (k !in SECRET) e.putString(k, o.optString(k)); n++ }
        e.apply()
        for (k in SECRET) if (o.has(k)) put(k, o.optString(k))
        watchedCache = null
        return n
    }

    var traktScrobble: Boolean
        get() = flag("traktScrobble", true)
        set(v) = setFlag("traktScrobble", v)

    var notifyEpisodes: Boolean
        get() = flag("notifyEp", true)
        set(v) = setFlag("notifyEp", v)

    /** Serviço de debrid usado com o Torrentio: torbox, realdebrid, alldebrid, premiumize, debridlink, offcloud */
    var debridService: String
        get() = str("debridSvc", "torbox")
        set(v) = put("debridSvc", v)

    var debridKey: String
        get() = str("debridKey")
        set(v) = put("debridKey", v.trim())

    /** Índice da cor de destaque (0 = vermelho da marca). */
    var accent: Int
        get() = str("accent", "0").toIntOrNull() ?: 0
        set(v) = put("accent", v.toString())

    /** Tamanho dos posters: 0 pequeno, 1 normal, 2 grande. */
    var posterSize: Int
        get() = (str("posterSize", "1").toIntOrNull() ?: 1).coerceIn(0, 2)
        set(v) = put("posterSize", v.toString())

    fun posterScale(): Float = floatArrayOf(0.85f, 1f, 1.2f)[posterSize]

    /** Há um serviço debrid configurado (TorBox ou outro)? */
    fun hasDebrid(): Boolean = if (debridService == "torbox") torboxKey.isNotEmpty() else debridKey.isNotEmpty()

    /** Tema: "dark" (padrão), "amoled", "light" ou "glass". */
    var themeMode: String
        get() = str("themeMode").ifEmpty { if (amoled) "amoled" else "dark" }
        set(v) { put("themeMode", v); amoled = v == "amoled" }

    var amoled: Boolean
        get() = flag("amoled", false)
        set(v) = setFlag("amoled", v)

    var audioLang: String
        get() = str("audioLang")
        set(v) = put("audioLang", v)

    fun str(key: String, def: String = ""): String {
        val raw = spFor(key).getString(key, null) ?: return def
        return if (key in SECRET) Secrets.decrypt(raw) else raw
    }
    fun put(key: String, value: String) { spFor(key).edit().putString(key, if (key in SECRET) Secrets.encrypt(value) else value).apply() }
    fun remove(key: String) { spFor(key).edit().remove(key).apply() }

    fun array(key: String): JSONArray = try { JSONArray(str(key, "[]")) } catch (e: Exception) { JSONArray() }
    fun putArray(key: String, arr: JSONArray) = put(key, arr.toString())

    var torboxKey: String
        get() = str("torboxKey")
        set(v) = put("torboxKey", v.trim())

    var stremioKey: String
        get() = str("stremioKey")
        set(v) = put("stremioKey", v)

    var stremioEmail: String
        get() = str("stremioEmail")
        set(v) = put("stremioEmail", v)

    /** Idioma das legendas: por, eng, spa, fre, off */
    var subLang: String
        get() = str("subLang", "por")
        set(v) = put("subLang", v)

    /** Qualidade máxima: 0 (todas), 2160, 1080, 720 */
    var maxQuality: Int
        get() = str("maxQ", "0").toIntOrNull() ?: 0
        set(v) = put("maxQ", v.toString())

    var cachedOnly: Boolean
        get() = str("cachedOnly", "0") == "1"
        set(v) = put("cachedOnly", if (v) "1" else "0")

    var hideRisky: Boolean
        get() = str("hideRisky", "0") == "1"
        set(v) = put("hideRisky", if (v) "1" else "0")

    var syncProgress: Boolean
        get() = str("syncProgress", "1") == "1"
        set(v) = put("syncProgress", if (v) "1" else "0")

    fun localAddons(): List<String> {
        val arr = array("addons")
        if (arr.length() == 0 && str("addons").isEmpty()) return listOf(Addons.CINEMETA)
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
    }

    fun setLocalAddons(list: List<String>) = putArray("addons", JSONArray(list))

    fun stremioAddons(): List<String> {
        val arr = array("stremioAddons")
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
    }

    fun setStremioAddons(list: List<String>) = putArray("stremioAddons", JSONArray(list))

    // ---------- Progresso local ("Continuar a ver") ----------

    fun progressList(): JSONArray = array("progress")

    fun saveProgress(metaId: String, videoId: String, posSec: Long, durSec: Long, name: String, poster: String, type: String) {
        val hid = array("hiddenContinue")
        val keep = JSONArray()
        for (i in 0 until hid.length()) if (hid.optString(i) != metaId) keep.put(hid.optString(i))
        if (keep.length() != hid.length()) putArray("hiddenContinue", keep)
        val out = JSONArray()
        out.put(JSONObject().apply {
            put("m", metaId); put("v", videoId); put("t", posSec); put("d", durSec)
            put("n", name); put("p", poster); put("y", type); put("a", System.currentTimeMillis() / 1000)
        })
        val old = progressList()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("m") != metaId && out.length() < 20) out.put(o)
        }
        putArray("progress", out)
    }

    fun removeProgress(metaId: String) {
        val out = JSONArray()
        val old = progressList()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("m") != metaId) out.put(o)
        }
        putArray("progress", out)
    }

    fun resumeFor(videoId: String, metaId: String): Long {
        val arr = progressList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("v") == videoId) return o.optLong("t")
        }
        val lib = Stremio.libraryItem(metaId)
        val st = lib?.optJSONObject("state")
        if (st != null) {
            val vid = st.optString("video_id")
            val off = st.optLong("timeOffset") / 1000
            if (off > 0 && (vid == videoId || vid.isEmpty() || vid == metaId)) return off
        }
        return 0
    }

    // ---------- IPTV ----------

    fun iptvLists(): JSONArray = array("iptvLists")
    fun setIptvLists(arr: JSONArray) = putArray("iptvLists", arr)

    fun favorites(): JSONArray = array("iptvFav")

    fun toggleFavorite(ch: Channel): Boolean {
        val old = favorites()
        val out = JSONArray()
        var removed = false
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("u") == ch.url) removed = true else out.put(o)
        }
        if (!removed) {
            val withNew = JSONArray()
            withNew.put(JSONObject().put("n", ch.name).put("u", ch.url).put("l", ch.logo))
            for (i in 0 until out.length()) withNew.put(out.get(i))
            putArray("iptvFav", withNew)
        } else {
            putArray("iptvFav", out)
        }
        return !removed
    }

    private fun strSet(key: String): MutableSet<String> {
        val a = array(key)
        return (0 until a.length()).map { a.optString(it) }.toMutableSet()
    }

    private fun putSet(key: String, set: Set<String>) = putArray(key, JSONArray(set.toList().takeLast(5000)))

    fun hiddenGroups(): Set<String> = strSet("hiddenGroups")
    fun setGroupHidden(title: String, hidden: Boolean) {
        val s = strSet("hiddenGroups"); if (hidden) s.add(title) else s.remove(title); putSet("hiddenGroups", s)
    }

    fun deadChannels(): Set<String> = strSet("deadCh")
    fun setDead(urls: Collection<String>, alive: Collection<String>) {
        val s = strSet("deadCh"); s.addAll(urls); s.removeAll(alive.toSet()); putSet("deadCh", s)
    }

    var hideDead: Boolean
        get() = flag("hideDead", false)
        set(v) = setFlag("hideDead", v)

    fun moveFavorite(url: String, dir: Int) {
        val a = favorites()
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
        val i = list.indexOfFirst { it.optString("u") == url }
        val j = i + dir
        if (i < 0 || j < 0 || j >= list.size) return
        val t = list[i]; list[i] = list[j]; list[j] = t
        putArray("iptvFav", JSONArray(list))
    }

    fun addRecentChannel(ch: Channel) {
        val old = array("recentCh")
        val out = JSONArray()
        out.put(JSONObject().put("n", ch.name).put("u", ch.url).put("l", ch.logo).put("g", ch.group).put("t", ch.tvgId))
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("u") != ch.url && out.length() < 15) out.put(o)
        }
        putArray("recentCh", out)
    }

    fun recentChannels(): List<Channel> {
        val a = array("recentCh")
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .map { Channel(it.optString("n"), it.optString("l"), it.optString("u"), it.optString("g"), it.optString("t")) }
    }

    fun favoriteChannels(): List<Channel> {
        val arr = favorites()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .map { Channel(it.optString("n"), it.optString("l"), it.optString("u"), "Favoritos") }
    }
}
