package com.hortencioflix.tv.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.RoundedCornersTransformation
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.HomeData
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Stremio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- Cartaz (usado nas filas e na grelha) ----------------

class PosterVH(v: View) : RecyclerView.ViewHolder(v) {
    val frame: FrameLayout = v.findViewById(R.id.poster_frame)
    val image: ImageView = v.findViewById(R.id.poster_image)
    val fallback: TextView = v.findViewById(R.id.poster_fallback)
    val progress: ProgressBar = v.findViewById(R.id.poster_progress)
    val title: TextView = v.findViewById(R.id.poster_title)
    val sub: TextView = v.findViewById(R.id.poster_sub)
    val badge: TextView = v.findViewById(R.id.poster_badge)

    init {
        v.setOnFocusChangeListener { view, f ->
            title.setTextColor(view.context.themeColor(if (f) R.attr.hfText else R.attr.hfMuted))
        }
    }

    fun bind(m: MetaItem, cellWidth: Int, onClick: (MetaItem) -> Unit, onLong: ((MetaItem) -> Unit)? = null) {
        val ctx = itemView.context
        if (cellWidth > 0) {
            val lp = itemView.layoutParams
            lp.width = cellWidth - ctx.dp(8)
            itemView.layoutParams = lp
            val flp = frame.layoutParams
            flp.height = ((cellWidth - ctx.dp(8)) * 1.5f).toInt()
            frame.layoutParams = flp
        } else {
            val k = com.hortencioflix.tv.data.Prefs.posterScale()
            val lp = itemView.layoutParams
            lp.width = ctx.dp((112 * k).toInt())
            itemView.layoutParams = lp
            val flp = frame.layoutParams
            flp.height = ctx.dp((168 * k).toInt())
            frame.layoutParams = flp
        }
        val skeleton = m.id.startsWith("sk:")
        title.text = if (skeleton) "" else m.name
        fallback.text = if (skeleton) "" else m.name
        val rating = Regex("IMDb (\\d+(?:\\.\\d)?)").find(m.info)?.groupValues?.get(1)
        val q = Regex("\\b(4K|1080p|720p)\\b").find(m.info)?.value
        val b = listOfNotNull(q, rating?.let { "★ $it" }).joinToString(" ")
        badge.text = b
        badge.visibility = if (b.isEmpty() || skeleton) View.GONE else View.VISIBLE
        itemView.alpha = 1f
        if (skeleton) {
            itemView.animate().cancel()
            itemView.alpha = 0.5f
            itemView.animate().alpha(1f).setDuration(700).withEndAction { itemView.animate().alpha(0.5f).setDuration(700).start() }.start()
        }
        image.setImageDrawable(null)
        if (m.poster.isNotEmpty()) image.load(m.poster) {
            crossfade(true)
            transformations(RoundedCornersTransformation(ctx.dp(8).toFloat()))
        }
        if (m.progress > 0f) {
            progress.visibility = View.VISIBLE
            progress.progress = (m.progress * 100).toInt().coerceAtLeast(3)
        } else {
            progress.visibility = View.GONE
        }
        if (m.progress > 0f && m.info.isNotEmpty()) {
            sub.text = m.info
            sub.visibility = View.VISIBLE
        } else sub.visibility = View.GONE
        itemView.setOnClickListener { onClick(m) }
        if (onLong != null) itemView.setOnLongClickListener { onLong(m); true } else itemView.setOnLongClickListener(null)
    }
}

/** Linha de etiquetas (pesquisas recentes) numa grelha. */
data class ChipsRow(val items: List<String>)

/** Grelha de cartazes com cabeçalhos (String), cartazes (MetaItem) e etiquetas (ChipsRow). */
class MetaGridAdapter(
    private val cellWidth: () -> Int,
    private val onClick: (MetaItem) -> Unit,
    private val onLong: ((MetaItem) -> Unit)? = null,
    private val onChip: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var entries: List<Any> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    fun spanLookup(span: Int) = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int) = if (entries.getOrNull(position) is MetaItem) 1 else span
    }

    override fun getItemViewType(position: Int) = when (entries[position]) { is String -> 0; is ChipsRow -> 2; else -> 1 }
    override fun getItemCount() = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        return when (viewType) {
            0 -> {
                val tv = TextView(ctx)
                tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                tv.setPadding(ctx.dp(4), ctx.dp(16), 0, ctx.dp(8))
                tv.setTextColor(ctx.themeColor(R.attr.hfText))
                tv.textSize = 17f
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
                object : RecyclerView.ViewHolder(tv) {}
            }
            2 -> {
                val rv = RecyclerView(ctx)
                rv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rv.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
                object : RecyclerView.ViewHolder(rv) {}
            }
            else -> PosterVH(LayoutInflater.from(ctx).inflate(R.layout.item_poster, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val e = entries[position]) {
            is String -> {
                val tv = holder.itemView as TextView
                tv.text = e
                val big = position == 0 && e == "Biblioteca"
                tv.textSize = if (big) 26f else 17f
                tv.typeface = androidx.core.content.res.ResourcesCompat.getFont(tv.context, R.font.hf_display)
                tv.setPadding(tv.paddingLeft, tv.context.dp(if (big) 24 else 16), 0, tv.context.dp(8))
            }
            is ChipsRow -> {
                val rv = holder.itemView as RecyclerView
                rv.chipStyle()
                rv.adapter = RowAdapter(e.items.map { Row(it) }, chip = true, onClick = { _, r -> onChip?.invoke(r.title) })
            }
            is MetaItem -> (holder as PosterVH).bind(e, cellWidth(), onClick, onLong)
        }
    }
}

class PosterAdapter(private var items: List<MetaItem>, private val onLong: ((MetaItem) -> Unit)? = null, private val onClick: (MetaItem) -> Unit) : RecyclerView.Adapter<PosterVH>() {
    fun update(list: List<MetaItem>) { items = list; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PosterVH(LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: PosterVH, position: Int) = h.bind(items[position], 0, onClick, onLong)
}

/** Pergunta se quer retirar o título de «Continuar a ver». */
fun continueMenu(ctx: android.content.Context, m: MetaItem, done: () -> Unit) {
    ctx.dialog().setTitle(m.name)
        .setItems(arrayOf("▶  Continuar a ver", "✕  Remover de «Continuar a ver»")) { _, w ->
            if (w == 0) ctx.startActivity(Intent(ctx, DetailsActivity::class.java).putExtra("meta", m).putExtra("play", true))
            else {
                Thread { HomeData.removeContinue(m.id) }.start()
                done()
            }
        }.show()
}

private fun Fragment.openMeta(m: MetaItem, play: Boolean = false) {
    val ctx = context ?: return
    if (m.id.startsWith("iptv:")) { val a = activity; if (a != null && IptvVod.resume(a, m)) return }
    ctx.startActivity(Intent(ctx, DetailsActivity::class.java).putExtra("meta", m).putExtra("play", play))
}

// ---------------- Início (telemóvel) ----------------

class MobileHomeFragment : Fragment() {
    companion object {
        const val CONTINUE = "Continuar a ver"
        const val NEW_EPS = "Novos episódios"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var hero: List<MetaItem> = emptyList()
    private var heroIndex = 0
    private var continueItems: List<MetaItem> = emptyList()
    private var library: List<MetaItem> = emptyList()
    private var newEps: List<MetaItem> = emptyList()
    private var traktWatch: List<MetaItem> = emptyList()
    private var traktRecs: List<MetaItem> = emptyList()
    private var catalogs: List<Pair<String, List<MetaItem>>> = emptyList()
    private var heroHolder: HeroVH? = null
    private val pool = RecyclerView.RecycledViewPool()
    private lateinit var adapter: HomeAdapter
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var loadingView: View
    private var loaded = false
    private var rowsView: RecyclerView? = null
    private var animatedOnce = false

    private val rotate = object : Runnable {
        override fun run() {
            if (hero.size > 1) {
                heroIndex = (heroIndex + 1) % hero.size
                heroHolder?.bind(hero[heroIndex])
            }
            handler.postDelayed(this, 8000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_mobile_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refresh = view.findViewById(R.id.refresh)
        loadingView = view.findViewById(R.id.loading)
        val ctx = requireContext()
        refresh.setColorSchemeColors(ctx.themeColor(R.attr.hfAccent))
        refresh.setProgressBackgroundColorSchemeColor(ctx.themeColor(R.attr.hfPanel))
        refresh.setColorSchemeColors(ctx.themeColor(R.attr.hfAccent), ContextCompat.getColor(ctx, R.color.gold))
        refresh.setOnRefreshListener { load(true) }
        val rows = view.findViewById<RecyclerView>(R.id.rows)
        rows.layoutManager = LinearLayoutManager(ctx)
        adapter = HomeAdapter()
        rows.adapter = adapter
        rowsView = rows
        adapter.rebuild()
        load(false)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(rotate)
        handler.postDelayed(rotate, 8000)
        if (loaded) refreshContinue()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(rotate)
    }

    private fun refreshContinue() {
        viewLifecycleOwner.lifecycleScope.launch {
            continueItems = withContext(Dispatchers.IO) { HomeData.continueItems() }
            adapter.rebuild()
        }
    }

    private fun load(force: Boolean) {
        loadingView.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (catalogs.isEmpty() && !force) {
                    // Mostrar logo o que ficou guardado da última vez.
                    val cached = withContext(Dispatchers.IO) { HomeData.loadRows() to HomeData.continueItems() }
                    if (cached.first.isNotEmpty()) {
                        loaded = true
                        catalogs = cached.first
                        continueItems = cached.second
                        hero = catalogs.first().second.take(6)
                        adapter.rebuild()
                        loadingView.visibility = View.GONE
                    }
                }
                withContext(Dispatchers.IO) { HomeData.syncIfNeeded(force) }
                val rows = Addons.homeRows()
                if (rows.isNotEmpty()) withContext(Dispatchers.IO) { HomeData.saveRows(rows) }
                val lists = withContext(Dispatchers.IO) { HomeData.continueItems() to Stremio.libraryItems() }
                continueItems = lists.first
                library = lists.second
                if (!animatedOnce) { animatedOnce = true; rowsView?.animateIn() }
                if (rows.isNotEmpty() || catalogs.isEmpty()) {
                    catalogs = rows
                    hero = rows.firstOrNull()?.second?.take(6) ?: emptyList()
                    heroIndex = 0
                }
                adapter.rebuild()
                loaded = true
                launch {
                    newEps = withContext(Dispatchers.IO) { try { HomeData.newEpisodes() } catch (e: Exception) { emptyList() } }
                    if (newEps.isNotEmpty()) adapter.rebuild()
                }
                if (com.hortencioflix.tv.data.Trakt.linked) launch {
                    val r = withContext(Dispatchers.IO) {
                        try { com.hortencioflix.tv.data.Trakt.watchlist() to com.hortencioflix.tv.data.Trakt.recommendations() } catch (e: Exception) { emptyList<MetaItem>() to emptyList() }
                    }
                    traktWatch = r.first; traktRecs = r.second
                    if (traktWatch.isNotEmpty() || traktRecs.isNotEmpty()) adapter.rebuild()
                }
                if (rows.isEmpty()) context?.toast(if (catalogs.isEmpty()) "Sem catálogos. Verifica a Internet ou os add-ons nas Definições." else "Sem ligação — a mostrar os catálogos guardados.")
            } catch (e: Exception) {
                context?.toast("Erro ao carregar: ${e.message}")
            } finally {
                refresh.isRefreshing = false
                loadingView.visibility = View.GONE
            }
        }
    }

    // Entradas: hero (null) + filas (título, itens)
    private inner class HomeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var sections: List<Pair<String, List<MetaItem>>> = emptyList()

        fun rebuild() {
            val s = ArrayList<Pair<String, List<MetaItem>>>()
            if (catalogs.isEmpty() && !loaded) {
                // Esqueletos enquanto carrega
                for (r in 0 until 3) s.add("" to (0 until 6).map { MetaItem("sk:$r$it", "", "", "") })
                sections = s
                notifyDataSetChanged()
                return
            }
            if (continueItems.isNotEmpty()) s.add(CONTINUE to continueItems)
            if (newEps.isNotEmpty()) s.add(NEW_EPS to newEps)
            if (traktWatch.isNotEmpty()) s.add("Trakt · para ver" to traktWatch)
            if (traktRecs.isNotEmpty()) s.add("Trakt · recomendados para ti" to traktRecs)
            if (library.isNotEmpty()) s.add("A minha biblioteca" to library)
            s.addAll(catalogs)
            // Início personalizado (Definições › Organizar o Início): filas escondidas e ordem
            sections = com.hortencioflix.tv.data.HomeLayout.apply(s)
            notifyDataSetChanged()
        }

        private fun hasHero() = hero.isNotEmpty()
        private fun top() = if (hasHero()) 2 else 1

        override fun getItemViewType(position: Int) = when {
            hasHero() && position == 0 -> 0
            position == top() - 1 -> 2
            else -> 1
        }
        override fun getItemCount() = sections.size + top()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            if (viewType == 0) {
                val v = inf.inflate(R.layout.item_hero, parent, false)
                val dm = parent.resources.displayMetrics
                val h = minOf((dm.widthPixels * 0.62f).toInt(), (dm.heightPixels * 0.62f).toInt())
                v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(h, parent.context.dp(300)))
                return HeroVH(v).also { heroHolder = it }
            }
            if (viewType == 2) {
                val rv = RecyclerView(parent.context)
                rv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rv.setPadding(parent.context.dp(16), parent.context.dp(12), 0, 0)
                rv.chipStyle()
                rv.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
                val chips = arrayListOf(Row("🧭  Explorar", tag = "movie"), Row("🎬  Filmes", tag = "movie"), Row("📺  Séries", tag = "series"))
                if (com.hortencioflix.tv.data.Prefs.torboxKey.isNotEmpty()) chips.add(Row("⬇  TorBox", tag = "torbox"))
                rv.adapter = RowAdapter(chips, chip = true, onClick = { _, r ->
                    if (r.tag == "torbox") parent.context.startActivity(Intent(parent.context, TorboxActivity::class.java))
                    else CatalogActivity.explore(parent.context, r.tag as String)
                })
                return object : RecyclerView.ViewHolder(rv) {}
            }
            val v = inf.inflate(R.layout.item_row_section, parent, false)
            val list = v.findViewById<RecyclerView>(R.id.section_list)
            list.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
            list.setRecycledViewPool(pool)
            return SectionVH(v)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeroVH) {
                heroHolder = holder
                if (hero.isNotEmpty()) holder.bind(hero[heroIndex % hero.size])
            } else if (holder is SectionVH) {
                val idx = position - top()
                val (t, items) = sections[idx]
                holder.bind(t, items)
            }
        }
    }

    private inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.section_title)
        val list: RecyclerView = v.findViewById(R.id.section_list)
        val more: TextView = v.findViewById(R.id.section_more)
        fun bind(t: String, items: List<MetaItem>) {
            title.text = t
            val ref = Addons.catalogRefs[t]
            more.visibility = if (ref != null) View.VISIBLE else View.GONE
            more.setOnClickListener { if (ref != null) CatalogActivity.openRef(requireContext(), ref, t) }
            val isContinue = t == CONTINUE
            // Adaptador novo por fila: o comportamento muda consoante a fila.
            list.adapter = if (isContinue) PosterAdapter(items, onLong = { m ->
                continueMenu(requireContext(), m) {
                    continueItems = continueItems.filter { it.id != m.id }
                    adapter.rebuild()
                }
            }) { openMeta(it, play = true) } else if (t == NEW_EPS) PosterAdapter(items) { openMeta(it) } else PosterAdapter(items) { openMeta(it) }
            list.scrollToPosition(0)
        }
    }

    private fun heroGo(i: Int) {
        if (i !in hero.indices || i == heroIndex) return
        heroIndex = i
        heroHolder?.bind(hero[heroIndex])
        handler.removeCallbacks(rotate)
        handler.postDelayed(rotate, 8000)
    }

    private fun heroStep(dir: Int) {
        if (hero.size < 2) return
        heroIndex = (heroIndex + dir + hero.size) % hero.size
        heroHolder?.bind(hero[heroIndex])
        handler.removeCallbacks(rotate)
        handler.postDelayed(rotate, 8000)
    }

    private inner class HeroVH(v: View) : RecyclerView.ViewHolder(v) {
        val dots: android.widget.LinearLayout = v.findViewById(R.id.hero_dots)
        init {
            val det = android.view.GestureDetector(v.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                    if (kotlin.math.abs(vx) > kotlin.math.abs(vy) && kotlin.math.abs(vx) > 600) { heroStep(if (vx < 0) 1 else -1); return true }
                    return false
                }
            })
            v.setOnTouchListener { _, ev -> det.onTouchEvent(ev); false }
        }
        val image: ImageView = v.findViewById(R.id.hero_image)
        val title: TextView = v.findViewById(R.id.hero_title)
        val info: TextView = v.findViewById(R.id.hero_info)
        val desc: TextView = v.findViewById(R.id.hero_desc)
        fun bind(m: MetaItem) {
            val bg = when {
                m.background.isNotEmpty() -> m.background
                m.id.startsWith("tt") -> "https://images.metahub.space/background/medium/${m.id}/img"
                else -> m.poster
            }
            image.load(bg) { crossfade(600) }
            // Efeito «Ken Burns»: zoom lento enquanto o destaque está visível.
            image.animate().cancel()
            image.scaleX = 1f; image.scaleY = 1f
            image.animate().scaleX(1.09f).scaleY(1.09f).setDuration(9000).setInterpolator(android.view.animation.LinearInterpolator()).start()
            // Texto entra com um pequeno deslize
            for ((i, t) in listOf(title, info, desc).withIndex()) {
                t.alpha = 0f; t.translationY = itemView.context.dp(10).toFloat()
                t.animate().alpha(1f).translationY(0f).setStartDelay(80L * i).setDuration(380).start()
            }
            dots.removeAllViews()
            if (hero.size > 1) for (i in hero.indices) {
                val d = View(itemView.context)
                val sel = hero[i].id == m.id
                val w = itemView.context.dp(if (sel) 24 else 8)
                d.layoutParams = android.widget.LinearLayout.LayoutParams(w, itemView.context.dp(8)).apply { marginStart = itemView.context.dp(6) }
                d.setOnClickListener { heroGo(i) }
                d.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = itemView.context.dp(4).toFloat()
                    setColor(if (sel) itemView.context.themeColor(R.attr.hfAccent) else 0x88FFFFFF.toInt())
                }
                dots.addView(d)
            }
            title.text = m.name
            info.text = listOf(if (m.type == "series") "Série" else "Filme", m.info).filter { it.isNotEmpty() }.joinToString("   ·   ")
            desc.text = m.description
            itemView.findViewById<View>(R.id.hero_play).setOnClickListener { openMeta(m, play = true) }
            itemView.findViewById<View>(R.id.hero_more).setOnClickListener { openMeta(m) }
            itemView.setOnClickListener { openMeta(m) }
        }
    }
}

// ---------------- Biblioteca (telemóvel) ----------------

class LibraryFragment : Fragment() {
    private lateinit var grid: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: MetaGridAdapter
    private var cellWidth = 0
    private var contIds: Set<String> = emptySet()
    private var filter = "Tudo"
    private var sortAz = false
    private var cont: List<MetaItem> = emptyList()
    private var lib: List<MetaItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx)
        grid = RecyclerView(ctx)
        grid.clipToPadding = false
        grid.setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(16))
        root.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        empty = TextView(ctx)
        empty.setTextColor(ctx.themeColor(R.attr.hfMuted))
        empty.textSize = 15f
        empty.setPadding(ctx.dp(32), 0, ctx.dp(32), 0)
        empty.gravity = android.view.Gravity.CENTER
        empty.visibility = View.GONE
        root.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val dm = resources.displayMetrics
        val avail = dm.widthPixels - ctx.dp(24)
        val span = maxOf(3, avail / ctx.dp((120 * com.hortencioflix.tv.data.Prefs.posterScale()).toInt()))
        cellWidth = avail / span
        adapter = MetaGridAdapter({ cellWidth }, { m -> openMeta(m, play = m.id in contIds) }, { m ->
            if (m.id in contIds) continueMenu(ctx, m) { reload() }
        }, { label -> if (label.startsWith("↕")) sortAz = !sortAz else filter = label.removePrefix("● "); render() })
        val lm = GridLayoutManager(ctx, span)
        lm.spanSizeLookup = adapter.spanLookup(span)
        grid.layoutManager = lm
        grid.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { HomeData.continueItems() to Stremio.libraryItems() }
            cont = r.first
            lib = r.second
            contIds = cont.map { it.id }.toSet()
            render()
            if (cont.isEmpty() && lib.isEmpty()) {
                empty.visibility = View.VISIBLE
                empty.text = if (Stremio.loggedIn) "📚\n\nAinda não tens nada aqui.\nToca em «+ Biblioteca» num filme ou série para o guardar."
                else "📚\n\nLiga a conta Stremio em Definições para veres aqui a tua biblioteca e o que estavas a ver."
            } else empty.visibility = View.GONE
        }
    }

    private fun render() {
        val tabs = listOf("Tudo", "Filmes", "Séries", "Em curso")
        fun pass(m: MetaItem) = when (filter) { "Filmes" -> m.type == "movie"; "Séries" -> m.type == "series"; else -> true }
        val e = ArrayList<Any>()
        e.add("Biblioteca")
        e.add(ChipsRow(tabs.map { if (it == filter) "● $it" else it } + (if (sortAz) "↕ A–Z" else "↕ Recentes")))
        val c = cont.filter { pass(it) }
        val l0 = if (filter == "Em curso") emptyList() else lib.filter { it.id !in contIds && pass(it) }
        val l = if (sortAz) l0.sortedBy { it.name.lowercase() } else l0
        if (c.isNotEmpty()) { e.add("Continuar a ver"); e.addAll(c) }
        if (l.isNotEmpty()) { e.add("Guardados no Stremio (${l.size})"); e.addAll(l) }
        adapter.entries = e
    }
}
