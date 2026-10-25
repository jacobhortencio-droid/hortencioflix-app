package com.hortencioflix.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Episode
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.SubFinder
import com.hortencioflix.tv.data.Session
import com.hortencioflix.tv.data.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filmes e séries de uma conta Xtream (com categorias). */
class XtreamActivity : BaseActivity() {
    companion object {
        fun open(ctx: Context, listUrl: String, name: String) =
            ctx.startActivity(Intent(ctx, XtreamActivity::class.java).putExtra("url", listUrl).putExtra("name", name))
    }

    private lateinit var xt: Xtream
    private var type = "movie"
    private var cats: List<Xtream.Category> = emptyList()
    private var cat: Xtream.Category? = null
    private var items: List<MetaItem> = emptyList()
    private var job: Job? = null
    private lateinit var adapter: MetaGridAdapter
    private lateinit var typesAdapter: RowAdapter
    private lateinit var catAdapter: RowAdapter
    private lateinit var progress: View
    private lateinit var empty: TextView
    private var cellWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)
        val x = Xtream.fromListUrl(intent.getStringExtra("url") ?: "")
        if (x == null) { toast("Esta lista não é uma conta Xtream."); finish(); return }
        xt = x
        findViewById<TextView>(R.id.cat_title).text = intent.getStringExtra("name") ?: "Xtream"
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        val grid = findViewById<RecyclerView>(R.id.grid)
        val dm = resources.displayMetrics
        val avail = dm.widthPixels - dp(24)
        val span = maxOf(3, avail / dp(((if (dm.widthPixels > dm.heightPixels) 140 else 120) * Prefs.posterScale()).toInt()))
        cellWidth = avail / span
        adapter = MetaGridAdapter({ cellWidth }, { onItem(it) })
        val lm = GridLayoutManager(this, span)
        lm.spanSizeLookup = adapter.spanLookup(span)
        grid.layoutManager = lm
        grid.adapter = adapter

        typesAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> type = r.tag as String; loadCats() })
        catAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> cat = r.tag as Xtream.Category?; chips(); loadItems() })
        for ((id, a) in listOf(R.id.types to typesAdapter, R.id.catalogs to catAdapter)) {
            val rv = findViewById<RecyclerView>(id)
            rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            rv.adapter = a
            rv.visibility = View.VISIBLE
        }
        loadCats()
    }

    private fun chips() {
        typesAdapter.update(listOf("movie" to "🎬 Filmes", "series" to "📺 Séries").map { (k, v) -> Row((if (k == type) "● " else "") + v, tag = k) })
        catAdapter.update(cats.map { c -> Row((if (c == cat) "● " else "") + c.name, tag = c) })
    }

    private fun loadCats() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            cats = withContext(Dispatchers.IO) { if (type == "movie") xt.vodCategories() else xt.seriesCategories() }
            cat = cats.firstOrNull()
            chips()
            loadItems()
        }
    }

    private fun loadItems() {
        job?.cancel()
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE
        adapter.entries = emptyList()
        val c = cat
        job = lifecycleScope.launch {
            items = withContext(Dispatchers.IO) { if (type == "movie") xt.vod(c?.id) else xt.series(c?.id) }
            progress.visibility = View.GONE
            adapter.entries = ArrayList<Any>(items)
            if (items.isEmpty()) { empty.text = "Sem conteúdo nesta categoria (ou a conta não inclui filmes/séries)."; empty.visibility = View.VISIBLE }
        }
    }

    private fun onItem(m: MetaItem) {
        if (m.id.startsWith("xtv:")) {
            val s = xt.streamFor(m.id) ?: return
            progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { try { kotlinx.coroutines.withTimeoutOrNull(7000) { SubFinder.find(m.name, "movie") } } catch (e: Exception) { null } }
                progress.visibility = View.GONE
                Session.vod(m, m.id, listOf(s), 0, found?.subs ?: emptyList(), emptyList(), Prefs.resumeFor(m.id, m.id))
                if (found != null && found.subs.isEmpty()) toast("Não encontrei legendas para «${found.title}».")
                PlayerActivity.start(this@XtreamActivity)
            }
            return
        }
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val eps = withContext(Dispatchers.IO) { xt.episodes(m.id.removePrefix("xts:")) }
            progress.visibility = View.GONE
            if (eps.isEmpty()) { toast("Esta série não tem episódios."); return@launch }
            val seasons = eps.map { it.season }.distinct()
            if (seasons.size == 1) pickEpisode(m, eps, seasons[0])
            else choiceDialog(m.name, seasons.map { "Temporada $it  ·  ${eps.count { e -> e.season == it }} episódios" }) { pickEpisode(m, eps, seasons[it]) }
        }
    }

    private fun pickEpisode(m: MetaItem, all: List<Episode>, season: Int) {
        val list = all.filter { it.season == season }
        choiceDialog("${m.name} · T$season", list.map { e -> (if (Prefs.isWatched(e.id)) "✓ " else "") + "E${e.episode}  ${e.name}" }) { i ->
            val ep = list[i]
            val s = xt.streamFor(ep.id) ?: return@choiceDialog
            progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { try { kotlinx.coroutines.withTimeoutOrNull(7000) { SubFinder.find(m.name, "series", ep.season, ep.episode) } } catch (e: Exception) { null } }
                progress.visibility = View.GONE
                Session.vod(m, ep.id, listOf(s), 0, found?.subs ?: emptyList(), all, Prefs.resumeFor(ep.id, ""))
                Session.directStream = { id -> xt.streamFor(id) }
                PlayerActivity.start(this@XtreamActivity)
            }
        }
    }
}
