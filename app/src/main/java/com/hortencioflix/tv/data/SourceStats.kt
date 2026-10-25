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

    fun ok(s: StreamItem?) { if (s != null) change(s, 2) }
    fun fail(s: StreamItem?) { if (s != null) change(s, -3) }
    /** A app morreu a meio do arranque desta fonte (falha do sistema/descodificador). */
    fun crashed(signature: String) { if (signature.isNotEmpty()) changeSig(signature, -8) }
}
