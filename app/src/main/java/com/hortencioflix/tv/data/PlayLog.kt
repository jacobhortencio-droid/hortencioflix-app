package com.hortencioflix.tv.data

import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registo das últimas tentativas de reprodução (fonte, resultado, erro) — visível no Diagnóstico. */
object PlayLog {
    private const val KEY = "playLog"

    fun add(msg: String) {
        try {
            val t = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
            val old = Prefs.array(KEY)
            val out = JSONArray().put("$t  ${msg.take(240)}")
            for (i in 0 until minOf(old.length(), 39)) out.put(old.optString(i))
            Prefs.putArray(KEY, out)
        } catch (e: Exception) {
        }
    }

    fun lines(): List<String> {
        val a = Prefs.array(KEY)
        return (0 until a.length()).map { a.optString(it) }
    }

    /** Nome curto da fonte para o registo. */
    fun label(s: StreamItem?): String =
        if (s == null) "?" else "${s.name.lineSequence().firstOrNull().orEmpty().take(40)} · ${SourcePicker.qualityLabel(s.quality)} · ${s.url.substringAfter("://").substringBefore('/')}"
}
