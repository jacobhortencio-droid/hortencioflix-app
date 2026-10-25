package com.hortencioflix.tv.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hortencioflix.tv.BuildConfig
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Stremio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity() {
    private lateinit var adapter: RowAdapter
    private var linkJob: Job? = null
    private var pendingExport = ""

    private val exportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray()) }
            toast("Cópia de segurança guardada.")
        } catch (e: Exception) { toast("Não foi possível guardar: ${e.message}") }
    }

    private val importLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val txt = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            importText(txt)
        } catch (e: Exception) { toast("Ficheiro inválido: ${e.message}") }
    }

    private fun importText(txt: String) {
        val o = try { org.json.JSONObject(txt) } catch (e: Exception) { null }
        if (o == null || o.optString("app") != "hortencioflix") { toast("Isto não é uma cópia de segurança da Hortêncioflix."); return }
        if (o.has("enc")) {
            inputDialog("Palavra-passe da cópia", "palavra-passe", "", password = true) { p ->
                val plain = com.hortencioflix.tv.data.BackupCrypto.decrypt(o, p)
                if (plain == null) toast("Palavra-passe errada ou ficheiro danificado.") else importText(plain)
            }
            return
        }
        val n = Prefs.importAll(o)
        Prefs.put("lastSync", "0")
        toast("Restauradas $n definições. Reabre a app para aplicar tudo.")
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (Prefs.pin.isNotEmpty() && savedInstanceState == null) askPin()
        if (Prefs.torboxKey.isNotEmpty()) lifecycleScope.launch {
            withContext(Dispatchers.IO) { com.hortencioflix.tv.data.TorboxApi.account() }
            if (!isFinishing) refresh()
        }
        val list = findViewById<RecyclerView>(R.id.list)
        adapter = RowAdapter(emptyList(), onClick = { _, r -> handle(r.tag as String) })
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            // Tablet: lista centrada com largura máxima, em vez de esticada de ponta a ponta.
            val side = maxOf(0, (resources.displayMetrics.widthPixels - dp(720)) / 2)
            list.clipToPadding = false
            list.setPadding(side + list.paddingLeft, list.paddingTop, side + list.paddingRight, list.paddingBottom)
        }
        refresh()
        list.animateIn()
        list.post { list.getChildAt(0)?.requestFocus() }
    }

    private fun curatorMenu() {
        val url = Prefs.str("curatorUrl")
        val on = Prefs.str("curatorOn", "1") == "1"
        val items = arrayListOf(if (url.isEmpty()) "Colar o endereço do servidor…" else "Mudar o endereço ($url)")
        if (url.isNotEmpty()) { items.add("Testar agora"); items.add(if (on) "Pausar (pedir a cada add-on)" else "Voltar a ligar"); items.add("Remover") }
        choiceDialog("Fontes curadas", items) { w ->
            when (w) {
                0 -> inputDialog("Endereço do servidor", "https://hortencio-fontes….workers.dev", url) { t ->
                    val u = t.trim().trimEnd('/').removeSuffix("/manifest.json")
                    if (!u.startsWith("https://")) { toast("O endereço tem de começar por https://"); return@inputDialog }
                    Prefs.put("curatorUrl", u); Prefs.put("curatorOn", "1"); refresh(); testCurator(u)
                    com.hortencioflix.tv.data.RokuSync.syncQuietly()
                }
                1 -> testCurator(url)
                2 -> { Prefs.put("curatorOn", if (on) "0" else "1"); refresh(); com.hortencioflix.tv.data.RokuSync.syncQuietly() }
                3 -> { Prefs.remove("curatorUrl"); refresh(); com.hortencioflix.tv.data.RokuSync.syncQuietly() }
            }
        }
    }

    private fun testCurator(u: String) {
        toast("A testar o servidor…")
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                val h = com.hortencioflix.tv.data.Http.getJson("$u/health")
                if (h?.optBoolean("ok") != true) return@withContext "O servidor não respondeu. Confirma o endereço."
                com.hortencioflix.tv.data.Addons.clearCuratorSkip()
                // um filme conhecido, para ver as fontes a chegar
                val t0 = android.os.SystemClock.elapsedRealtime()
                val bases = com.hortencioflix.tv.data.Addons.streamBases("movie", "tt0111161").filter { !com.hortencioflix.tv.data.Addons.isLocalAddon(it) }
                val d = com.hortencioflix.tv.data.Http.getJson("$u/${com.hortencioflix.tv.data.Addons.curatorConfig(bases)}/stream/movie/tt0111161.json")
                val ms = android.os.SystemClock.elapsedRealtime() - t0
                val n = d?.optJSONArray("streams")?.length() ?: 0
                val hf = d?.optJSONObject("hf")
                val arr = hf?.optJSONArray("addons")
                val ok = arr?.let { a -> (0 until a.length()).count { a.optJSONObject(it)?.optBoolean("ok") == true } } ?: 0
                val lines = StringBuilder()
                if (arr != null) for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    lines.append("\n• ").append(a.optString("name")).append(": ")
                    if (a.optBoolean("ok")) lines.append(a.optInt("n")).append(" fontes (").append(a.optInt("ms")).append(" ms)")
                    else lines.append("✗ ").append(a.optString("err").ifEmpty { "HTTP " + a.optInt("status") })
                }
                val note = if (ok < (arr?.length() ?: 0)) "\n\nOs add-ons com ✗ não aceitam o servidor: a app pede-os diretamente, como antes." else ""
                "Servidor a funcionar (versão ${h.optString("version")}).\n\nTeste (Os Condenados de Shawshank): $n fontes em $ms ms, $ok de ${arr?.length() ?: 0} add-on(s); ${hf?.optInt("ready") ?: 0} prontas no TorBox.$lines$note"
            }
            if (!isFinishing) dialog().setTitle("Fontes curadas").setMessage(r).setPositiveButton("OK", null).show()
        }
    }

    private fun askPin() {
        val et = android.widget.EditText(this)
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.hint = "PIN"
        val box = LinearLayout(this)
        box.setPadding(dp(24), dp(8), dp(24), 0)
        box.addView(et, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog().setTitle("PIN das definições").setView(box).setCancelable(false)
            .setPositiveButton("OK") { _, _ -> if (et.text.toString() != Prefs.pin) { toast("PIN errado"); finish() } }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .show()
    }

    /** A cópia inclui chaves (TorBox, Stremio…): por defeito protege-se com palavra-passe. */
    private fun askProtect(onText: (String) -> Unit) {
        choiceDialog("Como guardar a cópia?", listOf(
            "🔒  Encriptada com palavra-passe (recomendado)",
            "🧹  Sem chaves nem contas — pode partilhar-se à vontade",
            "⚠️  Sem proteção (chaves visíveis)…"
        )) { w ->
            when (w) {
                0 -> inputDialog("Palavra-passe da cópia", "mínimo 6 caracteres", "", password = true) { p ->
                    if (p.length < 6) { toast("Palavra-passe demasiado curta (mínimo 6)."); return@inputDialog }
                    inputDialog("Repete a palavra-passe", "", "", password = true) { p2 ->
                        if (p2 != p) toast("As palavras-passe não coincidem. Tenta de novo.")
                        else onText(com.hortencioflix.tv.data.BackupCrypto.encrypt(Prefs.exportAll().toString(), p))
                    }
                }
                1 -> onText(Prefs.exportAll(withSecrets = false).toString(2))
                else -> dialog().setTitle("Guardar sem proteção?")
                    .setMessage("O ficheiro vai ter as tuas chaves do TorBox, Trakt, GitHub e da Roku em texto legível. Quem o tiver fica com acesso a essas contas.\n\nSó o faças se o ficheiro nunca sair das tuas mãos.")
                    .setPositiveButton("Guardar assim") { _, _ -> onText(Prefs.exportAll().toString(2)) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun backup() {
        choiceDialog("Cópia de segurança", listOf("💾  Guardar num ficheiro", "📂  Restaurar de um ficheiro", "↗  Partilhar (texto)", "📋  Colar texto para restaurar")) { w ->
            try {
                when (w) {
                    0 -> askProtect { pendingExport = it; exportLauncher.launch("hortencioflix-backup.json") }
                    1 -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    2 -> askProtect { txt -> startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, txt), "Partilhar cópia")) }
                    3 -> inputDialog("Colar cópia de segurança", "{\"app\":\"hortencioflix\"…}", "") { importText(it) }
                }
            } catch (e: Exception) {
                toast("Esta ação não está disponível neste aparelho.")
            }
        }
    }

    private fun debridMenu() {
        val keys = listOf("torbox", "realdebrid", "alldebrid", "premiumize", "debridlink", "offcloud")
        val names = listOf("TorBox", "Real-Debrid", "AllDebrid", "Premiumize", "Debrid-Link", "Offcloud")
        choiceDialog("Serviço usado com o Torrentio", names.mapIndexed { i, n -> (if (keys[i] == Prefs.debridService) "● " else "") + n }) { i ->
            Prefs.debridService = keys[i]
            Prefs.put("lastSync", "0")
            if (keys[i] == "torbox") { refresh(); if (Prefs.torboxKey.isEmpty()) torbox(); return@choiceDialog }
            inputDialog("Chave API ${names[i]}", "cola aqui a tua chave", Prefs.debridKey) { k ->
                Prefs.debridKey = k
                toast(if (k.isEmpty()) "Sem chave" else "${names[i]} configurado")
                refresh()
            }
        }
    }

    private fun traktMenu() {
        val T = com.hortencioflix.tv.data.Trakt
        if (T.linked) {
            choiceDialog("Trakt", listOf("⬇  Importar histórico (marcar vistos)", (if (Prefs.traktScrobble) "✓ " else "") + "Enviar o que vejo (scrobble)", "Terminar sessão")) { w ->
                when (w) {
                    0 -> lifecycleScope.launch {
                        toast("A importar…")
                        val n = withContext(Dispatchers.IO) { try { T.importWatched() } catch (e: Exception) { 0 } }
                        toast("$n itens marcados como vistos")
                    }
                    1 -> { Prefs.traktScrobble = !Prefs.traktScrobble; toast(if (Prefs.traktScrobble) "Scrobble ligado" else "Scrobble desligado") }
                    2 -> { T.logout(); refresh() }
                }
            }
            return
        }
        dialog().setTitle("Ligar ao Trakt")
            .setMessage("O Trakt exige uma «app» tua (é grátis):\n\n1. Em trakt.tv → Settings → Your API Apps → New Application\n2. Nome: Hortêncioflix · Redirect uri: urn:ietf:wg:oauth:2.0:oob\n3. Copia o Client ID e o Client Secret para aqui.")
            .setPositiveButton("Continuar") { _, _ ->
                inputDialog("Client ID", "", T.clientId) { id ->
                    T.clientId = id
                    inputDialog("Client Secret", "", T.clientSecret, password = true) { sec ->
                        T.clientSecret = sec
                        traktLink()
                    }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun traktLink() {
        val T = com.hortencioflix.tv.data.Trakt
        lifecycleScope.launch {
            val dc = withContext(Dispatchers.IO) { T.deviceCode() }
            if (dc == null) { toast("Client ID inválido ou sem ligação ao Trakt."); return@launch }
            val box = LinearLayout(this@SettingsActivity)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(dp(24), dp(8), dp(24), dp(8))
            val tv = TextView(this@SettingsActivity)
            tv.text = "Abre ${dc.url} e introduz o código:\n\n${dc.userCode}"
            tv.textSize = 18f
            tv.setTextColor(this@SettingsActivity.themeColor(R.attr.hfText))
            box.addView(tv)
            val qr = ImageView(this@SettingsActivity)
            box.addView(qr, LinearLayout.LayoutParams(dp(180), dp(180)).apply { topMargin = dp(12) })
            qr.load("https://api.qrserver.com/v1/create-qr-code/?size=360x360&margin=10&data=" + Addons.enc(dc.url))
            val dlg = dialog().setTitle("Ligar ao Trakt").setView(box).setNegativeButton("Cancelar", null).create()
            dlg.show()
            val job = lifecycleScope.launch {
                val end = System.currentTimeMillis() + dc.expiresIn * 1000L
                while (System.currentTimeMillis() < end) {
                    delay(dc.interval * 1000L)
                    if (withContext(Dispatchers.IO) { T.poll(dc.deviceCode) }) {
                        dlg.dismiss()
                        toast("Trakt ligado!")
                        refresh()
                        return@launch
                    }
                }
                dlg.dismiss()
            }
            dlg.setOnDismissListener { job.cancel() }
        }
    }

    private fun pinMenu() {
        if (Prefs.pin.isEmpty()) {
            inputDialog("Novo PIN (4 a 8 dígitos)", "PIN", "", password = true) { p ->
                if (p.length < 4) { toast("O PIN tem de ter pelo menos 4 dígitos"); return@inputDialog }
                Prefs.pin = p
                toast("PIN ativado — protege as Definições e os grupos de canais para adultos.")
                refresh()
            }
        } else {
            inputDialog("PIN atual", "PIN", "", password = true) { p ->
                if (p == Prefs.pin) { Prefs.pin = ""; toast("PIN desativado"); refresh() } else toast("PIN errado")
            }
        }
    }

    private fun onOff(b: Boolean) = if (b) "Ligado" else "Desligado"

    private var filterQ = ""

    private fun refresh() {
        val rows = ArrayList<Row>()
        rows.add(Row(if (filterQ.isEmpty()) "🔍  Procurar nas definições" else "🔍  «$filterQ» — toca para alterar ou limpar", "", tag = "find"))
        rows.add(header("Perfil e aparência"))
        val pr = Prefs.currentProfile()
        rows.add(Row("Perfil: ${pr.name}" + if (pr.kids) " (infantil)" else "", "${Prefs.profiles().size} perfil(is) — cada um tem o seu progresso, vistos e favoritos", tag = "profile"))
        rows.add(Row("Organizar o Início", com.hortencioflix.tv.data.HomeLayout.hidden().size.let { if (it == 0) "Escolhe que filas aparecem e qual vem primeiro" else "$it fila(s) escondida(s)" }, tag = "homeLayout"))
        rows.add(Row("Tema", when (Prefs.themeMode) {
            "amoled" -> "AMOLED — preto puro (poupa bateria em ecrãs OLED)"
            "light" -> "Claro"
            "glass" -> "Vidro (liquid glass)"
            else -> "Escuro (padrão)"
        }, tag = "theme"))
        rows.add(Row("Cor de destaque", Accents.names.getOrElse(Prefs.accent) { Accents.names[0] }, tag = "accent"))
        rows.add(Row("Tamanho dos posters", listOf("Pequeno", "Normal", "Grande")[Prefs.posterSize], tag = "posterSize"))

        rows.add(header("Conta"))
        rows.add(Row("Conta Stremio", if (Stremio.loggedIn) "Ligada: ${Prefs.stremioEmail.ifEmpty { "conta Stremio" }} — add-ons e biblioteca sincronizados" else "Não ligada — liga para importar os teus add-ons, biblioteca e progresso", tag = "stremio"))
        if (Stremio.loggedIn) rows.add(Row("Sincronizar agora", "Atualiza add-ons, biblioteca e «Continuar a ver»", tag = "sync"))
        rows.add(Row("TorBox", if (Prefs.torboxKey.isNotEmpty()) (com.hortencioflix.tv.data.TorboxApi.cached()?.let { com.hortencioflix.tv.data.TorboxApi.summary(it) } ?: "Chave configurada — Torrentio com TorBox ativo") else "Sem chave — adiciona a tua chave da API TorBox", tag = "torbox"))
        val svcNames = mapOf("torbox" to "TorBox", "realdebrid" to "Real-Debrid", "alldebrid" to "AllDebrid", "premiumize" to "Premiumize", "debridlink" to "Debrid-Link", "offcloud" to "Offcloud")
        rows.add(Row("Serviço de debrid", (svcNames[Prefs.debridService] ?: Prefs.debridService) + (if (Prefs.debridService != "torbox") (if (Prefs.debridKey.isNotEmpty()) " — chave configurada" else " — falta a chave") else " — usa a chave TorBox acima"), tag = "debrid"))
        rows.add(Row("Trakt", if (com.hortencioflix.tv.data.Trakt.linked) "Ligado — progresso, lista para ver e recomendações" else "Não ligado — sincroniza o que vês com o Trakt", tag = "trakt"))
        if (Prefs.torboxKey.isNotEmpty()) rows.add(Row("TorBox · os meus downloads", "Ver, reproduzir, apagar e adicionar magnets", tag = "torboxList"))

        rows.add(header("Conteúdo"))
        rows.add(Row("Add-ons", "${Addons.list().size} em uso (${Prefs.stremioAddons().size} da conta Stremio) — ativar, ordenar, configurar e escolher catálogos", tag = "addons"))
        rows.add(Row("Ficheiros do TorBox no Início", onOff(Prefs.showTorboxRow), tag = "torboxRow"))

        rows.add(header("Reprodução"))
        val q = Prefs.maxQuality
        rows.add(Row("Qualidade máxima", if (q == 0) "Sem limite" else if (q == 2160) "4K" else "${q}p", tag = "quality"))
        rows.add(Row("Só fontes em cache (TorBox)", onOff(Prefs.cachedOnly), tag = "cached"))
        rows.add(Row("Ajustar a frequência do ecrã ao vídeo", onOff(Prefs.str("matchFps", "1") == "1") + " — Android TV: filmes a 24p sem solavancos (o ecrã pisca um instante ao começar)", tag = "matchFps"))
        rows.add(Row("Modo de vídeo avançado (tunneling)", onOff(Prefs.str("tunneling") == "1") + " — 4K mais suave em algumas Android TV; desliga se houver imagem preta ou legendas a falhar", tag = "tunneling"))
        rows.add(Row("Preferir imagem", when (Prefs.str("rangePref", "auto")) {
            "dv" -> "Dolby Vision"; "hdr" -> "HDR"; "sdr" -> "SDR (sem HDR)"; "off" -> "Indiferente"
            else -> "Automático — o melhor que este ecrã mostra (" + (com.hortencioflix.tv.data.VideoRange.wanted("auto").ifEmpty { "?" }.let { mapOf("dv" to "Dolby Vision", "hdr" to "HDR", "sdr" to "SDR")[it] ?: it }) + ")"
        } + " — dentro da mesma qualidade, essas fontes passam à frente", tag = "rangePref"))
        rows.add(Row("Filtros fixos das fontes", com.hortencioflix.tv.data.SourceFilters.pinned().let { if (it.isEmpty()) "Nenhum — fixa-os no painel das fontes (📌)" else com.hortencioflix.tv.data.SourceFilters.describe(it) + " — tocar para soltar" }, tag = "pinFilters"))
        rows.add(Row("Animação: preferir dobrado", when (Prefs.str("animDub", "any")) { "pt" -> "🇵🇹 Português de Portugal"; "br" -> "🇧🇷 Português do Brasil"; "off" -> "Não — como os outros filmes"; else -> "Português (Portugal ou Brasil)" } + " — desenhos animados e anime com as fontes dobradas primeiro", tag = "animDub"))
        rows.add(Row("Preferir áudio dobrado", when (Prefs.str("dubPref")) { "br" -> "🇧🇷 Português do Brasil"; "pt" -> "🇵🇹 Português de Portugal"; else -> "Não — a melhor qualidade primeiro" } + " — as fontes dobradas passam à frente", tag = "dubPref"))
        rows.add(Row("Esconder fontes que podem falhar", onOff(Prefs.hideRisky) + " — Dolby Vision sem HDR, AV1, 3D", tag = "risky"))
        rows.add(Row("Rede lenta: baixar a qualidade", when (Prefs.str("qualAuto", "ask")) { "auto" -> "Mudar sozinha"; "off" -> "Nunca"; else -> "Perguntar (padrão)" } + " — depois de 3 pausas para carregar em 3 minutos", tag = "qualAuto"))
        rows.add(Row("Trailer no fundo da página do título", onOff(Prefs.str("trailerAuto", "1") == "1") + " — começa sem som ao fim de 2,5 s (não toca com poupança de dados)", tag = "trailerAuto"))
        rows.add(Row("«A seguir» no fim do filme", onOff(Prefs.str("postPlay", "1") == "1") + " — nos créditos, o vídeo encolhe e aparecem filmes para ver a seguir", tag = "postPlay"))
        rows.add(Row("Sinopses em português", onOff(Prefs.str("ptMeta", "1") == "1") + (if (Prefs.str("tmdbKey").isEmpty()) " — pelo Trakt; com uma chave TMDB (grátis em themoviedb.org) há muito mais títulos traduzidos" else " — TMDB e Trakt"), tag = "ptMeta"))
        rows.add(Row("Fontes curadas (servidor próprio)", (if (Prefs.str("curatorUrl").isEmpty()) "Desligado" else if (Prefs.str("curatorOn", "1") == "1") "Ligado" else "Pausado") + " — um só pedido junta as fontes de todos os add-ons, sem repetidos, e confirma no TorBox o que está pronto", tag = "curator"))
        rows.add(Row("Mini-leitor automático", onOff(Prefs.str("pipAuto", "1") == "1") + " — ao sair da app com um vídeo a tocar, continua numa janela pequena", tag = "pipAuto"))
        rows.add(Row("Voltar onde estavas", onOff(Prefs.str("restoreSession", "1") == "1") + " — se o Android fechar a app a meio de um vídeo, ao abrir volta ao mesmo ponto", tag = "restoreSession"))
        rows.add(Row("Perguntar ao retomar", onOff(Prefs.askResume) + " — «Continuar de 23:10 / Começar do início»", tag = "askResume"))
        rows.add(Row("Próximo episódio automático", onOff(Prefs.autoNext) + " — contagem de 10 s no fim do episódio", tag = "autoNext"))
        rows.add(Row("Enviar progresso para o Stremio", onOff(Prefs.syncProgress), tag = "progress"))
        rows.add(Row("Avisar de novos episódios", onOff(Prefs.notifyEpisodes) + " — notificação quando sai um episódio de uma série que segues", tag = "notify"))
        rows.add(Row("Poupança de dados", onOff(Prefs.dataSaver) + " — em dados móveis, máximo 720p", tag = "dataSaver"))

        rows.add(header("Legendas"))
        val lang = mapOf("por" to "Português", "eng" to "Inglês", "spa" to "Espanhol", "fre" to "Francês", "off" to "Desligadas")
        rows.add(Row("Idioma das legendas", lang[Prefs.subLang] ?: Prefs.subLang, tag = "sub"))
        rows.add(Row("Tamanho", listOf("Pequeno", "Normal", "Grande", "Muito grande")[Prefs.subSize.coerceIn(0, 3)], tag = "subSize"))
        rows.add(Row("Cor", listOf("Branco", "Amarelo")[Prefs.subColor.coerceIn(0, 1)], tag = "subColor"))
        rows.add(Row("Fundo", listOf("Contorno", "Semitransparente", "Preto")[Prefs.subBg.coerceIn(0, 2)], tag = "subBg"))
        rows.add(Row("Posição", listOf("Em baixo", "Mais acima")[Prefs.subPos.coerceIn(0, 1)], tag = "subPos"))

        rows.add(header("TV ao vivo"))
        rows.add(Row("Guia de programação (EPG)", if (Prefs.epgUrl.isNotEmpty()) Prefs.epgUrl else "Automático — usa o guia indicado pela lista ou pela conta Xtream", tag = "epg"))

        rows.add(header("Segurança e dados"))
        rows.add(Row("PIN", if (Prefs.pin.isNotEmpty()) "Ativo — protege Definições e canais para adultos" else "Desativado", tag = "pin"))
        rows.add(Row("Cópia de segurança", "Guardar ou restaurar todas as definições (contas, listas, favoritos…)", tag = "backup"))
        rows.add(Row("Diagnóstico", "Testar Internet, add-ons, TorBox, conta e atualizações", tag = "diag"))

        rows.add(header("Armazenamento"))
        rows.add(Row("Limpar «Continuar a ver» local", "Apaga o progresso guardado neste aparelho", tag = "clear"))
        rows.add(Row("Limpar pesquisas recentes", "${Prefs.recentSearches().size} guardadas", tag = "clearSearch"))
        rows.add(Row("Limpar cache", "Catálogos e imagens guardados para abrir mais depressa", tag = "clearCache"))

        rows.add(header("Atualizações"))
        rows.add(Row("Procurar atualizações", "Versão instalada: ${BuildConfig.VERSION_NAME} — a app verifica sozinha todos os dias e deixa a versão nova descarregada", tag = "update"))
        rows.add(Row("Token GitHub para atualizações", if (Prefs.str("ghToken").isNotEmpty()) "Configurado" else "Necessário enquanto o repositório for privado", tag = "ghtoken"))
        rows.add(Row("Roku: endereço IP", Prefs.str("rokuIp").ifEmpty { "Não definido — na Roku: Definições › Rede › Acerca de" }, tag = "rokuIp"))
        rows.add(Row("Roku: palavra-passe de programador", if (Prefs.str("rokuPass").isNotEmpty()) "Configurada" else "A que escolheste ao ativar o modo de programador", tag = "rokuPass"))
        rows.add(Row("Roku: código de sincronização", Prefs.str("rokuSyncCode").ifEmpty { "Vê na Roku: Definições › Sincronizar com a app Android" }, tag = "rokuSyncCode"))
        rows.add(Row("Sincronizar com a Roku agora", Prefs.str("rokuLastSync").toLongOrNull()?.let { "Última vez: " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(java.util.Date(it)) + " — também é automático" } ?: "«Continuar a ver», vistos, listas e favoritos IPTV iguais nos dois aparelhos", tag = "rokuSync"))
        if (Prefs.str("rokuIp").isNotEmpty()) rows.add(Row("Escrever na TV", "Usa o teclado do telemóvel para escrever na Roku (ex.: na pesquisa)", tag = "rokuType"))
        rows.add(Row("Atualizar a app da Roku agora", Prefs.str("rokuInstalled").let { if (it.isEmpty()) "Envia a versão mais recente para a Roku pela rede" else "Última versão enviada: $it" }, tag = "rokuUpdate"))
        rows.add(Row("Atualizar a Roku automaticamente", onOff(Prefs.str("rokuAuto") == "1") + " — uma vez por dia, só quando a Hortêncioflix não está aberta na Roku", tag = "rokuAuto"))

        rows.add(header("Sobre"))
        rows.add(Row("Hortêncioflix", "Versão ${BuildConfig.VERSION_NAME} · ajuda e atalhos", tag = "about"))
        if (filterQ.isEmpty()) { adapter.update(rows); return }
        val fq = com.hortencioflix.tv.data.Epg.norm(filterQ)
        val hits = rows.filter { it.tag != Header && it.tag != "find" && com.hortencioflix.tv.data.Epg.norm(it.title + " " + it.subtitle).contains(fq) }
        adapter.update(listOf(rows.first()) + hits.ifEmpty { listOf(Row("Nada encontrado", "Tenta outra palavra", tag = "find")) })
    }

    private fun handle(key: String) {
        when (key) {
            "stremio" -> if (Stremio.loggedIn) stremioAccount() else stremioLogin()
            "sync" -> sync()
            "torbox" -> torbox()
            "addons" -> startActivity(android.content.Intent(this, AddonsActivity::class.java))
            "torboxList" -> startActivity(android.content.Intent(this, TorboxActivity::class.java))
            "diag" -> startActivity(android.content.Intent(this, DiagnosticsActivity::class.java))
            "backup" -> backup()
            "pin" -> pinMenu()
            "find" -> inputDialog("Procurar nas definições", "ex.: legendas, PIN, TorBox", filterQ) { filterQ = it.trim(); refresh() }
            "profile" -> Profiles.pick(this)
            "theme" -> choiceDialog("Tema", listOf("Escuro (padrão)", "AMOLED (preto puro)", "Claro", "Vidro (liquid glass)").mapIndexed { i, n ->
                (if (listOf("dark", "amoled", "light", "glass")[i] == Prefs.themeMode) "● " else "") + n }) {
                Prefs.themeMode = listOf("dark", "amoled", "light", "glass")[it]
                startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            "accent" -> choiceDialog("Cor de destaque", Accents.names.mapIndexed { i, n -> (if (i == Prefs.accent) "● " else "") + n }) {
                Prefs.accent = it
                startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            "posterSize" -> choiceDialog("Tamanho dos posters", listOf("Pequeno", "Normal", "Grande").mapIndexed { i, n -> (if (i == Prefs.posterSize) "● " else "") + n }) { Prefs.posterSize = it; refresh() }
            "debrid" -> debridMenu()
            "trakt" -> traktMenu()
            "notify" -> { Prefs.notifyEpisodes = !Prefs.notifyEpisodes; com.hortencioflix.tv.EpisodeWorker.schedule(this); refresh() }
            "dataSaver" -> { Prefs.dataSaver = !Prefs.dataSaver; refresh() }
            "sub" -> {
                val codes = listOf("por", "eng", "spa", "fre", "off")
                choiceDialog("Idioma das legendas", listOf("Português", "Inglês", "Espanhol", "Francês", "Desligadas")) { Prefs.subLang = codes[it]; refresh() }
            }
            "quality" -> {
                val qs = listOf(0, 2160, 1080, 720)
                choiceDialog("Qualidade máxima", listOf("Sem limite", "4K", "1080p", "720p")) { Prefs.maxQuality = qs[it]; refresh() }
            }
            "update" -> Updater.checkNow(this)
            "torboxRow" -> { Prefs.showTorboxRow = !Prefs.showTorboxRow; Prefs.put("lastSync", "0"); refresh() }
            "askResume" -> { Prefs.askResume = !Prefs.askResume; refresh() }
            "autoNext" -> { Prefs.autoNext = !Prefs.autoNext; refresh() }
            "subSize" -> choiceDialog("Tamanho das legendas", listOf("Pequeno", "Normal", "Grande", "Muito grande")) { Prefs.subSize = it; refresh() }
            "subColor" -> choiceDialog("Cor das legendas", listOf("Branco", "Amarelo")) { Prefs.subColor = it; refresh() }
            "subBg" -> choiceDialog("Fundo das legendas", listOf("Contorno", "Semitransparente", "Preto")) { Prefs.subBg = it; refresh() }
            "subPos" -> choiceDialog("Posição das legendas", listOf("Em baixo", "Mais acima")) { Prefs.subPos = it; refresh() }
            "epg" -> inputDialog("Link do guia EPG (XMLTV)", "Deixa vazio para automático", Prefs.epgUrl) { Prefs.epgUrl = it; refresh() }
            "clearSearch" -> { Prefs.clearRecentSearches(); toast("Pesquisas apagadas"); refresh() }
            "clearCache" -> {
                try {
                    java.io.File(filesDir, "home_cache.json").delete()
                    coil.Coil.imageLoader(this).memoryCache?.clear()
                } catch (e: Exception) {}
                toast("Cache limpa")
            }
            "ghtoken" -> inputDialog("Token GitHub (só leitura)", "github_pat_…", Prefs.str("ghToken"), password = true) {
                Prefs.put("ghToken", it)
                refresh()
                if (it.isNotEmpty()) Updater.checkNow(this)
            }
            "homeLayout" -> homeLayoutDialog()
            "qualAuto" -> choiceDialog("Rede lenta: baixar a qualidade", listOf("Perguntar (padrão)", "Mudar sozinha", "Nunca")) {
                Prefs.put("qualAuto", listOf("ask", "auto", "off")[it]); refresh()
            }
            "trailerAuto" -> { Prefs.put("trailerAuto", if (Prefs.str("trailerAuto", "1") == "1") "0" else "1"); refresh() }
            "postPlay" -> { Prefs.put("postPlay", if (Prefs.str("postPlay", "1") == "1") "0" else "1"); refresh() }
            "curator" -> curatorMenu()
            "ptMeta" -> choiceDialog("Sinopses em português", listOf(if (Prefs.str("ptMeta", "1") == "1") "Desligar" else "Ligar", "Chave TMDB…" + if (Prefs.str("tmdbKey").isNotEmpty()) " (definida)" else "")) { w ->
                if (w == 0) { Prefs.put("ptMeta", if (Prefs.str("ptMeta", "1") == "1") "0" else "1"); refresh() }
                else inputDialog("Chave TMDB (API key v3)", "32 letras e números", Prefs.str("tmdbKey")) { k ->
                    Prefs.put("tmdbKey", k.trim()); refresh(); com.hortencioflix.tv.data.RokuSync.syncQuietly()
                }
            }
            "pipAuto" -> { Prefs.put("pipAuto", if (Prefs.str("pipAuto", "1") == "1") "0" else "1"); refresh() }
            "restoreSession" -> { Prefs.put("restoreSession", if (Prefs.str("restoreSession", "1") == "1") "0" else "1"); refresh() }
            "rokuType" -> inputDialog("Escrever na TV", "Nome do filme…", "") { txt ->
                if (txt.isNotEmpty()) lifecycleScope.launch {
                    val ok = com.hortencioflix.tv.data.RokuRemote.type(txt)
                    toast(if (ok) "Enviado para a TV." else "A Roku não respondeu — confirma o endereço IP.")
                }
            }
            "rokuIp" -> inputDialog("IP da Roku", "192.168.1.50", Prefs.str("rokuIp")) { Prefs.put("rokuIp", it.trim()); refresh() }
            "rokuPass" -> inputDialog("Palavra-passe de programador da Roku", "", Prefs.str("rokuPass"), password = true) { Prefs.put("rokuPass", it); refresh() }
            "rokuSyncCode" -> inputDialog("Código de sincronização da Roku", "4 dígitos", Prefs.str("rokuSyncCode")) { Prefs.put("rokuSyncCode", it.trim()); refresh() }
            "rokuSync" -> {
                if (!com.hortencioflix.tv.data.RokuSync.configured) { toast("Indica o IP da Roku e o código de sincronização."); return }
                toast("A sincronizar com a Roku…")
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { com.hortencioflix.tv.data.RokuSync.sync() }
                    if (isFinishing) return@launch
                    dialog().setTitle(if (r.ok) "Roku sincronizada" else "Não foi possível sincronizar").setMessage(r.message).setPositiveButton("OK", null).show()
                    refresh()
                }
            }
            "rokuAuto" -> {
                if (!com.hortencioflix.tv.data.RokuUpdater.configured) toast("Define primeiro o IP e a palavra-passe da Roku.")
                else { Prefs.put("rokuAuto", if (Prefs.str("rokuAuto") == "1") "0" else "1"); refresh() }
            }
            "rokuUpdate" -> {
                if (!com.hortencioflix.tv.data.RokuUpdater.configured) { toast("Define primeiro o IP e a palavra-passe da Roku."); return }
                toast("A atualizar a Roku… (pode demorar um minuto)")
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { com.hortencioflix.tv.data.RokuUpdater.update(this@SettingsActivity) }
                    if (isFinishing) return@launch
                    dialog().setTitle(if (r.ok) "Roku" else "Não foi possível atualizar a Roku").setMessage(r.message).setPositiveButton("OK", null).show()
                    refresh()
                }
            }
            "matchFps" -> { Prefs.put("matchFps", if (Prefs.str("matchFps", "1") == "1") "0" else "1"); refresh() }
            "tunneling" -> { Prefs.put("tunneling", if (Prefs.str("tunneling") == "1") "0" else "1"); refresh() }
            "rangePref" -> choiceDialog("Preferir imagem", listOf("Automático (o melhor que este ecrã mostra)", "Dolby Vision", "HDR", "SDR (sem HDR)", "Indiferente")) {
                Prefs.put("rangePref", listOf("auto", "dv", "hdr", "sdr", "off")[it]); refresh()
            }
            "pinFilters" -> { com.hortencioflix.tv.data.SourceFilters.setPinned(emptyMap()); toast("Filtros fixos removidos."); refresh() }
            "animDub" -> choiceDialog("Animação: preferir dobrado", listOf("Português (Portugal ou Brasil)", "🇵🇹 Português de Portugal", "🇧🇷 Português do Brasil", "Não — como os outros filmes")) {
                Prefs.put("animDub", listOf("any", "pt", "br", "off")[it]); refresh()
            }
            "dubPref" -> choiceDialog("Preferir áudio dobrado", listOf("Não", "🇧🇷 Português do Brasil", "🇵🇹 Português de Portugal")) {
                Prefs.put("dubPref", listOf("", "br", "pt")[it]); refresh()
            }
            "cached" -> { Prefs.cachedOnly = !Prefs.cachedOnly; refresh() }
            "risky" -> { Prefs.hideRisky = !Prefs.hideRisky; refresh() }
            "progress" -> { Prefs.syncProgress = !Prefs.syncProgress; refresh() }
            "clear" -> dialog().setTitle("Limpar progresso local?").setPositiveButton("Limpar") { _, _ -> Prefs.remove("progress"); toast("Feito") }.setNegativeButton("Cancelar", null).show()
            "about" -> dialog().setTitle("Hortêncioflix").setMessage(
                "Versão ${BuildConfig.VERSION_NAME}\n\nFilmes e séries através de add-ons do Stremio, TorBox e listas IPTV.\n\n" +
                    (if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK))
                        "No leitor: MENU (ou INFO) abre legendas, áudio e fontes; ◀ ▶ recua/avança; ▲ ▼ muda de canal na TV ao vivo; VOLTAR com o vídeo a tocar abre o mini-leitor."
                    else
                        "No leitor: toca para ver os controlos; 2 toques nas laterais recuam/avançam 10 s; desliza na vertical para brilho (esquerda) e volume (direita); pinça para preencher o ecrã; arrasta para baixo ou VOLTAR para o mini-leitor; 🔒 bloqueia o ecrã.") +
                    "\n\nIdentidade: vermelho, preto e dourado — as cores de Angola."
            ).setPositiveButton("OK", null).show()
        }
    }

    private fun sync() {
        toast("A sincronizar…")
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { Stremio.sync() }
            Prefs.put("lastSync", System.currentTimeMillis().toString())
            toast(r)
            refresh()
        }
    }

    private fun stremioAccount() {
        choiceDialog("Conta Stremio", listOf("Sincronizar agora", "Terminar sessão")) {
            if (it == 0) sync() else {
                Stremio.logout()
                toast("Sessão terminada")
                refresh()
            }
        }
    }

    private fun stremioLogin() {
        choiceDialog("Ligar conta Stremio", listOf("Com código (recomendado — no telemóvel)", "Com email e palavra-passe")) {
            if (it == 0) linkLogin() else emailLogin()
        }
    }

    private fun emailLogin() {
        inputDialog("Email do Stremio", "email@exemplo.com", Prefs.stremioEmail) { email ->
            if (email.isEmpty()) return@inputDialog
            inputDialog("Palavra-passe", "", "", password = true) { pass ->
                lifecycleScope.launch {
                    val err = withContext(Dispatchers.IO) { Stremio.login(email, pass) }
                    if (err != null) { toast(err); return@launch }
                    toast("Conta ligada. A sincronizar…")
                    sync()
                }
            }
        }
    }

    private fun linkLogin() {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { Stremio.linkCreate() }
            if (r == null) { toast("Não foi possível obter um código. Tenta com email."); return@launch }
            val (code, link) = r
            val box = LinearLayout(this@SettingsActivity)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(dp(24), dp(8), dp(24), dp(8))
            val tv = TextView(this@SettingsActivity)
            tv.text = "1. No telemóvel ou computador abre:\n$link\n\n2. Inicia sessão e introduz o código:\n\n$code"
            tv.textSize = 18f
            tv.setTextColor(this@SettingsActivity.themeColor(R.attr.hfText))
            box.addView(tv)
            val qr = ImageView(this@SettingsActivity)
            box.addView(qr, LinearLayout.LayoutParams(dp(200), dp(200)).apply { topMargin = dp(12) })
            qr.load("https://api.qrserver.com/v1/create-qr-code/?size=400x400&margin=10&data=" + Addons.enc("$link?code=$code"))
            val dlg: AlertDialog = dialog().setTitle("Ligar conta Stremio").setView(box).setNegativeButton("Cancelar", null).create()
            dlg.setOnDismissListener { linkJob?.cancel() }
            dlg.show()
            linkJob?.cancel()
            linkJob = lifecycleScope.launch {
                repeat(100) {
                    delay(3000)
                    val key = withContext(Dispatchers.IO) { Stremio.linkRead(code) }
                    if (key != null) {
                        withContext(Dispatchers.IO) { Stremio.acceptKey(key) }
                        dlg.setOnDismissListener(null)
                        dlg.dismiss()
                        toast("Conta ligada. A sincronizar…")
                        sync()
                        return@launch
                    }
                }
                dlg.dismiss()
                toast("O código expirou. Tenta de novo.")
            }
        }
    }

    private fun torbox() {
        inputDialog("Chave da API TorBox", "torbox.app → Settings → API key", Prefs.torboxKey) { key ->
            if (key.isEmpty()) {
                Prefs.torboxKey = ""
                toast("Chave TorBox removida")
                refresh()
                return@inputDialog
            }
            lifecycleScope.launch {
                val me = withContext(Dispatchers.IO) { Http.getJsonAuth("https://api.torbox.app/v1/api/user/me", mapOf("Authorization" to "Bearer $key")) }
                if (me != null && me.optBoolean("success")) {
                    Prefs.torboxKey = key
                    withContext(Dispatchers.IO) { com.hortencioflix.tv.data.TorboxApi.account(fresh = true) }
                    toast("TorBox ligado: ${me.optJSONObject("data")?.optString("email") ?: "conta válida"}")
                } else {
                    Prefs.torboxKey = key
                    toast("Chave guardada, mas não foi possível confirmá-la agora.")
                }
                refresh()
            }
        }
    }

    private fun addons() {
        val local = Prefs.localAddons()
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        for (a in local) {
            labels.add("🗑 Remover: ${a.removePrefix("https://")}")
            acts.add {
                Prefs.setLocalAddons(local - a)
                if (Prefs.localAddons().isEmpty()) Prefs.put("addons", "[]")
                toast("Add-on removido"); refresh()
            }
        }
        labels.add("+ Adicionar add-on (link do manifest)")
        acts.add {
            inputDialog("Link do add-on", "https://…/manifest.json", "") { url ->
                if (!url.startsWith("http") && !url.startsWith("stremio")) { toast("Link inválido"); return@inputDialog }
                val base = Addons.normalize(url)
                lifecycleScope.launch {
                    val m = Addons.manifest(base)
                    if (m == null) { toast("Não foi possível ler o manifest deste add-on."); return@launch }
                    Prefs.setLocalAddons((Prefs.localAddons() + base).distinct())
                    toast("Add-on adicionado: ${m.optString("name")}")
                    refresh()
                }
            }
        }
        if (!local.contains(Addons.CINEMETA)) {
            labels.add("+ Repor Cinemeta (catálogo oficial)")
            acts.add { Prefs.setLocalAddons((local + Addons.CINEMETA).distinct()); refresh() }
        }
        if (Prefs.stremioAddons().isNotEmpty()) {
            labels.add("ℹ Add-ons da conta Stremio: ${Prefs.stremioAddons().size} (geridos no Stremio)")
            acts.add { toast(Prefs.stremioAddons().joinToString("\n") { it.removePrefix("https://").take(60) }) }
        }
        choiceDialog("Add-ons", labels) { acts[it].invoke() }
    }

    override fun onDestroy() {
        linkJob?.cancel()
        super.onDestroy()
    }

    /** Organizar o Início: marcar as filas visíveis e escolher uma para ir para o topo. */
    private fun homeLayoutDialog() {
        val HL = com.hortencioflix.tv.data.HomeLayout
        val titles = HL.known().let { k -> HL.order().filter { it in k } + k.filter { it !in HL.order() } }
        if (titles.isEmpty()) { toast("Abre primeiro o Início para a app conhecer as filas."); return }
        val hidden = HL.hidden().toMutableSet()
        val checked = BooleanArray(titles.size) { titles[it] !in hidden }
        dialog().setTitle("Filas do Início")
            .setMultiChoiceItems(titles.toTypedArray(), checked) { _, i, on -> if (on) hidden.remove(titles[i]) else hidden.add(titles[i]) }
            .setPositiveButton("Guardar") { _, _ -> HL.setHidden(hidden); toast("Início atualizado — volta ao Início para ver."); refresh() }
            .setNeutralButton("Mover para o topo…") { _, _ ->
                choiceDialog("Qual fila vem primeiro?", titles) { HL.moveToTop(titles[it]); toast("«${titles[it]}» passa a ser a primeira fila."); refresh() }
            }
            .setNegativeButton("Repor") { _, _ -> HL.reset(); toast("Início reposto."); refresh() }
            .show()
    }
}
