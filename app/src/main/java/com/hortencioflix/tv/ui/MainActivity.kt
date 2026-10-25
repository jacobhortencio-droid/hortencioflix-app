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
            restoreSession()
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

    override fun onResume() {
        super.onResume()
        // O leitor ficou em pausa em segundo plano: voltar a ele exatamente onde estava
        if (App.playerInBackground && App.playerTaskId != -1) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                am.moveTaskToFront(App.playerTaskId, 0)
            } catch (e: Exception) {
                App.playerInBackground = false
            }
        }
    }

    /**
     * O Android fechou a app enquanto estava a ver (pouca memória, muito tempo fora):
     * ao abrir de novo, volta ao mesmo título; se foi há pouco, continua logo a ver no mesmo ponto.
     */
    private fun restoreSession() {
        val raw = com.hortencioflix.tv.data.Prefs.str("playSession")
        if (raw.isEmpty() || App.playerInBackground) return
        com.hortencioflix.tv.data.Prefs.remove("playSession")
        if (com.hortencioflix.tv.data.Prefs.str("restoreSession", "1") != "1") return
        try {
            val o = org.json.JSONObject(raw)
            val age = System.currentTimeMillis() - o.optLong("at")
            if (age < 0 || age > 6 * 3600_000L) return
            val meta = com.hortencioflix.tv.data.MetaItem(
                id = o.optString("id"), type = o.optString("type").ifEmpty { "movie" },
                name = o.optString("name"), poster = o.optString("poster"), background = o.optString("bg")
            )
            if (meta.id.isEmpty()) return
            val play = age < 30 * 60_000L
            window.decorView.postDelayed({
                if (!isFinishing) startActivity(Intent(this, DetailsActivity::class.java).putExtra("meta", meta).putExtra("play", play))
            }, 1200)
        } catch (e: Exception) {}
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
