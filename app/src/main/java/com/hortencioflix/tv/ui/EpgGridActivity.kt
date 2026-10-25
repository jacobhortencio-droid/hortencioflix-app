package com.hortencioflix.tv.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Channel
import com.hortencioflix.tv.data.Epg
import com.hortencioflix.tv.data.Session
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Guia TV em grelha (canais × horas), como numa box de operador.
 * Janela de 3 horas; ◀ ▶ (ou os botões) avançam/recuam uma hora. OK num programa a dar agora abre o canal.
 */
class EpgGridActivity : BaseActivity() {
    companion object {
        private var pending: List<Channel> = emptyList()

        fun start(ctx: Context, channels: List<Channel>) {
            pending = channels
            ctx.startActivity(Intent(ctx, EpgGridActivity::class.java))
        }
    }

    private val windowMs = 3 * 3600_000L
    private var winStart = 0L
    private var all: List<Channel> = emptyList()
    private var rows: List<Channel> = emptyList()
    private var onlyGuide = true
    private lateinit var list: RecyclerView
    private lateinit var label: TextView
    private lateinit var ruler: LinearLayout
    private val adapter = RowsAdapter()
    private var timelineWidth = 0
    private val chanWidth by lazy { dp(if (isTv) 170 else 110) }
    private val isTv by lazy { packageManager.hasSystemFeature("android.software.leanback") }
    private var focusRow = -1
    private var focusEdge = 0   // -1 = primeiro bloco, 1 = último

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        all = pending
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(R.attr.hfBg))
            setPadding(dp(16), dp(14), dp(16), dp(8))
        }
        // Cabeçalho: título, janela de tempo e botões
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "Guia TV"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(themeColor(R.attr.hfAccent))
        })
        label = TextView(this).apply { textSize = 15f; setTextColor(this@EpgGridActivity.themeColor(R.attr.hfMuted)); setPadding(dp(16), 0, 0, 0) }
        head.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun btn(t: String, act: () -> Unit) = TextView(this, null, 0, R.style.HfButton).apply { text = t; setOnClickListener { act() } }
        head.addView(btn("◀ 1 h") { shift(-1) })
        head.addView(btn("Agora") { goNow() })
        head.addView(btn("1 h ▶") { shift(1) })
        val filter = btn("") { onlyGuide = !onlyGuide; applyFilter() }
        head.addView(filter)
        filterBtn = filter
        root.addView(head)

        ruler = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(chanWidth, dp(8), 0, dp(4)) }
        root.addView(ruler)

        list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        root.post {
            timelineWidth = root.width - root.paddingLeft - root.paddingRight - chanWidth
            goNow()
            applyFilter()
        }
    }

    private var filterBtn: TextView? = null

    private fun applyFilter() {
        val withGuide = all.filter { Epg.hasGuide(it) }
        if (withGuide.isEmpty()) onlyGuide = false
        rows = if (onlyGuide) withGuide else all
        filterBtn?.text = if (onlyGuide) "Só com guia (${withGuide.size})" else "Todos (${all.size})"
        filterBtn?.visibility = if (withGuide.isEmpty() || withGuide.size == all.size) View.GONE else View.VISIBLE
        if (withGuide.isEmpty()) label.text = "Esta lista não tem guia de programação. Indica um guia (EPG) em Definições › TV ao vivo."
        adapter.notifyDataSetChanged()
        list.post { list.getChildAt(0)?.let { firstBlock(it as ViewGroup)?.requestFocus() } }
    }

    private fun goNow() {
        val c = Calendar.getInstance()
        c.set(Calendar.MINUTE, if (c.get(Calendar.MINUTE) >= 30) 30 else 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        winStart = c.timeInMillis
        refreshWindow()
    }

    private fun shift(hours: Int) {
        val now = System.currentTimeMillis()
        val next = winStart + hours * 3600_000L
        // O guia guarda só de 3 h atrás até ~14 h à frente.
        if (next < now - 3 * 3600_000L || next > now + 12 * 3600_000L) return
        winStart = next
        refreshWindow()
    }

    private fun refreshWindow() {
        val f = SimpleDateFormat("EEE HH:mm", Locale("pt", "PT"))
        val h = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (all.any { Epg.hasGuide(it) }) label.text = "${f.format(Date(winStart))} – ${h.format(Date(winStart + windowMs))}"
        ruler.removeAllViews()
        if (timelineWidth > 0) for (i in 0 until 6) {
            ruler.addView(TextView(this).apply {
                text = h.format(Date(winStart + i * 1_800_000L)); textSize = 12f
                setTextColor(this@EpgGridActivity.themeColor(R.attr.hfMuted))
            }, LinearLayout.LayoutParams(timelineWidth / 6, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        adapter.notifyDataSetChanged()
        if (focusRow >= 0) list.post {
            val vh = list.findViewHolderForAdapterPosition(focusRow) as? RowsAdapter.VH
            val row = vh?.timeline
            if (row != null) (if (focusEdge > 0) lastBlock(row) else firstBlock(row))?.requestFocus()
        }
    }

    private fun firstBlock(g: ViewGroup): View? {
        for (i in 0 until g.childCount) { val v = g.getChildAt(i); if (v.isFocusable) return v; if (v is ViewGroup) firstBlock(v)?.let { return it } }
        return null
    }

    private fun lastBlock(g: ViewGroup): View? {
        for (i in g.childCount - 1 downTo 0) { val v = g.getChildAt(i); if (v.isFocusable) return v; if (v is ViewGroup) lastBlock(v)?.let { return it } }
        return null
    }

    /** ▶ no último programa da janela avança uma hora; ◀ no primeiro recua. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val f = currentFocus
            val tag = f?.tag as? BlockTag
            if (tag != null) {
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && tag.last) { focusRow = tag.row; focusEdge = -1; shift(1); return true }
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && tag.first) { focusRow = tag.row; focusEdge = 1; shift(-1); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private data class BlockTag(val row: Int, val first: Boolean, val last: Boolean)

    private fun open(row: Int, prog: Epg.Prog?) {
        val ch = rows.getOrNull(row) ?: return
        val now = System.currentTimeMillis()
        val playNow = {
            val idx = all.indexOf(ch).coerceAtLeast(0)
            Session.live(all, idx)
            PlayerActivity.start(this)
        }
        if (prog == null || (prog.start <= now && prog.end > now)) { playNow(); return }
        val h = SimpleDateFormat("HH:mm", Locale.getDefault())
        val d = SimpleDateFormat("EEEE, d MMM", Locale("pt", "PT"))
        dialog().setTitle(prog.title)
            .setMessage("${ch.name}\n${d.format(Date(prog.start))} · ${h.format(Date(prog.start))}–${h.format(Date(prog.end))}" +
                (if (prog.desc.isNotEmpty()) "\n\n${prog.desc}" else "") +
                (if (prog.end <= now) "\n\nEste programa já terminou." else ""))
            .setPositiveButton("Ver o canal agora") { _, _ -> playNow() }
            .setNegativeButton("Fechar", null)
            .show()
    }

    inner class RowsAdapter : RecyclerView.Adapter<RowsAdapter.VH>() {
        inner class VH(val box: LinearLayout, val name: TextView, val timeline: FrameLayout) : RecyclerView.ViewHolder(box)

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (isTv) 58 else 52)) }
            val name = TextView(ctx).apply {
                textSize = 13f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
                setTextColor(ctx.themeColor(R.attr.hfText)); setPadding(dp(4), 0, dp(8), 0)
            }
            box.addView(name, LinearLayout.LayoutParams(chanWidth, ViewGroup.LayoutParams.MATCH_PARENT))
            val tl = FrameLayout(ctx)
            box.addView(tl, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            return VH(box, name, tl)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val ch = rows[position]
            h.name.text = ch.name
            h.timeline.removeAllViews()
            val w = timelineWidth.takeIf { it > 0 } ?: return
            val now = System.currentTimeMillis()
            val end = winStart + windowMs
            val progs = Epg.between(ch, winStart, end)
            if (progs.isEmpty()) {
                h.timeline.addView(block(position, null, "Sem guia — OK para ver o canal", false, true, true), FrameLayout.LayoutParams(w - dp(4), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(0, dp(3), 0, dp(3)) })
                return
            }
            progs.forEachIndexed { i, p ->
                val s = maxOf(p.start, winStart)
                val e = minOf(p.end, end)
                val x = ((s - winStart) * w / windowMs).toInt()
                val bw = maxOf(dp(6), ((e - s) * w / windowMs).toInt() - dp(3))
                val live = p.start <= now && p.end > now
                val v = block(position, p, p.title, live, i == 0, i == progs.size - 1)
                h.timeline.addView(v, FrameLayout.LayoutParams(bw, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(x, dp(3), 0, dp(3)) })
            }
            // Linha do «agora»
            if (now in winStart until end) {
                val x = ((now - winStart) * w / windowMs).toInt()
                h.timeline.addView(View(h.timeline.context).apply { setBackgroundColor(themeColor(R.attr.hfAccent)); isFocusable = false },
                    FrameLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(x, 0, 0, 0) })
            }
        }

        private fun block(row: Int, p: Epg.Prog?, title: String, live: Boolean, first: Boolean, last: Boolean): TextView {
            val ctx = this@EpgGridActivity
            val h = SimpleDateFormat("HH:mm", Locale.getDefault())
            return TextView(ctx).apply {
                text = if (p == null) title else "$title\n${h.format(Date(p.start))}"
                textSize = 12f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(6), 0)
                setTextColor(if (live) Color.WHITE else ctx.themeColor(R.attr.hfText))
                isFocusable = true; isClickable = true
                tag = BlockTag(row, first, last)
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(if (live) ColorUtilsMix.mix(ctx.themeColor(R.attr.hfPanel2), themeColor(R.attr.hfAccent), 0.35f) else ctx.themeColor(R.attr.hfPanel))
                }
                background = bg
                setOnFocusChangeListener { _, has ->
                    bg.setStroke(if (has) dp(2) else 0, if (has) Color.WHITE else Color.TRANSPARENT)
                    if (has) focusRow = row
                }
                setOnClickListener { open(row, p) }
            }
        }
    }

    private object ColorUtilsMix {
        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        )
    }
}
