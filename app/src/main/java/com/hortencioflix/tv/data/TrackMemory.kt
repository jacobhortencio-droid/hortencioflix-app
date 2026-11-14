package com.hortencioflix.tv.data

import org.json.JSONObject

/**
 * Áudio e legendas escolhidos em cada título (série ou filme). Quem escolhe «Japonês + legendas PT» num
 * anime, ou desliga as legendas numa série dobrada, não tem de repetir a escolha em cada episódio.
 * Guarda os últimos 80 títulos.
 */
object TrackMemory {
    private const val KEY = "trackMem"
    private const val MAX = 80

    /** [audio] = língua do áudio ("" = sem escolha). [sub] = língua, "off" (desligadas), "on" (ligadas, língua desconhecida) ou "". */
    class Choice(val audio: String, val sub: String)

    private fun all(): JSONObject = try { JSONObject(Prefs.str(KEY, "{}")) } catch (e: Exception) { JSONObject() }

    fun get(metaId: String?): Choice? {
        if (metaId.isNullOrEmpty()) return null
        val o = all().optJSONObject(metaId) ?: return null
        return Choice(o.optString("a"), o.optString("s"))
    }

    fun setAudio(metaId: String?, lang: String) = update(metaId, "a", lang)

    fun setSub(metaId: String?, value: String) = update(metaId, "s", value)

    private fun update(metaId: String?, field: String, value: String) {
        if (metaId.isNullOrEmpty() || value.isEmpty()) return
        try {
            val o = all()
            val e = o.optJSONObject(metaId) ?: JSONObject()
            e.put(field, value)
            // volta a pôr no fim: os mais antigos são os primeiros a sair
            o.remove(metaId)
            o.put(metaId, e)
            val keys = o.keys().asSequence().toList()
            if (keys.size > MAX) keys.take(keys.size - MAX).forEach { o.remove(it) }
            Prefs.put(KEY, o.toString())
        } catch (e: Exception) {
        }
    }
}
