package com.hortencioflix.tv.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.HomeData
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : BaseActivity() {
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var progress: View
    private lateinit var adapter: MetaGridAdapter
    private var job: Job? = null
    private var results: List<MetaItem> = emptyList()
    private var popular: List<MetaItem> = emptyList()
    private var filter = "all"
    private var lastQuery = ""
    private var cellWidth = 0
    private lateinit var chips: Map<String, TextView>
    private lateinit var grid: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        query = findViewById(R.id.query)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        val clear = findViewById<TextView>(R.id.clear)

        grid = findViewById(R.id.grid)
        val dm = resources.displayMetrics
        val avail = dm.widthPixels - dp(24)
        val span = maxOf(3, avail / dp(((if (dm.widthPixels > dm.heightPixels) 140 else 120) * Prefs.posterScale()).toInt()))
        cellWidth = avail / span
        adapter = MetaGridAdapter({ cellWidth }, { open(it) }, null, { q -> query.setText(q); query.setSelection(q.length); run(q, true) })
        val lm = GridLayoutManager(this, span)
        lm.spanSizeLookup = adapter.spanLookup(span)
        grid.layoutManager = lm
        grid.adapter = adapter
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
            }
        })

        chips = mapOf("all" to findViewById(R.id.f_all), "movie" to findViewById(R.id.f_movie), "series" to findViewById(R.id.f_series))
        for ((k, v) in chips) v.setOnClickListener { filter = k; render() }

        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                clear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                schedule(q)
            }
        })
        query.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                run(query.text.toString().trim(), true)
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
                true
            } else false
        }
        clear.setOnClickListener { query.setText("") }

        lifecycleScope.launch {
            popular = withContext(Dispatchers.IO) {
                val rows = HomeData.loadRows().filter { r -> r.second.any { it.type == "movie" || it.type == "series" } }
                val a = rows.getOrNull(0)?.second ?: emptyList()
                val b = rows.getOrNull(1)?.second ?: emptyList()
                val mixed = ArrayList<MetaItem>()
                for (i in 0 until maxOf(a.size, b.size)) { a.getOrNull(i)?.let { mixed.add(it) }; b.getOrNull(i)?.let { mixed.add(it) } }
                mixed.distinctBy { it.id }.take(30)
            }
            render()
        }
        render()
        query.requestFocus()
    }

    private fun open(m: MetaItem) {
        if (lastQuery.length >= 2) Prefs.addRecentSearch(lastQuery)
        DetailsActivity.open(this, m)
    }

    private fun schedule(q: String) {
        job?.cancel()
        if (q.length < 2) {
            lastQuery = ""
            results = emptyList()
            progress.visibility = View.GONE
            render()
            return
        }
        job = lifecycleScope.launch {
            delay(600)
            search(q)
        }
    }

    private fun run(q: String, remember: Boolean) {
        if (q.length < 2) return
        if (remember) Prefs.addRecentSearch(q)
        job?.cancel()
        job = lifecycleScope.launch { search(q) }
    }

    private suspend fun search(q: String) {
        lastQuery = q
        progress.visibility = View.VISIBLE
        status.visibility = View.GONE
        results = try { Addons.searchMerged(q) } catch (e: Exception) { emptyList() }
        progress.visibility = View.GONE
        render()
        grid.animateIn()
    }

    private fun render() {
        for ((k, v) in chips) v.isSelected = k == filter
        val e = ArrayList<Any>()
        fun pass(m: MetaItem) = filter == "all" || m.type == filter
        if (lastQuery.isEmpty()) {
            status.visibility = View.GONE
            val rec = Prefs.recentSearches()
            if (rec.isNotEmpty()) { e.add("Pesquisas recentes"); e.add(ChipsRow(rec)) }
            val pop = popular.filter { pass(it) }
            if (pop.isNotEmpty()) { e.add("Populares agora"); e.addAll(pop) }
        } else {
            val list = results.filter { pass(it) }
            status.visibility = View.VISIBLE
            status.text = if (list.isEmpty() && progress.visibility != View.VISIBLE) "🔍  Sem resultados para «$lastQuery». Tenta outro título ou outro nome." else "${list.size} resultados para «$lastQuery»"
            e.addAll(list)
        }
        adapter.entries = e
    }
}
