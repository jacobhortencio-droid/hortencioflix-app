package com.hortencioflix.tv.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hortencioflix.tv.R

/** Entradas do ecrã de Definições. */
sealed class SEntry { abstract val id: String }

/** Estado de uma conta/serviço no cartão do topo: 0 = desligado, 1 = bem, 2 = atenção. */
data class SPill(val key: String, val text: String, val state: Int)

data class SHero(val name: String, val sub: String, val pills: List<SPill>, val actions: List<Pair<String, String>>) : SEntry() { override val id = "hero" }
data class SCat(val key: String, val icon: Int, val title: String, val summary: String) : SEntry() { override val id = "cat:$key" }
data class SItem(val key: String, val title: String, val desc: String, val switch: Boolean?, val section: String = "") : SEntry() { override val id = "item:$key" }
data class SLabel(val text: String) : SEntry() { override val id = "label:$text" }

/**
 * Definições em dois níveis: um cartão com o estado das contas + categorias; dentro de cada categoria,
 * as opções (as de ligar/desligar têm interruptor). Só redesenha o que mudou (DiffUtil), por isso mudar
 * uma opção não faz a lista saltar.
 */
class SettingsAdapter(
    private val onHero: (String) -> Unit,
    private val onCat: (String) -> Unit,
    private val onItem: (String) -> Unit
) : ListAdapter<SEntry, RecyclerView.ViewHolder>(DIFF) {
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SEntry>() {
            override fun areItemsTheSame(a: SEntry, b: SEntry) = a.id == b.id
            override fun areContentsTheSame(a: SEntry, b: SEntry) = a == b
        }
    }

    fun isWide(position: Int) = getItem(position) !is SCat

    override fun getItemViewType(position: Int) = when (getItem(position)) { is SHero -> 0; is SCat -> 1; is SItem -> 2; is SLabel -> 3 }

    private class VH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return VH(when (viewType) {
            0 -> inf.inflate(R.layout.item_setting_hero, parent, false)
            1 -> inf.inflate(R.layout.item_setting_cat, parent, false)
            2 -> inf.inflate(R.layout.item_setting, parent, false)
            else -> inf.inflate(R.layout.item_header, parent, false)
        })
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        val v = h.itemView
        val ctx = v.context
        when (val e = getItem(position)) {
            is SHero -> {
                val accent = ctx.themeColor(R.attr.hfAccent)
                v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf((accent and 0x00FFFFFF) or 0x59000000, ctx.themeColor(R.attr.hfPanel))).apply {
                    cornerRadius = ctx.dp(20).toFloat()
                    setStroke(ctx.dp(1), ctx.themeColor(R.attr.hfCardStroke))
                }
                v.findViewById<TextView>(R.id.hero_avatar).text = e.name.trim().take(1).uppercase().ifEmpty { "P" }
                v.findViewById<TextView>(R.id.hero_name).text = e.name
                v.findViewById<TextView>(R.id.hero_sub).text = e.sub
                v.findViewById<View>(R.id.hero_profile_row).setOnClickListener { onHero("profile") }
                val pills = v.findViewById<LinearLayout>(R.id.hero_pills)
                pills.removeAllViews()
                for (p in e.pills) pills.addView(pill(ctx, p))
                val actions = v.findViewById<LinearLayout>(R.id.hero_actions)
                actions.removeAllViews()
                for ((key, label) in e.actions) actions.addView(action(ctx, key, label))
            }
            is SCat -> {
                v.findViewById<ImageView>(R.id.cat_icon).apply {
                    setImageResource(e.icon)
                    imageTintList = ColorStateList.valueOf(ctx.themeColor(R.attr.hfAccent))
                }
                v.findViewById<TextView>(R.id.cat_title).text = e.title
                v.findViewById<TextView>(R.id.cat_summary).text = e.summary
                v.setOnClickListener { onCat(e.key) }
            }
            is SItem -> {
                v.findViewById<TextView>(R.id.set_title).text = e.title
                v.findViewById<TextView>(R.id.set_desc).apply { text = e.desc; visibility = if (e.desc.isEmpty()) View.GONE else View.VISIBLE }
                v.findViewById<TextView>(R.id.set_section).apply { text = e.section; visibility = if (e.section.isEmpty()) View.GONE else View.VISIBLE }
                v.findViewById<Switch>(R.id.set_switch).apply {
                    visibility = if (e.switch == null) View.GONE else View.VISIBLE
                    if (e.switch != null) {
                        val accent = ctx.themeColor(R.attr.hfAccent)
                        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            thumbTintList = ColorStateList(states, intArrayOf(accent, 0xFFBDBDBD.toInt()))
                            trackTintList = ColorStateList(states, intArrayOf((accent and 0x00FFFFFF) or 0x80000000.toInt(), 0x55FFFFFF))
                        }
                        isChecked = e.switch
                    }
                }
                v.setOnClickListener { onItem(e.key) }
            }
            is SLabel -> (v as TextView).text = e.text
        }
    }

    private fun pill(ctx: android.content.Context, p: SPill): View {
        val t = TextView(ctx)
        t.text = p.text
        t.textSize = 12f
        t.setTextColor(ctx.themeColor(R.attr.hfText))
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setPadding(ctx.dp(10), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        t.background = GradientDrawable().apply { cornerRadius = ctx.dp(15).toFloat(); setColor(ctx.themeColor(R.attr.hfOverlay)) }
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(when (p.state) { 1 -> 0xFF22C55E.toInt(); 2 -> 0xFFFFB319.toInt(); else -> 0x66FFFFFF })
            setSize(ctx.dp(8), ctx.dp(8))
        }
        dot.setBounds(0, 0, ctx.dp(8), ctx.dp(8))
        t.setCompoundDrawablesRelative(dot, null, null, null)
        t.compoundDrawablePadding = ctx.dp(7)
        t.isClickable = true
        t.isFocusable = true
        t.setOnClickListener { onHero(p.key) }
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ctx.dp(8) }
        return t
    }

    private fun action(ctx: android.content.Context, key: String, label: String): View {
        val t = TextView(ctx)
        t.text = label
        t.textSize = 13f
        t.setTextColor(0xFFFFFFFF.toInt())
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setPadding(ctx.dp(14), ctx.dp(8), ctx.dp(14), ctx.dp(8))
        t.background = GradientDrawable().apply { cornerRadius = ctx.dp(18).toFloat(); setColor(ctx.themeColor(R.attr.hfAccent)) }
        t.isClickable = true
        t.isFocusable = true
        t.setOnClickListener { onHero(key) }
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ctx.dp(8) }
        return t
    }
}
