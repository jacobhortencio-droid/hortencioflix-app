package com.hortencioflix.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Channel
import com.hortencioflix.tv.data.ChannelName
import com.hortencioflix.tv.data.Epg
import com.hortencioflix.tv.data.XtreamNow

/**
 * Lista de canais. Tudo é calculado ao desenhar cada linha (nome limpo, selos, programa atual), por isso
 * uma lista de 20 000 canais abre na hora: só as ~10 linhas visíveis dão trabalho.
 */
class ChannelAdapter(
    private val touch: Boolean,
    private val onClick: (Int, Channel) -> Unit,
    private val onLong: (Channel) -> Unit,
    private val onFav: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    var items: List<Channel> = emptyList()
        private set
    var favorites: Set<String> = emptySet()
    var dead: Set<String> = emptySet()
    /** Logótipos repetidos por muitos canais (o ícone genérico do fornecedor): mostram-se as iniciais. */
    var genericLogos: Set<String> = emptySet()
    /** Mostrar o grupo por baixo do nome (listas misturadas: «Todos», pesquisa, recentes, favoritos). */
    var showGroup = true
    /** Mosaico (cartões com o logótipo grande) em vez de lista. */
    var grid = false
    /** País já escolhido no filtro: o selo repetido em todos os canais («PT») não aparece. */
    var hideRegion = ""
    /** Quantas versões (SD/HD/FHD/4K, cópias) tem cada canal mostrado — endereço do canal → total. */
    var variantCount: Map<String, Int> = emptyMap()

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    /** Volta a desenhar o canal com este endereço (chegou o programa atual, mudou o favorito). */
    fun refresh(url: String, rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val h = rv.getChildViewHolder(rv.getChildAt(i)) as? VH ?: continue
            if (h.url == url && h.bindingAdapterPosition >= 0) notifyItemChanged(h.bindingAdapterPosition, "now")
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.ch_logo)
        val initials: TextView = v.findViewById(R.id.ch_initials)
        val name: TextView = v.findViewById(R.id.ch_name)
        val badges: TextView = v.findViewById(R.id.ch_badges)
        val now: TextView = v.findViewById(R.id.ch_now)
        val progress: ProgressBar = v.findViewById(R.id.ch_progress)
        val fav: ImageView = v.findViewById(R.id.ch_fav)
        var url = ""
    }

    override fun getItemViewType(position: Int) = if (grid) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(if (viewType == 1) R.layout.item_channel_card else R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(h, position) else bindNow(h, items[position])
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val ch = items[position]
        h.url = ch.url
        val clean = ChannelName.of(ch.name)
        h.name.text = clean.name
        val extra = (variantCount[ch.url] ?: 1) - 1
        val tags = clean.badges.filter { it != hideRegion }.joinToString(" · ") + (if (extra > 0) (if (clean.badges.any { it != hideRegion }) "  " else "") + "+$extra" else "")
        h.badges.text = tags
        h.badges.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        // logótipo real, ou iniciais com cor estável
        initials(h, clean.name)
        h.logo.dispose()
        h.logo.setImageDrawable(null)
        if (ch.logo.isNotEmpty() && ch.logo !in genericLogos) {
            h.logo.visibility = View.VISIBLE
            h.logo.load(ch.logo) {
                crossfade(true)
                listener(onSuccess = { _, _ -> h.initials.visibility = View.INVISIBLE }, onError = { _, _ -> h.logo.visibility = View.GONE; h.initials.visibility = View.VISIBLE })
            }
        } else h.logo.visibility = View.GONE
        bindNow(h, ch)
        h.itemView.alpha = if (ch.url in dead) 0.5f else 1f
        h.itemView.setOnClickListener { val p = h.bindingAdapterPosition; if (p >= 0) onClick(p, items[p]) }
        h.itemView.setOnLongClickListener { val p = h.bindingAdapterPosition; if (p >= 0) onLong(items[p]); true }
        if (touch) h.fav.setOnClickListener { val p = h.bindingAdapterPosition; if (p >= 0) onFav(items[p]) }
    }

    /** Programa a dar agora (do guia, ou da conta Xtream) e estado do favorito. */
    private fun bindNow(h: VH, ch: Channel) {
        val ctx = h.itemView.context
        val isFav = ch.url in favorites
        // no telemóvel a estrela é um botão; na TV (e no mosaico) só aparece nos favoritos, como marca
        h.fav.visibility = if (isFav || (touch && !grid)) View.VISIBLE else View.GONE
        h.fav.isClickable = touch
        h.fav.setImageResource(if (isFav) R.drawable.ic_star else R.drawable.ic_star_outline)
        h.fav.imageTintList = android.content.res.ColorStateList.valueOf(if (isFav) 0xFFFFC107.toInt() else ctx.themeColor(R.attr.hfMuted))
        val t = System.currentTimeMillis()
        val epg = Epg.nowNext(ch).first
        val x = if (epg == null) XtreamNow.cached(ch.url) else null
        val title = epg?.title ?: x?.title
        val start = epg?.start ?: x?.start ?: 0L
        val end = epg?.end ?: x?.end ?: 0L
        val off = if (ch.url in dead) "offline  ·  " else ""
        if (title != null && end > start) {
            h.now.text = if (grid) off + title else off + title + "  ·  até " + Epg.hhmm(end)
            h.now.setTextColor(ctx.themeColor(R.attr.hfText))
            h.progress.visibility = View.VISIBLE
            h.progress.progress = (((t - start) * 100) / (end - start)).toInt().coerceIn(2, 100)
        } else {
            val g = if (showGroup && !grid) com.hortencioflix.tv.data.ChannelFacets.groupLabel(ch.group) else ""
            h.now.text = off + g
            h.now.setTextColor(ctx.themeColor(R.attr.hfMuted))
            // no mosaico a linha fica sempre (vazia), para os cartões terem todos a mesma altura
            h.now.visibility = if ((off + g).isEmpty() && !grid) View.GONE else View.VISIBLE
            h.progress.visibility = View.GONE
            return
        }
        h.now.visibility = View.VISIBLE
    }

    private fun initials(h: VH, name: String) {
        val words = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        h.initials.text = when {
            words.isEmpty() -> "TV"
            words.size == 1 -> words[0].take(3).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
        val palette = intArrayOf(0xFFE3122B.toInt(), 0xFFB00D21.toInt(), 0xFFFFB319.toInt(), 0xFF7C3AED.toInt(), 0xFF0EA5E9.toInt(), 0xFF22C55E.toInt(), 0xFFF97316.toInt())
        val c = palette[(name.hashCode() and 0x7fffffff) % palette.size]
        h.initials.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = h.itemView.context.dp(8).toFloat(); setColor(c) }
        h.initials.visibility = View.VISIBLE
    }
}
