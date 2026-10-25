package com.hortencioflix.tv.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.hortencioflix.tv.App
import com.hortencioflix.tv.R

class MainActivity : BaseActivity() {
    private var isTv = true
    private var tab = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.mainTaskId = taskId
        isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        if (isTv) {
            setContentView(R.layout.activity_main)
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction().replace(R.id.main_container, HomeFragment()).commit()
            }
        } else {
            setContentView(R.layout.activity_mobile)
            setupNav()
            tab = savedInstanceState?.getString("tab") ?: "home"
            if (savedInstanceState == null) showTab(tab) else selectNav(tab)
        }
        if (savedInstanceState == null) {
            showSplash()
            WhatsNew.maybeShow(this)
            CrashReport.maybeShow(this)
            if (com.hortencioflix.tv.data.Prefs.profiles().size > 1) window.decorView.postDelayed({ if (!isFinishing) Profiles.pick(this, true) }, 1900)
        }
        Updater.checkOnStart(this)
        com.hortencioflix.tv.EpisodeWorker.schedule(this)
        com.hortencioflix.tv.UpdateWorker.schedule(this)
        com.hortencioflix.tv.TorboxWatchWorker.schedule(this)
        TorboxQueue.checkAccount(this)
        com.hortencioflix.tv.data.RokuSync.syncQuietly()
        if (!isTv && Build.VERSION.SDK_INT >= 33 && com.hortencioflix.tv.data.Prefs.str("askedNotif") != "1" &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            com.hortencioflix.tv.data.Prefs.put("askedNotif", "1")
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", tab)
    }

    private val navIds = mapOf(
        "home" to R.id.nav_home, "search" to R.id.nav_search, "live" to R.id.nav_live,
        "library" to R.id.nav_library, "settings" to R.id.nav_settings
    )

    private fun setupNav() {
        val defs = listOf(
            Triple("home", R.drawable.ic_home, "Início"),
            Triple("search", R.drawable.ic_search, "Pesquisa"),
            Triple("live", R.drawable.ic_live, "TV ao vivo"),
            Triple("library", R.drawable.ic_library, "Biblioteca"),
            Triple("settings", R.drawable.ic_settings, "Definições")
        )
        for ((key, icon, label) in defs) {
            val v = findViewById<View>(navIds.getValue(key))
            v.findViewById<ImageView>(R.id.nav_icon).setImageResource(icon)
            v.findViewById<TextView>(R.id.nav_label).text = label
            v.setOnClickListener { onNav(key) }
        }
        // Cor do separador ativo segue a cor de destaque escolhida.
        val tint = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(themeColor(R.attr.hfAccent), this.themeColor(R.attr.hfMuted))
        )
        for (id in navIds.values) {
            val v = findViewById<View>(id)
            v.findViewById<ImageView>(R.id.nav_icon).imageTintList = tint
            v.findViewById<TextView>(R.id.nav_label).setTextColor(tint)
        }
    }

    private fun onNav(key: String) {
        when (key) {
            "home", "library" -> if (key != tab) showTab(key)
            "search" -> startActivity(Intent(this, SearchActivity::class.java))
            "live" -> startActivity(Intent(this, IptvActivity::class.java))
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun selectNav(key: String) {
        for ((k, id) in navIds) {
            val v = findViewById<View>(id)
            val was = v.isSelected
            v.isSelected = k == key
            if (k == key && !was) {
                // Ícone dá um pequeno salto e o indicador desliza até ao separador.
                val icon = v.findViewById<View>(R.id.nav_icon)
                icon.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).withEndAction {
                    icon.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }.start()
            }
            if (k == key) {
                val ind = findViewById<View>(R.id.nav_indicator)
                v.post { ind.animate().translationX(v.left + (v.width - ind.width) / 2f).setDuration(260).setInterpolator(android.view.animation.DecelerateInterpolator()).start() }
            }
        }
    }

    private fun showTab(key: String) {
        tab = key
        selectNav(key)
        val f = if (key == "library") LibraryFragment() else MobileHomeFragment()
        supportFragmentManager.beginTransaction().replace(R.id.tab_container, f).commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isTv && tab != "home") {
            showTab("home")
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
