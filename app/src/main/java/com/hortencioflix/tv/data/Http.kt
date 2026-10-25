package com.hortencioflix.tv.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val UA = "Hortencioflix-Android/1.0"

    /** Cliente com tempos curtos, para testar fontes antes de reproduzir. */
    private val quick: OkHttpClient by lazy {
        client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
    }

    private val warmClient: OkHttpClient by lazy {
        client.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }

    /** true se o link responde com vídeo (não uma página de erro). */
    fun probe(url: String, headers: Map<String, String>): Boolean? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Range", "bytes=0-1")
        for ((k, v) in headers) b.header(k, v)
        quick.newCall(b.build()).execute().use { r ->
            val ct = r.header("Content-Type") ?: ""
            r.code in 200..299 && !ct.startsWith("text/") && !ct.contains("json")
        }
    } catch (e: java.io.InterruptedIOException) {
        null
    } catch (e: Exception) {
        false
    }

    /**
     * «Aquece» uma fonte antes de carregares em Ver: o add-on (ex.: Torrentio) pede já o link ao
     * serviço debrid e a ligação ao servidor fica aberta — o leitor arranca segundos mais cedo.
     */
    fun warm(url: String, headers: Map<String, String>) {
        try {
            val b = Request.Builder().url(url).header("User-Agent", UA).header("Range", "bytes=0-262143")
            for ((k, v) in headers) b.header(k, v)
            warmClient.newCall(b.build()).execute().use { r -> r.body?.byteStream()?.let { s -> val buf = ByteArray(65536); while (s.read(buf) > 0) {} } }
        } catch (e: Exception) {
        }
    }

    /** GET com uma nova tentativa em caso de falha de rede (não em respostas de erro). */
    fun getString(url: String): String? {
        for (attempt in 0..1) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", UA).build()
                return client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            } catch (e: java.io.IOException) {
                if (attempt == 1) return null
                try { Thread.sleep(800) } catch (ie: InterruptedException) { return null }
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    /** Tempo de resposta (ms) e código HTTP de um GET; -1 se falhar. */
    fun ping(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, Long> {
        val t0 = System.currentTimeMillis()
        return try {
            val b = Request.Builder().url(url).header("User-Agent", UA)
            for ((k, v) in headers) b.header(k, v)
            client.newCall(b.build()).execute().use { r -> r.code to (System.currentTimeMillis() - t0) }
        } catch (e: Exception) {
            -1 to (System.currentTimeMillis() - t0)
        }
    }

    fun postAuth(url: String, json: JSONObject, headers: Map<String, String>): JSONObject? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA).post(json.toString().toRequestBody("application/json".toMediaType()))
        for ((k, v) in headers) b.header(k, v)
        client.newCall(b.build()).execute().use { r -> r.body?.string()?.let { JSONObject(it) } }
    } catch (e: Exception) {
        null
    }

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): JSONObject? = try {
        val mb = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
        for ((k, v) in fields) mb.addFormDataPart(k, v)
        val b = Request.Builder().url(url).header("User-Agent", UA).post(mb.build())
        for ((k, v) in headers) b.header(k, v)
        client.newCall(b.build()).execute().use { r -> r.body?.string()?.let { JSONObject(it) } }
    } catch (e: Exception) {
        null
    }

    fun getStringH(url: String, headers: Map<String, String>): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        for ((k, v) in headers) b.header(k, v)
        client.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) {
        null
    }

    fun getJson(url: String): JSONObject? {
        val s = getString(url) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    fun getJsonArray(url: String, headers: Map<String, String>): JSONArray? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        for ((k, v) in headers) b.header(k, v)
        client.newCall(b.build()).execute().use { r ->
            val body = r.body?.string() ?: return null
            if (body.trimStart().startsWith("[")) JSONArray(body) else null
        }
    } catch (e: Exception) {
        null
    }

    fun getJsonAuth(url: String, headers: Map<String, String>): JSONObject? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        for ((k, v) in headers) b.header(k, v)
        client.newCall(b.build()).execute().use { r ->
            val body = r.body?.string() ?: return null
            JSONObject(body)
        }
    } catch (e: Exception) {
        null
    }

    fun postJson(url: String, body: JSONObject): JSONObject? = try {
        val rb = body.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).header("User-Agent", UA).post(rb).build()
        client.newCall(req).execute().use { r -> r.body?.string()?.let { JSONObject(it) } }
    } catch (e: Exception) {
        null
    }

    /** Testa se um link responde (HEAD). Devolve o código HTTP ou -1. */
    fun headCode(url: String, headers: Map<String, String>): Int = try {
        val b = Request.Builder().url(url).head().header("User-Agent", UA)
        for ((k, v) in headers) b.header(k, v)
        client.newCall(b.build()).execute().use { r -> r.code }
    } catch (e: Exception) {
        -1
    }
}
