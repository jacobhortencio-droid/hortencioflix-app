package com.hortencioflix.tv.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.hortencioflix.tv.R
import kotlin.math.PI
import kotlin.math.sin

/** Indicador de carregamento da marca: três barras a «tocar», em vermelho → dourado. */
class HfLoader @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        paint.shader = LinearGradient(0f, h.toFloat(), w.toFloat(), 0f, context.themeColor(R.attr.hfAccent), 0xFFFFB319.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (visibility == VISIBLE) anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isAttachedToWindow) return
        if (visibility == VISIBLE && isShown) { if (!anim.isStarted) anim.start() } else anim.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val n = 3
        val gap = w * 0.12f
        val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val t = (sin((phase - i * 0.18f) * 2 * PI).toFloat() + 1f) / 2f
            val bh = h * (0.3f + 0.7f * t)
            val x = i * (bw + gap)
            rect.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f)
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
    }
}
