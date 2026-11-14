package com.hortencioflix.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Contentor que se desvanece até ficar transparente na base (máscara de alfa, não uma cor por cima).
 * Usado no destaque do Início: a imagem funde-se com o fundo real da página — seja ele preto, claro ou
 * o gradiente do tema Vidro — sem a linha horizontal que uma cor fixa deixava.
 */
class FadeFrameLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    /** Fração da altura onde começa a desvanecer (0..1). */
    var fadeFrom = 0.62f
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mask.shader = LinearGradient(0f, h * fadeFrom, 0f, h.toFloat(), Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width == 0 || height == 0) { super.dispatchDraw(canvas); return }
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
        canvas.restoreToCount(save)
    }
}
