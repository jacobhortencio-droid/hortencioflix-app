package com.hortencioflix.tv.data

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Instala a versão nova da Hortêncioflix na Roku pela rede de casa, usando o instalador do
 * modo de programador da própria Roku (http://IP-da-Roku, utilizador «rokudev»).
 */
object RokuUpdater {
    private const val BASE = "https://raw.githubusercontent.com/jacobhortencio-droid/hortencioflix-app/apk/"

    val configured get() = Prefs.str("rokuIp").isNotEmpty() && Prefs.str("rokuPass").isNotEmpty()

    private val lan by lazy {
        Http.client.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS).followRedirects(false).build()
    }

    private fun gh(): Map<String, String> {
        val t = Prefs.str("ghToken")
        return if (t.isEmpty()) emptyMap() else mapOf("Authorization" to "token $t")
    }

    /** Versão publicada da app Roku (ex.: «0.7.3»), ou null. */
    fun latest(): String? = Http.getStringH(BASE + "roku-version.txt?t=" + System.currentTimeMillis() / 60000, gh())?.trim()?.ifEmpty { null }

    /** Versão instalada na Roku (canal de programador), pelo protocolo de controlo da Roku (porta 8060). */
    fun installed(ip: String): String? = try {
        val req = Request.Builder().url("http://$ip:8060/query/apps").build()
        lan.newCall(req).execute().use { r ->
            val xml = r.body?.string() ?: return null
            Regex("<app id=\"dev\"[^>]*version=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
        }
    } catch (e: Exception) { null }

    /** A Roku está a mostrar a Hortêncioflix neste momento? (não atualizar a meio de um filme) */
    fun inUse(ip: String): Boolean = try {
        val req = Request.Builder().url("http://$ip:8060/query/active-app").build()
        lan.newCall(req).execute().use { r -> (r.body?.string() ?: "").contains("id=\"dev\"") }
    } catch (e: Exception) { false }

    fun newer(a: String, b: String?): Boolean {
        if (b == null) return true
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun download(ctx: Context): File? = try {
        val f = File(ctx.cacheDir, "roku-update.zip")
        val rb = Request.Builder().url(BASE + "Hortencioflix-roku.zip?t=" + System.currentTimeMillis() / 60000)
        for ((k, v) in gh()) rb.header(k, v)
        Http.client.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.byteStream()?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return null
        }
        // um zip começa sempre por «PK»
        if (f.length() < 1000 || f.inputStream().use { it.read() != 'P'.code }) { f.delete(); null } else f
    } catch (e: Exception) { null }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Resultado legível para mostrar ao utilizador. */
    class Outcome(val ok: Boolean, val message: String)

    /** Descarrega a versão nova e envia-a para a Roku. */
    fun update(ctx: Context, force: Boolean = false): Outcome {
        val ip = Prefs.str("rokuIp").trim()
        val pass = Prefs.str("rokuPass")
        if (ip.isEmpty() || pass.isEmpty()) return Outcome(false, "Falta o IP da Roku ou a palavra-passe de programador.")
        val want = latest() ?: return Outcome(false, "Não foi possível ler a versão publicada (verifica o token GitHub).")
        val have = installed(ip)
        if (!force && !newer(want, have)) return Outcome(true, "A Roku já tem a versão mais recente ($have).")
        if (inUse(ip)) {
            // Fecha a Hortêncioflix na Roku (tecla Início pelo controlo de rede) antes de a substituir.
            if (!force && Prefs.str("rokuAutoRunning") == "1") return Outcome(false, "A Hortêncioflix está aberta na Roku; fica para depois.")
            try { lan.newCall(Request.Builder().url("http://$ip:8060/keypress/Home").post(ByteArray(0).toRequestBody(null)).build()).execute().close() } catch (e: Exception) {}
            Thread.sleep(2500)
        }
        val zip = download(ctx) ?: return Outcome(false, "Falhou a transferência da versão Roku.")
        return try {
            val r = install(ip, pass, zip)
            PlayLog.add((if (r.ok) "🔄 Roku atualizada para $want" else "🔄 Roku: ") + if (r.ok) "" else r.message)
            if (r.ok) Prefs.put("rokuInstalled", want)
            if (r.ok) Outcome(true, "Roku atualizada para a versão $want.") else r
        } finally {
            zip.delete()
        }
    }

    private fun install(ip: String, pass: String, zip: File): Outcome {
        val url = "http://$ip/plugin_install"
        fun body() = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("mysubmit", "Replace")
            .addFormDataPart("archive", "hortencioflix.zip", zip.asRequestBody("application/zip".toMediaType()))
            .build()
        return try {
            // 1.º pedido (sem o ficheiro): a Roku responde 401 com os dados da autenticação «digest»
            val challenge = lan.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.code != 401) return Outcome(false, "Resposta inesperada da Roku (${r.code}). O modo de programador está ativo e o IP está certo?")
                r.header("WWW-Authenticate") ?: return Outcome(false, "A Roku não pediu autenticação.")
            }
            fun field(n: String) = Regex("$n=\"([^\"]*)\"").find(challenge)?.groupValues?.get(1) ?: ""
            val realm = field("realm")
            val nonce = field("nonce")
            val qop = if (challenge.contains("auth")) "auth" else ""
            val cnonce = md5(System.nanoTime().toString()).take(16)
            val nc = "00000001"
            val ha1 = md5("rokudev:$realm:$pass")
            val ha2 = md5("POST:/plugin_install")
            val resp = if (qop.isNotEmpty()) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
            var auth = "Digest username=\"rokudev\", realm=\"$realm\", nonce=\"$nonce\", uri=\"/plugin_install\", response=\"$resp\""
            if (qop.isNotEmpty()) auth += ", qop=$qop, nc=$nc, cnonce=\"$cnonce\""
            val opaque = field("opaque")
            if (opaque.isNotEmpty()) auth += ", opaque=\"$opaque\""
            lan.newCall(Request.Builder().url(url).header("Authorization", auth).post(body()).build()).execute().use { r ->
                val html = r.body?.string() ?: ""
                when {
                    r.code == 401 -> Outcome(false, "Palavra-passe de programador da Roku errada.")
                    html.contains("Success", true) || html.contains("Identical to previous version", true) -> Outcome(true, "Instalado.")
                    r.isSuccessful -> Outcome(false, "A Roku recusou o pacote: " + Regex("<font color=\"red\">([^<]+)").find(html)?.groupValues?.get(1).orEmpty().ifEmpty { "erro desconhecido" })
                    else -> Outcome(false, "Erro da Roku (${r.code}).")
                }
            }
        } catch (e: Exception) {
            Outcome(false, "Não foi possível ligar à Roku em $ip (${e.javaClass.simpleName}). Está ligada e na mesma rede?")
        }
    }
}
