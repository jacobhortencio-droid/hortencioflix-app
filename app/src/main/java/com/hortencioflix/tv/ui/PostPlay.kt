package com.hortencioflix.tv.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.MetaItem

/**
 * «A seguir» no fim de um filme (quando começam os créditos): o vídeo encolhe para o canto e aparece a
 * recomendação principal em grande, com contagem para começar sozinha, e mais sugestões por baixo.
 * «Ver os créditos» (ou Voltar) devolve o vídeo ao ecrã inteiro.
 */
class PostPlay(
    private val a: Activity,
    private val video: View,
    private val onPlay: (MetaItem) -> Unit,
    private val onDismiss: () -> Unit
) {
    private var root: FrameLayout? = null
    private var countdown = -1
    private var countView: TextView? = null
    private var top: MetaItem? = null
    var visible = false; private set

    private fun dp(v: Int) = a.dp(v)

    private fun art(m: MetaItem): String =
        m.background.ifEmpty { if (m.id.startsWith("tt")) "https://images.metahub.space/background/medium/${m.id.substringBefore(':')}/img" else m.poster }

    fun show(recs: List<MetaItem>) {
        if (visible || recs.isEmpty()) return
        visible = true
        top = recs.first()
        val parent = a.findViewById<ViewGroup>(android.R.id.content)
        val r = FrameLayout(a).apply {
            isClickable = true
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x66000000, 0xF2000000.toInt()))
            alpha = 0f
        }
        root = r

        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val colLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginStart = (a.resources.displayMetrics.widthPixels * 0.40f).toInt(); marginEnd = dp(28)
        }
        r.addView(col, colLp)

        col.addView(TextView(a).apply {
            text = "A SEGUIR"; setTextColor(a.themeColor(R.attr.hfAccent)); textSize = 13f; letterSpacing = 0.15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val m = recs.first()
        val hero = ImageView(a).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(art(m)) { crossfade(true); transformations(RoundedCornersTransformation(dp(14).toFloat())) }
        }
        col.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { height = ((a.resources.displayMetrics.widthPixels * 0.6f - dp(28)) * 9 / 16 * 0.62f).toInt(); topMargin = dp(8) })
        col.addView(TextView(a).apply {
            text = m.name; setTextColor(Color.WHITE); textSize = 24f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2; setPadding(0, dp(10), 0, 0)
        })
        if (m.info.isNotEmpty()) col.addView(TextView(a).apply { text = m.info; setTextColor(0xCCFFFFFF.toInt()); textSize = 13f })

        val buttons = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        val play = chip("▶  Ver agora", true) { go(m) }
        countView = play
        buttons.addView(play)
        buttons.addView(chip("Ver os créditos", false) { dismiss() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        col.addView(buttons)

        if (recs.size > 1) {
            col.addView(TextView(a).apply { text = "Mais para ti"; setTextColor(0xCCFFFFFF.toInt()); textSize = 13f; setPadding(0, dp(6), 0, dp(6)) })
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for (x in recs.drop(1).take(6)) {
                val iv = ImageView(a).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    isFocusable = true; isClickable = true
                    setBackgroundResource(R.drawable.poster_focus)
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    load(x.poster) { crossfade(true); transformations(RoundedCornersTransformation(dp(8).toFloat())) }
                    contentDescription = x.name
                    setOnClickListener { go(x) }
                    setOnFocusChangeListener { v, f -> v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(150).start(); if (f) stopCountdown() }
                }
                row.addView(iv, LinearLayout.LayoutParams(dp(92), dp(138)).apply { marginEnd = dp(10) })
            }
            col.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }

        parent.addView(r, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.animate().alpha(1f).setDuration(450).start()
        // o vídeo (os créditos) continua a correr, pequeno, no canto superior esquerdo
        video.pivotX = 0f; video.pivotY = 0f
        video.animate().scaleX(0.36f).scaleY(0.36f).translationX(dp(28).toFloat()).translationY(dp(28).toFloat())
            .setDuration(550).setInterpolator(DecelerateInterpolator()).start()
        play.requestFocus()
        countdown = 15
        tickText()
    }

    private fun chip(label: String, primary: Boolean, click: () -> Unit) = TextView(a).apply {
        text = label; textSize = 15f; isFocusable = true; isClickable = true
        setTextColor(if (primary) Color.WHITE else 0xEEFFFFFF.toInt())
        setPadding(dp(18), dp(10), dp(18), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (primary) a.themeColor(R.attr.hfAccent) else 0x33FFFFFF)
        }
        setOnClickListener { click() }
        setOnFocusChangeListener { v, f -> v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(140).start() }
    }

    /** Chamado a cada segundo pelo leitor. */
    fun tick() {
        if (!visible || countdown < 0) return
        countdown--
        tickText()
        if (countdown <= 0) top?.let { go(it) }
    }

    private fun tickText() {
        if (countdown >= 0) countView?.text = "▶  Ver agora  ·  ${countdown}s"
    }

    private fun stopCountdown() {
        if (countdown < 0) return
        countdown = -1
        countView?.text = "▶  Ver agora"
    }

    private fun go(m: MetaItem) {
        if (!visible) return
        countdown = -1
        onPlay(m)
    }

    fun dismiss() {
        if (!visible) return
        visible = false
        countdown = -1
        video.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(400).start()
        root?.let { r -> r.animate().alpha(0f).setDuration(300).withEndAction { (r.parent as? ViewGroup)?.removeView(r) }.start() }
        root = null
        onDismiss()
    }
}
