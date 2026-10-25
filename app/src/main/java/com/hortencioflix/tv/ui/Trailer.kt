package com.hortencioflix.tv.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hortencioflix.tv.data.Prefs

/**
 * Trailers do YouTube dentro da app: pré-visualização sem som e sem controlos no fundo da página do título,
 * e ecrã inteiro com som (botão «Trailer»). O YouTube só deixa tocar incorporado com uma origem válida,
 * por isso a página é carregada com um endereço base próprio.
 */
object Trailer {
    private const val BASE = "https://hortencioflix.app/"

    fun autoEnabled() = Prefs.str("trailerAuto", "1") == "1" && !(Prefs.dataSaver && Prefs.onMeteredNetwork())

    fun html(id: String, preview: Boolean): String {
        val params = if (preview)
            "autoplay=1&mute=1&controls=0&loop=1&playlist=$id&modestbranding=1&playsinline=1&rel=0&iv_load_policy=3&disablekb=1&fs=0&start=8"
        else
            "autoplay=1&controls=1&modestbranding=1&playsinline=1&rel=0&iv_load_policy=3&fs=0"
        // em pré-visualização o vídeo cobre o ecrã todo (corta as barras pretas), como uma imagem de fundo viva
        val box = if (preview)
            "position:absolute;top:50%;left:50%;width:177.78vh;height:100vh;min-width:100vw;min-height:56.25vw;transform:translate(-50%,-50%)"
        else "position:absolute;inset:0;width:100%;height:100%"
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}</style></head><body>
<div style="$box"><iframe src="https://www.youtube-nocookie.com/embed/$id?$params&origin=${BASE.trimEnd('/')}"
style="width:100%;height:100%;border:0;pointer-events:${if (preview) "none" else "auto"}" allow="autoplay; encrypted-media; picture-in-picture"></iframe></div>
</body></html>"""
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(w: WebView) {
        w.setBackgroundColor(Color.BLACK)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.webChromeClient = WebChromeClient()
    }

    fun load(w: WebView, id: String, preview: Boolean) = w.loadDataWithBaseURL(BASE, html(id, preview), "text/html", "utf-8", null)

    fun open(ctx: Context, id: String) = ctx.startActivity(Intent(ctx, TrailerActivity::class.java).putExtra("id", id))
}

/** Trailer em ecrã inteiro, com som. Voltar fecha. */
class TrailerActivity : Activity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val id = intent.getStringExtra("id").orEmpty()
        if (id.isEmpty()) { finish(); return }
        val w = WebView(this)
        Trailer.setup(w)
        w.webViewClient = WebViewClient()
        setContentView(w)
        Trailer.load(w, id, preview = false)
        web = w
    }

    override fun onPause() {
        super.onPause()
        web?.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onDestroy() {
        web?.apply { loadUrl("about:blank"); destroy() }
        web = null
        super.onDestroy()
    }
}
