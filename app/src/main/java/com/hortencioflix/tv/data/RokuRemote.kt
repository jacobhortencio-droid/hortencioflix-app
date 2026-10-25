package com.hortencioflix.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * «Enviar para a TV» e teclado da Roku, pelo protocolo de controlo da Roku (ECP, porta 8060, na rede de casa).
 * Abrir um título usa o deep link da app de programador: se a app estiver fechada abre-a, se estiver aberta
 * recebe o pedido na hora (roInput) e começa a reproduzir.
 */
object RokuRemote {
    private val lan by lazy {
        OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    }

    val available get() = Prefs.str("rokuIp").isNotBlank()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun post(path: String): Boolean {
        val ip = Prefs.str("rokuIp").trim()
        if (ip.isEmpty()) return false
        return try {
            lan.newCall(Request.Builder().url("http://$ip:8060/$path").post(ByteArray(0).toRequestBody(null)).build())
                .execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /** Abre e reproduz na Roku. `videoId` = id do episódio (tt…:T:E) ou do filme. */
    suspend fun sendTitle(metaId: String, type: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        val ep = videoId.count { it == ':' } >= 2
        val cid = if (ep) videoId else metaId
        val mt = if (ep) "episode" else if (type == "series") "series" else "movie"
        post("launch/dev?contentId=${enc(cid)}&mediaType=$mt")
    }

    /** Escreve texto na Roku (ex.: na pesquisa), letra a letra. */
    suspend fun type(text: String): Boolean = withContext(Dispatchers.IO) {
        var ok = true
        for (c in text) {
            ok = post("keypress/Lit_" + enc(c.toString())) && ok
        }
        ok
    }

    /** Uma tecla do comando: Home, Back, Select, Up, Down, Left, Right, Play, Rev, Fwd, Info, Backspace… */
    suspend fun key(k: String): Boolean = withContext(Dispatchers.IO) { post("keypress/$k") }
}
