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
import com.hortencioflix.tv.data.ChannelFacets
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
    private lateinit var channelAdapter: ChannelAdapter
    private var touch = false
    /** Todos os canais da lista aberta e os nomes já normalizados para a pesquisa (calculados uma vez). */
    private var allChannels: List<Channel> = emptyList()
    private var normNames: List<String> = emptyList()
    private var mixedGroup = true
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

    // Filtros por tipo de conteúdo e por país (calculados a partir dos grupos e dos nomes dos canais)
    private lateinit var typeAdapter: RowAdapter
    private lateinit var typesView: RecyclerView
    private var btnRegion: TextView? = null
    private var baseGroups: List<ChannelGroup> = emptyList()
    private var kinds: Map<String, ChannelFacets.Kind> = emptyMap()
    private var regions: Map<String, String> = emptyMap()
    private var kindFilter: ChannelFacets.Kind? = null
    private var regionFilter = ""
    private var typeKinds: List<ChannelFacets.Kind?> = emptyList()
    private var regionCounts: List<Pair<String, Int>> = emptyList()
    private var gridMode = false
    /** Versões do mesmo canal juntas numa linha: endereço do canal mostrado → todas (a preferida primeiro). */
    private var variants: Map<String, List<Channel>> = emptyMap()
    private var showJob: kotlinx.coroutines.Job? = null
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

        touch = !MobileNav.isTv(this)
        channelAdapter = ChannelAdapter(touch, onClick = { i, _ ->
            val ch = shown.getOrNull(i)
            if (ch != null && IptvVod.isVod(ch)) {
                IptvVod.open(this, ch) { b -> loader.visibility = if (b) View.VISIBLE else View.GONE; if (b) status.text = "A procurar legendas…" }
            } else {
                Session.live(shown, i)
                Session.liveVariants = variants.mapValues { e -> e.value.map { it.url } }
                PlayerActivity.start(this)
            }
        }, onLong = { ch -> if (showingFavs) favMenu(ch) else if (variants[ch.url].orEmpty().size > 1) variantMenu(ch) else toggleFav(ch) }, onFav = { ch -> toggleFav(ch) })
        channelAdapter.favorites = Prefs.favoriteChannels().map { it.url }.toSet()
        gridMode = findViewById<View?>(R.id.btn_view) != null && Prefs.str("iptvGrid") == "1"
        applyViewMode()
        findViewById<View?>(R.id.btn_view)?.setOnClickListener {
            gridMode = !gridMode
            Prefs.put("iptvGrid", if (gridMode) "1" else "0")
            applyViewMode()
            channelsView.post { requestNowForVisible() }
        }
        typesView = findViewById(R.id.types)
        typeAdapter = RowAdapter(emptyList(), chip = true, onClick = { i, _ -> kindFilter = typeKinds.getOrNull(i); onFacetsChanged() })
        typesView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        typesView.adapter = typeAdapter
        btnRegion = findViewById(R.id.btn_region)
        btnRegion?.setOnClickListener { regionMenu() }
        channelsView.itemAnimator = null
        channelsView.setHasFixedSize(true)
        // «A dar agora» das contas Xtream: pede só para os canais que ficam no ecrã quando a lista pára
        channelsView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) requestNowForVisible()
            }
        })

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

        MobileNav.attach(this, "live")
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

    private fun toggleFav(ch: Channel) {
        val on = Prefs.toggleFavorite(ch)
        channelAdapter.favorites = Prefs.favoriteChannels().map { it.url }.toSet()
        channelAdapter.refresh(ch.url, channelsView)
        val name = com.hortencioflix.tv.data.ChannelName.of(ch.name).name
        status.text = if (on) "★ $name adicionado aos favoritos" else "$name removido dos favoritos"
        if (showingFavs && !on) showFavorites()
    }

    private fun requestNowForVisible() {
        val lm = channelsView.layoutManager as? LinearLayoutManager ?: return
        val a = lm.findFirstVisibleItemPosition()
        val b = lm.findLastVisibleItemPosition()
        if (a < 0) return
        for (i in a..b) {
            val ch = shown.getOrNull(i) ?: continue
            if (!com.hortencioflix.tv.data.XtreamNow.supports(ch.url) || Epg.nowNext(ch).first != null) continue
            com.hortencioflix.tv.data.XtreamNow.request(ch.url) { if (!isFinishing) channelAdapter.refresh(ch.url, channelsView) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::search.isInitialized && search.text.isNotEmpty()) { search.setText(""); return }
        if (MobileNav.back(this)) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun showGroup(i: Int) {
        val g = groups.getOrNull(i) ?: return
        mixedGroup = g.title == "Todos" || g.title.startsWith("🕘") || g.title == "Favoritos"
        if (groupAdapterChip()) groupAdapter.select(i)
        if (currentGroup === g.channels && search.text.isEmpty()) return
        currentGroup = g.channels
        if (search.text.isNotEmpty()) { applyFilter(); return }
        showChannels(g.channels)
    }

    private fun showChannels(input: List<Channel>) {
        val dead = Prefs.deadChannels()
        val list = if (Prefs.hideDead) input.filter { it.url !in dead } else input
        showJob?.cancel()
        val merge = !showingFavs && list.size > 1 && Prefs.str("iptvMerge", "1") == "1"
        if (!merge) { present(list, emptyMap(), dead); return }
        if (list.size <= 400) {
            val m = mergeVariants(list, regions)
            present(m.first, m.second, dead)
            return
        }
        // listas grandes: junta as versões fora do ecrã (a lista anterior fica à vista entretanto)
        val reg = regions
        showJob = lifecycleScope.launch {
            val m = withContext(Dispatchers.Default) { mergeVariants(list, reg) }
            present(m.first, m.second, dead)
        }
    }

    private fun present(list: List<Channel>, vars: Map<String, List<Channel>>, dead: Set<String>) {
        shown = list
        variants = vars
        if (list.isEmpty() && groups.isNotEmpty() && loader.visibility != View.VISIBLE) status.text = if (search.text.isNotEmpty()) "Nenhum canal com esse nome." else "Este grupo não tem canais (ou estão todos marcados como offline)."
        channelAdapter.dead = dead
        channelAdapter.showGroup = mixedGroup || search.text.isNotEmpty()
        channelAdapter.hideRegion = if (facetsActive()) regionFilter else ""
        channelAdapter.variantCount = vars.mapValues { it.value.size }
        channelAdapter.submit(list)
        channelsView.scrollToPosition(0)
        channelsView.post { requestNowForVisible() }
    }

    /** Resolução de um canal pelos selos do nome: 4 = 4K, 3 = FHD, 2 = HD (ou sem indicação), 1 = SD. */
    private fun rank(c: Channel): Int {
        val b = com.hortencioflix.tv.data.ChannelName.of(c.name).badges
        return when { "4K" in b -> 4; "FHD" in b -> 3; "SD" in b -> 1; else -> 2 }
    }

    /**
     * Junta as versões do mesmo canal («RTP 1 FHD», «RTP 1 HD», «RTP 1 SD», cópias) numa só linha. Fica à frente a
     * melhor que a Internet medida aguenta; as outras servem de reserva se essa falhar e podem ser escolhidas à mão.
     */
    private fun mergeVariants(list: List<Channel>, reg: Map<String, String>): Pair<List<Channel>, Map<String, List<Channel>>> {
        val kbps = com.hortencioflix.tv.data.NetSpeed.kbps()
        val max = when { kbps <= 0 -> 3; kbps >= 25_000 -> 4; kbps >= 8_000 -> 3; kbps >= 4_000 -> 2; else -> 1 }
        val byKey = LinkedHashMap<String, MutableList<Channel>>()
        for (c in list) {
            val cl = com.hortencioflix.tv.data.ChannelName.of(c.name)
            val key = (reg[c.url] ?: cl.region) + "|" + cl.name.lowercase()
            byKey.getOrPut(key) { ArrayList(1) }.add(c)
        }
        val out = ArrayList<Channel>(byKey.size)
        val vars = HashMap<String, List<Channel>>()
        for (l in byKey.values) {
            if (l.size == 1) { out.add(l[0]); continue }
            // primeiro as que a rede aguenta (da melhor para a pior), depois as pesadas demais (da mais leve para a mais pesada)
            val sorted = l.sortedBy { val r = rank(it); if (r <= max) max - r else 10 + r - max }
            out.add(sorted[0])
            vars[sorted[0].url] = sorted
        }
        return out to vars
    }

    /** Manter premido num canal com várias versões: escolher a versão ou marcar favorito. */
    private fun variantMenu(ch: Channel) {
        val all = variants[ch.url].orEmpty()
        val fav = ch.url in channelAdapter.favorites
        val labels = listOf(if (fav) "★  Remover dos favoritos" else "☆  Adicionar aos favoritos") + all.map { v ->
            val c = com.hortencioflix.tv.data.ChannelName.of(v.name)
            "▶  " + c.name + (if (c.badges.isNotEmpty()) "  ·  " + c.badges.joinToString(" · ") else "") + (if (v.group.isNotEmpty()) "   (" + ChannelFacets.groupLabel(v.group) + ")" else "")
        }
        choiceDialog(com.hortencioflix.tv.data.ChannelName.of(ch.name).name, labels) { w ->
            if (w == 0) { toggleFav(ch); return@choiceDialog }
            val pick = all.getOrNull(w - 1) ?: return@choiceDialog
            val i = shown.indexOfFirst { it.url == ch.url }
            if (i < 0) return@choiceDialog
            // toca a versão escolhida; as restantes ficam como reserva
            val list = shown.toMutableList()
            list[i] = pick
            Session.live(list, i)
            Session.liveVariants = mapOf(pick.url to (listOf(pick.url) + all.filter { it.url != pick.url }.map { it.url }))
            PlayerActivity.start(this)
        }
    }

    /** Procura por nome em todos os canais da lista (ignora acentos e maiúsculas). */
    private fun applyFilter() {
        val q = Epg.norm(search.text.toString())
        if (q.isEmpty()) { showChannels(currentGroup); return }
        // nomes normalizados uma só vez por lista: procurar em 20 000 canais é imediato
        val names = normNames
        val all = allChannels
        val found = ArrayList<Channel>()
        val facet = facetsActive()
        if (names.size == all.size) { for (i in all.indices) if (names[i].contains(q) && (!facet || passes(all[i]))) found.add(all[i]) }
        else for (c in all) if (Epg.norm(c.name).contains(q) && (!facet || passes(c))) found.add(c)
        status.text = com.hortencioflix.tv.data.Text.plural(found.size, "canal encontrado", "canais encontrados") + (if (facet) " em " + facetLabel() else "")
        showChannels(found)
    }

    private fun loadEpg(url: String) {
        val epgUrl = Prefs.epgUrl.ifEmpty { Iptv.lastEpgUrl.ifEmpty { Iptv.xtreamEpg(url) } }
        if (epgUrl.isEmpty() || url == ADDONS) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Epg.load(epgUrl) }
            if (ok && lastUrl == url) {
                // redesenha só as linhas visíveis, sem saltar para o topo nem tapar a lista com um aviso
                channelAdapter.notifyItemRangeChanged(0, channelAdapter.itemCount, "now")
                if (status.text.isNotEmpty() && !status.text.contains("guia")) status.text = "${status.text}  ·  guia carregado"
            }
        }
    }

    private fun isSeparator(name: String) =
        name.contains("###") || name.contains("===") || name.contains("***") || name.contains("---") || name.contains("▬▬") || name.contains("━━") || name.contains("•••") || name.contains("★★")

    private fun adultLocked() = Prefs.isKids || (Prefs.pin.isNotEmpty() && !adultUnlocked)

    private fun setGroups(input: List<ChannelGroup>) {
        val hidden = Prefs.hiddenGroups()
        val locked = adultLocked()
        val visible = input.filter { g -> g.title !in hidden && !(locked && adultRe.containsMatchIn(g.title)) }
            .map { g ->
                // os «canais» que são só separadores do fornecedor («##### CRIANÇAS #####») não são canais
                val real = g.channels.filter { c -> !isSeparator(c.name) && !(locked && g.title == "Todos" && adultRe.containsMatchIn(c.group)) }
                if (real.size == g.channels.size) g else ChannelGroup(g.title, real)
            }
            .filter { it.channels.isNotEmpty() || input.size <= 1 }
        baseGroups = visible
        // índice de pesquisa, logótipos genéricos (o mesmo ícone em muitos canais), tipo de conteúdo e país:
        // tudo calculado fora do ecrã, uma vez por lista
        val flat = LinkedHashMap<String, Channel>()
        for (g in visible) for (c in g.channels) flat.putIfAbsent(c.url, c)
        allChannels = flat.values.toList()
        normNames = emptyList()
        kinds = emptyMap()
        regions = emptyMap()
        typesView.visibility = View.GONE
        btnRegion?.visibility = View.GONE
        val snapshot = allChannels
        lifecycleScope.launch {
            class Calc(val names: List<String>, val generic: Set<String>, val k: Map<String, ChannelFacets.Kind>, val r: Map<String, String>)
            val c = withContext(Dispatchers.Default) {
                val k = HashMap<String, ChannelFacets.Kind>(snapshot.size * 2)
                val r = HashMap<String, String>()
                for (ch in snapshot) {
                    k[ch.url] = ChannelFacets.kind(ch)
                    val reg = ChannelFacets.region(ch)
                    if (reg.isNotEmpty()) r[ch.url] = reg
                }
                Calc(snapshot.map { Epg.norm(it.name) }, snapshot.groupingBy { it.logo }.eachCount().filter { it.key.isNotEmpty() && it.value >= 8 }.keys, k, r)
            }
            if (allChannels === snapshot && !isFinishing) {
                normNames = c.names
                kinds = c.k
                regions = c.r
                if (c.generic != channelAdapter.genericLogos) { channelAdapter.genericLogos = c.generic; channelAdapter.notifyDataSetChanged() }
                updateRegionButton()
                buildTypes()
                if (facetsActive()) { renderGroups(); status.text = facetLabel() + "  ·  " + com.hortencioflix.tv.data.Text.plural(facetCount(), "canal", "canais") }
            }
        }
        renderGroups()
    }

    private fun facetsActive() = kinds.isNotEmpty() && (kindFilter != null || regionFilter.isNotEmpty())

    private fun passes(c: Channel): Boolean {
        val k = kindFilter
        if (k != null && kinds[c.url] != k) return false
        if (regionFilter.isNotEmpty() && regions[c.url] != regionFilter) return false
        return true
    }

    private fun facetLabel(): String =
        listOfNotNull(kindFilter?.label, regionFilter.takeIf { it.isNotEmpty() }?.let { ChannelFacets.regionLabel(it) }).joinToString(" · ")

    private fun facetCount(): Int = allChannels.count { passes(it) }

    /** Grupos a mostrar: os do fornecedor, só com os canais do tipo/país escolhido (os grupos vazios desaparecem). */
    private fun renderGroups() {
        val recents = Prefs.recentChannels()
        val filtering = facetsActive()
        val vis: List<ChannelGroup> = if (!filtering) baseGroups else {
            val out = ArrayList<ChannelGroup>()
            for (g in baseGroups) {
                val l = g.channels.filter { passes(it) }
                if (l.isNotEmpty()) out.add(ChannelGroup(g.title, l))
            }
            if (out.size > 1 && out[0].title != "Todos") {
                val seen = HashSet<String>()
                out.add(0, ChannelGroup("Todos", out.flatMap { it.channels }.filter { seen.add(it.url) }))
            }
            out
        }
        val withRecents = vis.isNotEmpty() && recents.isNotEmpty() && !filtering
        val list = if (withRecents) listOf(ChannelGroup("🕘 Recentes", recents)) + vis else vis
        groups = list
        groupAdapter.select(-1)
        groupAdapter.update(list.map {
            val recent = it.title.startsWith("🕘")
            var label = if (recent) "Recentes" else ChannelFacets.groupLabel(it.title)
            // com um país escolhido, o código repetido em todos os grupos («PT · ») só faz ruído
            if (regionFilter.isNotEmpty()) label = label.removePrefix("$regionFilter · ")
            val n = com.hortencioflix.tv.data.Text.num(it.channels.size)
            Row(if (groupAdapterChip()) "$label  $n" else label, com.hortencioflix.tv.data.Text.plural(it.channels.size, "canal", "canais"), icon = if (recent) R.drawable.ic_history else 0)
        })
        shown = emptyList()
        currentGroup = emptyList()
        if (list.isEmpty()) showChannels(emptyList()) else showGroup(if (withRecents) 1 else 0)
    }

    private fun kindIcon(k: ChannelFacets.Kind) = when (k) {
        ChannelFacets.Kind.SPORT -> R.drawable.ic_sports
        ChannelFacets.Kind.MOVIES -> R.drawable.ic_movie
        ChannelFacets.Kind.SERIES -> R.drawable.ic_tv
        ChannelFacets.Kind.NEWS -> R.drawable.ic_news
        ChannelFacets.Kind.KIDS -> R.drawable.ic_kids
        ChannelFacets.Kind.DOCS -> R.drawable.ic_docs
        ChannelFacets.Kind.MUSIC -> R.drawable.ic_music
        ChannelFacets.Kind.ENT -> R.drawable.ic_ticket
        ChannelFacets.Kind.RELIGION -> R.drawable.ic_faith
        ChannelFacets.Kind.ADULT -> R.drawable.ic_lock
        ChannelFacets.Kind.OTHER -> R.drawable.ic_more
    }

    /** Fila «Tudo · Desporto · Filmes · Notícias…» com o número de canais de cada tipo (no país escolhido). */
    private fun buildTypes() {
        val all = ChannelFacets.Kind.values()
        val counts = IntArray(all.size)
        var total = 0
        val locked = adultLocked()
        for (c in allChannels) {
            if (regionFilter.isNotEmpty() && regions[c.url] != regionFilter) continue
            val k = kinds[c.url] ?: continue
            total++
            if (locked && k == ChannelFacets.Kind.ADULT) continue
            counts[k.ordinal]++
        }
        val present = all.filter { counts[it.ordinal] > 0 }
        val useful = !showingFavs && total >= 30 && present.count { it != ChannelFacets.Kind.OTHER } >= 2
        val cur = kindFilter
        if (!useful || (cur != null && counts[cur.ordinal] == 0)) kindFilter = null
        if (!useful) { typesView.visibility = View.GONE; typeKinds = emptyList(); return }
        typeKinds = listOf<ChannelFacets.Kind?>(null) + present
        typeAdapter.update(typeKinds.map { k ->
            if (k == null) Row("Tudo  " + com.hortencioflix.tv.data.Text.num(total), icon = R.drawable.ic_live)
            else Row(k.label + "  " + com.hortencioflix.tv.data.Text.num(counts[k.ordinal]), icon = kindIcon(k))
        })
        typeAdapter.select(typeKinds.indexOf(kindFilter))
        typesView.visibility = View.VISIBLE
    }

    private fun updateRegionButton() {
        val b = btnRegion ?: return
        val counts = HashMap<String, Int>()
        var known = 0
        for (c in allChannels) {
            val r = regions[c.url] ?: continue
            counts[r] = (counts[r] ?: 0) + 1
            known++
        }
        regionCounts = counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
        // só vale a pena quando a lista mistura países e a maior parte dos canais diz de onde é
        val useful = !showingFavs && allChannels.size >= 30 && regionCounts.size >= 2 && known * 5 >= allChannels.size
        if (!useful || (regionFilter.isNotEmpty() && regionFilter !in counts)) regionFilter = ""
        b.visibility = if (useful) View.VISIBLE else View.GONE
        b.text = if (regionFilter.isEmpty()) "País" else ChannelFacets.regionLabel(regionFilter)
        b.isSelected = regionFilter.isNotEmpty()
    }

    private fun regionMenu() {
        val num = { n: Int -> com.hortencioflix.tv.data.Text.num(n) }
        val labels = listOf("Todos os países  (" + num(allChannels.size) + ")") +
            regionCounts.map { (if (it.first == regionFilter) "✓ " else "") + ChannelFacets.regionLabel(it.first) + "  (" + num(it.second) + ")" }
        choiceDialog("País ou língua", labels) { w ->
            regionFilter = if (w == 0) "" else regionCounts.getOrNull(w - 1)?.first ?: ""
            if (lastUrl.isNotEmpty()) Prefs.put("iptvRegion:" + Integer.toHexString(lastUrl.hashCode()), regionFilter)
            onFacetsChanged()
        }
    }

    /** Mudou o tipo ou o país: refaz os chips, os grupos e a lista (a pesquisa escrita mantém-se, dentro do filtro). */
    private fun onFacetsChanged() {
        updateRegionButton()
        buildTypes()
        renderGroups()
        if (search.text.isEmpty()) {
            status.text = if (facetsActive()) facetLabel() + "  ·  " + com.hortencioflix.tv.data.Text.plural(facetCount(), "canal", "canais")
            else com.hortencioflix.tv.data.Text.plural(allChannels.size, "canal", "canais")
        }
    }

    /** Lista (com o programa a dar) ou mosaico (logótipos grandes). */
    private fun applyViewMode() {
        channelAdapter.grid = gridMode
        val span = maxOf(3, (resources.displayMetrics.widthPixels - dp(32)) / dp(118))
        channelsView.layoutManager = if (gridMode) androidx.recyclerview.widget.GridLayoutManager(this, span) else LinearLayoutManager(this)
        // volta a ligar o adaptador: as linhas do outro formato não são reaproveitadas
        channelsView.adapter = channelAdapter
        findViewById<android.widget.ImageView?>(R.id.btn_view)?.setImageResource(if (gridMode) R.drawable.ic_list else R.drawable.ic_grid)
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
        kindFilter = null
        regionFilter = ""
        val favs = Prefs.favoriteChannels()
        listName.text = "★ Favoritos"
        if (favs.isEmpty()) {
            status.text = if (touch) "Ainda não tens favoritos. Toca na estrela de um canal para o adicionar." else "Ainda não tens favoritos. Mantém OK premido num canal para o adicionar."
            setGroups(emptyList())
            return
        }
        status.text = com.hortencioflix.tv.data.Text.plural(favs.size, "canal favorito", "canais favoritos")
        allGroups = emptyList()
        setGroups(listOf(ChannelGroup("Favoritos", favs)))
        channelsView.post { channelsView.getChildAt(0)?.requestFocus() }
    }

    private fun loadList(name: String, url: String) {
        showingFavs = false
        kindFilter = null
        // o país escolhido fica guardado por lista (quem só quer os canais portugueses não escolhe de novo)
        regionFilter = Prefs.str("iptvRegion:" + Integer.toHexString(url.hashCode()))
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
                var loadErr: String? = null
                var netParsed: List<ChannelGroup>? = null
                if (text == null) {
                    status.text = "A carregar lista… (contas Xtream pela API, só os canais ao vivo)"
                    val res = withContext(Dispatchers.IO) { com.hortencioflix.tv.data.IptvLoader.load(url) }
                    if (res.m3uText != null && res.groups.isNotEmpty()) {
                        withContext(Dispatchers.IO) { try { cache.writeText(res.m3uText) } catch (e: Exception) {} }
                        text = res.m3uText
                        netParsed = res.groups
                    } else {
                        loadErr = res.error
                        if (cache.exists()) {
                            // Sem ligação / servidor em baixo: usar a cópia antiga.
                            text = withContext(Dispatchers.IO) { try { cache.readText() } catch (e: Exception) { null } }
                        }
                    }
                }
                val t = text
                parsed = netParsed ?: if (t == null) emptyList() else withContext(Dispatchers.Default) { Iptv.parse(t, 60000) }
                if (parsed.isEmpty() && loadErr != null) {
                    status.text = loadErr
                    retry.visibility = View.VISIBLE
                    return
                }
            }
            if (parsed.isEmpty()) {
                status.text = if (text == null) "Não foi possível descarregar a lista (link inválido ou servidor em baixo)." else "A lista não tem canais reconhecidos."
                retry.visibility = View.VISIBLE
                return
            }
            Prefs.put("iptvLast", url)
            Prefs.put("iptvLastName", name)
            val total = parsed.first().channels.size
            status.text = com.hortencioflix.tv.data.Text.plural(total, "canal", "canais") + if (touch) "" else "  ·  mantém OK premido para favoritos"
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
