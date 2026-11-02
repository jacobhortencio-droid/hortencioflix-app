package com.hortencioflix.tv.data

import org.json.JSONObject

/**
 * Memória do que funciona neste aparelho: cada tipo de fonte (add-on + qualidade + grupo de lançamento)
 * ganha pontos quando toca e perde quando falha. As que falham ficam para o fim nas próximas vezes.
 */
object SourceStats {
    private const val KEY = "srcStats"
    private var cache: JSONObject? = null

    private fun data(): JSONObject {
        cache?.let { return it }
        val o = try { JSONObject(Prefs.str(KEY, "{}")) } catch (e: Exception) { JSONObject() }
        cache = o
        return o
    }

    fun score(s: StreamItem): Int = data().optInt(Addons.sourceSignature(s), 0)

    private fun change(s: StreamItem, delta: Int) = changeSig(Addons.sourceSignature(s), delta)

    private fun changeSig(k: String, delta: Int) {
        try {
            val o = data()
            o.put(k, (o.optInt(k, 0) + delta).coerceIn(-12, 12))
            if (o.length() > 300) {
                // descarta as entradas neutras para não crescer sem fim
                val keys = o.keys().asSequence().toList()
                for (key in keys) if (o.length() > 250 && kotlin.math.abs(o.optInt(key)) <= 1) o.remove(key)
            }
            Prefs.put(KEY, o.toString())
        } catch (e: Exception) {
        }
    }

    fun ok(s: StreamItem?) { if (s != null) { change(s, 2); addonChange(s.addon, +4) } }
    fun fail(s: StreamItem?) { if (s != null) { change(s, -3); addonChange(s.addon, -3) } }

    // ---------- Saúde de cada add-on (ex.: um add-on cujas fontes «⚡ prontas» dão sempre erro) ----------
    // Cada falha -3, cada vídeo que toca +4; esquece ao fim de 48 h sem novidades.
    private const val AKEY = "addonHealth"

    private fun health(): JSONObject = try { JSONObject(Prefs.str(AKEY, "{}")) } catch (e: Exception) { JSONObject() }

    private fun addonChange(addon: String, delta: Int) {
        if (addon.isBlank()) return
        try {
            val o = health()
            val now = System.currentTimeMillis()
            val cur = o.optJSONObject(addon)
            val old = if (cur != null && now - cur.optLong("t") < 48 * 3600_000L) cur.optInt("s") else 0
            val fails = (if (cur != null && now - cur.optLong("t") < 48 * 3600_000L) cur.optInt("f") else 0) + (if (delta < 0) 1 else 0)
            val oks = (if (cur != null && now - cur.optLong("t") < 48 * 3600_000L) cur.optInt("o") else 0) + (if (delta > 0) 1 else 0)
            o.put(addon, JSONObject().put("s", (old + delta).coerceIn(-15, 12)).put("t", now).put("f", fails).put("o", oks))
            Prefs.put(AKEY, o.toString())
        } catch (e: Exception) {}
    }

    /** O add-on está a falhar (2+ falhas a mais do que sucessos nas últimas 48 h): as fontes dele vão para o fim. */
    fun addonBad(addon: String): Boolean {
        val c = health().optJSONObject(addon) ?: return false
        if (System.currentTimeMillis() - c.optLong("t") > 48 * 3600_000L) return false
        return c.optInt("s") <= -6
    }

    /** Para o Diagnóstico: «Comet: 0 de 5 fontes tocaram». */
    fun badAddons(): List<String> {
        val o = health()
        val now = System.currentTimeMillis()
        return o.keys().asSequence().mapNotNull { k ->
            val c = o.optJSONObject(k) ?: return@mapNotNull null
            if (now - c.optLong("t") > 48 * 3600_000L || c.optInt("s") > -6) null
            else "$k: ${c.optInt("o")} de ${c.optInt("o") + c.optInt("f")} fontes tocaram"
        }.toList()
    }
    /** A app morreu a meio do arranque desta fonte (falha do sistema/descodificador). */
    fun crashed(signature: String) { if (signature.isNotEmpty()) changeSig(signature, -8) }
}
