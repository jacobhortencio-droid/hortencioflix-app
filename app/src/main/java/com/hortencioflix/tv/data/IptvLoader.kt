package com.hortencioflix.tv.data

import android.net.Uri
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Carrega listas IPTV como as outras apps de IPTV fazem:
 *  - Conta Xtream (link get.php ou servidor/utilizador/palavra-passe): pela API (player_api.php) — só os canais
 *    ao vivo, em JSON. O link get.php «m3u_plus» traz também milhares de filmes e séries (dezenas de MB) e
 *    muitas vezes falhava a descarregar ou esgotava a memória.
 *  - Lista M3U: lida linha a linha (sem carregar o ficheiro inteiro em memória).
 *  - Vários servidores IPTV recusam programas que não conhecem: se a lista falhar, tenta com a identificação
 *    de outros leitores (VLC, IPTV Smarters…) e lembra-se da que funcionou, também para ver os canais.
 * As mensagens de erro dizem o que aconteceu (conta expirada, palavra-passe errada, servidor em baixo…).
 */
object IptvLoader {
    private val UAS = listOf(
        Http.UA,
        "VLC/3.0.20 LibVLC/3.0.20",
        "IPTVSmartersPlayer",
        "okhttp/4.12.0",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    )

    class Result(val groups: List<ChannelGroup>, val m3uText: String?, val error: String?, val epgUrl: String = "")

    private fun host(url: String) = try { Uri.parse(url).host?.lowercase() ?: "" } catch (e: Exception) { "" }

    /** Identificação (User-Agent) que este servidor aceita (a que funcionou ao carregar a lista). */
    fun uaFor(url: String): String = Prefs.str("iptvUa:" + host(url)).ifEmpty { Http.UA }

    private fun rememberUa(url: String, ua: String) {
        val h = host(url)
        if (h.isEmpty()) return
        if (ua == Http.UA) Prefs.remove("iptvUa:$h") else Prefs.put("iptvUa:$h", ua)
    }

    private fun candidates(url: String): List<String> = (listOf(uaFor(url)) + UAS).distinct()

    fun load(url: String): Result {
        Xtream.fromListUrl(url)?.let { x ->
            val r = xtreamLive(x, url)
            // erro definitivo da conta (expirada, palavra-passe): não vale a pena tentar o M3U
            if (r.groups.isNotEmpty() || r.error?.startsWith("!") == true) return Result(r.groups, r.m3uText, r.error?.removePrefix("!"), r.epgUrl)
        }
        return m3u(url)
    }

    // ---------- Xtream (API) ----------

    private fun xtreamLive(x: Xtream, listUrl: String): Result {
        val base = "${x.server}/player_api.php?username=${Addons.enc(x.user)}&password=${Addons.enc(x.pass)}"
        var lastErr = "O servidor não respondeu."
        for (ua in candidates(listUrl)) {
            val h = mapOf("User-Agent" to ua)
            val authTxt = Http.getStringH(base, h)
            if (authTxt == null) { lastErr = "O servidor não respondeu (pode estar em baixo, ou o endereço/porta estão errados)."; continue }
            val t = authTxt.trimStart()
            if (!t.startsWith("{")) { lastErr = if (t.startsWith("<")) "O servidor respondeu com uma página web em vez da conta (bloqueado?)." else "Resposta inesperada do servidor."; continue }
            val auth = try { JSONObject(t) } catch (e: Exception) { continue }
            val ui = auth.optJSONObject("user_info") ?: return Result(emptyList(), null, "O servidor não é Xtream ou a conta não existe.")
            if (ui.optInt("auth", 1) == 0) return Result(emptyList(), null, "!Utilizador ou palavra-passe errados.")
            val status = ui.optString("status")
            val exp = ui.optString("exp_date").toLongOrNull() ?: 0L
            val expTxt = if (exp > 0) java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(exp * 1000)) else ""
            if (status.isNotEmpty() && !status.equals("Active", true)) {
                val why = when (status.lowercase()) { "expired" -> "A conta expirou" + (if (expTxt.isNotEmpty()) " em $expTxt" else ""); "banned" -> "A conta foi bloqueada pelo fornecedor"; "disabled" -> "A conta está desativada"; else -> "Conta: $status" }
                return Result(emptyList(), null, "!$why.")
            }
            if (exp > 0 && exp * 1000 < System.currentTimeMillis()) return Result(emptyList(), null, "!A conta expirou em $expTxt.")
            rememberUa(listUrl, ua)
            val formats = ui.optJSONArray("allowed_output_formats")
            val fmt = (0 until (formats?.length() ?: 0)).map { formats!!.optString(it) }
            val ext = if (fmt.isEmpty() || "ts" in fmt) "ts" else if ("m3u8" in fmt) "m3u8" else fmt.first()
            val cats = HashMap<String, String>()
            Http.getStringH("$base&action=get_live_categories", h)?.let { s ->
                try { val a = JSONArray(s); for (i in 0 until a.length()) a.optJSONObject(i)?.let { cats[it.optString("category_id")] = it.optString("category_name") } } catch (e: Exception) {}
            }
            val streamsTxt = Http.getStringH("$base&action=get_live_streams", h) ?: return Result(emptyList(), null, "A conta está ativa mas o servidor não enviou os canais. Tenta de novo.")
            val arr = try { JSONArray(streamsTxt) } catch (e: Exception) { return Result(emptyList(), null, "A lista de canais do servidor veio inválida.") }
            val sb = StringBuilder("#EXTM3U url-tvg=\"${Iptv.xtreamEpg(listUrl)}\"\n")
            for (i in 0 until minOf(arr.length(), 20000)) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("stream_id")
                if (id.isEmpty()) continue
                val name = o.optString("name").replace(",", " ").trim().ifEmpty { "Canal $id" }
                val group = cats[o.optString("category_id")] ?: "Outros"
                sb.append("#EXTINF:-1 tvg-id=\"").append(o.optString("epg_channel_id").replace("\"", "")).append("\" tvg-logo=\"")
                    .append(o.optString("stream_icon").replace("\"", "")).append("\" group-title=\"").append(group.replace("\"", "'")).append("\",").append(name).append('\n')
                sb.append(x.server).append("/live/").append(x.user).append('/').append(x.pass).append('/').append(id).append('.').append(ext).append('\n')
            }
            val text = sb.toString()
            val groups = Iptv.parse(text, 20000)
            if (groups.isEmpty()) return Result(emptyList(), null, "A conta está ativa mas não tem canais ao vivo.")
            PlayLog.add("📺 Xtream: ${arr.length()} canais pela API" + (if (ua != Http.UA) " (identificação: ${ua.substringBefore('/')})" else "") + (if (expTxt.isNotEmpty()) " · conta até $expTxt" else ""))
            return Result(groups, text, null, Iptv.lastEpgUrl)
        }
        return Result(emptyList(), null, lastErr)
    }

    // ---------- M3U ----------

    private fun m3u(url: String): Result {
        var lastErr = "Não foi possível descarregar a lista."
        for (ua in candidates(url)) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept", "*/*").build()
                Http.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        lastErr = when (r.code) {
                            401, 403 -> "O servidor recusou o acesso (HTTP ${r.code}): conta inválida, expirada ou com o máximo de ligações."
                            404 -> "A lista não existe nesse endereço (HTTP 404)."
                            in 500..599 -> "O servidor da lista está com problemas (HTTP ${r.code})."
                            else -> "O servidor respondeu HTTP ${r.code}."
                        }
                        if (r.code == 404) return Result(emptyList(), null, lastErr)
                        return@use
                    }
                    val body = r.body ?: return@use
                    // lê linha a linha e guarda só o que interessa (canais ao vivo), sem o ficheiro inteiro em memória
                    val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8), 64 * 1024)
                    val out = StringBuilder()
                    var first = true
                    var skipNext = false
                    var kept = 0
                    var pendingInf: String? = null
                    while (true) {
                        val raw = reader.readLine() ?: break
                        val line = raw.trim()
                        if (line.isEmpty()) continue
                        if (first) {
                            first = false
                            if (line.startsWith("<")) { lastErr = "O link abre uma página web, não uma lista M3U (bloqueado ou link errado)."; return@use }
                            if (line.startsWith("{")) { lastErr = "O link devolve dados, não uma lista M3U."; return@use }
                        }
                        when {
                            line.startsWith("#EXTM3U", true) -> out.append(line).append('\n')
                            line.startsWith("#EXTINF", true) -> pendingInf = line
                            line.startsWith("#") -> if (pendingInf != null && line.startsWith("#EXTGRP", true)) pendingInf += "\n$line"
                            else -> {
                                // filmes e séries de contas Xtream ficam de fora (vêem-se em «Filmes e séries desta conta»)
                                skipNext = line.contains("/movie/") || line.contains("/series/")
                                if (pendingInf != null && !skipNext) { out.append(pendingInf).append('\n').append(line).append('\n'); kept++ }
                                pendingInf = null
                                if (kept >= 20000) break
                            }
                        }
                    }
                    if (kept == 0) { lastErr = "A lista não tem canais ao vivo reconhecidos."; return@use }
                    val text = out.toString()
                    val groups = Iptv.parse(text, 20000)
                    if (groups.isNotEmpty()) {
                        rememberUa(url, ua)
                        if (ua != Http.UA) PlayLog.add("📺 Lista IPTV carregada com a identificação ${ua.substringBefore('/')}")
                        return Result(groups, text, null, Iptv.lastEpgUrl)
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                return Result(emptyList(), null, "O endereço do servidor não existe (${host(url)}). Confirma o link.")
            } catch (e: java.io.InterruptedIOException) {
                lastErr = "O servidor demorou demasiado a responder."
            } catch (e: OutOfMemoryError) {
                return Result(emptyList(), null, "A lista é grande demais para este aparelho.")
            } catch (e: Exception) {
                lastErr = "Falha de ligação (${e.javaClass.simpleName})."
            }
        }
        return Result(emptyList(), null, lastErr)
    }
}
