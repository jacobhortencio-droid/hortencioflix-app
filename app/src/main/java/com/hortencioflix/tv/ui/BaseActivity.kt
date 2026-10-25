package com.hortencioflix.tv.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Prefs

/** Cores de destaque disponíveis (a primeira é a da marca). */
object Accents {
    val names = listOf("Vermelho (marca)", "Azul", "Verde", "Violeta", "Laranja")
    val styles = intArrayOf(0, R.style.Accent_Blue, R.style.Accent_Green, R.style.Accent_Violet, R.style.Accent_Orange)
    val swatches = intArrayOf(0xFFE3122B.toInt(), 0xFF2F7BFF.toInt(), 0xFF22C55E.toInt(), 0xFF8B5CF6.toInt(), 0xFFF97316.toInt())
}

/** Aplica o tema escolhido (normal ou AMOLED) e a cor de destaque antes de criar cada ecrã. */
open class BaseActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(when (Prefs.themeMode) {
            "amoled" -> R.style.Theme_Hortencioflix_Amoled
            "light" -> R.style.Theme_Hortencioflix_Light
            "glass" -> R.style.Theme_Hortencioflix_Glass
            else -> R.style.Theme_Hortencioflix
        })
        val s = Accents.styles.getOrElse(Prefs.accent) { 0 }
        if (s != 0) theme.applyStyle(s, true)
        super.onCreate(savedInstanceState)
        // Tema claro: ícones da barra de estado e de navegação escuros
        if (Prefs.themeMode == "light" && this !is PlayerActivity) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).let {
                it.isAppearanceLightStatusBars = true
                it.isAppearanceLightNavigationBars = true
            }
        }
        // Relatório de erro da última vez, em qualquer ecrã onde a app reabrir (antes só aparecia no Início).
        CrashReport.maybeShow(this)
    }
}
