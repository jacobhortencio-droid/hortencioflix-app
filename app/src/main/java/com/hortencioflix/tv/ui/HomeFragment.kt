package com.hortencioflix.tv.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.ActionItem
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.HomeData
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Stremio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : BrowseSupportFragment() {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val continueAdapter = ArrayObjectAdapter(CardPresenter())
    private val continueRow = ListRow(HeaderItem(1L, "Continuar a ver"), continueAdapter)
    private var loaded = false
    private var loading = false
    private var isTv = true

    private fun appTitle(suffix: String = ""): String? = if (isTv) getString(R.string.app_name) + suffix else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTv = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        title = appTitle()
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = requireContext().themeColor(R.attr.hfBgSolid)
        if (isTv) badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.logo_wordmark)
        searchAffordanceColor = requireContext().themeColor(R.attr.hfAccent)
        adapter = rowsAdapter
        // No telemóvel o título fica por cima dos cartazes ao deslizar: só o mostramos na TV.
        if (isTv) setOnSearchClickedListener { startActivity(Intent(requireContext(), SearchActivity::class.java)) }
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is MetaItem -> DetailsActivity.open(requireContext(), item)
                is ActionItem -> action(item.key)
            }
        }
        val actions = ArrayObjectAdapter(ActionPresenter())
        actions.add(ActionItem("search", "🔍  Pesquisar"))
        actions.add(ActionItem("live", "📺  TV ao vivo"))
        actions.add(ActionItem("explore", "🧭  Explorar"))
        if (Prefs.torboxKey.isNotEmpty()) actions.add(ActionItem("torbox", "⬇  TorBox"))
        actions.add(ActionItem("settings", "⚙  Definições"))
        actions.add(ActionItem("reload", "↻  Atualizar"))
        rowsAdapter.add(ListRow(HeaderItem(0L, "Menu"), actions))
        load(false)
    }

    override fun onResume() {
        super.onResume()
        if (loaded) refreshContinue()
    }

    private fun action(key: String) {
        val ctx = requireContext()
        when (key) {
            "search" -> startActivity(Intent(ctx, SearchActivity::class.java))
            "live" -> startActivity(Intent(ctx, IptvActivity::class.java))
            "settings" -> startActivity(Intent(ctx, SettingsActivity::class.java))
            "reload" -> load(true)
            "explore" -> CatalogActivity.explore(ctx)
            "torbox" -> startActivity(Intent(ctx, TorboxActivity::class.java))
            else -> if (key.startsWith("more:")) {
                val t = key.removePrefix("more:")
                Addons.catalogRefs[t]?.let { CatalogActivity.openRef(ctx, it, t) }
            }
        }
    }

    private fun refreshContinue() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                HomeData.continueItems()
            }
            continueAdapter.clear()
            continueAdapter.addAll(0, items)
            val idx = rowsAdapter.indexOf(continueRow)
            if (items.isEmpty() && idx >= 0) rowsAdapter.remove(continueRow)
            if (items.isNotEmpty() && idx < 0) rowsAdapter.add(1, continueRow)
        }
    }

    private fun showRows(rows: List<Pair<String, List<MetaItem>>>, lib: List<MetaItem>) {
        while (rowsAdapter.size() > 1) rowsAdapter.removeItems(1, rowsAdapter.size() - 1)
        var id = 10L
        // Início personalizado (Definições › Organizar o Início)
        val all = (if (lib.isNotEmpty()) listOf("A minha biblioteca" to lib) else emptyList()) + rows
        for ((t, items) in com.hortencioflix.tv.data.HomeLayout.apply(all)) {
            if (t == "A minha biblioteca" && items === lib) {
                val a = ArrayObjectAdapter(CardPresenter())
                a.addAll(0, lib)
                rowsAdapter.add(ListRow(HeaderItem(2L, t), a))
                continue
            }
            val sel = androidx.leanback.widget.ClassPresenterSelector()
            sel.addClassPresenter(MetaItem::class.java, CardPresenter())
            sel.addClassPresenter(ActionItem::class.java, ActionPresenter())
            val a = ArrayObjectAdapter(sel)
            a.addAll(0, items)
            if (Addons.catalogRefs[t] != null) a.add(ActionItem("more:$t", "Ver tudo ›"))
            rowsAdapter.add(ListRow(HeaderItem(id++, t), a))
        }
        refreshContinue()
    }

    private fun load(forceSync: Boolean) {
        if (loading) return
        loading = true
        title = appTitle("  ·  a carregar…")
        lifecycleScope.launch {
            try {
                if (!loaded && !forceSync) {
                    val cached = withContext(Dispatchers.IO) { HomeData.loadRows() }
                    if (cached.isNotEmpty()) showRows(cached, emptyList())
                }
                withContext(Dispatchers.IO) { HomeData.syncIfNeeded(forceSync) }
                val fresh = Addons.homeRows()
                if (fresh.isNotEmpty()) withContext(Dispatchers.IO) { HomeData.saveRows(fresh) }
                val rows = if (fresh.isEmpty()) withContext(Dispatchers.IO) { HomeData.loadRows() } else fresh
                val lib = withContext(Dispatchers.IO) { Stremio.libraryItems() }
                showRows(rows, lib)
                if (rows.isEmpty()) {
                    requireContext().toast("Sem catálogos. Verifica a ligação à Internet ou os add-ons nas Definições.")
                }
                loaded = true
                refreshContinue()
                val eps = withContext(Dispatchers.IO) { try { HomeData.newEpisodes() } catch (e: Exception) { emptyList() } }
                if (eps.isNotEmpty()) {
                    val a = ArrayObjectAdapter(CardPresenter())
                    a.addAll(0, eps)
                    rowsAdapter.add(minOf(2, rowsAdapter.size()), ListRow(HeaderItem(3L, "Novos episódios"), a))
                }
            } catch (e: Exception) {
                context?.toast("Erro ao carregar: ${e.message}")
            } finally {
                loading = false
                if (isAdded) title = appTitle()
            }
        }
    }
}
