package com.hortencioflix.tv.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sincroniza com a app da Roku pela rede de casa (porta 8889, com o código de 4 dígitos que a Roku mostra
 * em Definições): «Continuar a ver», episódios vistos, listas IPTV e favoritos ficam iguais nos dois.
 */
object RokuSync {
    val configured get() = Prefs.str("rokuIp").isNotEmpty() && Prefs.str("rokuSyncCode").isNotEmpty()

    private val lan by lazy {
        Http.client.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    }

    class Result(val ok: Boolean, val message: String)

    @Volatile private var running = false

    fun sync(): Result {
        if (!configured) return Result(false, "Indica o IP da Roku e o código de sincronização.")
        if (running) return Result(true, "Já está a sincronizar.")
        running = true
        try {
            val base = "http://${Prefs.str("rokuIp").trim()}:8889"
            val k = Prefs.str("rokuSyncCode").trim()
            val remote = try {
                lan.newCall(Request.Builder().url("$base/sync.json?k=$k").build()).execute().use { r ->
                    if (r.code == 403) return Result(false, "Código de sincronização errado. Vê o código na Roku: Definições › Sincronizar com a app Android.")
                    if (!r.isSuccessful) return Result(false, "A Roku respondeu ${r.code}.")
                    JSONObject(r.body?.string() ?: "{}")
                }
            } catch (e: Exception) {
                return Result(false, "Não foi possível ligar à Roku. A Hortêncioflix tem de estar aberta na Roku, na mesma rede.")
            }

            // Vistos: junta os dois lados
            val localWatched = Prefs.array("watched")
            val ids = LinkedHashSet<String>()
            for (i in 0 until localWatched.length()) ids.add(localWatched.optString(i))
            ids.addAll(SyncMerge.rokuToIds(remote.optJSONArray("watched") ?: JSONArray()))
            // Títulos acabados de ver num dos lados não voltam ao «Continuar a ver»
            val finished = HashSet<String>()
            val done = remote.optJSONArray("done") ?: JSONArray()
            for (i in 0 until done.length()) finished.add(done.optString(i))
            for (id in ids) if (!id.contains(':')) finished.add(id)
            val hidden = Prefs.array("hiddenContinue")
            for (i in 0 until hidden.length()) finished.add(hidden.optString(i))

            val progress = SyncMerge.progress(Prefs.progressList(), remote.optJSONArray("progress") ?: JSONArray(), finished)
            val lists = SyncMerge.byUrl(Prefs.iptvLists(), remote.optJSONArray("iptvLists") ?: JSONArray(), 15)
            val favs = SyncMerge.byUrl(Prefs.favorites(), remote.optJSONArray("iptvFav") ?: JSONArray(), 40)
            // Add-ons adicionados à mão: junta os dois lados (sem repetidos)
            val addons = LinkedHashSet<String>()
            Prefs.localAddons().forEach { addons.add(Addons.normalize(it)) }
            remote.optJSONArray("addons")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.startsWith("http") }?.let { addons.add(Addons.normalize(it)) } }

            // Guarda no Android
            Prefs.putArray("watched", JSONArray(ids.toList().takeLast(4000)))
            Prefs.putArray("progress", progress)
            Prefs.setIptvLists(lists)
            Prefs.putArray("iptvFav", favs)
            Prefs.setLocalAddons(addons.toList())

            // Envia para a Roku (já junto)
            val rokuProgress = JSONArray()
            for (i in 0 until minOf(progress.length(), 12)) rokuProgress.put(progress.get(i))
            val rokuFavs = JSONArray()
            for (i in 0 until minOf(favs.length(), 20)) favs.optJSONObject(i)?.let { f ->
                val logo = f.optString("l")
                rokuFavs.put(JSONObject().put("n", f.optString("n")).put("u", f.optString("u")).put("l", if (logo.length > 110) "" else logo))
            }
            val body = JSONObject()
                .put("progress", rokuProgress)
                .put("watched", SyncMerge.idsToRoku(ids.filter { it.count { c -> c == ':' } == 2 }.takeLast(2000)))
                .put("iptvLists", lists)
                .put("iptvFav", rokuFavs)
                .put("addons", JSONArray(addons.toList()))
                // Preferências de reprodução: as do telemóvel passam para a Roku
                .put("prefs", JSONObject()
                    .put("subLang", Prefs.subLang)
                    .put("dubPref", Prefs.str("dubPref"))
                    .put("rangePref", Prefs.str("rangePref", "auto"))
                    // servidor de fontes curadas (vazio = desligado na Roku também)
                    .put("curatorUrl", Prefs.str("curatorUrl"))
                    .put("curatorOn", Prefs.str("curatorOn", "1")))
            val ok = try {
                lan.newCall(Request.Builder().url("$base/sync?k=$k")
                    .post(SyncMerge.asciiJson(body).toRequestBody("application/json".toMediaType())).build())
                    .execute().use { it.isSuccessful }
            } catch (e: Exception) { false }
            if (!ok) return Result(false, "A Roku não aceitou os dados. Tenta de novo.")
            Prefs.put("rokuLastSync", System.currentTimeMillis().toString())
            return Result(true, "Sincronizado: ${progress.length()} em «Continuar a ver», ${ids.size} vistos, ${lists.length()} listas IPTV, ${favs.length()} favoritos, ${addons.size} add-ons e as preferências de legendas, áudio e imagem.")
        } finally {
            running = false
        }
    }

    /** Em segundo plano, sem avisos (ao abrir a app e ao acabar de ver algo). */
    fun syncQuietly() {
        if (!configured) return
        Thread { try { sync() } catch (e: Exception) {} }.start()
    }
}
