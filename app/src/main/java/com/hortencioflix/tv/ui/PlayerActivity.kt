package com.hortencioflix.tv.ui

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.hortencioflix.tv.App
import com.hortencioflix.tv.R
import coil.load
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.PlayLog
import com.hortencioflix.tv.data.SourceStats
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.SourcePicker
import com.hortencioflix.tv.data.Session
import com.hortencioflix.tv.data.Stremio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class PlayerActivity : BaseActivity() {
    companion object {
        const val PIP_ACTION = "com.hortencioflix.tv.PIP"
        fun start(ctx: Context) {
            if (!Debounce.ok("player")) return
            val i = Intent(ctx, PlayerActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var osd: TextView
    private lateinit var skipHint: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private var streamIndex = 0
    private var channelIndex = 0
    private var triedHls = false
    private var introStart = -1L
    private var introEnd = -1L
    private var recapStart = -1L
    private var recapEnd = -1L
    private var locked = false
    private var sleepAt = 0L
    private var sleepEndOfEpisode = false
    private val hideUnlock = Runnable { findViewById<View>(R.id.btn_unlock).visibility = View.GONE }
    private var inPip = false
    private var loadingNext = false
    private var isTv = true
    private lateinit var topBar: View
    private lateinit var topTitle: TextView
    private lateinit var topSub: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnSources: TextView
    private lateinit var loadingBox: View
    private lateinit var loadingText: TextView
    private var controlsVisible = false
    private var loadStartedAt = 0L
    private var reachedReady = false
    private var zapToken = 0
    private var chStreams: List<com.hortencioflix.tv.data.StreamItem> = emptyList()
    private var chStreamIdx = 0
    private var outroStart = -1L
    private var nextCancelled = false
    private var nextCountdown = -1
    private var subDelayMs = 0L
    private var netRetries = 0
    private var pausedAt = 0L
    // Saída para segundo plano sem mini-leitor: o leitor fica em pausa e retoma no mesmo ponto ao voltar
    private var bgAt = 0L
    private var bgWasPlaying = false
    private var pipReceiver: android.content.BroadcastReceiver? = null
    private var sameRetriedAt = 0L
    private var appliedDelayFor = ""
    private val failedSources = HashSet<Int>()
    private var qualityCap = Int.MAX_VALUE
    private var prefetchFor = ""
    private var reachedLong = false
    // «A seguir» no fim do filme
    private var postPlay: PostPlay? = null
    private var postPlayDone = false
    private var recs: List<com.hortencioflix.tv.data.MetaItem>? = null
    private var recsFor = ""
    private var prefetched: Pair<List<com.hortencioflix.tv.data.StreamItem>, List<com.hortencioflix.tv.data.SubtitleItem>>? = null
    private var origSubs: List<com.hortencioflix.tv.data.SubtitleItem> = emptyList()
    private lateinit var nextCard: View

    private val hideOsd = Runnable { osd.visibility = View.GONE }

    private val tick = object : Runnable {
        override fun run() {
            ticks++
            checkIntro()
            checkNextEpisode()
            checkPostPlay()
            postPlay?.tick()
            if (loadStartedAt > 0 && !reachedReady && System.currentTimeMillis() - loadStartedAt > (if (Session.live) 20000 else 30000)) onTimeout()
            if (reachedReady) loadStartedAt = 0
            if (ticks % 10 == 0) saveProgress(ticks % 30 == 0)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Vídeo também por baixo do entalhe da câmara (os botões ficam fora dele).
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        registerPipReceiver()
        Prefs.str("resizeMode").toIntOrNull()?.let { playerView.resizeMode = it }
        osd = findViewById(R.id.osd)
        skipHint = findViewById(R.id.skip_hint)

        topBar = findViewById(R.id.top_bar)
        keepControlsOutOfCutout()
        topTitle = findViewById(R.id.top_title)
        topSub = findViewById(R.id.top_sub)
        btnNext = findViewById(R.id.btn_next)
        btnSources = findViewById(R.id.btn_sources)
        loadingBox = findViewById(R.id.loading_box)
        loadingText = findViewById(R.id.loading_text)

        // Se o descodificador principal falhar, tenta outro do próprio aparelho.
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        // Arranca com 1,5 s em memória, mantém até 60 s à frente e 20 s para trás (recuar sem recarregar).
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 1_500, 3_000)
            .setBackBuffer(20_000, true)
            .build()
        val p = ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        // Saltos na barra de tempo vão ao ponto de entrada mais próximo: instantâneo mesmo em MKV grandes.
        p.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        player = p
        playerView.player = p
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        playerView.setShowSubtitleButton(true)
        playerView.setShowPreviousButton(false)
        playerView.setShowNextButton(false)
        playerView.controllerShowTimeoutMs = 4000
        playerView.requestFocus()
        isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        findViewById<View>(R.id.btn_back).setOnClickListener { exit() }
        findViewById<View>(R.id.btn_subs).setOnClickListener { chooseTrack(C.TRACK_TYPE_TEXT) }
        findViewById<View>(R.id.btn_audio).setOnClickListener { chooseTrack(C.TRACK_TYPE_AUDIO) }
        findViewById<View>(R.id.btn_more).setOnClickListener { showMenu() }
        btnSources.setOnClickListener { if (Session.live) chooseChannel() else chooseSource() }
        btnNext.setOnClickListener { playNextEpisode() }
        nextCard = findViewById(R.id.next_card)
        findViewById<View>(R.id.next_now).setOnClickListener { hideNextCard(); playNextEpisode() }
        findViewById<View>(R.id.next_cancel).setOnClickListener { nextCancelled = true; hideNextCard() }
        applySubtitleStyle()
        skipHint.setOnClickListener { skipTarget()?.let { player?.seekTo(it) }; skipHint.visibility = View.GONE }
        findViewById<View>(R.id.btn_lock).setOnClickListener { setLocked(true) }
        findViewById<View>(R.id.btn_unlock).setOnClickListener { setLocked(false) }
        if (isTv) {
            // Na TV os botões estão no MENU; a barra superior só mostra o título.
            for (id in listOf(R.id.btn_back, R.id.btn_lock, R.id.btn_subs, R.id.btn_audio, R.id.btn_sources, R.id.btn_more)) findViewById<View>(id).visibility = View.GONE
            for (v in listOf(btnNext, skipHint)) v.isFocusable = false
        } else {
            setupGestures()
        }
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            controlsVisible = v == View.VISIBLE && !inPip
            updateOverlay()
        })

        // Modo de vídeo avançado (tunneling): vídeo e som entregues direto ao ecrã — mais suave nas Android TV que o suportam.
        if (Prefs.str("tunneling") == "1") {
            p.trackSelectionParameters = androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder(this)
                .setTunnelingEnabled(true).build()
        }
        val lang = Prefs.subLang
        val b = p.trackSelectionParameters.buildUpon()
        if (lang == "off") {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setPreferredTextLanguage(iso2(lang))
        }
        if (Prefs.audioLang.isNotEmpty()) b.setPreferredAudioLanguage(Prefs.audioLang)
        p.trackSelectionParameters = b.build()

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
                if (state == Player.STATE_READY && !reachedReady && !Session.live &&
                    SourcePicker.looksLikePlaceholder(Session.streams.getOrNull(streamIndex), p.duration, Session.meta?.type ?: "")) {
                    handler.post { sourceNotReady() }
                    return
                }
                if (state == Player.STATE_READY) {
                    triedHls = false
                    if (!reachedReady && !Session.live) {
                        PlayLog.add("✅ ${PlayLog.label(Session.streams.getOrNull(streamIndex))} — a tocar (${p.duration / 60000} min)")
                        if (p.duration > 120_000) SourceStats.ok(Session.streams.getOrNull(streamIndex))
                        if (p.duration > 120_000) reachedLong = true
                        handler.postDelayed({ Prefs.remove("playGuard") }, 15_000)
                    }
                    if (!reachedReady && !Session.live) Session.meta?.let { m -> Session.streams.getOrNull(streamIndex)?.let { Addons.rememberSource(m.id, it) } }
                    if (!reachedReady && !Session.live && subDelayMs == 0L && appliedDelayFor != Session.videoId) {
                        // Atraso das legendas que ajustaste da última vez neste título.
                        appliedDelayFor = Session.videoId
                        val saved = Prefs.str("subDelay:" + Session.videoId).toLongOrNull() ?: 0L
                        if (saved != 0L && origSubs.isNotEmpty()) handler.post { reloadSubtitles(origSubs, saved) }
                    }
                    reachedReady = true
                    netRetries = 0
                    hideLoading()
                } else if (state == Player.STATE_BUFFERING && reachedReady && !Session.live) {
                    noteRebuffer()
                    showLoading("A carregar…")
                } else if (state == Player.STATE_BUFFERING && reachedReady) {
                    showLoading("A carregar…")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (!Session.live) handler.post { announceHdr(); updateOverlay(); matchFrameRate() }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) lastSeekAt = android.os.SystemClock.elapsedRealtime()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!Session.live && reachedReady) scrobble(if (isPlaying) "start" else "pause")
                updatePip()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                updatePip()
            }

            // Retomar depois de uma pausa longa: a ligação e o link do debrid já expiraram — abre de novo
            // a mesma fonte no mesmo ponto, em vez de ficar parado a carregar ou dar erro.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (!playWhenReady) { pausedAt = now; return }
                val pausedFor = if (pausedAt > 0) now - pausedAt else 0L
                pausedAt = 0L
                if (pausedFor >= 300_000 && reachedReady && !Session.live) {
                    val pos = player?.currentPosition ?: 0L
                    PlayLog.add("⏯ Pausa de ${pausedFor / 60000} min — a reabrir a mesma fonte em ${pos / 60000} min")
                    handler.post { if (!isFinishing && !isDestroyed) playStream(streamIndex, pos) }
                }
            }
        })
        startPlayback()
        handler.postDelayed(tick, 1000)
    }

    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val c = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        immersive()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        saveProgress(true)
        startPlayback()
    }

    private fun iso2(code: String): String = when (code.lowercase()) {
        "por", "pob", "pt-br", "pt-pt", "pt" -> "pt"
        "eng", "en" -> "en"
        "spa", "es" -> "es"
        "fre", "fra", "fr" -> "fr"
        "ita", "it" -> "it"
        else -> code
    }

    private fun startPlayback() {
        appliedDelayFor = ""
        failedSources.clear()
        reachedLong = false
        postPlayDone = false
        qualDropDeclined = false
        postPlay?.dismiss()
        if (recsFor != Session.meta?.id) recs = null
        prefetchFor = ""
        prefetched = null
        introStart = -1; introEnd = -1; outroStart = -1; recapStart = -1; recapEnd = -1
        nextCancelled = false
        subDelayMs = 0
        origSubs = Session.subtitles
        hideNextCard()
        skipHint.visibility = View.GONE
        if (Session.live) {
            playChannel(Session.channelIndex)
            return
        }
        if (Session.pending) { resolvePending(); return }
        loadSegments()
        val start = Session.startAt
        if (Prefs.askResume && start > 30) {
            // Perguntar se continua ou começa do início.
            var chosen = false
            dialog().setTitle(Session.meta?.name ?: "Continuar a ver?")
                .setItems(arrayOf("▶  Continuar de ${formatTime(start)}", "↺  Começar do início")) { _, w ->
                    chosen = true
                    playStream(Session.streamIndex, if (w == 0) start * 1000 else 0L)
                }
                .setOnDismissListener { if (!chosen) playStream(Session.streamIndex, start * 1000) }
                .show()
        } else {
            playStream(Session.streamIndex, start * 1000)
        }
    }

    /** O leitor abriu antes das fontes: procura-as aqui (normalmente já estão em cache) e arranca. */
    private fun resolvePending() {
        val meta = Session.meta ?: return exit()
        val vid = Session.videoId
        topTitle.text = meta.name
        showLoading("A preparar o vídeo…")
        lifecycleScope.launch {
            val subsJob = async { try { withTimeoutOrNull(6000) { Addons.subtitles(meta.type, vid) } ?: emptyList() } catch (e: Exception) { emptyList() } }
            val list = try { Addons.streams(meta.type, vid) } catch (e: Exception) { emptyList() }
            val subs = Session.subtitles.ifEmpty { subsJob.await() }
            if (isFinishing || isDestroyed) return@launch
            if (list.isEmpty()) {
                hideLoading()
                dialog().setTitle("Sem fontes")
                    .setMessage(if (Prefs.hasDebrid()) "Não foi encontrada nenhuma fonte para este título nos teus add-ons." else "Não há fontes diretas para este título. Para torrents precisas de um serviço debrid (TorBox, Real-Debrid…) — configura-o nas Definições.")
                    .setPositiveButton("Voltar") { _, _ -> exit() }
                    .setOnCancelListener { exit() }
                    .show()
                return@launch
            }
            Session.vod(meta, vid, list, 0, subs, Session.episodes, Session.startAt)
            if (!SourcePicker.hasReady(list)) {
                hideLoading()
                val best = TorboxQueue.best(list)
                val b = dialog().setTitle("Ainda a descarregar")
                    .setMessage("Nenhuma fonte deste título está pronta no teu serviço debrid." + (com.hortencioflix.tv.data.TorboxApi.warning()?.let { "\n\n$it" } ?: "") +
                        (if (best != null) "\n\nPosso pedir ao TorBox para descarregar a melhor e avisar-te quando estiver pronta." else ""))
                    .setNegativeButton("Voltar") { _, _ -> exit() }
                if (best != null) b.setPositiveButton("Descarregar e avisar-me") { _, _ -> TorboxQueue.queue(this@PlayerActivity, best, meta, vid) { exit() } }
                    .setNeutralButton("Escolher fonte") { _, _ -> chooseSource() }
                else b.setPositiveButton("Escolher fonte") { _, _ -> chooseSource() }
                b.show()
                return@launch
            }
            startPlayback()
        }
    }

    // ---------- Próximo episódio (contagem) ----------

    private fun hideNextCard() {
        nextCountdown = -1
        if (::nextCard.isInitialized) nextCard.visibility = View.GONE
    }

    /** Nos últimos 3 minutos já procura as fontes do próximo episódio, para arrancar sem espera. */
    private fun prefetchNext(id: String) {
        val meta = Session.meta ?: return
        prefetchFor = id
        prefetched = null
        lifecycleScope.launch {
            val st = try { Addons.streams(meta.type, id) } catch (e: Exception) { emptyList() }
            val sb = try { Addons.subtitles(meta.type, id) } catch (e: Exception) { emptyList() }
            if (prefetchFor == id && st.isNotEmpty()) prefetched = st to sb
        }
    }

    /**
     * Quando começam os créditos de um filme (ou do último episódio): o vídeo encolhe e aparecem
     * recomendações para ver a seguir. As sugestões são preparadas a meio do filme, para estarem prontas.
     */
    private fun checkPostPlay() {
        val p = player ?: return
        if (Session.live || postPlayDone || inPip || Prefs.str("postPlay", "1") != "1") return
        val meta = Session.meta ?: return
        val isMovie = meta.type == "movie"
        if (!isMovie && !(meta.type == "series" && nextEpisodeId() == null)) return
        val dur = p.duration
        if (dur <= 0 || dur == C.TIME_UNSET || dur < 20 * 60_000) return
        val pos = p.currentPosition
        if (recs == null && recsFor != meta.id && pos > dur / 2) {
            recsFor = meta.id
            lifecycleScope.launch {
                recs = try { com.hortencioflix.tv.data.Addons.recommendFor(meta.type, meta.id) } catch (e: Exception) { emptyList() }
            }
        }
        // início dos créditos: o marcador do IntroDB quando existe; senão os últimos ~4,5 % (entre 1 e 5 min)
        val trigger = if (outroStart > 0 && outroStart < dur) dur - outroStart else (dur * 0.045).toLong().coerceIn(60_000L, 300_000L)
        val left = dur - pos
        if (left > trigger || left < 5_000) return
        val list = recs ?: return
        postPlayDone = true
        if (list.isEmpty()) return
        playerView.hideController()
        postPlay = PostPlay(this, playerView, onPlay = { m -> playRecommendation(m) }, onDismiss = { postPlay = null; playerView.requestFocus() })
            .also { it.show(list) }
    }

    private fun playRecommendation(m: com.hortencioflix.tv.data.MetaItem) {
        Session.meta?.let { cur ->
            Prefs.removeProgress(cur.id)
            Prefs.setWatched(listOf(Session.videoId), true)
            com.hortencioflix.tv.data.WatchNext.remove(this, cur.id)
        }
        scrobble("stop")
        player?.pause()
        player?.stop()
        startActivity(Intent(this, DetailsActivity::class.java).putExtra("meta", m).putExtra("play", true))
        finish()
    }

    private fun checkNextEpisode() {
        val p = player ?: return
        if (Session.live || !Prefs.autoNext || nextCancelled || loadingNext || inPip) return
        val next = nextEpisodeId() ?: return
        val dur = p.duration
        if (dur <= 0 || dur == C.TIME_UNSET || dur < 5 * 60_000) return
        val left = dur - p.currentPosition
        // Procura as fontes do próximo episódio logo no início (fica em cache), para arrancar sem espera.
        if (p.currentPosition > 60_000 && prefetchFor != next && Session.directStream == null) prefetchNext(next)
        val trigger = if (outroStart > 0 && outroStart < dur) dur - outroStart else 25_000L
        if (left > trigger || left <= 0) {
            if (nextCountdown >= 0 && left > trigger) hideNextCard()
            return
        }
        if (nextCountdown < 0) {
            nextCountdown = 10
            val ep = Session.episodes.firstOrNull { it.id == next }
            findViewById<TextView>(R.id.next_title).text = if (ep != null) "${ep.label} · ${ep.name.ifEmpty { "Episódio ${ep.episode}" }}" else "Próximo episódio"
            val thumb = findViewById<android.widget.ImageView>(R.id.next_thumb)
            if (ep != null && ep.thumbnail.isNotEmpty()) thumb.load(ep.thumbnail) else thumb.setImageDrawable(null)
            nextCard.visibility = View.VISIBLE
            nextCard.translationX = dp(360).toFloat()
            nextCard.animate().translationX(0f).setDuration(380).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            if (isTv) findViewById<View>(R.id.next_now).requestFocus()
        } else {
            nextCountdown--
        }
        findViewById<TextView>(R.id.next_count).text = "PRÓXIMO EPISÓDIO EM $nextCountdown s"
        if (nextCountdown <= 0) {
            hideNextCard()
            playNextEpisode()
        }
    }

    // ---------- Legendas: aspeto, atraso e mais legendas ----------

    private fun applySubtitleStyle() {
        val sv = playerView.subtitleView ?: return
        val fg = if (Prefs.subColor == 1) android.graphics.Color.YELLOW else android.graphics.Color.WHITE
        val bg = when (Prefs.subBg) { 1 -> 0x99000000.toInt(); 2 -> android.graphics.Color.BLACK; else -> android.graphics.Color.TRANSPARENT }
        val edge = if (Prefs.subBg == 0) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
        sv.setStyle(androidx.media3.ui.CaptionStyleCompat(fg, bg, android.graphics.Color.TRANSPARENT, edge, android.graphics.Color.BLACK, android.graphics.Typeface.DEFAULT_BOLD))
        sv.setApplyEmbeddedStyles(false)
        sv.setApplyEmbeddedFontSizes(false)
        val scale = listOf(0.8f, 1f, 1.25f, 1.55f).getOrElse(Prefs.subSize) { 1f }
        sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
        sv.setBottomPaddingFraction(if (Prefs.subPos == 1) 0.22f else androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    }

    private fun subtitleLook() {
        val opts = listOf(
            "Tamanho: " + listOf("Pequeno", "Normal", "Grande", "Muito grande")[Prefs.subSize.coerceIn(0, 3)],
            "Cor: " + listOf("Branco", "Amarelo")[Prefs.subColor.coerceIn(0, 1)],
            "Fundo: " + listOf("Contorno", "Semitransparente", "Preto")[Prefs.subBg.coerceIn(0, 2)],
            "Posição: " + listOf("Em baixo", "Mais acima")[Prefs.subPos.coerceIn(0, 1)]
        )
        glassChoice("Aspeto das legendas", opts) { w ->
            when (w) {
                0 -> glassChoice("Tamanho", listOf("Pequeno", "Normal", "Grande", "Muito grande")) { Prefs.subSize = it; applySubtitleStyle(); subtitleLook() }
                1 -> glassChoice("Cor", listOf("Branco", "Amarelo")) { Prefs.subColor = it; applySubtitleStyle(); subtitleLook() }
                2 -> glassChoice("Fundo", listOf("Contorno", "Semitransparente", "Preto")) { Prefs.subBg = it; applySubtitleStyle(); subtitleLook() }
                3 -> glassChoice("Posição", listOf("Em baixo", "Mais acima")) { Prefs.subPos = it; applySubtitleStyle(); subtitleLook() }
            }
        }
    }

    private fun selectedSubLabel(): String? {
        val p = player ?: return null
        for (g in p.currentTracks.groups) if (g.type == C.TRACK_TYPE_TEXT) for (j in 0 until g.length) if (g.isTrackSelected(j)) return g.getTrackFormat(j).label
        return null
    }

    private fun subtitleDelay() {
        val values = listOf(-3000L, -2000L, -1000L, -500L, -250L, 0L, 250L, 500L, 1000L, 2000L, 3000L)
        val labels = values.map { v -> (if (v == subDelayMs) "● " else "") + (if (v > 0) "+" else "") + String.format("%.2f s", v / 1000f).replace('.', ',') + (if (v < 0) "  (mais cedo)" else if (v > 0) "  (mais tarde)" else "") }
        glassChoice("Atraso das legendas", labels) { reloadSubtitles(origSubs, values[it]) }
    }

    private fun shiftSubtitle(text: String, delay: Long): String {
        val re = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})([,.])(\\d{3})")
        return re.replace(text) { m ->
            val ms = ((m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()) * 1000 + m.groupValues[5].toLong() + delay).coerceAtLeast(0)
            String.format("%02d:%02d:%02d%s%03d", ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60, m.groupValues[4], ms % 1000)
        }
    }

    /** Volta a carregar o vídeo com as legendas desfasadas (ou novas), mantendo a posição. */
    private fun reloadSubtitles(list: List<com.hortencioflix.tv.data.SubtitleItem>, delay: Long) {
        if (list.isEmpty()) { toast("Não há legendas externas para ajustar."); return }
        val sel = selectedSubLabel()
        val ordered = list.sortedBy { if (it.label == sel) 0 else 1 }
        showLoading("A ajustar legendas…")
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                val dir = java.io.File(cacheDir, "subs"); dir.mkdirs()
                ordered.take(8).mapIndexedNotNull { i, sub ->
                    if (delay == 0L) return@mapIndexedNotNull sub
                    val txt = Http.getString(sub.url) ?: return@mapIndexedNotNull null
                    val ext = if (sub.url.contains(".vtt", true) || txt.startsWith("WEBVTT")) "vtt" else "srt"
                    val f = java.io.File(dir, "s$i.$ext")
                    f.writeText(shiftSubtitle(txt, delay))
                    sub.copy(url = Uri.fromFile(f).toString() + (if (ext == "vtt") "#.vtt" else ""))
                }
            }
            if (out.isEmpty()) { hideLoading(); toast("Não foi possível descarregar as legendas."); return@launch }
            subDelayMs = delay
            if (delay == 0L) Prefs.remove("subDelay:" + Session.videoId) else Prefs.put("subDelay:" + Session.videoId, delay.toString())
            Session.subtitles = out
            playStream(streamIndex, player?.currentPosition ?: 0L)
            toast(if (delay == 0L) "Legendas sem atraso" else "Atraso das legendas: ${delay / 1000f} s")
        }
    }

    private fun moreSubtitles() {
        val meta = Session.meta ?: return
        toast("A procurar legendas…")
        lifecycleScope.launch {
            val all = try { Addons.allSubtitles(meta.type, Session.videoId) } catch (e: Exception) { emptyList() }
            if (all.isEmpty()) { toast("Não foram encontradas legendas."); return@launch }
            glassChoice("Legendas disponíveis (${all.size})", all.map { it.label }) { i ->
                val chosen = all[i]
                origSubs = listOf(chosen) + origSubs.filter { it.url != chosen.url }
                subDelayMs = 0
                Session.subtitles = origSubs
                playStream(streamIndex, player?.currentPosition ?: 0L)
                toast("Legenda: ${chosen.label}")
            }
        }
    }

    private fun showOsd(text: String, ms: Long = 4000) {
        osd.text = text
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsd)
        handler.postDelayed(hideOsd, ms)
    }

    private fun showLoading(text: String) {
        loadingText.text = text
        loadingBox.findViewById<View>(R.id.loading_spin).visibility = View.VISIBLE
        loadingBox.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingBox.visibility = View.GONE
    }

    private fun beginLoad(text: String) {
        loadStartedAt = System.currentTimeMillis()
        reachedReady = false
        showLoading(text)
    }

    private fun qualityLabel(q: Int) = if (q <= 0) "" else if (q == 2160) "4K" else "${q}p"

    private fun fade(v: View, show: Boolean) {
        if (show && v.visibility != View.VISIBLE) {
            v.alpha = 0f; v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(200).start()
        } else if (!show && v.visibility == View.VISIBLE) {
            v.animate().alpha(0f).setDuration(200).withEndAction { v.visibility = View.GONE; v.alpha = 1f }.start()
        }
    }

    /** Atualiza a barra superior e os botões que acompanham os controlos. */
    /** Afasta a barra de cima e os controlos do entalhe; o vídeo continua a ocupar o ecrã todo. */
    private fun keepControlsOutOfCutout() {
        val ctrl = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
        val views = listOfNotNull(topBar, ctrl)
        val base = views.map { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, ins ->
            val c = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            views.forEachIndexed { i, w -> w.setPadding(base[i][0] + c.left, base[i][1] + c.top, base[i][2] + c.right, base[i][3] + c.bottom) }
            androidx.core.view.ViewCompat.onApplyWindowInsets(v, ins)
        }
    }

    private var fpsMatchedFor = ""

    /**
     * Android TV: muda a frequência do ecrã para a do filme (ex.: 23,976 Hz num filme a 24p) — tira os
     * pequenos solavancos nos movimentos de câmara. Volta ao normal ao sair do leitor.
     */
    private fun matchFrameRate() {
        if (!isTv || Build.VERSION.SDK_INT < 23 || Prefs.str("matchFps", "1") != "1") return
        val fps = player?.videoFormat?.frameRate ?: return
        if (fps <= 0f) return
        val key = Session.videoId + fps
        if (fpsMatchedFor == key) return
        fpsMatchedFor = key
        try {
            @Suppress("DEPRECATION")
            val d = if (Build.VERSION.SDK_INT >= 30) display ?: windowManager.defaultDisplay else windowManager.defaultDisplay
            val cur = d.mode
            fun fits(r: Float) = kotlin.math.abs(r - fps) < 0.05f || kotlin.math.abs(r - 2 * fps) < 0.1f
            if (fits(cur.refreshRate)) return
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight && fits(it.refreshRate) }
                .minByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
            PlayLog.add("🖥 Ecrã ajustado a ${"%.3f".format(best.refreshRate)} Hz (vídeo a ${"%.3f".format(fps)} fps)")
        } catch (e: Exception) {
        }
    }

    private fun resetDisplayMode() {
        if (Build.VERSION.SDK_INT >= 23 && window.attributes.preferredDisplayModeId != 0) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
        }
    }

    /** HDR do vídeo que está a tocar: «HDR10», «HLG», «Dolby Vision» ou null (SDR). */
    private fun videoHdr(): String? {
        val f = player?.videoFormat ?: return null
        if (f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION || f.codecs?.startsWith("dv", true) == true) return "Dolby Vision"
        return when (f.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        }
    }

    private var hdrAnnounced = ""

    /** Mostra uma vez por vídeo se está em HDR e se o ecrã o mostra de verdade. */
    private fun announceHdr() {
        val h = videoHdr() ?: return
        val key = Session.videoId + h
        if (hdrAnnounced == key) return
        hdrAnnounced = key
        val screen = com.hortencioflix.tv.data.DeviceCaps
        val msg = when {
            screen.hdrTypes.contains(h) || (h == "HDR10" && screen.hdrTypes.contains("HDR10+")) -> "✨ $h — o teu ecrã mostra este vídeo em HDR"
            screen.hdr -> "$h — o ecrã suporta ${screen.hdrTypes.joinToString("/")}, não $h: cores adaptadas"
            else -> "$h — o teu ecrã não é HDR: cores convertidas para SDR"
        }
        showOsd(msg, 4000)
        updateOverlay()
    }

    private fun updateOverlay() {
        if (Session.live) {
            val ch = Session.channels.getOrNull(channelIndex)
            topTitle.text = ch?.name ?: ""
            val epgNow = if (ch != null) com.hortencioflix.tv.data.Epg.nowNext(ch).first else null
            topSub.text = if (ch == null) "" else if (epgNow != null) "▶ ${epgNow.title} · até ${com.hortencioflix.tv.data.Epg.hhmm(epgNow.end)}   ·   Canal ${channelIndex + 1}/${Session.channels.size}" else "Canal ${channelIndex + 1} de ${Session.channels.size}  ·  ${ch.group}"
            btnSources.text = "Canais"
        } else {
            val meta = Session.meta
            val ep = Session.episodes.firstOrNull { it.id == Session.videoId }
            val s = Session.streams.getOrNull(streamIndex)
            topTitle.text = meta?.name ?: ""
            val parts = ArrayList<String>()
            if (ep != null) parts.add(ep.label + (if (ep.name.isNotEmpty()) " · " + ep.name else ""))
            val tags = listOfNotNull(s?.quality?.takeIf { it > 0 }?.let { qualityLabel(it) }, videoHdr())
            if (tags.isNotEmpty()) parts.add(tags.joinToString(" · "))
            topSub.text = parts.joinToString("   ·   ")
            btnSources.text = "Fontes"
        }
        fade(topBar, controlsVisible)
        btnNext.visibility = if (controlsVisible && !Session.live && nextEpisodeId() != null) View.VISIBLE else View.GONE
    }

    private fun source(url: String, headers: Map<String, String>, item: MediaItem) =
        DefaultMediaSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(
                this,
                OkHttpDataSource.Factory(Http.client)
                    .setUserAgent("Hortencioflix-Android/1.0")
                    .setDefaultRequestProperties(headers)
            )
        ).createMediaSource(item)

    private fun playStream(index: Int, startMs: Long) {
        val p = player ?: return
        val s = Session.streams.getOrNull(index) ?: return
        streamIndex = index
        Session.streamIndex = index
        // Se a app morrer a meio do arranque desta fonte, no próximo arranque ela fica marcada como má.
        Prefs.put("playGuard", Addons.sourceSignature(s))
        val b = MediaItem.Builder().setUri(s.url)
        if (s.url.contains(".m3u8", true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (Prefs.subLang != "off") {
            b.setSubtitleConfigurations(Session.subtitles.mapIndexed { i, sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(if (sub.url.contains(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(iso2(sub.lang))
                    .setLabel(sub.label)
                    .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            })
        }
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, qualityCap).build()
        p.setMediaSource(source(s.url, s.headers, b.build()), startMs)
        p.prepare()
        p.playWhenReady = true
        val meta = Session.meta
        val ep = Session.episodes.firstOrNull { it.id == Session.videoId }
        val q = qualityLabel(s.quality)
        val where = if (startMs > 0) "\nA retomar em ${formatTime(startMs / 1000)}" else ""
        beginLoad((if (failedSources.isEmpty()) "A preparar o vídeo…" else "A procurar a melhor fonte…") + where)
        updateOverlay()
        if (!isTv && index == Session.streamIndex && startMs == Session.startAt * 1000 && Prefs.str("gestureHint") != "1") {
            Prefs.put("gestureHint", "1")
            showOsd("Dica: 2 toques nas laterais para ±10 s  ·  deslizar para cima/baixo para brilho e volume", 6000)
        }
    }

    private fun playChannel(i: Int) {
        val list = Session.channels
        if (list.isEmpty()) return
        val idx = ((i % list.size) + list.size) % list.size
        channelIndex = idx
        Session.channelIndex = idx
        val ch = list[idx]
        Prefs.put("lastChannel", ch.url)
        Prefs.addRecentChannel(ch)
        val token = ++zapToken
        beginLoad("A sintonizar ${ch.name}…")
        updateOverlay()
        showOsd("${idx + 1}.  ${ch.name}" + (if (isTv) "\n▲▼ mudar de canal" else ""), 3000)
        if (ch.url.startsWith("stremio:")) {
            // Canal de um add-on de TV: pedir as fontes ao add-on.
            player?.stop()
            val rest = ch.url.removePrefix("stremio:")
            val type = rest.substringBefore(':')
            val id = rest.substringAfter(':')
            lifecycleScope.launch {
                val streams = try { Addons.streams(type, id) } catch (e: Exception) { emptyList() }
                if (token != zapToken) return@launch
                chStreams = streams
                chStreamIdx = 0
                if (streams.isEmpty()) { liveFail("Este canal não tem fontes disponíveis agora."); return@launch }
                playLiveUrl(streams[0].url, streams[0].headers)
            }
        } else {
            chStreams = emptyList()
            chStreamIdx = 0
            playLiveUrl(ch.url, emptyMap())
        }
    }

    private fun playLiveUrl(url: String, headers: Map<String, String>) {
        val p = player ?: return
        val b = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8", true) || triedHls) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaSource(source(url, headers, b.build()))
        p.prepare()
        p.playWhenReady = true
        loadStartedAt = System.currentTimeMillis()
    }

    private fun liveFail(msg: String) {
        loadStartedAt = 0
        triedHls = false
        showLoading(msg + if (isTv) "\n▲▼ para outro canal" else "\nToca em «Canais» para escolher outro")
        loadingBox.findViewById<View>(R.id.loading_spin).visibility = View.GONE
    }

    /** Fonte demorou demasiado sem começar. */
    private fun onTimeout() {
        loadStartedAt = 0
        if (Session.live) {
            if (chStreamIdx + 1 < chStreams.size) {
                chStreamIdx++
                playLiveUrl(chStreams[chStreamIdx].url, chStreams[chStreamIdx].headers)
            } else liveFail("O canal não respondeu.")
            return
        }
        if (streamIndex + 1 < Session.streams.size) {
            failedSources.add(streamIndex)
            SourceStats.fail(Session.streams.getOrNull(streamIndex))
            PlayLog.add("⌛ ${PlayLog.label(Session.streams.getOrNull(streamIndex))} — não respondeu a tempo")
            val next = SourcePicker.nextAfterFail(Session.streams, failedSources, streamIndex) ?: (streamIndex + 1)
            playStream(next, player?.currentPosition ?: 0L)
        } else {
            hideLoading()
            dialog().setTitle("Nenhuma fonte respondeu")
                .setMessage("Tentei todas as fontes disponíveis sem sucesso.")
                .setPositiveButton("Escolher fonte") { _, _ -> chooseSource() }
                .setNegativeButton("Sair") { _, _ -> exit() }
                .show()
        }
    }

    @Suppress("DEPRECATION")
    private fun networkUp(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        else cm.activeNetworkInfo?.isConnected == true
    } catch (e: Exception) { true }

    private var netWaitPos = -1L
    private val netCheck = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || netWaitPos < 0) return
            if (networkUp()) {
                val pos = netWaitPos
                netWaitPos = -1L
                PlayLog.add("📶 A Internet voltou — a continuar em ${pos / 60000} min")
                if (Session.live) playChannel(channelIndex) else playStream(streamIndex, pos)
            } else handler.postDelayed(this, 3000)
        }
    }

    private fun waitForNetwork(pos: Long) {
        if (netWaitPos >= 0) return
        netWaitPos = pos
        if (!Session.live) saveProgress(false)
        showLoading("Sem ligação à Internet — continuo sozinho quando voltar.")
        PlayLog.add("📵 Sem Internet a meio do vídeo — à espera")
        handler.postDelayed(netCheck, 3000)
    }

    private fun handleError(error: PlaybackException) {
        if (Session.live && !networkUp()) { waitForNetwork(0L); return }
        if (Session.live) {
            val cur = if (chStreams.isNotEmpty()) chStreams.getOrNull(chStreamIdx)?.url.orEmpty() else Session.channels.getOrNull(channelIndex)?.url.orEmpty()
            val hdr = chStreams.getOrNull(chStreamIdx)?.headers ?: emptyMap()
            if (!triedHls && !cur.contains(".m3u8", true)) {
                triedHls = true
                playLiveUrl(cur, hdr)
                return
            }
            triedHls = false
            if (chStreamIdx + 1 < chStreams.size) {
                chStreamIdx++
                playLiveUrl(chStreams[chStreamIdx].url, chStreams[chStreamIdx].headers)
                return
            }
            liveFail("Canal indisponível (${error.errorCodeName}).")
            return
        }
        val pos = player?.currentPosition ?: 0L
        // Sem Internet: não é culpa da fonte — espera que volte e continua sozinho no mesmo ponto
        if (!networkUp()) { waitForNetwork(pos); return }
        // Já estava a tocar e o servidor recusou (link do debrid expirado): uma vez a mesma fonte, com link novo
        val httpErr = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (httpErr && reachedReady && nowMs - sameRetriedAt > 120_000) {
            sameRetriedAt = nowMs
            PlayLog.add("🔁 Link recusado a meio — a reabrir a mesma fonte")
            showLoading("A retomar…")
            playStream(streamIndex, pos)
            return
        }
        val netErr = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        if (netErr && netRetries < 3) {
            // Falha de ligação (não da fonte): tenta a mesma fonte, na mesma posição, antes de mudar.
            netRetries++
            showLoading("Sem ligação — a tentar de novo ($netRetries/3)…")
            handler.postDelayed({ if (!isFinishing && !isDestroyed) playStream(streamIndex, pos) }, 3000L)
            return
        }
        failedSources.add(streamIndex)
        Prefs.remove("playGuard")
        PlayLog.add("❌ ${PlayLog.label(Session.streams.getOrNull(streamIndex))} — ${error.errorCodeName}: ${(error.cause?.message ?: error.message).orEmpty()}")
        SourceStats.fail(Session.streams.getOrNull(streamIndex))
        if (failedSources.size >= 5 && failedSources.size % 5 == 0) {
            // Várias fontes seguidas falharam: perguntar em vez de percorrer dezenas de fontes.
            hideLoading()
            val parsing = error.errorCode in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
            dialog().setTitle("${failedSources.size} fontes falharam")
                .setMessage((com.hortencioflix.tv.data.TorboxApi.warning()?.let { "$it\n\n" } ?: "") + if (parsing) "Estes ficheiros não abrem no leitor da app (formato ou serviço debrid com erro). Podes tentar outras fontes, escolher uma, ou abrir noutra app (ex.: VLC).\n\nÚltimo erro: ${error.errorCodeName}"
                    else "Último erro: ${error.errorCodeName}\n${error.message ?: ""}")
                .setPositiveButton("Tentar mais fontes") { _, _ ->
                    SourcePicker.nextAfterFail(Session.streams, failedSources, streamIndex)?.let { playStream(it, pos) } ?: chooseSource()
                }
                .setNeutralButton("Abrir noutra app") { _, _ -> openExternal() }
                .setNegativeButton("Escolher fonte") { _, _ -> chooseSource() }
                .show()
            return
        }
        val alt = SourcePicker.nextAfterFail(Session.streams, failedSources, streamIndex)
        if (alt != null) {
            // Troca em silêncio: o pormenor fica em Diagnóstico › Últimas reproduções.
            playStream(alt, pos)
        } else {
            hideLoading()
            dialog().setTitle("Não foi possível reproduzir")
                .setMessage("Nenhuma das fontes funcionou.\n\nErro: ${error.errorCodeName}\n${error.message ?: ""}")
                .setPositiveButton("Escolher fonte") { _, _ -> chooseSource() }
                .setNegativeButton("Sair") { _, _ -> exit() }
                .show()
        }
    }

    /** A fonte tocou o vídeo «downloading to debrid»: passa a outra pronta, sem marcar nada como visto. */
    /** A fonte acabou sem mostrar imagem: tenta a seguinte; sem mais nenhuma, explica e deixa escolher. */
    private fun sourceEmpty(resumeAt: Long) {
        val p = player ?: return
        p.stop()
        Prefs.remove("playGuard")
        PlayLog.add("⚠ ${PlayLog.label(Session.streams.getOrNull(streamIndex))} — " + (if (resumeAt > 0) "parou a meio (${resumeAt / 60000} min)" else "terminou sem tocar (link vazio ou expirado)"))
        SourceStats.fail(Session.streams.getOrNull(streamIndex))
        failedSources.add(streamIndex)
        val alt = SourcePicker.nextAfterFail(Session.streams, failedSources, streamIndex)
        if (alt != null) {
            showLoading(if (resumeAt > 0) "A ligação caiu — a continuar noutra fonte…" else "A procurar a melhor fonte…")
            playStream(alt, if (resumeAt > 0) resumeAt else Session.startAt)
            return
        }
        hideLoading()
        if (resumeAt > 0) saveProgress(false)
        dialog().setTitle("Não foi possível reproduzir")
            .setMessage("As fontes tentadas terminaram sem mostrar imagem (${failedSources.size} tentadas). Costuma ser o serviço debrid a recusar o link." +
                (com.hortencioflix.tv.data.TorboxApi.warning()?.let { "\n\n$it" } ?: "") +
                "\n\nVê os detalhes em Definições › Diagnóstico › Últimas reproduções.")
            .setPositiveButton("Escolher fonte") { _, _ -> chooseSource() }
            .setNegativeButton("Sair") { _, _ -> exit() }
            .show()
    }

    private fun sourceNotReady() {
        val p = player ?: return
        p.stop()
        Prefs.remove("playGuard")
        PlayLog.add("⏳ ${PlayLog.label(Session.streams.getOrNull(streamIndex))} — vídeo de espera/erro do debrid (${p.duration / 1000} s)")
        SourceStats.fail(Session.streams.getOrNull(streamIndex))
        failedSources.add(streamIndex)
        val alt = SourcePicker.nextAfterFail(Session.streams, failedSources, streamIndex)
        if (alt != null) {
            showLoading("A procurar a melhor fonte…")
            playStream(alt, Session.startAt)
        } else {
            hideLoading()
            // A fonte que tocou o vídeo de espera já está a descarregar: vigiar e avisar quando estiver pronta.
            val cur = Session.streams.getOrNull(streamIndex)
            val h = cur?.let { com.hortencioflix.tv.data.TorboxApi.hashOf(it.url) }
            val m = Session.meta
            if (h != null && m != null && Prefs.torboxKey.isNotEmpty()) {
                com.hortencioflix.tv.data.TorboxApi.watch(h, m, Session.videoId, qualityLabel(cur?.quality ?: 0))
                com.hortencioflix.tv.TorboxWatchWorker.schedule(this)
            }
            dialog().setTitle("Ainda a descarregar")
                .setMessage("Nenhuma fonte deste título está pronta no teu serviço debrid. O download já começou" +
                    (if (h != null) " — avisamos-te com uma notificação quando estiver pronto." else " — tenta de novo daqui a alguns minutos ou escolhe outra fonte.") +
                    (com.hortencioflix.tv.data.TorboxApi.warning()?.let { "\n\n$it" } ?: ""))
                .setPositiveButton("Escolher fonte") { _, _ -> chooseSource() }
                .setNegativeButton("Sair") { _, _ -> exit() }
                .show()
        }
    }

    // ---------- Gestos (telemóvel) ----------

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    private fun setupGestures() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        var mode = 0
        var startVal = 0f
        val det = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                mode = 0
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) playerView.hideController() else playerView.showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (Session.live) return false
                seekBy(if (e.x < playerView.width / 2f) -10000L else 10000L)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val s = e1 ?: return false
                val dy = s.y - e2.y
                if (mode == 0) {
                    if (abs(dy) < dp(18) || abs(dy) < abs(e2.x - s.x)) return false
                    // Arrastar para baixo a partir do meio do ecrã: minimizar (mini-leitor).
                    if (s.x > playerView.width / 3f && s.x < playerView.width * 2 / 3f && dy < 0) { mode = 3; return true }
                    mode = if (s.x < playerView.width / 2f) 1 else 2
                    startVal = if (mode == 1) currentBrightness() else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                }
                if (mode == 3) {
                    if (-dy > playerView.height * 0.25f) {
                        mode = 4
                        if (pipSupported()) {
                            playerView.hideController()
                            @Suppress("DEPRECATION")
                            onBackPressed()
                        }
                    }
                    return true
                }
                if (mode == 4) return true
                val v = (startVal + dy / (playerView.height * 0.8f)).coerceIn(0f, 1f)
                if (mode == 1) {
                    val lp = window.attributes
                    lp.screenBrightness = v.coerceAtLeast(0.02f)
                    window.attributes = lp
                    showOsd("☀  Brilho ${(v * 100).toInt()}%", 1000)
                } else {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
                    showOsd("🔊  Volume ${(v * 100).toInt()}%", 1000)
                }
                return true
            }
        })
        // Pinça: afastar os dedos preenche o ecrã, juntar volta a ajustar.
        var scaleAcc = 1f
        val pinch = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: android.view.ScaleGestureDetector): Boolean { scaleAcc = 1f; return true }
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean { scaleAcc *= d.scaleFactor; return true }
            override fun onScaleEnd(d: android.view.ScaleGestureDetector) {
                if (scaleAcc > 1.1f) { setResize(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM); showOsd("Preencher ecrã", 1200) }
                else if (scaleAcc < 0.9f) { setResize(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT); showOsd("Ajustar ao ecrã", 1200) }
            }
        })
        playerView.setOnTouchListener { _, ev ->
            if (locked) {
                if (ev.action == MotionEvent.ACTION_UP) showUnlock()
            } else {
                pinch.onTouchEvent(ev)
                if (!pinch.isInProgress && ev.pointerCount == 1) det.onTouchEvent(ev)
            }
            true
        }
    }

    // ---------- Introdução (IntroDB) ----------

    private fun loadSegments() {
        val vid = Session.videoId
        val parts = vid.split(":")
        if (parts.isEmpty() || !parts[0].startsWith("tt")) return
        val type = Session.meta?.type ?: ""
        var url = "https://api.introdb.app/segments?imdb_id=${parts[0]}"
        url += if (type == "series" && parts.size >= 3) "&season=${parts[1]}&episode=${parts[2]}" else "&is_movie=true"
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) { Http.getJson(url) }
            if (Session.videoId != vid || d == null) return@launch
            d.optJSONObject("outro")?.let { o -> if (o.has("start_sec")) outroStart = (o.optDouble("start_sec") * 1000).toLong() }
            d.optJSONObject("recap")?.let { o ->
                if (o.has("start_sec") && o.has("end_sec")) {
                    recapStart = (o.optDouble("start_sec") * 1000).toLong()
                    recapEnd = (o.optDouble("end_sec") * 1000).toLong()
                }
            }
            val intro = d.optJSONObject("intro") ?: return@launch
            if (intro.has("start_sec") && intro.has("end_sec")) {
                introStart = (intro.optDouble("start_sec") * 1000).toLong()
                introEnd = (intro.optDouble("end_sec") * 1000).toLong()
            }
        }
    }

    /** Posição para onde saltar (fim do resumo ou da introdução), se estivermos dentro de um. */
    private fun skipTarget(): Long? {
        val p = player ?: return null
        val pos = p.currentPosition
        if (recapEnd > 0 && pos >= recapStart && pos < recapEnd - 2000) return recapEnd
        if (introEnd > 0 && pos >= introStart && pos < introEnd - 2000) return introEnd
        return null
    }

    private fun inIntro(): Boolean = skipTarget() != null

    private fun checkIntro() {
        val t = skipTarget()
        if (t != null && !inPip && !locked) {
            skipHint.text = if (t == recapEnd) "Saltar resumo  ⏭" else "Saltar introdução  ⏭"
            skipHint.visibility = View.VISIBLE
        } else skipHint.visibility = View.GONE
        checkSleep()
    }

    // ---------- Bloqueio, temporizador, velocidade, informação técnica ----------

    private fun setLocked(on: Boolean) {
        locked = on
        playerView.useController = !on
        if (on) {
            playerView.hideController()
            controlsVisible = false
            updateOverlay()
            showUnlock()
            toast("Ecrã bloqueado. Toca no ecrã para ver o botão de desbloquear.")
        } else {
            findViewById<View>(R.id.btn_unlock).visibility = View.GONE
            playerView.showController()
        }
    }

    private fun showUnlock() {
        findViewById<View>(R.id.btn_unlock).visibility = View.VISIBLE
        handler.removeCallbacks(hideUnlock)
        handler.postDelayed(hideUnlock, 3000)
    }

    private fun checkSleep() {
        if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
            sleepAt = 0
            toast("Temporizador: a reprodução terminou.")
            exit()
        }
    }

    private fun chooseSleep() {
        val mins = listOf(15, 30, 45, 60, 90)
        val labels = ArrayList<String>()
        labels.add(if (sleepAt > 0 || sleepEndOfEpisode) "Desligar temporizador" else "Sem temporizador")
        labels.addAll(mins.map { "$it minutos" })
        labels.add("No fim deste episódio/filme")
        glassChoice("Temporizador para parar", labels) { i ->
            when {
                i == 0 -> { sleepAt = 0; sleepEndOfEpisode = false; toast("Temporizador desligado") }
                i <= mins.size -> { sleepAt = System.currentTimeMillis() + mins[i - 1] * 60_000L; sleepEndOfEpisode = false; toast("Vai parar daqui a ${mins[i - 1]} minutos") }
                else -> { sleepEndOfEpisode = true; sleepAt = 0; toast("Vai parar no fim deste vídeo") }
            }
        }
    }

    private fun chooseSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val cur = player?.playbackParameters?.speed ?: 1f
        glassChoice("Velocidade", speeds.map { (if (it == cur) "● " else "") + (if (it == 1f) "Normal" else "${it}×".replace('.', ',')) }) {
            player?.setPlaybackSpeed(speeds[it])
        }
    }

    private fun techInfo() {
        val p = player ?: return
        val lines = ArrayList<String>()
        p.videoFormat?.let { f ->
            lines.add("Vídeo: ${f.width}×${f.height}" + (if (f.frameRate > 0) " · ${"%.0f".format(f.frameRate)} fps" else "") + (f.codecs?.let { " · $it" } ?: f.sampleMimeType?.let { " · $it" } ?: ""))
            if (f.bitrate > 0) lines.add("Débito: ${f.bitrate / 1000} kbps")
        }
        p.audioFormat?.let { f ->
            lines.add("Áudio: " + listOfNotNull(f.sampleMimeType?.substringAfter('/'), if (f.channelCount > 0) "${f.channelCount} canais" else null, f.language).joinToString(" · "))
        }
        lines.add("HDR do vídeo: " + (videoHdr() ?: "não (SDR)"))
        val dc = com.hortencioflix.tv.data.DeviceCaps
        lines.add("HDR do ecrã: " + (if (dc.hdr) dc.hdrTypes.joinToString(", ") else "não suporta"))
        lines.add("Buffer: ${p.totalBufferedDuration / 1000} s")
        if (!Session.live) Session.streams.getOrNull(streamIndex)?.let { s ->
            lines.add("Fonte ${streamIndex + 1}/${Session.streams.size}: ${s.addon}")
            lines.add("Servidor: ${Uri.parse(s.url).host ?: "?"}")
        }
        lines.add("Velocidade: ${p.playbackParameters.speed}×")
        dialog().setTitle("Informação técnica").setMessage(lines.joinToString("\n")).setPositiveButton("OK", null).show()
    }

    // ---------- Progresso ----------

    private fun saveProgress(push: Boolean) {
        val p = player ?: return
        if (Session.live) return
        val meta = Session.meta ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0 || dur == C.TIME_UNSET) return
        val vid = Session.videoId
        if (pos > dur * 0.92) {
            Prefs.remove("playSession")
            Prefs.removeProgress(meta.id)
            Prefs.setWatched(listOf(vid), true)
            com.hortencioflix.tv.data.WatchNext.remove(this, meta.id)
        }
        else if (pos > 15000) {
            Prefs.saveProgress(meta.id, vid, pos / 1000, dur / 1000, meta.name, meta.poster, meta.type)
            // se o Android fechar a app em segundo plano, ao voltar abre este título no mesmo ponto
            Prefs.put("playSession", org.json.JSONObject().put("id", meta.id).put("type", meta.type).put("name", meta.name)
                .put("poster", meta.poster).put("bg", meta.background).put("at", System.currentTimeMillis()).toString())
            // cartão no ecrã inicial do Google TV (só ao sair ou a cada gravação com envio, para não escrever a toda a hora)
            if (push) com.hortencioflix.tv.data.WatchNext.update(this, meta, vid, pos, dur)
        }
        if (push && pos > 15000) Thread { try { Stremio.pushProgress(meta, vid, pos, dur) } catch (e: Exception) {} }.start()
    }

    // ---------- Trakt ----------

    private fun scrobble(action: String) {
        val m = Session.meta ?: return
        if (!com.hortencioflix.tv.data.Trakt.linked) return
        val p = player ?: return
        val dur = p.duration
        val pct = if (dur > 0 && dur != C.TIME_UNSET) p.currentPosition.toFloat() / dur else 0f
        val vid = Session.videoId
        Thread { try { com.hortencioflix.tv.data.Trakt.scrobble(action, m, vid, pct) } catch (e: Exception) {} }.start()
    }

    // ---------- Próximo episódio ----------

    private fun nextEpisodeId(): String? {
        val eps = Session.episodes
        val i = eps.indexOfFirst { it.id == Session.videoId }
        if (i < 0 || i + 1 >= eps.size) return null
        return eps[i + 1].id
    }

    private fun onEnded() {
        if (Session.live) return
        // Terminou em menos de 2 minutos: era um vídeo de aviso do debrid, não o filme — não fechar nem marcar visto.
        val d = player?.duration ?: 0L
        val t = Session.meta?.type ?: ""
        if (!reachedLong && d in 1..120_000 && (t == "movie" || t == "series")) { sourceNotReady(); return }
        // «Terminou» sem nunca ter tocado (ligação cortada, ficheiro vazio, link expirado): é uma falha da fonte,
        // não o fim do filme — tentar outra em vez de voltar em silêncio para a página do título.
        val pos = player?.currentPosition ?: 0L
        if (!reachedReady || (pos < 5_000 && d <= 0)) { sourceEmpty(0); return }
        // Acabou a meio (mais de 5 min antes do fim de um filme/episódio longo): a ligação caiu — continuar noutra fonte
        if (d > 600_000 && pos < d - 300_000) { sourceEmpty(pos); return }
        scrobble("stop")
        saveProgress(true)
        if (sleepEndOfEpisode) { toast("Temporizador: a reprodução terminou."); exit(); return }
        if (nextEpisodeId() != null) playNextEpisode() else exit()
    }

    private fun playNextEpisode() {
        val next = nextEpisodeId() ?: return toast("Não há próximo episódio.")
        val meta = Session.meta ?: return
        if (loadingNext) return
        loadingNext = true
        saveProgress(true)
        val ep = Session.episodes.firstOrNull { it.id == next }
        if (!(Session.directStream == null && prefetchFor == next && prefetched != null))
            showOsd("A preparar ${ep?.label ?: "o próximo episódio"}…", 15000)
        lifecycleScope.launch {
            val direct = Session.directStream
            val pre = if (direct == null && prefetchFor == next) prefetched else null
            val streams = pre?.first ?: if (direct != null) listOfNotNull(direct(next)) else try { Addons.streams(meta.type, next) } catch (e: Exception) { emptyList() }
            val subs = pre?.second ?: if (direct != null) emptyList() else try { Addons.subtitles(meta.type, next) } catch (e: Exception) { emptyList() }
            loadingNext = false
            if (streams.isEmpty()) {
                showOsd("Sem fontes para o próximo episódio.", 6000)
                return@launch
            }
            if (direct == null && !SourcePicker.hasReady(streams)) {
                showOsd("O próximo episódio ainda não está pronto no teu serviço debrid.", 8000)
                return@launch
            }
            Session.vod(meta, next, streams, 0, subs, Session.episodes, Prefs.resumeFor(next, ""))
            Session.directStream = direct
            startPlayback()
        }
    }

    // ---------- Menus ----------

    private fun showMenu() {
        val opts = ArrayList<Pair<String, () -> Unit>>()
        if (Session.live) {
            opts.add("Lista de canais" to { chooseChannel() })
            opts.add("Áudio" to { chooseTrack(C.TRACK_TYPE_AUDIO) })
            opts.add("Legendas" to { chooseTrack(C.TRACK_TYPE_TEXT) })
        } else {
            opts.add("Legendas" to { chooseTrack(C.TRACK_TYPE_TEXT) })
            opts.add("Áudio" to { chooseTrack(C.TRACK_TYPE_AUDIO) })
            opts.add("Mudar de fonte (${streamIndex + 1}/${Session.streams.size})" to { chooseSource() })
            if (nextEpisodeId() != null) opts.add("Próximo episódio" to { playNextEpisode() })
            if (introEnd > 0) opts.add("Saltar introdução" to { player?.seekTo(introEnd) })
            if (recapEnd > 0) opts.add("Saltar resumo" to { player?.seekTo(recapEnd) })
            opts.add("Velocidade" to { chooseSpeed() })
            if (introEnd <= 0) opts.add("⏩  Saltar 85 s" to { seekBy(85_000) })
        }
        if (!Session.live || Session.streams.isNotEmpty()) opts.add("↗  Abrir noutra app (VLC, Web Video Caster…)" to { openExternal() })
        opts.add("Qualidade de vídeo" to { chooseQuality() })
        opts.add("Ajuste da imagem" to { chooseResize() })
        opts.add((if (sleepAt > 0 || sleepEndOfEpisode) "⏲  Temporizador (ativo)" else "⏲  Temporizador") to { chooseSleep() })
        opts.add("ℹ  Informação técnica" to { techInfo() })
        glassChoice("Opções", opts.map { it.first }) { opts[it].second.invoke() }
    }

    /**
     * Troca de qualidade: procura, entre as fontes, a melhor com a resolução pedida (e, se faltar,
     * volta a pedir aos add-ons sem o limite de qualidade) e continua do mesmo ponto.
     */
    private fun chooseQuality() {
        val p = player ?: return
        if (Session.live || Session.meta?.id?.startsWith("tt") != true) {
            val labels = listOf("Automática", "Até 1080p", "Até 720p", "Até 480p")
            val heights = listOf(Int.MAX_VALUE, 1080, 720, 480)
            glassChoice("Qualidade (fontes adaptativas)", labels) { i ->
                qualityCap = heights[i]
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, qualityCap).build()
                toast(if (i == 0) "Qualidade automática" else "Limitada a ${labels[i].removePrefix("Até ")} — se a fonte for um ficheiro único, não muda.")
            }
            return
        }
        val list = Session.streams
        val cur = list.getOrNull(streamIndex)?.quality ?: 0
        val counts = list.filter { it.quality > 0 }.groupingBy { it.quality }.eachCount().toSortedMap(compareByDescending { it })
        val targets = ArrayList<Int>()
        val labels = ArrayList<String>()
        targets.add(0); labels.add("✨  Automática — a melhor fonte")
        for ((q, n) in counts) { targets.add(q); labels.add((if (q == cur) "● " else "") + SourcePicker.qualityLabel(q) + "  ·  $n fonte" + (if (n > 1) "s" else "")) }
        for (q in listOf(2160, 1080, 720, 480)) if (q !in counts.keys && Session.directStream == null) { targets.add(q); labels.add("🔎  Procurar " + SourcePicker.qualityLabel(q)) }
        glassChoice("Qualidade", labels) { i -> switchQuality(targets[i]) }
    }

    private val rebuffers = ArrayList<Long>()
    private var lastSeekAt = 0L

    /** 3 pausas para carregar em 3 minutos: a rede não aguenta esta qualidade — baixa um nível sozinha. */
    private fun noteRebuffer() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSeekAt < 4000) return   // carregar depois de saltar na barra é normal
        rebuffers.add(now)
        rebuffers.removeAll { now - it > 180_000 }
        if (rebuffers.size < 3 || Session.directStream != null) return
        val cur = Session.streams.getOrNull(streamIndex)?.quality ?: return
        val lower = when { cur >= 2160 -> 1080; cur >= 1080 -> 720; cur >= 720 -> 480; else -> return }
        if (Session.streams.none { it.cached && it.quality in 1..lower }) return
        rebuffers.clear()
        val mode = Prefs.str("qualAuto", "ask")
        if (mode == "off" || qualDropDeclined) return
        if (mode == "auto") {
            PlayLog.add("📉 Muitas pausas a carregar — a baixar de ${qualityLabel(cur)} para ${qualityLabel(lower)}")
            toast("A rede está lenta — a mudar para ${qualityLabel(lower)} para evitar pausas")
            switchQuality(lower)
            return
        }
        // por defeito pergunta: o vídeo continua a tocar enquanto decides
        glassChoice("A rede está lenta (${qualityLabel(cur)})", listOf("Mudar para ${qualityLabel(lower)} — menos pausas", "Manter ${qualityLabel(cur)} neste vídeo")) { i ->
            if (i == 0) { PlayLog.add("📉 Rede lenta — mudou para ${qualityLabel(lower)} (escolhido)"); switchQuality(lower) }
            else qualDropDeclined = true
        }
    }

    /** «Manter» na pergunta da rede lenta: não volta a perguntar até ao próximo vídeo. */
    private var qualDropDeclined = false

    private fun switchQuality(target: Int) {
        val meta = Session.meta
        lifecycleScope.launch {
            var list = Session.streams
            var idx = SourcePicker.indexFor(list, target)
            val exact = idx != null && (target == 0 || list[idx].quality == target)
            if (!exact && meta != null && Session.directStream == null) {
                showLoading("A procurar fontes em ${SourcePicker.qualityLabel(target)}…")
                val fresh = try { Addons.streams(meta.type, Session.videoId, ignoreMax = true) } catch (e: Exception) { emptyList() }
                hideLoading()
                if (fresh.isNotEmpty()) {
                    val playing = list.getOrNull(streamIndex)?.url
                    list = fresh
                    Session.streams = fresh
                    idx = SourcePicker.indexFor(fresh, target)
                    if (playing != null && idx != null && fresh[idx].url == playing) { toast("Já estás na melhor fonte desta qualidade."); streamIndex = idx; return@launch }
                }
            }
            val i = idx ?: run { toast("Não há outras fontes."); return@launch }
            val s = list[i]
            if (i == streamIndex && s.url == Session.streams.getOrNull(streamIndex)?.url && exact && player?.currentMediaItem != null && failedSources.isEmpty() && qualityCap == (if (target == 0) Int.MAX_VALUE else target)) {
                toast("Já estás nesta qualidade."); return@launch
            }
            qualityCap = if (target == 0) Int.MAX_VALUE else target
            failedSources.clear()
            toast(if (!exact && target != 0) "Sem ${SourcePicker.qualityLabel(target)}: a usar ${SourcePicker.qualityLabel(s.quality)}" else "Fonte em ${SourcePicker.qualityLabel(s.quality)}")
            playStream(i, player?.currentPosition ?: 0L)
        }
    }

    /** Entrega a ligação a outra app (VLC, MX Player, Web Video Caster para transmitir para a TV…). */
    private fun openExternal() {
        val url = if (Session.live) Session.channels.getOrNull(channelIndex)?.url else Session.streams.getOrNull(streamIndex)?.url
        if (url.isNullOrEmpty() || url.startsWith("stremio:")) { toast("Esta fonte não pode ser aberta noutra app."); return }
        player?.pause()
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
            startActivity(Intent.createChooser(i, "Abrir com…"))
        } catch (e: Exception) {
            toast("Não há nenhuma app para abrir o vídeo.")
        }
    }

    private fun chooseResize() {
        val names = listOf("Ajustar", "Preencher (cortar)", "Esticar")
        val modes = listOf(
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        )
        glassChoice("Imagem", names) { setResize(modes[it]) }
    }

    /** O modo de imagem escolhido (ajustar/preencher) fica guardado para os próximos vídeos. */
    private fun setResize(mode: Int) {
        playerView.resizeMode = mode
        Prefs.put("resizeMode", mode.toString())
    }

    private fun chooseTrack(type: Int) {
        val p = player ?: return
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (type == C.TRACK_TYPE_TEXT) {
            labels.add("Desligadas")
            actions.add {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }
        }
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (j in 0 until g.length) {
                if (!g.isTrackSupported(j)) continue
                val f = g.getTrackFormat(j)
                val parts = ArrayList<String>()
                f.label?.let { parts.add(it) }
                f.language?.let { if (f.label == null) parts.add(Addons.langName(it)) }
                if (type == C.TRACK_TYPE_AUDIO) {
                    if (f.channelCount > 0) parts.add("${f.channelCount}ch")
                    f.sampleMimeType?.let { parts.add(it.substringAfter("/").uppercase()) }
                }
                if (parts.isEmpty()) parts.add("Faixa ${labels.size + 1}")
                val sel = if (g.isTrackSelected(j)) "✓ " else ""
                labels.add(sel + parts.joinToString("  ·  "))
                val group = g.mediaTrackGroup
                val lng = f.language
                actions.add {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group, j))
                        .build()
                    if (type == C.TRACK_TYPE_AUDIO && !lng.isNullOrEmpty()) Prefs.audioLang = lng
                }
            }
        }
        if (type == C.TRACK_TYPE_AUDIO && actions.size <= 1) {
            toast("Só há uma faixa de áudio.")
            return
        }
        if (type == C.TRACK_TYPE_TEXT) {
            labels.add("🎨  Aspeto das legendas…"); actions.add { subtitleLook() }
            if (!Session.live) {
                labels.add("⏱  Atraso (${subDelayMs / 1000f} s)…"); actions.add { subtitleDelay() }
                labels.add("🔎  Procurar mais legendas…"); actions.add { moreSubtitles() }
            }
        }
        glassChoice(if (type == C.TRACK_TYPE_TEXT) "Legendas" else "Áudio", labels) { actions[it].invoke() }
    }

    private fun chooseSource() {
        val list = Session.streams
        if (list.isEmpty()) return
        val labels = list.mapIndexed { i, s ->
            val q = if (s.quality > 0) (if (s.quality == 2160) "4K" else "${s.quality}p") else ""
            (if (i == streamIndex) "▶ " else "") + listOf(q, s.name.replace("\n", " "), s.title.lineSequence().firstOrNull() ?: "").filter { it.isNotEmpty() }.joinToString("  ·  ")
        }
        glassChoice("Fontes", labels) { playStream(it, player?.currentPosition ?: 0L) }
    }

    private fun chooseChannel() {
        GlassChannelPicker.show(this, Session.channels, channelIndex) { playChannel(it) }
    }

    // ---------- Teclas ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val controls = playerView.isControllerFullyVisible
        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_CAPTIONS -> { showMenu(); return true }
            // Números do comando: escrever o número do canal (ex.: 1 2 → canal 12, muda ao fim de 1,5 s)
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> if (Session.live && Session.channels.size > 1) {
                typeChannelDigit(event.keyCode - KeyEvent.KEYCODE_0); return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> if (Session.live) { playChannel(channelIndex + 1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> if (Session.live) { playChannel(channelIndex - 1); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (Session.live && !controls) { playChannel(channelIndex - 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (Session.live && !controls) { playChannel(channelIndex + 1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (!controls && inIntro()) {
                skipTarget()?.let { player?.seekTo(it) }; skipHint.visibility = View.GONE; return true
            }
            // Tecla premida: os saltos aceleram (10/30 s → 1 min → 2 min).
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!Session.live && !controls) { seekBy(-seekStep(event.repeatCount, 10_000L)); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!Session.live && !controls) { seekBy(seekStep(event.repeatCount, 30_000L)); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    private var chDigits = ""
    private val chCommit = Runnable {
        val n = chDigits.toIntOrNull()
        chDigits = ""
        if (n != null && n in 1..Session.channels.size) playChannel(n - 1)
        else if (n != null) showOsd("Não há canal $n (há ${Session.channels.size})")
    }

    private fun typeChannelDigit(d: Int) {
        if (chDigits.length >= 4) chDigits = ""
        chDigits += d
        val n = chDigits.toInt()
        val name = Session.channels.getOrNull(n - 1)?.name ?: "—"
        showOsd("Canal $chDigits  ·  $name", 1800)
        handler.removeCallbacks(chCommit)
        // com 4 algarismos, ou se não houver canais com mais algarismos, muda já
        if (chDigits.length >= Session.channels.size.toString().length) handler.post(chCommit)
        else handler.postDelayed(chCommit, 1500)
    }

    private fun seekStep(repeat: Int, base: Long): Long = when {
        repeat >= 15 -> 120_000L
        repeat >= 5 -> 60_000L
        else -> base
    }

    private fun seekBy(ms: Long) {
        val p = player ?: return
        val dur = p.duration
        var t = p.currentPosition + ms
        if (t < 0) t = 0
        if (dur > 0 && dur != C.TIME_UNSET && t > dur - 1000) t = dur - 1000
        p.seekTo(t)
        showOsd((if (ms > 0) "⏩ " else "⏪ ") + formatTime(t / 1000) + (if (dur > 0) " / " + formatTime(dur / 1000) else ""), 1500)
    }

    // ---------- Mini-leitor (imagem na imagem) ----------

    private fun pipSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (locked) { showUnlock(); return }
        if (postPlay?.visible == true) { postPlay?.dismiss(); return }
        if (playerView.isControllerFullyVisible) {
            playerView.hideController()
            return
        }
        if (pipSupported() && player?.isPlaying == true && enterPip()) {
            mainToFront()
            return
        }
        exit()
    }

    private fun mainToFront() {
        if (App.mainTaskId == -1) return
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(App.mainTaskId, 0)
        } catch (e: Exception) {
        }
    }

    private fun exit() {
        Prefs.remove("playSession")
        if (!Session.live) scrobble("stop")
        saveProgress(true)
        if (!Session.live) com.hortencioflix.tv.data.RokuSync.syncQuietly()
        player?.pause()
        player?.stop()
        mainToFront()
        finish()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            osd.visibility = View.GONE
            skipHint.visibility = View.GONE
            controlsVisible = false
            updateOverlay()
        } else if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            // O mini-leitor foi fechado (X ou arrastado para fora): parar o som.
            Prefs.remove("playSession")
            closePlayer()
        }
    }

    private fun closePlayer() {
        saveProgress(true)
        player?.pause()
        player?.stop()
        if (!isFinishing) finish()
    }

    override fun onStop() {
        super.onStop()
        Prefs.remove("playGuard")
        saveProgress(true)
        val pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (pip || isChangingConfigurations || isFinishing) return
        if (inPip) {
            // O mini-leitor foi fechado: parar.
            closePlayer()
            return
        }
        // App em segundo plano (botão Início, chamada, outra app): fica em pausa no mesmo ponto.
        // Ao voltar (pelo ícone da app ou pelas recentes) retoma exatamente onde estava.
        bgWasPlaying = player?.playWhenReady == true
        bgAt = android.os.SystemClock.elapsedRealtime()
        player?.pause()
        App.playerInBackground = true
        App.playerTaskId = taskId
        PlayLog.add("⏸ App em segundo plano — leitor em pausa em ${(player?.currentPosition ?: 0L) / 60000} min")
        // ao fim de 30 min em segundo plano liberta o leitor (o progresso fica guardado)
        handler.postDelayed(bgRelease, 30 * 60_000L)
    }

    private val bgRelease = Runnable {
        if (bgAt > 0 && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            App.playerInBackground = false
            closePlayer()
        }
    }

    override fun onStart() {
        super.onStart()
        App.playerInBackground = false
        handler.removeCallbacks(bgRelease)
        if (bgAt <= 0) return
        val away = android.os.SystemClock.elapsedRealtime() - bgAt
        bgAt = 0
        val p = player ?: return
        if (bgWasPlaying && away < 3 * 60_000L) {
            // saída rápida: continua logo (a lógica da pausa longa reabre a fonte se o link tiver expirado)
            p.play()
        } else {
            // muito tempo fora: fica em pausa no ponto exato, com os controlos à vista
            playerView.showController()
            if (isTv) playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
        }
    }

    // ---------- Mini-leitor (imagem na imagem) ----------

    private fun pipAuto() = Prefs.str("pipAuto", "1") == "1"

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val b = PictureInPictureParams.Builder()
        val vs = player?.videoSize
        var ratio = Rational(16, 9)
        if (vs != null && vs.width > 0 && vs.height > 0) {
            val w = (vs.width * vs.pixelWidthHeightRatio).roundToInt().coerceAtLeast(1)
            val r = w.toFloat() / vs.height
            ratio = when { r > 2.39f -> Rational(239, 100); r < 0.42f -> Rational(42, 100); else -> Rational(w, vs.height) }
        }
        b.setAspectRatio(ratio)
        val playing = player?.isPlaying == true
        val actions = ArrayList<android.app.RemoteAction>()
        fun act(icon: Int, title: String, code: Int) {
            val pi = android.app.PendingIntent.getBroadcast(this, code,
                Intent(PIP_ACTION).setPackage(packageName).putExtra("a", code),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            actions.add(android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, icon), title, title, pi))
        }
        if (!Session.live) act(android.R.drawable.ic_media_rew, "Recuar 10 s", 1)
        if (playing) act(android.R.drawable.ic_media_pause, "Pausa", 2) else act(android.R.drawable.ic_media_play, "Continuar", 2)
        if (Session.live) act(android.R.drawable.ic_media_next, "Canal seguinte", 4)
        else act(android.R.drawable.ic_media_ff, "Avançar 30 s", 3)
        b.setActions(actions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: ao ir para o ecrã inicial com o vídeo a tocar, passa sozinho a mini-leitor
            b.setAutoEnterEnabled(pipAuto() && playing)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun updatePip() {
        if (!pipSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try { pipParams()?.let { setPictureInPictureParams(it) } } catch (e: Exception) {}
    }

    private fun enterPip(): Boolean {
        if (!pipSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try { enterPictureInPictureMode(pipParams()!!) } catch (e: Exception) { false }
    }

    // Botão Início com o vídeo a tocar: mini-leitor (no Android 12+ isto já é feito pelo sistema)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (pipAuto() && player?.isPlaying == true && !locked) enterPip()
    }

    private fun registerPipReceiver() {
        if (pipReceiver != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val p = player ?: return
                when (i?.getIntExtra("a", 0)) {
                    1 -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                    2 -> if (p.isPlaying) p.pause() else p.play()
                    3 -> p.seekTo(p.currentPosition + 30_000)
                    4 -> dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_UP))
                }
                updatePip()
            }
        }
        pipReceiver = r
        androidx.core.content.ContextCompat.registerReceiver(this, r, android.content.IntentFilter(PIP_ACTION), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        App.playerInBackground = false
        pipReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        pipReceiver = null
        resetDisplayMode()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
