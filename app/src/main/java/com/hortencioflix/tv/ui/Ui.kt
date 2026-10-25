package com.hortencioflix.tv.ui

import android.app.AlertDialog
import com.hortencioflix.tv.data.Prefs
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hortencioflix.tv.R

/** Evita abrir o mesmo ecrã duas vezes com dois toques rápidos. */
object Debounce {
    private val last = HashMap<String, Long>()
    fun ok(key: String, ms: Long = 800): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - (last[key] ?: 0L) < ms) return false
        last[key] = now
        return true
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Cor de um atributo do tema (fundo/painel mudam no modo AMOLED). */
fun Context.themeColor(attr: Int): Int {
    val tv = android.util.TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

/** Linha de filtros: margem final, sem animações de item e desvanecer na margem para mostrar que há mais. */
fun androidx.recyclerview.widget.RecyclerView.chipStyle() {
    clipToPadding = false
    setPadding(paddingLeft, paddingTop, context.dp(24), paddingBottom)
    itemAnimator = null
    isHorizontalFadingEdgeEnabled = true
    setFadingEdgeLength(context.dp(28))
    overScrollMode = android.view.View.OVER_SCROLL_NEVER
}

/** Faz os itens de uma lista entrarem em cascata (fade + subida). */
fun androidx.recyclerview.widget.RecyclerView.animateIn() {
    val anim = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.hf_item_in)
    val c = android.view.animation.LayoutAnimationController(anim, 0.06f)
    layoutAnimation = c
    scheduleLayoutAnimation()
}

/** Ecrã de abertura animado: símbolo com brilho, nome a revelar-se e saída em zoom. */
fun android.app.Activity.showSplash() {
    // Respeita «reduzir animações» do Android: sem abertura animada.
    val scale = try { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) } catch (e: Exception) { 1f }
    if (scale == 0f) return
    val root = window.decorView as? android.view.ViewGroup ?: return
    val overlay = android.widget.FrameLayout(this)
    overlay.setBackgroundColor(themeColor(R.attr.hfBgSolid))
    overlay.isClickable = true
    val center = android.view.Gravity.CENTER

    val glow = View(this)
    glow.background = android.graphics.drawable.GradientDrawable().apply {
        gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
        gradientRadius = dp(150).toFloat()
        val ac = themeColor(R.attr.hfAccent)
        colors = intArrayOf((ac and 0x00FFFFFF) or 0x88000000.toInt(), ac and 0x00FFFFFF)
    }
    overlay.addView(glow, android.widget.FrameLayout.LayoutParams(dp(320), dp(320), center).apply { bottomMargin = dp(70) })

    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.gravity = android.view.Gravity.CENTER_HORIZONTAL
    val mark = ImageView(this)
    mark.setImageResource(R.drawable.logo_mark)
    box.addView(mark, LinearLayout.LayoutParams(dp(112), dp(112)))
    val word = ImageView(this)
    word.setImageResource(R.drawable.logo_wordmark)
    word.adjustViewBounds = true
    box.addView(word, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { topMargin = dp(18) })
    val tag = TextView(this)
    tag.text = "Filmes  ·  Séries  ·  TV ao vivo"
    tag.textSize = 13f
    tag.letterSpacing = 0.12f
    tag.setTextColor(this.themeColor(R.attr.hfMuted))
    box.addView(tag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
    overlay.addView(box, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, center))
    root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

    // Estado inicial
    glow.alpha = 0f; glow.scaleX = 0.5f; glow.scaleY = 0.5f
    mark.alpha = 0f; mark.scaleX = 0.3f; mark.scaleY = 0.3f; mark.rotation = -25f
    tag.alpha = 0f
    word.post { word.clipBounds = android.graphics.Rect(0, 0, 0, word.height) }

    mark.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setDuration(650)
        .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
    glow.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f).setStartDelay(150).setDuration(900).start()
    val reveal = android.animation.ValueAnimator.ofFloat(0f, 1f)
    reveal.startDelay = 380
    reveal.duration = 560
    reveal.interpolator = android.view.animation.DecelerateInterpolator()
    reveal.addUpdateListener { v -> word.clipBounds = android.graphics.Rect(0, 0, (word.width * (v.animatedValue as Float)).toInt(), word.height) }
    reveal.start()
    tag.animate().alpha(1f).setStartDelay(850).setDuration(400).start()

    overlay.postDelayed({
        mark.animate().scaleX(1.5f).scaleY(1.5f).setDuration(380).start()
        overlay.animate().alpha(0f).setDuration(380).withEndAction { root.removeView(overlay) }.start()
    }, 1650)
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun Context.dialog(): AlertDialog.Builder = when (Prefs.themeMode) {
    "light" -> AlertDialog.Builder(this, R.style.HfDialog_Light)
    "glass" -> GlassAlertBuilder(this)
    else -> AlertDialog.Builder(this, R.style.HfDialog)
}

/** Diálogos do tema Vidro: o ecrã por trás fica desfocado (Android 12 ou mais recente). */
class GlassAlertBuilder(ctx: Context) : AlertDialog.Builder(ctx, R.style.HfDialog_Glass) {
    override fun create(): AlertDialog {
        val d = super.create()
        if (android.os.Build.VERSION.SDK_INT >= 31) d.window?.let { w ->
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            w.attributes = w.attributes.apply { blurBehindRadius = context.dp(20) }
        }
        return d
    }
}

fun Context.inputDialog(title: String, hint: String, initial: String, password: Boolean = false, onOk: (String) -> Unit) {
    val et = EditText(this)
    et.hint = hint
    et.setText(initial)
    et.setSingleLine(true)
    if (password) et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    else et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    val box = LinearLayout(this)
    box.setPadding(dp(24), dp(8), dp(24), 0)
    box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    dialog().setTitle(title).setView(box)
        .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
        .setNegativeButton("Cancelar", null)
        .show()
}

fun Context.choiceDialog(title: String, items: List<String>, onPick: (Int) -> Unit) {
    if (Prefs.themeMode == "glass" && this is android.app.Activity) { glassChoice(title, items, onPick); return }
    dialog().setTitle(title).setItems(items.toTypedArray()) { _, which -> onPick(which) }
        .setNegativeButton("Fechar", null).show()
}

fun formatTime(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

/** Linha genérica para listas (fontes, episódios, canais, definições). */
data class Row(val title: String, val subtitle: String = "", val image: String = "", val tag: Any? = null)

/** Marca de cabeçalho de secção numa lista de [Row]. */
object Header

fun header(title: String) = Row(title, tag = Header)

class RowAdapter(
    items: List<Row>,
    private val chip: Boolean = false,
    private val onClick: (Int, Row) -> Unit,
    private val onLong: ((Int, Row) -> Unit)? = null,
    private val onFocus: ((Int, Row) -> Unit)? = null
) : RecyclerView.Adapter<RowAdapter.VH>() {

    var items: List<Row> = items
        private set

    fun update(list: List<Row>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.row_title)
        val subtitle: TextView? = v.findViewById(R.id.row_subtitle)
        val image: ImageView? = v.findViewById(R.id.row_image)
        val initials: TextView? = v.findViewById(R.id.row_initials)
    }

    /** Avatar com as iniciais (canais sem logótipo), com cor estável por nome. */
    private fun showInitials(h: VH, name: String) {
        val tv = h.initials ?: return
        val words = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        tv.text = when {
            words.isEmpty() -> "TV"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
        val palette = intArrayOf(0xFFE3122B.toInt(), 0xFFB00D21.toInt(), 0xFFFFB319.toInt(), 0xFF7C3AED.toInt(), 0xFF0EA5E9.toInt(), 0xFF22C55E.toInt(), 0xFFF97316.toInt())
        val c = palette[(name.hashCode() and 0x7fffffff) % palette.size]
        tv.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = tv.context.dp(8).toFloat(); setColor(c) }
        tv.visibility = View.VISIBLE
    }

    override fun getItemViewType(position: Int): Int = if (items[position].tag === Header) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 1) R.layout.item_header else if (chip) R.layout.item_chip else R.layout.item_row
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.title.text = r.title
        if (r.tag === Header) return
        h.subtitle?.let {
            it.text = r.subtitle
            it.visibility = if (r.subtitle.isEmpty()) View.GONE else View.VISIBLE
        }
        h.initials?.visibility = View.GONE
        h.image?.let {
            if (r.image == "#") {
                it.visibility = View.GONE
                showInitials(h, r.title)
            } else if (r.image.isEmpty()) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                it.load(r.image) {
                    crossfade(true)
                    listener(onError = { _, _ -> it.visibility = View.GONE; showInitials(h, r.title) })
                }
            }
        }
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0) onClick(p, items[p])
        }
        h.itemView.setOnLongClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0 && onLong != null) { onLong.invoke(p, items[p]); true } else false
        }
        h.itemView.setOnFocusChangeListener { _, has ->
            val p = h.bindingAdapterPosition
            if (has && p >= 0) onFocus?.invoke(p, items[p])
        }
    }
}
