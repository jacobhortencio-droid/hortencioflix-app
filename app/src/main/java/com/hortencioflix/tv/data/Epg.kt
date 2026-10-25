package com.hortencioflix.tv.data

import android.util.Xml
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/** Guia de programação (XMLTV): «Agora» e «A seguir» por canal. */
object Epg {
    data class Prog(val start: Long, val end: Long, val title: String) { var desc: String = "" }

    private val byId = ConcurrentHashMap<String, List<Prog>>()
    private val nameToId = ConcurrentHashMap<String, String>()
    @Volatile var loadedUrl: String = ""
        private set

    val isLoaded: Boolean get() = byId.isNotEmpty()

    fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\b(hd|fhd|uhd|4k|sd|h265|hevc|\\(backup\\))\\b"), "")
        .replace(Regex("[^a-z0-9]"), "")

    private fun parseTime(s: String): Long {
        val t = s.trim()
        for (f in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")) {
            try { return SimpleDateFormat(f, Locale.US).parse(t)?.time ?: continue } catch (e: Exception) {}
        }
        return 0
    }

    /** Descarrega e lê o XMLTV. Bloqueante (chamar em Dispatchers.IO). */
    fun load(url: String, headers: Map<String, String> = emptyMap()): Boolean {
        if (url.isEmpty()) return false
        if (url == loadedUrl && isLoaded) return true
        return try {
            val rb = Request.Builder().url(url).header("User-Agent", "Hortencioflix-Android/1.0")
            for ((k, v) in headers) rb.header(k, v)
            Http.client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) return false
                val body = r.body ?: return false
                var input: InputStream = BufferedInputStream(body.byteStream())
                input.mark(2)
                val b1 = input.read(); val b2 = input.read()
                input.reset()
                if (b1 == 0x1f && b2 == 0x8b) input = GZIPInputStream(input)
                parse(input)
            }
            loadedUrl = url
            isLoaded
        } catch (e: Exception) {
            false
        }
    }

    private fun parse(input: InputStream) {
        val now = System.currentTimeMillis()
        val from = now - 3 * 3600_000L
        val to = now + 14 * 3600_000L
        val tmp = HashMap<String, ArrayList<Prog>>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        var curChannel = ""
        var progChannel = ""
        var start = 0L
        var stop = 0L
        var inProg = false
        var last: Prog? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> curChannel = p.getAttributeValue(null, "id") ?: ""
                    "display-name" -> if (curChannel.isNotEmpty()) {
                        val n = norm(p.nextText() ?: "")
                        if (n.isNotEmpty()) nameToId.putIfAbsent(n, curChannel.lowercase())
                    }
                    "programme" -> {
                        progChannel = (p.getAttributeValue(null, "channel") ?: "").lowercase()
                        last = null
                        start = parseTime(p.getAttributeValue(null, "start") ?: "")
                        stop = parseTime(p.getAttributeValue(null, "stop") ?: "")
                        inProg = stop > from && start < to
                    }
                    "title" -> if (inProg) {
                        val title = p.nextText() ?: ""
                        val pr = Prog(start, stop, title)
                        tmp.getOrPut(progChannel) { ArrayList() }.add(pr)
                        last = pr
                        inProg = false
                    }
                    "desc" -> last?.let { pr -> if (pr.desc.isEmpty()) pr.desc = (p.nextText() ?: "").trim().take(400) }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "channel") {
                curChannel = ""
            }
            ev = p.next()
        }
        byId.clear()
        for ((k, v) in tmp) byId[k] = v.sortedBy { it.start }
    }

    private fun progs(ch: Channel): List<Prog>? {
        if (ch.tvgId.isNotEmpty()) byId[ch.tvgId.lowercase()]?.let { return it }
        val id = nameToId[norm(ch.name)] ?: return null
        return byId[id]
    }

    /** Programas de um canal que tocam entre «from» e «to» (para a grelha). */
    fun between(ch: Channel, from: Long, to: Long): List<Prog> = progs(ch)?.filter { it.end > from && it.start < to } ?: emptyList()

    fun hasGuide(ch: Channel): Boolean = progs(ch)?.isNotEmpty() == true

    /** (agora, a seguir) para um canal. */
    fun nowNext(ch: Channel): Pair<Prog?, Prog?> {
        val list = progs(ch) ?: return null to null
        val t = System.currentTimeMillis()
        val i = list.indexOfFirst { it.start <= t && it.end > t }
        if (i < 0) return null to list.firstOrNull { it.start > t }
        return list[i] to list.getOrNull(i + 1)
    }

    fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(ms))

    fun describe(ch: Channel): String {
        val (now, next) = nowNext(ch)
        val parts = ArrayList<String>()
        if (now != null) parts.add("▶ ${now.title} · até ${hhmm(now.end)}")
        if (next != null) parts.add("A seguir: ${next.title} · ${hhmm(next.start)}")
        return parts.joinToString("\n")
    }
}
