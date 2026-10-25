package com.hortencioflix.tv.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.BuildConfig
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.Epg
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.PlayLog
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Stremio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Diagnóstico: testa Internet, add-ons, TorBox, conta Stremio e atualizações. */
class DiagnosticsActivity : ListScreen() {
    override val screenTitle = "Diagnóstico"
    private val rows = ArrayList<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAction("Partilhar") { share() }
        run()
    }

    private fun mark(code: Int, ms: Long) = when {
        code in 200..299 -> "✅ ${ms} ms" + if (ms > 3000) " (lento)" else ""
        code == -1 -> "❌ sem resposta (${ms} ms)"
        else -> "⚠️ HTTP $code (${ms} ms)"
    }

    private fun run() {
        rows.clear()
        progress.visibility = android.view.View.VISIBLE
        setStatus("A testar…")
        lifecycleScope.launch {
            rows.add(header("Aparelho"))
            rows.add(Row("Hortêncioflix ${BuildConfig.VERSION_NAME}", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"))
            rows.add(Row("Rede", Prefs.networkKind() + (if (Prefs.onMeteredNetwork()) " (limitada)" + (if (Prefs.dataSaver) " · poupança de dados ativa (máx. 720p)" else "") else " (ilimitada)")))
            adapter.update(ArrayList(rows))

            rows.add(header("Ligações"))
            val net = withContext(Dispatchers.IO) { Http.ping("${Addons.CINEMETA}/manifest.json") }
            rows.add(Row("Internet (Cinemeta)", mark(net.first, net.second)))
            // velocidade real (teste de ~8 MB) e o que isso permite ver sem pausas
            val kb = withContext(Dispatchers.IO) { com.hortencioflix.tv.data.NetSpeed.measure(force = true) }
            if (kb > 0) {
                val mb = kb / 1000.0
                val ok = when { mb >= 60 -> "4K Remux"; mb >= 25 -> "4K WEB e 1080p Blu-ray"; mb >= 12 -> "1080p (cópias até ~8 GB num filme) e 4K leves"; mb >= 6 -> "1080p WEB (até ~4 GB num filme)"; else -> "720p" }
                rows.add(Row("Velocidade", String.format("%.1f Mbps", mb) + " — sem pausas: $ok"))
            } else rows.add(Row("Velocidade", "— não foi possível medir"))
            if (Prefs.torboxKey.isNotEmpty()) {
                val acc = withContext(Dispatchers.IO) { com.hortencioflix.tv.data.TorboxApi.account(fresh = true) }
                rows.add(Row("TorBox", com.hortencioflix.tv.data.TorboxApi.summary(acc)))
                com.hortencioflix.tv.data.TorboxApi.warning(acc)?.let { rows.add(Row("⚠️ Conta TorBox", it)) }
                val w = com.hortencioflix.tv.data.TorboxApi.watched()
                if (w.length() > 0) rows.add(Row("A descarregar para ti", (0 until w.length()).joinToString("  ·  ") { w.optJSONObject(it)?.optString("title").orEmpty() }))
            } else rows.add(Row("TorBox", "— sem chave configurada"))
            if (Stremio.loggedIn) {
                val st = withContext(Dispatchers.IO) { Http.ping("https://api.strem.io/api/") }
                rows.add(Row("Conta Stremio", "${Prefs.stremioEmail} · servidor " + mark(if (st.first > 0) 200 else -1, st.second)))
            } else rows.add(Row("Conta Stremio", "— não ligada"))
            val tok = Prefs.str("ghToken")
            val up = withContext(Dispatchers.IO) {
                Http.ping("https://raw.githubusercontent.com/jacobhortencio-droid/hortencioflix-app/apk/version.txt", if (tok.isEmpty()) emptyMap() else mapOf("Authorization" to "token $tok"))
            }
            rows.add(Row("Atualizações automáticas", if (up.first == 404) "❌ sem acesso (repositório privado sem token)" else mark(up.first, up.second)))
            rows.add(Row("Guia EPG", if (Epg.isLoaded) "✅ carregado" else "— não carregado (abre uma lista com guia na TV ao vivo)"))
            adapter.update(ArrayList(rows))

            val bases = Addons.list()
            rows.add(header("Add-ons (${bases.size})"))
            val res = bases.map { b -> async(Dispatchers.IO) { b to Http.ping("$b/manifest.json") } }.awaitAll()
            for ((b, r) in res.sortedByDescending { it.second.second }) {
                val name = Addons.cachedManifest(b)?.optString("name")?.ifEmpty { null } ?: b.removePrefix("https://").take(40)
                rows.add(Row(name, mark(r.first, r.second)))
            }
            rows.add(Row("O que este aparelho toca", com.hortencioflix.tv.data.DeviceCaps.summary()))
            com.hortencioflix.tv.data.DeviceCaps.let { dc -> rows.add(Row("Ecrã HDR", if (dc.hdr) "✅ " + dc.hdrTypes.joinToString(", ") else "❌ este ecrã não é HDR (vídeos HDR são convertidos para SDR)")) }
            rows.add(header("Últimas reproduções"))
            val log = PlayLog.lines()
            if (log.isEmpty()) rows.add(Row("Ainda sem registo", "Abre um vídeo e volta aqui se alguma fonte falhar"))
            for (l in log.take(25)) rows.add(Row(l.substringAfter("  ", l), l.substringBefore("  ")))
            progress.visibility = android.view.View.GONE
            val bad = rows.count { it.subtitle.startsWith("❌") }
            setStatus(if (bad == 0) "Tudo a funcionar." else "$bad problema(s) encontrado(s).")
            adapter.update(ArrayList(rows))
        }
    }

    override fun onRow(index: Int, row: Row) {}

    private fun share() {
        val text = "Diagnóstico Hortêncioflix\n\n" + rows.joinToString("\n") { if (it.tag === Header) "\n== ${it.title} ==" else "${it.title}: ${it.subtitle}" }
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partilhar diagnóstico")) } catch (e: Exception) {}
    }
}
