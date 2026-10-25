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
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.MetaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** «Ver tudo» de um catálogo, ou Explorar (tipo, catálogo e género) com carregamento contínuo. */
class CatalogActivity : BaseActivity() {
    companion object {
        fun openRef(ctx: Context, ref: Addons.CatalogRef, title: String) =
            ctx.startActivity(Intent(ctx, CatalogActivity::class.java).putExtra("ref", ref).putExtra("title", title))

        fun explore(ctx: Context, type: String = "movie") =
            ctx.startActivity(Intent(ctx, CatalogActivity::class.java).putExtra("explore", type))
    }

    private var ref: Addons.CatalogRef? = null
    private var explore = false
    private var type = "movie"
    private var catalogs: List<Addons.CatalogRef> = emptyList()
    private var genre: String? = null
    private val items = ArrayList<MetaItem>()
    private var skip = 0
    private var loading = false
    private var ended = false
    private var job: Job? = null
    private lateinit var adapter: MetaGridAdapter
    private lateinit var progress: View
    private lateinit var empty: TextView
    private lateinit var titleView: TextView
    private lateinit var typesAdapter: RowAdapter
    private lateinit var catAdapter: RowAdapter
    private lateinit var genreAdapter: RowAdapter
    private var cellWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)
        titleView = findViewById(R.id.cat_title)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val grid = findViewById<RecyclerView>(R.id.grid)
        val dm = resources.displayMetrics
        val avail = dm.widthPixels - dp(24)
        val span = maxOf(3, avail / dp(((if (dm.widthPixels > dm.heightPixels) 140 else 120) * Prefs.posterScale()).toInt()))
        cellWidth = avail / span
        adapter = MetaGridAdapter({ cellWidth }, { DetailsActivity.open(this, it) })
        val lm = GridLayoutManager(this, span)
        lm.spanSizeLookup = adapter.spanLookup(span)
        grid.layoutManager = lm
        grid.adapter = adapter
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (lm.findLastVisibleItemPosition() >= items.size - span * 2) loadMore()
            }
        })

        typesAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> type = r.tag as String; loadCatalogs() })
        catAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> ref = r.tag as Addons.CatalogRef; genre = null; refreshChips(); reset() })
        genreAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> genre = r.tag as String?; refreshChips(); reset() })
        for ((id, a) in listOf(R.id.types to typesAdapter, R.id.catalogs to catAdapter, R.id.genres to genreAdapter)) {
            val rv = findViewById<RecyclerView>(id)
            rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            rv.adapter = a
            rv.chipStyle()
            rv.setHasFixedSize(false)
        }

        @Suppress("DEPRECATION")
        val r = intent.getSerializableExtra("ref") as? Addons.CatalogRef
        val ex = intent.getStringExtra("explore")
        if (ex != null) {
            explore = true
            type = ex
            titleView.text = "Explorar"
            findViewById<View>(R.id.types).visibility = View.VISIBLE
            loadCatalogs()
        } else if (r != null) {
            ref = r
            genre = r.fixedGenre
            titleView.text = intent.getStringExtra("title") ?: r.name
            refreshChips()
            reset()
        } else finish()
    }

    private fun loadCatalogs() {
        lifecycleScope.launch {
            catalogs = Addons.browseCatalogs(type)
            ref = catalogs.firstOrNull()
            genre = null
            refreshChips()
            reset()
        }
    }

    private fun refreshChips() {
        if (explore) {
            typesAdapter.update(listOf("movie" to "Filmes", "series" to "Séries").map { (k, v) -> Row((if (k == type) "● " else "") + v, tag = k) })
            val showCats = catalogs.size > 1
            findViewById<View>(R.id.catalogs).visibility = if (showCats) View.VISIBLE else View.GONE
            catAdapter.update(catalogs.map { c ->
                val label = c.name.ifEmpty { c.id } + (if (catalogs.count { it.name == c.name } > 1) " · ${c.addonName}" else "")
                Row((if (c == ref) "● " else "") + label, tag = c)
            })
        }
        val g = ref?.genres ?: emptyList()
        val showGenres = ref?.supportsGenre == true && g.isNotEmpty()
        findViewById<View>(R.id.genres).visibility = if (showGenres) View.VISIBLE else View.GONE
        if (showGenres) genreAdapter.update(listOf(Row((if (genre == null) "● " else "") + "Todos", tag = null)) + g.map { Row((if (it == genre) "● " else "") + it, tag = it) })
    }

    private fun reset() {
        job?.cancel()
        items.clear()
        skip = 0
        ended = false
        loading = false
        adapter.entries = emptyList()
        loadMore()
    }

    private fun loadMore() {
        val r = ref ?: run { empty.text = "Não há catálogos deste tipo nos teus add-ons."; empty.visibility = View.VISIBLE; return }
        if (loading || ended) return
        loading = true
        empty.visibility = View.GONE
        if (items.isEmpty()) progress.visibility = View.VISIBLE
        job = lifecycleScope.launch {
            val page = try { Addons.catalogPage(r, genre ?: r.fixedGenre, skip) } catch (e: Exception) { emptyList() }
            progress.visibility = View.GONE
            val fresh = page.filter { m -> items.none { it.id == m.id } }
            if (fresh.isEmpty() || !r.supportsSkip) ended = true
            skip += page.size
            items.addAll(fresh)
            val first = skip == page.size
            adapter.entries = ArrayList<Any>(items)
            if (first) findViewById<RecyclerView>(R.id.grid).animateIn()
            if (items.isEmpty()) { empty.text = "Sem resultados."; empty.visibility = View.VISIBLE }
            loading = false
        }
    }
}
