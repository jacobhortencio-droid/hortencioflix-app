package com.hortencioflix.tv.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.data.FileName
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Session
import com.hortencioflix.tv.data.StreamItem
import com.hortencioflix.tv.data.SubFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Os meus downloads no TorBox: ver estado, reproduzir ficheiros, apagar e adicionar magnets. */
class TorboxActivity : ListScreen() {
    override val screenTitle = "TorBox · os meus downloads"
    private val api = "https://api.torbox.app/v1/api/torrents"
    private var torrents: List<JSONObject> = emptyList()

    private fun auth() = mapOf("Authorization" to "Bearer ${Prefs.torboxKey}")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.torboxKey.isEmpty()) {
            setStatus("Adiciona primeiro a tua chave TorBox em Definições → TorBox.")
            return
        }
        setAction("+ Magnet") { addMagnet() }
        // Atualiza sozinho a cada 15 s enquanto o ecrã está aberto (progresso dos downloads).
        lifecycleScope.launch {
            var first = true
            while (isActive) {
                load(first)
                first = false
                delay(15_000)
            }
        }
    }

    private suspend fun load(showProgress: Boolean) {
        if (showProgress) progress.visibility = View.VISIBLE
        val d = withContext(Dispatchers.IO) { Http.getJsonAuth("$api/mylist?bypass_cache=true", auth()) }
        progress.visibility = View.GONE
        val arr = d?.optJSONArray("data")
        if (arr == null) {
            setStatus(if (d?.optBoolean("success") == false) "TorBox: ${d.optString("detail", "erro")}" else "Não foi possível ligar ao TorBox.")
            return
        }
        torrents = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        val rows = ArrayList<Row>()
        val done = torrents.filter { it.optBoolean("download_finished") || it.optBoolean("download_present") }
        val active = torrents - done.toSet()
        if (active.isNotEmpty()) {
            rows.add(header("A descarregar (${active.size})"))
            for (t in active) rows.add(row(t))
        }
        if (done.isNotEmpty()) {
            rows.add(header("Prontos (${done.size})"))
            for (t in done) rows.add(row(t))
        }
        adapter.update(rows)
        setStatus(if (torrents.isEmpty()) "Ainda não tens downloads no TorBox." else "Toca para ver e reproduzir os ficheiros · mantém premido para apagar")
        if (showProgress) focusFirst()
    }

    private fun gb(bytes: Long) = if (bytes >= 1L shl 30) "%.1f GB".format(bytes / 1073741824.0) else "%.0f MB".format(bytes / 1048576.0)

    private fun row(t: JSONObject): Row {
        val p = (t.optDouble("progress", 0.0) * 100).toInt()
        val state = t.optString("download_state")
        val speed = t.optLong("download_speed")
        val sub = listOfNotNull(
            gb(t.optLong("size")),
            if (t.optBoolean("download_finished")) "pronto" else "$p%",
            state.ifEmpty { null },
            if (speed > 0) "${speed / 1024} KB/s" else null,
            "${t.optJSONArray("files")?.length() ?: 0} ficheiros"
        ).joinToString("  ·  ")
        return Row(t.optString("name"), sub, tag = t)
    }

    private val videoExt = Regex("\\.(mkv|mp4|avi|m4v|ts|mov|webm|wmv)$", RegexOption.IGNORE_CASE)

    override fun onRow(index: Int, row: Row) {
        val t = row.tag as? JSONObject ?: return
        if (!(t.optBoolean("download_finished") || t.optBoolean("download_present"))) {
            toast("Ainda a descarregar (${(t.optDouble("progress", 0.0) * 100).toInt()}%).")
            return
        }
        val files = t.optJSONArray("files") ?: return
        val vids = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
            .filter { videoExt.containsMatchIn(it.optString("name")) }
            .sortedBy { it.optString("name") }
        if (vids.isEmpty()) { toast("Este download não tem ficheiros de vídeo."); return }
        if (vids.size == 1) play(t, vids[0]) else
            choiceDialog(t.optString("name"), vids.map { it.optString("short_name").ifEmpty { it.optString("name").substringAfterLast('/') } + "  ·  " + gb(it.optLong("size")) }) { play(t, vids[it]) }
    }

    private fun play(t: JSONObject, f: JSONObject) {
        val tid = t.optLong("id")
        val fid = f.optLong("id")
        val fname = f.optString("short_name").ifEmpty { f.optString("name").substringAfterLast('/') }
        val parsed = FileName.parse(fname)
        val title = parsed?.let { p -> listOfNotNull(p.title, if (p.season != null) "T${p.season} E${p.episode}" else null).joinToString(" · ") } ?: fname
        val url = "$api/requestdl?token=${Prefs.torboxKey}&torrent_id=$tid&file_id=$fid&redirect=true"
        val stream = StreamItem("TorBox", parsed?.quality ?: "", fname, url, fname, emptyMap(), 0, true, false)
        val id = "torbox:$tid:$fid"
        val meta = MetaItem(id, "movie", title, "")
        // Os ficheiros do TorBox não trazem legendas: procura-as pelo título.
        lifecycleScope.launch {
            val found = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { kotlinx.coroutines.withTimeoutOrNull(7000) { SubFinder.find(fname) } } catch (e: Exception) { null }
            }
            val m2 = if (found != null) meta.copy(poster = found.poster, name = if (parsed?.season != null) "${found.title} · T${parsed.season} E${parsed.episode}" else found.title) else meta
            Session.vod(m2, id, listOf(stream), 0, found?.subs ?: emptyList(), emptyList(), Prefs.resumeFor(id, id))
            PlayerActivity.start(this@TorboxActivity)
        }
    }

    override fun onRowLong(index: Int, row: Row) {
        val t = row.tag as? JSONObject ?: return
        dialog().setTitle("Apagar do TorBox?").setMessage(t.optString("name"))
            .setPositiveButton("Apagar") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { Http.postAuth("$api/controltorrent", JSONObject().put("torrent_id", t.optLong("id")).put("operation", "delete"), auth()) }
                    toast(if (r?.optBoolean("success") == true) "Apagado" else "Não foi possível apagar.")
                    load(false)
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun addMagnet() {
        inputDialog("Adicionar magnet", "magnet:?xt=urn:btih:…", "") { m ->
            if (!m.startsWith("magnet:")) { toast("Tem de ser um link magnet."); return@inputDialog }
            lifecycleScope.launch {
                val r = withContext(Dispatchers.IO) { Http.postForm("$api/createtorrent", mapOf("magnet" to m), auth()) }
                toast(if (r?.optBoolean("success") == true) "Adicionado ao TorBox" else "TorBox: ${r?.optString("detail") ?: "falhou"}")
                load(false)
            }
        }
    }
}
