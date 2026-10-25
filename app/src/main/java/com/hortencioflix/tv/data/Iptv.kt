package com.hortencioflix.tv.data

import org.json.JSONObject

object Iptv {
    data class Source(val name: String, val url: String)

    /** Listas públicas e legais (canais em sinal aberto). */
    val PUBLIC = listOf(
        Source("Portugal (iptv-org)", "https://iptv-org.github.io/iptv/countries/pt.m3u"),
        Source("Angola (iptv-org)", "https://iptv-org.github.io/iptv/countries/ao.m3u"),
        Source("Brasil (iptv-org)", "https://iptv-org.github.io/iptv/countries/br.m3u"),
        Source("Moçambique (iptv-org)", "https://iptv-org.github.io/iptv/countries/mz.m3u"),
        Source("Língua portuguesa (iptv-org)", "https://iptv-org.github.io/iptv/languages/por.m3u"),
        Source("Notícias (iptv-org)", "https://iptv-org.github.io/iptv/categories/news.m3u"),
        Source("Desporto (iptv-org)", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
        Source("Filmes (iptv-org)", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
        Source("Infantil (iptv-org)", "https://iptv-org.github.io/iptv/categories/kids.m3u"),
        Source("Música (iptv-org)", "https://iptv-org.github.io/iptv/categories/music.m3u"),
        Source("Free-TV (mundo)", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8")
    )

    fun xtreamUrl(server: String, user: String, pass: String): String {
        var s = server.trim().trimEnd('/')
        if (!s.startsWith("http")) s = "http://$s"
        return "$s/get.php?username=${Addons.enc(user)}&password=${Addons.enc(pass)}&type=m3u_plus&output=ts"
    }

    private val attr = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")

    /** Link do guia EPG indicado no cabeçalho da última lista lida (url-tvg / x-tvg-url). */
    @Volatile var lastEpgUrl: String = ""

    /** Link XMLTV para uma lista Xtream (get.php → xmltv.php). */
    fun xtreamEpg(listUrl: String): String =
        if (listUrl.contains("/get.php", true)) listUrl.substringBefore("/get.php") + "/xmltv.php?" + listUrl.substringAfter("?").split("&")
            .filter { it.startsWith("username=") || it.startsWith("password=") }.joinToString("&") else ""

    fun parse(text: String, maxChannels: Int = 6000): List<ChannelGroup> {
        val groups = LinkedHashMap<String, ArrayList<Channel>>()
        var name = ""
        var logo = ""
        var group = ""
        var tvgId = ""
        var total = 0
        lastEpgUrl = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U", true)) {
                for (m in attr.findAll(line)) {
                    val k = m.groupValues[1].lowercase()
                    if ((k == "url-tvg" || k == "x-tvg-url") && m.groupValues[2].isNotEmpty()) lastEpgUrl = m.groupValues[2].split(",").first().trim()
                }
            } else if (line.startsWith("#EXTINF", true)) {
                val comma = line.lastIndexOf(',')
                name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                logo = ""; group = ""; tvgId = ""
                for (m in attr.findAll(line)) {
                    when (m.groupValues[1].lowercase()) {
                        "tvg-logo" -> logo = m.groupValues[2]
                        "group-title" -> group = m.groupValues[2]
                        "tvg-name" -> if (name.isEmpty()) name = m.groupValues[2]
                        "tvg-id" -> tvgId = m.groupValues[2]
                    }
                }
            } else if (line.startsWith("#EXTGRP:", true)) {
                group = line.substring(8).trim()
            } else if (!line.startsWith("#")) {
                if (name.isNotEmpty() && (line.startsWith("http") || line.startsWith("rtmp"))) {
                    val g = group.ifEmpty { "Outros" }.split(';').first().trim().ifEmpty { "Outros" }
                    groups.getOrPut(g) { ArrayList() }.add(Channel(name, logo, line, g, tvgId))
                    total++
                    if (total >= maxChannels) break
                }
                name = ""
            }
        }
        val out = groups.entries.sortedBy { it.key.lowercase() }.map { ChannelGroup(it.key, it.value) }
        val all = out.flatMap { it.channels }
        return if (out.size > 1) listOf(ChannelGroup("Todos", all)) + out else out
    }

    /** Listas guardadas: [{"n":nome,"u":url}] */
    fun lists(): List<Source> {
        val a = Prefs.iptvLists()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Source(it.optString("n"), it.optString("u")) }
    }

    fun addList(name: String, url: String) {
        val a = Prefs.iptvLists()
        for (i in 0 until a.length()) if (a.optJSONObject(i)?.optString("u") == url) return
        a.put(JSONObject().put("n", name).put("u", url))
        Prefs.setIptvLists(a)
    }

    fun removeList(url: String) {
        val a = Prefs.iptvLists()
        val out = org.json.JSONArray()
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optString("u") != url) out.put(o) }
        Prefs.setIptvLists(out)
    }
}
