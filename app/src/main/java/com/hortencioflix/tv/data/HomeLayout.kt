package com.hortencioflix.tv.data

import org.json.JSONArray

/**
 * Início personalizável: que filas aparecem e por que ordem (por perfil).
 * As filas são identificadas pelo título; filas novas (de um add-on novo) aparecem no fim, visíveis.
 */
object HomeLayout {
    private fun k(name: String) = if (Prefs.profileId == "main") name else name + "_" + Prefs.profileId

    fun known(): List<String> = list(k("homeKnown"))
    fun hidden(): Set<String> = list(k("homeHidden")).toSet()
    fun order(): List<String> = list(k("homeOrder"))

    private fun list(key: String): List<String> {
        val a = Prefs.array(key)
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
    }

    private fun save(key: String, l: Collection<String>) = Prefs.putArray(key, JSONArray(l.toList()))

    /** Guarda os títulos das filas que existem, para as poder escolher nas Definições. */
    fun remember(titles: List<String>) {
        val cur = LinkedHashSet(known())
        var changed = false
        for (t in titles) if (t.isNotBlank() && cur.add(t)) changed = true
        if (changed) save(k("homeKnown"), cur.toList().takeLast(60))
    }

    fun setHidden(h: Set<String>) = save(k("homeHidden"), h)
    fun setOrder(o: List<String>) = save(k("homeOrder"), o)

    fun moveToTop(title: String) {
        val o = order().toMutableList()
        o.remove(title)
        o.add(0, title)
        setOrder(o)
    }

    fun reset() { setHidden(emptySet()); setOrder(emptyList()) }

    /** Aplica a escolha: tira as escondidas e põe primeiro as que foram ordenadas (o resto mantém a ordem). */
    fun <T> apply(rows: List<Pair<String, T>>): List<Pair<String, T>> {
        remember(rows.map { it.first })
        val h = hidden()
        val o = order()
        val visible = rows.filter { it.first !in h }
        if (o.isEmpty()) return visible
        return visible.sortedBy { r -> o.indexOf(r.first).let { if (it < 0) Int.MAX_VALUE else it } }
    }
}
