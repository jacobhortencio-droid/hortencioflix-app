package com.hortencioflix.tv.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.hortencioflix.tv.R

/**
 * Barra de navegação inferior (telemóvel/tablet), a mesma nos cinco ecrãs de primeiro nível:
 * Início, Pesquisa, TV ao vivo, Biblioteca e Definições. Mudar de separador não tem animação nem
 * empilha ecrãs (cada um é trazido para a frente e mantém o estado). Na TV fica escondida.
 */
object MobileNav {
    private val ids = linkedMapOf(
        "home" to R.id.nav_home, "search" to R.id.nav_search, "live" to R.id.nav_live,
        "library" to R.id.nav_library, "settings" to R.id.nav_settings
    )
    private val defs = mapOf(
        "home" to (R.drawable.ic_home to "Início"),
        "search" to (R.drawable.ic_search to "Pesquisa"),
        "live" to (R.drawable.ic_live to "TV ao vivo"),
        "library" to (R.drawable.ic_library to "Biblioteca"),
        "settings" to (R.drawable.ic_settings to "Definições")
    )

    fun isTv(a: Activity) = a.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Prepara a barra neste ecrã. [onLocal] trata os separadores que vivem no próprio ecrã (Início/Biblioteca). */
    fun attach(a: Activity, current: String, onLocal: ((String) -> Boolean)? = null) {
        val root = a.findViewById<View?>(R.id.nav_root) ?: return
        if (isTv(a)) { root.visibility = View.GONE; return }
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(a.themeColor(R.attr.hfAccent), a.themeColor(R.attr.hfMuted))
        )
        for ((key, id) in ids) {
            val v = a.findViewById<View?>(id) ?: continue
            val (icon, label) = defs.getValue(key)
            v.findViewById<ImageView>(R.id.nav_icon).apply { setImageResource(icon); imageTintList = tint }
            v.findViewById<TextView>(R.id.nav_label).apply { text = label; setTextColor(tint) }
            v.contentDescription = label
            v.setOnClickListener { if (onLocal?.invoke(key) != true) go(a, key) }
        }
        select(a, current, animate = false)
        // com o teclado aberto a barra esconde-se (senão ficava a flutuar por cima do teclado)
        val content = a.findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            content.getWindowVisibleDisplayFrame(r)
            val keyboard = content.rootView.height - r.bottom > a.dp(140)
            val want = if (keyboard) View.GONE else View.VISIBLE
            if (root.visibility != want) root.visibility = want
        }
    }

    fun select(a: Activity, key: String, animate: Boolean = true) {
        val ind = a.findViewById<View?>(R.id.nav_indicator)
        for ((k, id) in ids) {
            val v = a.findViewById<View?>(id) ?: continue
            val was = v.isSelected
            v.isSelected = k == key
            if (k != key) continue
            if (animate && !was) {
                val icon = v.findViewById<View>(R.id.nav_icon)
                icon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).withEndAction { icon.animate().scaleX(1f).scaleY(1f).setDuration(160).start() }.start()
            }
            if (ind != null) v.post {
                val x = v.left + (v.width - ind.width) / 2f
                if (animate) ind.animate().translationX(x).setDuration(240).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                else ind.translationX = x
            }
        }
    }

    /** Vai para outro ecrã de primeiro nível, sem animação e sem empilhar. */
    fun go(a: Activity, key: String) {
        val i = when (key) {
            "search" -> Intent(a, SearchActivity::class.java)
            "live" -> Intent(a, IptvActivity::class.java)
            "settings" -> Intent(a, SettingsActivity::class.java)
            else -> Intent(a, MainActivity::class.java).putExtra("tab", key)
        }
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        a.startActivity(i)
        @Suppress("DEPRECATION")
        a.overridePendingTransition(0, 0)
    }

    /** Botão Voltar num ecrã de primeiro nível (que não o Início): volta ao Início. true = tratado. */
    fun back(a: Activity): Boolean {
        if (isTv(a) || a.findViewById<View?>(R.id.nav_root) == null) return false
        go(a, "home")
        return true
    }
}
