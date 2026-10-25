package com.hortencioflix.tv.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.Channel
import com.hortencioflix.tv.data.ChannelGroup
import com.hortencioflix.tv.data.Epg
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.Iptv
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvActivity : BaseActivity() {
    companion object {
        const val ADDONS = "addons:"
    }

    private var groups: List<ChannelGroup> = emptyList()
    private var shown: List<Channel> = emptyList()
    private lateinit var groupAdapter: RowAdapter
    private lateinit var channelAdapter: RowAdapter
    private lateinit var listName: TextView
    private lateinit var status: TextView
    private lateinit var channelsView: RecyclerView
    private lateinit var search: android.widget.EditText
    private lateinit var retry: TextView
    private lateinit var loader: View
    private var currentGroup: List<Channel> = emptyList()
    private var filterJob: kotlinx.coroutines.Job? = null
    private var lastName = ""
    private var lastUrl = ""
    private var adultUnlocked = false
    private var showingFavs = false
    private val adultRe = Regex("(adult|xxx|\\+18|18\\+|porn|erotic|for adults)", RegexOption.IGNORE_CASE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iptv)
        listName = findViewById(R.id.list_name)
        status = findViewById(R.id.status)
        channelsView = findViewById(R.id.channels)
        val groupsView = findViewById<RecyclerView>(R.id.groups)

        val portrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        groupAdapter = RowAdapter(emptyList(), chip = portrait, onClick = { i, _ -> showGroup(i); channelsView.post { channelsView.getChildAt(0)?.requestFocus() } },
            onLong = { i, _ -> groupMenu(i) },
            onFocus = { i, _ -> showGroup(i) })
        groupsView.layoutManager = if (portrait) LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false) else LinearLayoutManager(this)
        groupsView.adapter = groupAdapter

        channelAdapter = RowAdapter(emptyList(), onClick = { i, _ ->
            val ch = shown.getOrNull(i)
            if (ch != null && IptvVod.isVod(ch)) {
                IptvVod.open(this, ch) { b -> loader.visibility = if (b) View.VISIBLE else View.GONE; if (b) status.text = "A procurar legendas…" }
            } else {
                Session.live(shown, i)
                PlayerActivity.start(this)
            }
        }, onLong = { _, r ->
            val ch = r.tag as Channel
            if (showingFavs) favMenu(ch) else {
                val on = Prefs.toggleFavorite(ch)
                toast(if (on) "★ ${ch.name} adicionado aos favoritos" else "${ch.name} removido dos favoritos")
            }
        })
        channelsView.layoutManager = LinearLayoutManager(this)
        channelsView.adapter = channelAdapter

        search = findViewById(R.id.search)
        retry = findViewById(R.id.btn_retry)
        loader = findViewById(R.id.loader)
        retry.setOnClickListener { if (lastUrl.isNotEmpty()) loadList(lastName, lastUrl) }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                filterJob?.cancel()
                filterJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(250)
                    applyFilter()
                }
            }
        })
        findViewById<TextView>(R.id.btn_lists).setOnClickListener { listsMenu() }
        findViewById<TextView>(R.id.btn_favs).setOnClickListener { showFavorites() }
        findViewById<TextView>(R.id.btn_guide).setOnClickListener {
            if (shown.isEmpty()) toast("Abre primeiro uma lista ou categoria de canais.")
            else EpgGridActivity.start(this, shown)
        }

        val last = Prefs.str("iptvLast")
        if (last.isNotEmpty()) {
            loadList(Prefs.str("iptvLastName", "Lista"), last)
        } else if (Prefs.favorites().length() > 0) {
            showFavorites()
        } else if (Addons.hasTvAddons()) {
            loadList("Add-ons de TV", ADDONS)
        } else {
            status.text = "Escolhe uma lista em «Listas» (há listas públicas de canais abertos) ou adiciona a tua M3U/Xtream."
            findViewById<TextView>(R.id.btn_lists).requestFocus()
        }
    }

    private fun showGroup(i: Int) {
        val g = groups.getOrNull(i) ?: return
        if (currentGroup === g.channels && search.text.isEmpty()) return
        currentGroup = g.channels
        if (search.text.isNotEmpty()) { applyFilter(); return }
        showChannels(g.channels)
    }

    private fun showChannels(input: List<Channel>) {
        val dead = Prefs.deadChannels()
        val list = if (Prefs.hideDead) input.filter { it.url !in dead } else input
        shown = list
        if (list.isEmpty() && groups.isNotEmpty() && loader.visibility != View.VISIBLE) status.text = if (search.text.isNotEmpty()) "Nenhum canal com esse nome." else "Este grupo não tem canais (ou estão todos marcados como offline)."
        channelAdapter.update(list.take(3000).map { ch ->
            val epg = Epg.describe(ch)
            val off = if (ch.url in dead) "✗ offline  ·  " else ""
            Row(ch.name, off + epg.ifEmpty { ch.group }, ch.logo.ifEmpty { "#" }, ch)
        })
        channelsView.scrollToPosition(0)
    }

    /** Procura por nome em todos os canais da lista (ignora acentos e maiúsculas). */
    private fun applyFilter() {
        val q = Epg.norm(search.text.toString())
        if (q.isEmpty()) { showChannels(currentGroup); return }
        val all = groups.flatMap { it.channels }.distinctBy { it.url }
        val found = all.filter { Epg.norm(it.name).contains(q) }
        status.text = "${found.size} canais encontrados"
        showChannels(found)
    }

    private fun loadEpg(url: String) {
        val epgUrl = Prefs.epgUrl.ifEmpty { Iptv.lastEpgUrl.ifEmpty { Iptv.xtreamEpg(url) } }
        if (epgUrl.isEmpty() || url == ADDONS) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Epg.load(epgUrl) }
            if (ok && lastUrl == url) {
                showChannels(shown)
                toast("Guia de programação carregado")
            }
        }
    }

    private fun setGroups(input: List<ChannelGroup>) {
        val recents = Prefs.recentChannels()
        val hidden = Prefs.hiddenGroups()
        val locked = Prefs.isKids || (Prefs.pin.isNotEmpty() && !adultUnlocked)
        val visible = input.filter { g -> g.title !in hidden && !(locked && adultRe.containsMatchIn(g.title)) }
            .map { g -> if (locked && g.title == "Todos") ChannelGroup(g.title, g.channels.filter { !adultRe.containsMatchIn(it.group) }) else g }
        val list = if (visible.isNotEmpty() && recents.isNotEmpty()) listOf(ChannelGroup("🕘 Recentes", recents)) + visible else visible
        groups = list
        groupAdapter.update(list.map { Row(if (groupAdapterChip()) "${it.title} (${it.channels.size})" else it.title, "${it.channels.size} canais") })
        shown = emptyList()
        currentGroup = emptyList()
        if (list.isEmpty()) showChannels(emptyList()) else showGroup(if (recents.isNotEmpty() && visible.isNotEmpty()) 1 else 0)
    }

    private fun groupAdapterChip() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    private var allGroups: List<ChannelGroup> = emptyList()

    private fun groupMenu(i: Int) {
        val g = groups.getOrNull(i) ?: return
        if (g.title.startsWith("🕘") || g.title == "Todos" || g.title == "Favoritos") return
        choiceDialog(g.title, listOf("👁  Esconder este grupo", "🩺  Verificar canais deste grupo")) { w ->
            if (w == 0) { Prefs.setGroupHidden(g.title, true); toast("Grupo escondido — podes voltar a mostrá-lo em Listas."); setGroups(allGroups) }
            else checkChannels(g.channels)
        }
    }

    private fun favMenu(ch: Channel) {
        choiceDialog(ch.name, listOf("↑  Mover para cima", "↓  Mover para baixo", "✕  Remover dos favoritos")) { w ->
            when (w) {
                0 -> Prefs.moveFavorite(ch.url, -1)
                1 -> Prefs.moveFavorite(ch.url, 1)
                2 -> Prefs.toggleFavorite(ch)
            }
            showFavorites()
        }
    }

    /** Testa os canais (em paralelo) e marca os que não respondem. */
    private fun checkChannels(list: List<Channel>) {
        val targets = list.filter { !it.url.startsWith("stremio:") }.take(400)
        if (targets.isEmpty()) { toast("Nada para verificar."); return }
        status.text = "A verificar ${targets.size} canais…"
        lifecycleScope.launch {
            val sem = kotlinx.coroutines.sync.Semaphore(10)
            var done = 0
            val results = targets.map { ch ->
                async(Dispatchers.IO) {
                    sem.acquire()
                    try {
                        val code = Http.headCode(ch.url, emptyMap())
                        ch.url to !(code == -1 || (code >= 400 && code != 405 && code != 403))
                    } finally {
                        sem.release()
                        withContext(Dispatchers.Main) { done++; status.text = "A verificar canais… $done/${targets.size}" }
                    }
                }
            }.awaitAll()
            val dead = results.filter { !it.second }.map { it.first }
            Prefs.setDead(dead, results.filter { it.second }.map { it.first })
            status.text = "${targets.size - dead.size} a funcionar · ${dead.size} offline"
            showChannels(currentGroup)
            if (dead.isNotEmpty() && !Prefs.hideDead) toast("Podes esconder os canais offline em Listas.")
        }
    }

    private fun showFavorites() {
        showingFavs = true
        val favs = Prefs.favoriteChannels()
        listName.text = "★ Favoritos"
        if (favs.isEmpty()) {
            status.text = "Ainda não tens favoritos. Mantém OK premido num canal para o adicionar."
            setGroups(emptyList())
            return
        }
        status.text = "${favs.size} canais favoritos"
        allGroups = emptyList()
        setGroups(listOf(ChannelGroup("Favoritos", favs)))
        channelsView.post { channelsView.getChildAt(0)?.requestFocus() }
    }

    private fun loadList(name: String, url: String) {
        showingFavs = false
        listName.text = name
        status.text = "A carregar lista…"
        lastName = name
        lastUrl = url
        retry.visibility = View.GONE
        setGroups(emptyList())
        loader.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = try { kotlinx.coroutines.withTimeoutOrNull(60_000) { loadListInner(name, url) } } finally { loader.visibility = View.GONE }
            if (ok == null) {
                status.text = "A lista demorou demasiado a responder."
                retry.visibility = View.VISIBLE
                retry.requestFocus()
            }
        }
    }

    private suspend fun loadListInner(name: String, url: String) {
        run {
            var text: String? = null
            val parsed: List<ChannelGroup>
            if (url == ADDONS) {
                val g = try { Addons.tvGroups() } catch (e: Exception) { emptyList() }
                val all = g.flatMap { it.channels }
                parsed = if (g.size > 1) listOf(ChannelGroup("Todos", all)) + g else g
                if (parsed.isEmpty()) {
                    status.text = "Não há add-ons de TV instalados (ou não responderam). Os add-ons de TV da tua conta Stremio aparecem aqui."
                    retry.visibility = View.VISIBLE
                    return
                }
            } else {
                // Lista guardada no aparelho: abre logo; volta a descarregar se tiver mais de 6 h.
                val cache = java.io.File(java.io.File(cacheDir, "iptv").apply { mkdirs() }, Integer.toHexString(url.hashCode()) + ".m3u")
                val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 6 * 3600_000L
                text = if (fresh) withContext(Dispatchers.IO) { try { cache.readText() } catch (e: Exception) { null } } else null
                if (text == null) {
                    val net = withContext(Dispatchers.IO) { Http.getString(url) }
                    if (net != null) {
                        withContext(Dispatchers.IO) { try { cache.writeText(net) } catch (e: Exception) {} }
                        text = net
                    } else if (cache.exists()) {
                        // Sem ligação: usar a cópia antiga.
                        text = withContext(Dispatchers.IO) { try { cache.readText() } catch (e: Exception) { null } }
                    }
                }
                val t = text
                parsed = if (t == null) emptyList() else withContext(Dispatchers.Default) { Iptv.parse(t) }
            }
            if (parsed.isEmpty()) {
                status.text = if (text == null) "Não foi possível descarregar a lista (link inválido ou servidor em baixo)." else "A lista não tem canais reconhecidos."
                retry.visibility = View.VISIBLE
                return
            }
            Prefs.put("iptvLast", url)
            Prefs.put("iptvLastName", name)
            val total = parsed.first().channels.size
            status.text = "$total canais  ·  mantém OK premido para favoritos"
            allGroups = parsed
            setGroups(parsed)
            findViewById<RecyclerView>(R.id.groups).post { findViewById<RecyclerView>(R.id.groups).getChildAt(0)?.requestFocus() }
            loadEpg(url)
        }
    }

    private fun listsMenu() {
        val mine = Iptv.lists()
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add("📡 Add-ons de TV do Stremio"); acts.add { loadList("Add-ons de TV", ADDONS) }
        if (com.hortencioflix.tv.data.Xtream.fromListUrl(lastUrl) != null) { labels.add("🎬 Filmes e séries desta conta Xtream"); acts.add { XtreamActivity.open(this, lastUrl, lastName) } }
        for (s in mine) { labels.add("📋 ${s.name}"); acts.add { loadList(s.name, s.url) } }
        labels.add("+ Adicionar lista M3U (link)"); acts.add { addM3u() }
        labels.add("+ Adicionar conta Xtream Codes"); acts.add { addXtream() }
        labels.add("🌍 Listas públicas (canais abertos)"); acts.add { publicLists() }
        if (mine.isNotEmpty()) { labels.add("🗑 Remover uma lista"); acts.add { removeList() } }
        if (currentGroup.isNotEmpty()) { labels.add("🩺 Verificar canais do grupo atual"); acts.add { checkChannels(currentGroup) } }
        labels.add(if (Prefs.hideDead) "✓ Esconder canais offline (ligado)" else "Esconder canais offline"); acts.add { Prefs.hideDead = !Prefs.hideDead; showChannels(currentGroup) }
        val hid = Prefs.hiddenGroups()
        if (hid.isNotEmpty()) { labels.add("👁 Mostrar grupos escondidos (${hid.size})"); acts.add { for (g in hid) Prefs.setGroupHidden(g, false); setGroups(allGroups) } }
        if (Prefs.pin.isNotEmpty() && !adultUnlocked) { labels.add("🔞 Desbloquear grupos para adultos"); acts.add { unlockAdult() } }
        choiceDialog("Listas IPTV", labels) { acts[it].invoke() }
    }

    private fun unlockAdult() {
        inputDialog("PIN", "PIN", "", password = true) { p ->
            if (p == Prefs.pin) { adultUnlocked = true; setGroups(allGroups); toast("Desbloqueado até saíres deste ecrã") } else toast("PIN errado")
        }
    }

    private fun publicLists() {
        choiceDialog("Listas públicas", Iptv.PUBLIC.map { it.name }) {
            val s = Iptv.PUBLIC[it]
            loadList(s.name, s.url)
        }
    }

    private fun addM3u() {
        inputDialog("Link da lista M3U", "http://…/lista.m3u", "") { url ->
            if (!url.startsWith("http")) { toast("O link tem de começar por http"); return@inputDialog }
            inputDialog("Nome da lista", "A minha lista", "") { n ->
                val name = n.ifEmpty { "Lista ${Iptv.lists().size + 1}" }
                Iptv.addList(name, url)
                loadList(name, url)
            }
        }
    }

    private fun addXtream() {
        inputDialog("Servidor Xtream", "http://servidor:porta", "") { server ->
            if (server.isEmpty()) return@inputDialog
            inputDialog("Utilizador", "", "") { user ->
                inputDialog("Palavra-passe", "", "", password = true) { pass ->
                    val url = Iptv.xtreamUrl(server, user, pass)
                    val name = "Xtream · $user"
                    Iptv.addList(name, url)
                    loadList(name, url)
                }
            }
        }
    }

    private fun removeList() {
        val mine = Iptv.lists()
        choiceDialog("Remover lista", mine.map { it.name }) {
            Iptv.removeList(mine[it].url)
            if (Prefs.str("iptvLast") == mine[it].url) { Prefs.remove("iptvLast"); listName.text = "" ; setGroups(emptyList()) }
            toast("Lista removida")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && groups.isEmpty()) status.visibility = View.VISIBLE
    }
}
