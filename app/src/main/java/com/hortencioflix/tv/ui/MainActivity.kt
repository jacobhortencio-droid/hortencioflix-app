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
        com.hortencioflix.tv.data.CloudSync.syncQuietly(force = true)
        // velocidade real da Internet (no máximo de 6 em 6 h, só em Wi-Fi/sem limite de dados): escolhe fontes que não param
        if (!com.hortencioflix.tv.data.Prefs.onMeteredNetwork()) Thread { com.hortencioflix.tv.data.NetSpeed.measure() }.start()
        if (!isTv && Build.VERSION.SDK_INT >= 33 && com.hortencioflix.tv.data.Prefs.str("askedNotif") != "1" &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            com.hortencioflix.tv.data.Prefs.put("askedNotif", "1")
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    private fun resumePlayerIfNeeded() {
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

    /** Separadores que vivem neste ecrã (Início e Biblioteca); os outros são ecrãs próprios com a mesma barra. */
    private fun setupNav() {
        MobileNav.attach(this, tab) { key ->
            if (key == "home" || key == "library") { if (key != tab) showTab(key); true } else false
        }
    }

    private fun selectNav(key: String) = MobileNav.select(this, key)

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val t = intent?.getStringExtra("tab") ?: return
        if (!isTv && (t == "home" || t == "library") && t != tab) showTab(t)
    }

    override fun onResume() {
        super.onResume()
        if (!isTv) MobileNav.select(this, tab, animate = false)
        resumePlayerIfNeeded()
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
        if (!isTv) {
            // sair de vez: os outros separadores (Pesquisa, TV ao vivo, Definições) podem estar abertos por baixo
            finishAffinity()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
