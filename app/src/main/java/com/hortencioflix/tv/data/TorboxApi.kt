package com.hortencioflix.tv.data

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Conta TorBox (plano, validade) e downloads pedidos pela app («Adicionar ao TorBox e avisar-me»). */
object TorboxApi {
    private const val BASE = "https://api.torbox.app/v1/api"

    data class Account(
        val reachable: Boolean,
        val invalidKey: Boolean = false,
        val plan: String = "",
        val expiresAt: Long = 0L,
        val email: String = "",
        val free: Boolean = false
    ) {
        val expired get() = expiresAt in 1 until System.currentTimeMillis()
        val daysLeft get() = if (expiresAt <= 0) -1L else ((expiresAt - System.currentTimeMillis()) / 86_400_000L)
    }

    private fun auth() = mapOf("Authorization" to "Bearer ${Prefs.torboxKey}")

    private fun parseDate(s: String): Long = try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(s.take(19))?.time ?: 0L
    } catch (e: Exception) { 0L }

    private var last: Account? = null
    private var lastAt = 0L

    /** Estado da conta (guardado 30 min). Corre fora do ecrã (rede). */
    fun account(fresh: Boolean = false): Account? {
        if (Prefs.torboxKey.isEmpty()) return null
        val now = System.currentTimeMillis()
        if (!fresh && last != null && now - lastAt < 30 * 60_000L) return last
        val acc = try {
            val b = Request.Builder().url("$BASE/user/me?settings=false").header("User-Agent", Http.UA)
            for ((k, v) in auth()) b.header(k, v)
            Http.client.newCall(b.build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.code == 401 || r.code == 403) return@use Account(reachable = true, invalidKey = true)
                val o = try { JSONObject(body) } catch (e: Exception) { null }
                val d = o?.optJSONObject("data")
                if (d == null) {
                    val detail = o?.optString("error").orEmpty() + o?.optString("detail").orEmpty()
                    Account(reachable = true, invalidKey = detail.contains("auth", true) || detail.contains("token", true))
                } else {
                    val planNo = d.optInt("plan", -1)
                    val plan = when (planNo) { 0 -> "Gratuito"; 1 -> "Essential"; 2 -> "Pro"; 3 -> "Standard"; else -> "" }
                    Account(reachable = true, plan = plan, expiresAt = parseDate(d.optString("premium_expires_at")),
                        email = d.optString("email"), free = planNo == 0)
                }
            }
        } catch (e: Exception) {
            Account(reachable = false)
        }
        if (acc.reachable) { last = acc; lastAt = now }
        return acc
    }

    /** Último estado conhecido, sem ir à rede (para ecrãs). */
    fun cached(): Account? = last

    /** Aviso para mostrar ao utilizador, ou null se a conta está bem. */
    fun warning(a: Account? = last): String? {
        if (a == null || !a.reachable) return null
        return when {
            a.invalidKey -> "A chave do TorBox foi recusada. Volta a colá-la em Definições › TorBox."
            a.free -> "A tua conta TorBox está no plano gratuito: muitas fontes não tocam ou ficam por descarregar. Ativa um plano pago em torbox.app."
            a.expired -> "A tua subscrição TorBox terminou. Renova em torbox.app para as fontes voltarem a funcionar."
            a.daysLeft in 0..4 -> "A tua subscrição TorBox termina em ${a.daysLeft} dia(s)."
            else -> null
        }
    }

    fun summary(a: Account?): String = when {
        a == null -> "sem chave configurada"
        !a.reachable -> "sem resposta do TorBox"
        a.invalidKey -> "❌ chave recusada"
        else -> (if (a.expired || a.free) "❌ " else "✅ ") + listOfNotNull(
            a.plan.ifEmpty { null }?.let { "plano $it" },
            if (a.expiresAt > 0) (if (a.expired) "terminou" else "válido até") + " " + SimpleDateFormat("dd/MM/yyyy", Locale.US).format(a.expiresAt) else null,
            a.email.ifEmpty { null }
        ).joinToString(" · ")
    }

    // ---------- Adicionar ao TorBox e avisar ----------

    private val rxHash = Regex("(?<![0-9a-fA-F])([0-9a-fA-F]{40})(?![0-9a-fA-F])")

    /** O «hash» do torrent dentro do link de uma fonte (ex.: links do Torrentio/TorBox). */
    fun hashOf(url: String): String? = rxHash.find(url)?.groupValues?.get(1)?.lowercase()

    fun canQueue(s: StreamItem) = Prefs.torboxKey.isNotEmpty() && !s.cached && hashOf(s.url) != null

    /** Envia o torrent para o TorBox. Devolve (ok, mensagem). */
    fun queue(hash: String): Pair<Boolean, String> {
        val r = Http.postForm("$BASE/torrents/createtorrent", mapOf("magnet" to "magnet:?xt=urn:btih:$hash"), auth())
            ?: return false to "Sem resposta do TorBox."
        if (r.optBoolean("success")) return true to "Adicionado ao TorBox."
        val detail = r.optString("detail").ifEmpty { r.optString("error") }
        return when {
            detail.contains("already", true) || detail.contains("exist", true) -> true to "Já estava no TorBox."
            detail.contains("limit", true) -> false to "Atingiste o limite de downloads do teu plano TorBox. Apaga alguns em «TorBox · os meus downloads»."
            else -> false to "O TorBox recusou: ${detail.ifEmpty { "erro desconhecido" }}"
        }
    }

    /** Downloads da conta, por hash. */
    fun list(): Map<String, JSONObject> {
        val o = Http.getJsonAuth("$BASE/torrents/mylist?bypass_cache=true", auth()) ?: return emptyMap()
        val arr: JSONArray = o.optJSONArray("data") ?: return emptyMap()
        val out = HashMap<String, JSONObject>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { t -> t.optString("hash").lowercase().takeIf { it.isNotEmpty() }?.let { out[it] = t } }
        return out
    }

    fun isReady(t: JSONObject): Boolean {
        val st = t.optString("download_state").lowercase()
        return t.optBoolean("download_finished") || t.optBoolean("download_present") ||
            st.contains("cached") || st.contains("completed") || st.contains("uploading") || st.contains("seeding")
    }

    fun progress(t: JSONObject): Int = (t.optDouble("progress", 0.0) * 100).toInt().coerceIn(0, 100)

    // Lista do que a app está a vigiar: [{h, n, m, t, v, p, at}]
    private const val WATCH = "tbWatch"

    fun watched(): JSONArray = Prefs.array(WATCH)

    fun watch(hash: String, meta: MetaItem, videoId: String, label: String) {
        val arr = watched()
        val out = JSONArray()
        out.put(JSONObject().put("h", hash).put("n", label).put("m", meta.id).put("t", meta.type).put("v", videoId)
            .put("p", meta.poster).put("title", meta.name).put("at", System.currentTimeMillis()))
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { if (it.optString("h") != hash && out.length() < 20) out.put(it) }
        Prefs.putArray(WATCH, out)
    }

    fun setWatched(arr: JSONArray) = Prefs.putArray(WATCH, arr)
}
