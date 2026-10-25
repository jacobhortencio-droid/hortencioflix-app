package com.hortencioflix.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Conta Stremio: login, add-ons, biblioteca e progresso. Funções bloqueantes (chamar em Dispatchers.IO). */
object Stremio {
    private const val API = "https://api.strem.io/api/"

    @Volatile private var library: JSONArray? = null

    val loggedIn: Boolean get() = Prefs.stremioKey.isNotEmpty()

    private fun call(method: String, body: JSONObject): JSONObject? {
        val r = Http.postJson(API + method, body) ?: return null
        if (r.has("error") && !r.isNull("error")) return null
        return r
    }

    /** Devolve null se correu bem, ou uma mensagem de erro. */
    fun login(email: String, password: String): String? {
        val r = Http.postJson(API + "login", JSONObject().put("type", "Login").put("email", email).put("password", password).put("facebook", false))
            ?: return "Sem ligação ao Stremio."
        val err = r.optJSONObject("error")
        if (err != null) return err.optString("message", "Email ou palavra-passe errados.")
        val key = r.optJSONObject("result")?.optString("authKey") ?: ""
        if (key.isEmpty()) return "Resposta inesperada do Stremio."
        Prefs.stremioKey = key
        Prefs.stremioEmail = email
        return null
    }

    /** Cria um código de ligação (link.stremio.com). Devolve (código, link) ou null. */
    fun linkCreate(): Pair<String, String>? {
        val r = Http.getJson("https://link.stremio.com/api/v2/create?type=Create")?.optJSONObject("result") ?: return null
        val code = r.optString("code")
        val link = r.optString("link").ifEmpty { "https://link.stremio.com" }
        return if (code.isEmpty()) null else code to link
    }

    /** Verifica se o código já foi aprovado. Devolve authKey ou null. */
    fun linkRead(code: String): String? {
        val r = Http.getJson("https://link.stremio.com/api/v2/read?type=Read&code=$code")?.optJSONObject("result") ?: return null
        val key = r.optString("authKey")
        return key.ifEmpty { null }
    }

    fun acceptKey(key: String) {
        Prefs.stremioKey = key
        val u = call("getUser", JSONObject().put("type", "GetUser").put("authKey", key))
        Prefs.stremioEmail = u?.optJSONObject("result")?.optString("email") ?: ""
    }

    fun logout() {
        Prefs.stremioKey = ""
        Prefs.stremioEmail = ""
        Prefs.setStremioAddons(emptyList())
        Prefs.remove("library")
        library = null
    }

    /** Sincroniza add-ons e biblioteca. Devolve um resumo. */
    fun sync(): String {
        val key = Prefs.stremioKey
        if (key.isEmpty()) return "Sem conta Stremio."
        var nAddons = -1
        val ac = call("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", key).put("update", true))
        val arr = ac?.optJSONObject("result")?.optJSONArray("addons")
        if (arr != null) {
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val url = a.optString("transportUrl")
                if (!url.startsWith("http")) continue
                val base = Addons.normalize(url)
                list.add(base)
                a.optJSONObject("manifest")?.let { Addons.putManifest(base, it) }
            }
            Prefs.setStremioAddons(list)
            nAddons = list.size
        }
        var nLib = -1
        val lg = call("datastoreGet", JSONObject().put("authKey", key).put("collection", "libraryItem").put("ids", JSONArray()).put("all", true))
        val items = lg?.optJSONArray("result")
        if (items != null) {
            val keep = JSONArray()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                if (it.optBoolean("removed") && !hasProgress(it)) continue
                keep.put(it)
            }
            library = keep
            Prefs.put("library", keep.toString())
            nLib = keep.length()
        }
        if (nAddons < 0 && nLib < 0) return "Não foi possível sincronizar (sessão expirada?)."
        return "Sincronizado: ${maxOf(nAddons, 0)} add-ons, ${maxOf(nLib, 0)} itens na biblioteca."
    }

    private fun hasProgress(it: JSONObject): Boolean {
        val st = it.optJSONObject("state") ?: return false
        return st.optLong("timeOffset") > 0
    }

    private fun lib(): JSONArray {
        library?.let { return it }
        val s = Prefs.str("library", "[]")
        val a = try { JSONArray(s) } catch (e: Exception) { JSONArray() }
        library = a
        return a
    }

    fun libraryItem(metaId: String): JSONObject? {
        val a = lib()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("_id") == metaId) return o
        }
        return null
    }

    private fun mtime(o: JSONObject): String = o.optJSONObject("state")?.optString("lastWatched")?.takeIf { it.isNotEmpty() } ?: o.optString("_mtime")

    /** "Continuar a ver" da conta Stremio. */
    fun continueItems(): List<MetaItem> {
        val a = lib()
        val list = ArrayList<JSONObject>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val st = o.optJSONObject("state") ?: continue
            if (st.optLong("timeOffset") <= 0) continue
            if (o.optString("type") != "movie" && o.optString("type") != "series") continue
            list.add(o)
        }
        list.sortByDescending { mtime(it) }
        return list.take(20).map { o ->
            val st = o.optJSONObject("state")!!
            val dur = st.optLong("duration")
            val prog = if (dur > 0) (st.optLong("timeOffset").toFloat() / dur).coerceIn(0f, 1f) else 0f
            MetaItem(o.optString("_id"), o.optString("type"), o.optString("name"), o.optString("poster"), videoId = st.optString("video_id"), progress = prog)
        }
    }

    /** Biblioteca (filmes e séries guardados). */
    fun libraryItems(): List<MetaItem> {
        val a = lib()
        val list = ArrayList<JSONObject>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optBoolean("removed") || o.optBoolean("temp")) continue
            list.add(o)
        }
        list.sortByDescending { mtime(it) }
        return list.take(60).map { MetaItem(it.optString("_id"), it.optString("type"), it.optString("name"), it.optString("poster")) }
    }

    private fun iso(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    fun inLibrary(metaId: String): Boolean {
        val o = libraryItem(metaId) ?: return false
        return !o.optBoolean("removed") && !o.optBoolean("temp")
    }

    private fun baseItem(meta: MetaItem): JSONObject {
        val now = iso(System.currentTimeMillis())
        return libraryItem(meta.id) ?: JSONObject().apply {
            put("_id", meta.id); put("name", meta.name); put("type", meta.type); put("poster", meta.poster)
            put("posterShape", "poster"); put("removed", true); put("temp", true); put("_ctime", now)
            put("state", JSONObject().apply {
                put("lastWatched", now); put("timeWatched", 0); put("timeOffset", 0); put("overallTimeWatched", 0)
                put("timesWatched", 0); put("flaggedWatched", 0); put("duration", 0); put("video_id", meta.id)
                put("watched", ""); put("noNotif", false)
            })
            put("behaviorHints", JSONObject().put("defaultVideoId", JSONObject.NULL).put("featuredVideoId", JSONObject.NULL).put("hasScheduledVideos", false))
        }
    }

    private fun upsert(item: JSONObject) {
        val a = lib()
        val out = JSONArray()
        out.put(item)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("_id") != item.optString("_id")) out.put(o)
        }
        library = out
        Prefs.put("library", out.toString())
    }

    private fun put(item: JSONObject): Boolean {
        upsert(item)
        val key = Prefs.stremioKey
        if (key.isEmpty()) return false
        val r = call("datastorePut", JSONObject().put("authKey", key).put("collection", "libraryItem").put("changes", JSONArray().put(item)))
        return r != null
    }

    fun setInLibrary(meta: MetaItem, add: Boolean): Boolean {
        val item = JSONObject(baseItem(meta).toString())
        item.put("removed", !add)
        item.put("temp", false)
        item.put("_mtime", iso(System.currentTimeMillis()))
        return put(item)
    }

    /** Retira o progresso de um título na conta (deixa de aparecer em «Continuar a ver»). */
    fun clearProgress(metaId: String) {
        val cur = libraryItem(metaId) ?: return
        val item = JSONObject(cur.toString())
        val st = item.optJSONObject("state") ?: return
        st.put("timeOffset", 0)
        item.put("state", st)
        item.put("_mtime", iso(System.currentTimeMillis()))
        put(item)
    }

    /** Envia o progresso de reprodução para a conta. */
    fun pushProgress(meta: MetaItem, videoId: String, posMs: Long, durMs: Long) {
        if (!loggedIn || !Prefs.syncProgress) return
        val item = JSONObject(baseItem(meta).toString())
        val now = iso(System.currentTimeMillis())
        val st = item.optJSONObject("state") ?: JSONObject()
        st.put("lastWatched", now)
        st.put("timeOffset", posMs)
        st.put("duration", durMs)
        st.put("video_id", videoId)
        st.put("timeWatched", st.optLong("timeWatched") + 10000)
        st.put("overallTimeWatched", st.optLong("overallTimeWatched") + 10000)
        if (durMs > 0 && posMs > durMs * 0.9) {
            st.put("timeOffset", 0)
            st.put("timesWatched", st.optInt("timesWatched") + 1)
            st.put("flaggedWatched", 1)
        }
        item.put("state", st)
        item.put("_mtime", now)
        if (item.optBoolean("removed")) item.put("temp", true)
        put(item)
    }
}
