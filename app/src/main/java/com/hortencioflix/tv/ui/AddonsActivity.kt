package com.hortencioflix.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.Prefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Gestor de add-ons: ativar/desativar, ordenar, configurar e escolher catálogos do Início. */
class AddonsActivity : ListScreen() {
    override val screenTitle = "Add-ons"
    private var bases: List<String> = emptyList()
    private val mans = HashMap<String, JSONObject?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAction("+ Adicionar") { add() }
        load()
    }

    private fun load() {
        progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            bases = Addons.allInstalled()
            bases.map { b -> async { b to Addons.manifest(b) } }.awaitAll().forEach { (b, m) -> mans[b] = m }
            progress.visibility = android.view.View.GONE
            render()
            focusFirst()
        }
    }

    private fun source(b: String) = when {
        b in Prefs.stremioAddons() -> "Conta Stremio"
        b in Prefs.localAddons() -> "Adicionado neste aparelho"
        else -> "Automático (TorBox)"
    }

    private fun render() {
        val off = Prefs.disabledAddons()
        val rows = ArrayList<Row>()
        rows.add(header("Instalados (${bases.size})"))
        for (b in bases) {
            val m = mans[b]
            val name = m?.optString("name")?.ifEmpty { null } ?: b.removePrefix("https://")
            val ver = m?.optString("version") ?: ""
            val desc = (m?.optString("description") ?: "Não respondeu").take(110)
            val res = m?.optJSONArray("resources")
            val kinds = ArrayList<String>()
            if (res != null) for (i in 0 until res.length()) {
                val r = res.opt(i)
                val n = if (r is JSONObject) r.optString("name") else r.toString()
                when (n) { "catalog" -> kinds.add("catálogos"); "stream" -> kinds.add("fontes"); "subtitles" -> kinds.add("legendas"); "meta" -> kinds.add("informação") }
            }
            val state = if (b in off) "⛔ DESATIVADO  ·  " else ""
            rows.add(Row("$name  ${if (ver.isNotEmpty()) "v$ver" else ""}".trim(), "$state$desc\n${source(b)}" + (if (kinds.isNotEmpty()) "  ·  " + kinds.distinct().joinToString(", ") else ""), m?.optString("logo") ?: "", b))
        }
        adapter.update(rows)
        setStatus("Toca num add-on para o ativar/desativar, escolher catálogos do Início, configurar ou remover.")
    }

    override fun onRow(index: Int, row: Row) {
        val b = row.tag as? String ?: return
        val m = mans[b]
        val off = b in Prefs.disabledAddons()
        val local = b in Prefs.localAddons()
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add(if (off) "✓  Ativar" else "⛔  Desativar"); acts.add { Prefs.setAddonEnabled(b, off); Prefs.put("lastSync", "0"); render() }
        if ((m?.optJSONArray("catalogs")?.length() ?: 0) > 0) { labels.add("☰  Catálogos no Início…"); acts.add { catalogs(b, m!!) } }
        if (m?.optJSONObject("behaviorHints")?.optBoolean("configurable") == true) { labels.add("⚙  Configurar (abre no navegador)"); acts.add { open("$b/configure") } }
        if (local) {
            labels.add("↑  Mover para cima"); acts.add { move(b, -1) }
            labels.add("↓  Mover para baixo"); acts.add { move(b, 1) }
            labels.add("🗑  Remover"); acts.add {
                Prefs.setLocalAddons(Prefs.localAddons() - b)
                if (Prefs.localAddons().isEmpty()) Prefs.put("addons", "[]")
                load()
            }
        } else if (b in Prefs.stremioAddons()) {
            labels.add("ℹ  Gerido na conta Stremio"); acts.add { toast("Para remover este add-on usa o Stremio; depois sincroniza nas Definições.") }
        }
        labels.add("🔗  Ver link"); acts.add { dialog().setTitle("Link do add-on").setMessage("$b/manifest.json").setPositiveButton("OK", null).show() }
        choiceDialog(row.title, labels) { acts[it].invoke() }
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { toast("Não há navegador para abrir: $url") }
    }

    private fun move(b: String, dir: Int) {
        val list = Prefs.localAddons().toMutableList()
        val i = list.indexOf(b)
        val j = i + dir
        if (i < 0 || j < 0 || j >= list.size) return
        list[i] = list[j]; list[j] = b
        Prefs.setLocalAddons(list)
        load()
    }

    private fun catalogs(b: String, m: JSONObject) {
        val cats = m.optJSONArray("catalogs") ?: return
        val keys = ArrayList<String>()
        val names = ArrayList<String>()
        for (i in 0 until cats.length()) {
            val c = cats.optJSONObject(i) ?: continue
            val type = c.optString("type")
            if (Addons.isTvType(type)) continue
            keys.add(Addons.catalogKey(b, type, c.optString("id")))
            names.add(c.optString("name", c.optString("id")) + " · " + Addons.typeLabel(type))
        }
        if (keys.isEmpty()) { toast("Este add-on só tem catálogos de TV (aparecem na TV ao vivo)."); return }
        val checked = BooleanArray(keys.size) { !Prefs.isCatalogHidden(keys[it]) }
        dialog().setTitle("Catálogos no Início")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, w, on -> checked[w] = on }
            .setPositiveButton("Guardar") { _, _ ->
                for (i in keys.indices) Prefs.setCatalogHidden(keys[i], !checked[i])
                Prefs.put("lastSync", "0")
                toast("Guardado — o Início atualiza na próxima abertura (ou em ↻ Atualizar).")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun add() {
        inputDialog("Link do add-on", "https://…/manifest.json", "") { url ->
            if (!url.startsWith("http") && !url.startsWith("stremio")) { toast("Link inválido"); return@inputDialog }
            val base = Addons.normalize(url)
            lifecycleScope.launch {
                val m = Addons.manifest(base)
                if (m == null) { toast("Não foi possível ler o manifest deste add-on."); return@launch }
                Prefs.setLocalAddons((Prefs.localAddons() + base).distinct())
                toast("Add-on adicionado: ${m.optString("name")}")
                load()
            }
        }
    }
}
