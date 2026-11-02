package com.hortencioflix.tv.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Sincronização na nuvem (pelo teu servidor de Fontes curadas): «Continuar a ver», vistos e acabados ficam
 * iguais no telemóvel e na Roku em qualquer rede — sem a Roku ter de estar ligada nem na mesma Wi-Fi.
 * Cada lado envia o que tem e recebe a junção (o mais recente de cada título ganha).
 */
object CloudSync {
    private val client by lazy { Http.client.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build() }
    @Volatile private var lastAt = 0L
    @Volatile private var running = false

    fun enabled() = Prefs.str("cloudSync", "1") == "1" && Prefs.str("curatorUrl").startsWith("https://")

    /** Código secreto da casa (criado uma vez; passa para a Roku na sincronização local). */
    fun id(): String {
        var id = Prefs.str("cloudSyncId")
        if (id.length < 20) {
            val abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val r = SecureRandom()
            id = (1..28).map { abc[r.nextInt(abc.length)] }.joinToString("")
            Prefs.put("cloudSyncId", id)
        }
        return id
    }

    fun addDone(metaId: String) {
        if (metaId.isEmpty()) return
        val a = Prefs.array("doneRecent")
        val out = JSONArray().put(metaId)
        for (i in 0 until a.length()) if (a.optString(i) != metaId && out.length() < 50) out.put(a.optString(i))
        Prefs.putArray("doneRecent", out)
    }

    /** Bloqueante (chamar fora da thread principal). force = ignora o intervalo mínimo de 20 s. */
    fun sync(force: Boolean = false): String {
        if (!enabled()) return "Sincronização na nuvem desligada (precisa do servidor de Fontes curadas)."
        val now = System.currentTimeMillis()
        if (running || (!force && now - lastAt < 20_000)) return "Já sincronizado."
        running = true
        try {
            val body = JSONObject()
                .put("device", "android")
                .put("progress", Prefs.progressList())
                .put("watched", JSONArray(Prefs.array("watched").let { a -> (maxOf(0, a.length() - 3000) until a.length()).map { a.optString(it) } }))
                .put("done", Prefs.array("doneRecent"))
                .put("hidden", Prefs.array("hiddenContinue"))
            val url = Prefs.str("curatorUrl").trimEnd('/') + "/sync/" + id()
            val d = client.newCall(Request.Builder().url(url).header("User-Agent", Http.UA)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                if (!r.isSuccessful) return "O servidor respondeu ${r.code}."
                JSONObject(r.body?.string() ?: "{}")
            }
            if (!d.optBoolean("ok")) return d.optString("error", "Resposta inválida do servidor.")
            // «Continuar a ver»: a junção dos dois lados
            d.optJSONArray("progress")?.let { p ->
                val out = JSONArray()
                for (i in 0 until minOf(p.length(), 20)) out.put(p.get(i))
                Prefs.putArray("progress", out)
            }
            // vistos: junta
            d.optJSONArray("watched")?.let { w ->
                val local = Prefs.array("watched")
                val set = LinkedHashSet<String>()
                for (i in 0 until local.length()) set.add(local.optString(i))
                for (i in 0 until w.length()) set.add(w.optString(i))
                Prefs.putArray("watched", JSONArray(set.toList().takeLast(4000)))
            }
            lastAt = System.currentTimeMillis()
            Prefs.put("cloudSyncAt", lastAt.toString())
            return "Sincronizado: ${d.optJSONArray("progress")?.length() ?: 0} em «Continuar a ver»."
        } catch (e: Exception) {
            return "Sem ligação ao servidor (${e.javaClass.simpleName})."
        } finally {
            running = false
        }
    }

    fun syncQuietly(force: Boolean = false) {
        if (enabled()) Thread { try { sync(force) } catch (e: Exception) {} }.start()
    }
}
