package com.hortencioflix.tv.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Channel
import com.hortencioflix.tv.data.Epg
import com.hortencioflix.tv.data.Prefs

/**
 * Menus em vidro («liquid glass»): painel translúcido com o vídeo desfocado por trás (Android 12+),
 * cantos arredondados e um contorno claro. Em versões antigas fica um vidro fosco mais opaco.
 */
object Glass {
    val blur get() = Build.VERSION.SDK_INT >= 31

    fun panel(ctx: Context, radiusDp: Int = 28): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (blur) intArrayOf(Color.parseColor("#47FFFFFF"), Color.parseColor("#1AFFFFFF"))
        else intArrayOf(Color.parseColor("#F02A2A33"), Color.parseColor("#F0141419"))
    ).apply {
        cornerRadius = ctx.dp(radiusDp).toFloat()
        setStroke(ctx.dp(1), Color.parseColor("#55FFFFFF"))
    }

    /** Fundo das linhas: transparente; ao focar/tocar fica vidro mais claro. */
    fun rowBg(ctx: Context, accent: Boolean = false): StateListDrawable {
        fun shape(c: String) = GradientDrawable().apply { cornerRadius = ctx.dp(16).toFloat(); setColor(Color.parseColor(c)) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape("#40FFFFFF"))
            addState(intArrayOf(android.R.attr.state_pressed), shape("#33FFFFFF"))
            addState(intArrayOf(), shape(if (accent) "#1FFFFFFF" else "#00000000"))
        }
    }

    /** Janela de vidro com o conteúdo dado. gravity END = painel lateral. */
    fun dialog(a: Activity, content: View, widthPx: Int, heightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT, gravity: Int = Gravity.CENTER): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        d.window?.let { w ->
            w.setBackgroundDrawable(panel(a))
            w.setLayout(widthPx, heightPx)
            w.setGravity(gravity)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(if (blur) 0.15f else 0.35f)
            if (blur) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = a.dp(18) }
                w.setBackgroundBlurRadius(a.dp(36))
            }
            if (gravity and Gravity.END == Gravity.END) w.attributes = w.attributes.apply { x = a.dp(16) }
            // mantém o ecrã inteiro (sem barras do sistema) enquanto o menu está aberto
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).let { c ->
                c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        return d
    }

    fun title(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(10))
    }
}

/** Escolha numa lista, em vidro (substitui o choiceDialog no leitor). */
fun Activity.glassChoice(title: String, items: List<String>, onPick: (Int) -> Unit) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(12)) }
    box.addView(Glass.title(this, title))
    val list = RecyclerView(this)
    list.layoutManager = LinearLayoutManager(this)
    var dlg: Dialog? = null
    list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = object : RecyclerView.ViewHolder(TextView(p.context).apply {
            textSize = 16f; setTextColor(Color.WHITE); isFocusable = true; isClickable = true
            setPadding(dp(16), dp(13), dp(16), dp(13)); background = Glass.rowBg(context)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }) {}
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            (h.itemView as TextView).text = items[pos]
            h.itemView.setOnClickListener { dlg?.dismiss(); onPick(pos) }
        }
    }
    box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    val w = minOf(resources.displayMetrics.widthPixels - dp(48), dp(520))
    val maxH = (resources.displayMetrics.heightPixels * 0.86).toInt()
    val wanted = dp(80) + items.size * dp(50)
    dlg = Glass.dialog(this, box, w, minOf(maxH, wanted))
    dlg.show()
    list.post { list.getChildAt(0)?.requestFocus() }
}

/**
 * Lista de canais do leitor: painel de vidro lateral com pesquisa, favoritos e o que está a dar agora.
 * OK/toque abre o canal; manter premido põe/tira dos favoritos.
 */
object GlassChannelPicker {
    fun show(a: Activity, channels: List<Channel>, current: Int, onPick: (Int) -> Unit) {
        val ctx = a
        val favUrls = HashSet<String>().apply { val f = Prefs.favorites(); for (i in 0 until f.length()) f.optJSONObject(i)?.optString("u")?.let { add(it) } }
        var onlyFavs = false
        var query = ""
        var shown: List<Int> = channels.indices.toList()

        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(18), a.dp(18), a.dp(18), a.dp(14)) }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = Glass.title(ctx, "Canais")
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val favChip = TextView(ctx).apply {
            textSize = 14f; setTextColor(Color.WHITE); isFocusable = true; isClickable = true
            setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8)); background = Glass.rowBg(ctx, accent = true)
        }
        head.addView(favChip)
        box.addView(head)

        val search = EditText(ctx).apply {
            hint = "Procurar canal ou número…"
            setHintTextColor(Color.parseColor("#B3FFFFFF")); setTextColor(Color.WHITE); textSize = 16f
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12))
            background = GradientDrawable().apply { cornerRadius = a.dp(16).toFloat(); setColor(Color.parseColor("#26FFFFFF")); setStroke(a.dp(1), Color.parseColor("#33FFFFFF")) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = a.dp(10)
        }
        box.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = a.dp(10) })

        val list = RecyclerView(ctx)
        list.layoutManager = LinearLayoutManager(ctx)
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        var dlg: Dialog? = null
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = shown.size
            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(p.context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                    setPadding(a.dp(12), a.dp(9), a.dp(12), a.dp(9))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                row.addView(TextView(p.context).apply { textSize = 14f; setTextColor(Color.parseColor("#B3FFFFFF")); gravity = Gravity.END }, LinearLayout.LayoutParams(a.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = a.dp(10) })
                row.addView(ImageView(p.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(a.dp(44), a.dp(32)).apply { marginEnd = a.dp(12) })
                val texts = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(TextView(p.context).apply { textSize = 16f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                texts.addView(TextView(p.context).apply { textSize = 13f; setTextColor(Color.parseColor("#CCFFFFFF")); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                return object : RecyclerView.ViewHolder(row) {}
            }
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val i = shown[pos]
                val ch = channels[i]
                val row = h.itemView as LinearLayout
                val num = row.getChildAt(0) as TextView
                val logo = row.getChildAt(1) as ImageView
                val texts = row.getChildAt(2) as LinearLayout
                val name = texts.getChildAt(0) as TextView
                val sub = texts.getChildAt(1) as TextView
                num.text = "${i + 1}"
                logo.setImageDrawable(null)
                if (ch.logo.isNotEmpty()) logo.load(ch.logo) { crossfade(true) }
                name.text = (if (i == current) "▶  " else "") + (if (ch.url in favUrls) "★ " else "") + ch.name
                val (now, _) = Epg.nowNext(ch)
                sub.text = now?.let { "${it.title} · até ${Epg.hhmm(it.end)}" } ?: ch.group
                sub.visibility = if (sub.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                row.background = Glass.rowBg(a, accent = i == current)
                row.setOnClickListener { dlg?.dismiss(); onPick(i) }
                row.setOnLongClickListener {
                    val on = Prefs.toggleFavorite(ch)
                    if (on) favUrls.add(ch.url) else favUrls.remove(ch.url)
                    a.toast(if (on) "★ ${ch.name} nos favoritos" else "${ch.name} removido dos favoritos")
                    notifyItemChanged(pos)
                    true
                }
            }
        }
        list.adapter = adapter

        fun refilter() {
            val q = Epg.norm(query.trim())
            shown = channels.indices.filter { i ->
                val ch = channels[i]
                (!onlyFavs || ch.url in favUrls) &&
                    (q.isEmpty() || Epg.norm(ch.name).contains(q) || (q.all { it.isDigit() } && (i + 1).toString().startsWith(q)) || Epg.norm(ch.group).contains(q))
            }
            title.text = if (shown.size == channels.size) "Canais (${channels.size})" else "Canais (${shown.size} de ${channels.size})"
            favChip.text = if (onlyFavs) "★ Favoritos ✓" else "★ Favoritos"
            favChip.visibility = if (favUrls.isEmpty() && !onlyFavs) View.GONE else View.VISIBLE
            adapter.notifyDataSetChanged()
        }
        favChip.setOnClickListener { onlyFavs = !onlyFavs; refilter() }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); refilter() }
        })
        search.setOnEditorActionListener { _, _, _ -> list.post { list.getChildAt(0)?.requestFocus() }; true }
        // Comando: ▼ na pesquisa vai para a lista
        search.setOnKeyListener { _, code, ev -> if (code == KeyEvent.KEYCODE_DPAD_DOWN && ev.action == KeyEvent.ACTION_DOWN) { list.getChildAt(0)?.requestFocus(); true } else false }
        refilter()

        val dm = a.resources.displayMetrics
        val landscape = dm.widthPixels > dm.heightPixels
        val w = if (landscape) minOf((dm.widthPixels * 0.46).toInt(), a.dp(560)) else dm.widthPixels - a.dp(24)
        val h = (dm.heightPixels * (if (landscape) 0.92 else 0.8)).toInt()
        dlg = Glass.dialog(a, box, w, h, if (landscape) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER)
        dlg.show()
        val start = shown.indexOf(current).coerceAtLeast(0)
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(start, a.dp(80))
        list.post { list.findViewHolderForAdapterPosition(start)?.itemView?.requestFocus() }
    }
}
