package com.hortencioflix.tv.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Addons
import com.hortencioflix.tv.data.SourcePicker
import com.hortencioflix.tv.data.Episode
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.Session
import com.hortencioflix.tv.data.Stremio
import com.hortencioflix.tv.data.StreamItem
import com.hortencioflix.tv.data.SubtitleItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** Informação extraída do texto de uma fonte (tamanho, codec, áudio, idioma…). */
data class SourceInfo(val quality: String, val size: String, val seeds: Int, val badges: List<String>, val portuguese: Boolean)

object SourceParser {
    private val sizeRe = Regex("(\\d+(?:[.,]\\d+)?)\\s?(GB|MB|GiB|MiB)", RegexOption.IGNORE_CASE)
    private val seedsRe = Regex("👤\\s?(\\d+)")
    private val flagRe = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")

    fun qualityLabel(q: Int) = when (q) { 2160 -> "4K"; 1440 -> "1440p"; 1080 -> "1080p"; 720 -> "720p"; 480 -> "SD"; else -> "?" }

    fun parse(s: StreamItem): SourceInfo {
        val text = "${s.name} ${s.title} ${s.filename}"
        val t = text.lowercase()
        val badges = ArrayList<String>()
        val size = sizeRe.find(text)?.let { "${it.groupValues[1]} ${it.groupValues[2].uppercase().replace("IB", "B")}" } ?: ""
        if (size.isNotEmpty()) badges.add("💾 $size")
        when {
            "hevc" in t || "x265" in t || "h265" in t || "h.265" in t -> badges.add("HEVC")
            "av1" in t -> badges.add("AV1")
            "x264" in t || "h264" in t || "h.264" in t || "avc" in t -> badges.add("H.264")
        }
        com.hortencioflix.tv.data.VideoRange.label(text)?.let { badges.add(it) }
        when (com.hortencioflix.tv.data.SourceFilters.source(text)) {
            "remux" -> badges.add("Remux")
            "cam" -> badges.add("📹 CAM/TS")
        }
        when {
            "atmos" in t -> badges.add("Atmos")
            "7.1" in t -> badges.add("7.1")
            "5.1" in t || "6ch" in t || "ddp5" in t -> badges.add("5.1")
        }
        when (com.hortencioflix.tv.data.AudioLang.of(text)) {
            "br" -> badges.add("🔊 PT-BR")
            "pt" -> badges.add("🔊 PT-PT")
            "por" -> badges.add("🔊 Português")
        }
        val flags = flagRe.findAll(text).map { it.value }.distinct().take(4).toList()
        if (flags.isNotEmpty()) badges.add(flags.joinToString(""))
        val seeds = seedsRe.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        if (seeds >= 0) badges.add("👤 $seeds")
        val pt = "🇵🇹" in text || "🇧🇷" in text || "portugu" in t || "dublado" in t || "pt-br" in t || Regex("\\b(por|pt)\\b").containsMatchIn(t)
        return SourceInfo(qualityLabel(s.quality), size, seeds, badges, pt)
    }
}

class DetailsActivity : BaseActivity() {
    companion object {
        fun open(ctx: Context, meta: MetaItem) {
            if (!Debounce.ok("details")) return
            if (meta.id.startsWith("iptv:") && ctx is androidx.fragment.app.FragmentActivity && IptvVod.resume(ctx, meta)) return
            ctx.startActivity(Intent(ctx, DetailsActivity::class.java).putExtra("meta", meta))
        }
    }

    private lateinit var meta: MetaItem
    private var full: JSONObject? = null
    private var episodes: List<Episode> = emptyList()
    private var seasons: List<Int> = emptyList()
    private var currentSeason = -1
    private var videoId = ""
    private var streams: List<StreamItem> = emptyList()
    private var infos: List<SourceInfo> = emptyList()
    private var subs: List<SubtitleItem> = emptyList()
    private var streamsFor = ""
    private var loadJob: Job? = null
    private var autoPlayed = false
    /** Filtros ativos: grupo → valores (ex.: "img" → {"dv","hdr10p"}, "q" → {"4K"}). OU no grupo, E entre grupos. */
    private val filters = LinkedHashMap<String, MutableSet<String>>()
    /** Ordem da lista: "" (a melhor primeiro), "big" (maior ficheiro) ou "small" (mais leve). */
    private var sortMode = ""
    /** Aviso por cima da lista (ex.: «Sem fontes Dolby Vision neste título»). */
    private var notice = ""
    private var trailerId = ""

    private lateinit var titleView: TextView
    private lateinit var info: TextView
    private lateinit var desc: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnLib: TextView
    private lateinit var seasonsView: RecyclerView
    private lateinit var episodesView: RecyclerView
    private lateinit var streamsView: RecyclerView
    private lateinit var streamsTitle: TextView
    private lateinit var streamsLoading: View
    private lateinit var sourcesPanel: View
    private lateinit var filtersView: RecyclerView
    private lateinit var seasonAdapter: RowAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private lateinit var sourceAdapter: SourceAdapter
    private lateinit var filterAdapter: RowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        @Suppress("DEPRECATION")
        val m = intent.getSerializableExtra("meta") as? MetaItem
        if (m == null) { finish(); return }
        meta = m
        if (savedInstanceState != null) autoPlayed = true

        titleView = findViewById(R.id.title)
        info = findViewById(R.id.info)
        desc = findViewById(R.id.description)
        btnPlay = findViewById(R.id.btn_play)
        btnLib = findViewById(R.id.btn_library)
        seasonsView = findViewById(R.id.seasons)
        episodesView = findViewById(R.id.episodes)
        streamsView = findViewById(R.id.streams)
        streamsTitle = findViewById(R.id.streams_title)
        streamsLoading = findViewById(R.id.streams_loading)
        sourcesPanel = findViewById(R.id.sources_panel)
        filtersView = findViewById(R.id.source_filters)
        if (savedInstanceState == null) animateEntry()

        layoutForOrientation()

        titleView.text = meta.name
        info.text = meta.info
        desc.text = meta.description
        showBackground(meta.background)

        seasonAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> showSeason(r.tag as Int) })
        seasonsView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        seasonsView.adapter = seasonAdapter

        episodeAdapter = EpisodeAdapter({ ep -> loadStreams(ep.id, autoPlay = false) }, { ep -> episodeMenu(ep) })
        episodesView.layoutManager = LinearLayoutManager(this)
        episodesView.adapter = episodeAdapter

        sourceAdapter = SourceAdapter { i ->
            val s = streams.getOrNull(i)
            if (s != null && com.hortencioflix.tv.data.TorboxApi.canQueue(s)) TorboxQueue.ask(this, s, meta.copy(name = titleView.text.toString()), streamsFor) { play(i) }
            else play(i)
        }
        streamsView.layoutManager = LinearLayoutManager(this)
        streamsView.adapter = sourceAdapter

        filterAdapter = RowAdapter(emptyList(), chip = true, onClick = { _, r -> onFilterChip(r.tag as String) })
        filtersView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        filtersView.adapter = filterAdapter

        btnPlay.setOnClickListener { quickPlay() }
        findViewById<View>(R.id.btn_sources).setOnClickListener { loadStreams(currentVideo(), autoPlay = false) }
        findViewById<View>(R.id.sources_close).setOnClickListener { hideSources() }
        btnLib.setOnClickListener { toggleLibrary() }
        btnLib.visibility = if (Stremio.loggedIn) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_trailer).setOnClickListener { openTrailer() }
        findViewById<View>(R.id.btn_share).setOnClickListener { share() }
        // «Ver na TV»: só no telemóvel e com a Roku configurada (Definições › Roku)
        findViewById<View>(R.id.btn_tv).apply {
            visibility = if (com.hortencioflix.tv.data.RokuRemote.available && !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) View.VISIBLE else View.GONE
            setOnClickListener { sendToTv() }
        }
        desc.setOnClickListener { desc.maxLines = if (desc.maxLines == 3) 50 else 3 }
        updateLibButton()
        btnPlay.requestFocus()
        entrance()

        loadMeta()
    }

    /** Entrada animada: fundo com zoom suave e conteúdo em cascata. */
    private fun entrance() {
        val bg = findViewById<ImageView>(R.id.bg_image)
        bg.scaleX = 1.12f; bg.scaleY = 1.12f; bg.alpha = 0f
        bg.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(900).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        val content = findViewById<LinearLayout>(R.id.content)
        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(18).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L * minOf(i, 8)).setDuration(420)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    private fun showPanel() {
        if (sourcesPanel.visibility == View.VISIBLE) return
        sourcesPanel.visibility = View.VISIBLE
        sourcesPanel.alpha = 0f
        sourcesPanel.translationY = dp(60).toFloat()
        sourcesPanel.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun layoutForOrientation() {
        val dm = resources.displayMetrics
        val bg = findViewById<ImageView>(R.id.bg_image)
        val scrim = findViewById<View>(R.id.bg_scrim)
        val content = findViewById<LinearLayout>(R.id.content)
        if (isLandscape()) {
            content.setPadding(dp(48), dp(40), (dm.widthPixels * 0.40f).toInt(), dp(32))
            btnPlay.layoutParams = (btnPlay.layoutParams as LinearLayout.LayoutParams).apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
        } else {
            val h = (dm.widthPixels * 0.66f).toInt()
            bg.layoutParams = (bg.layoutParams as FrameLayout.LayoutParams).apply { height = h }
            scrim.layoutParams = (scrim.layoutParams as FrameLayout.LayoutParams).apply { height = h }
            scrim.setBackgroundResource(R.drawable.scrim_bottom)
            content.setPadding(dp(20), h - dp(120), dp(20), dp(32))
        }
    }

    /** Entrada suave: o fundo surge e o texto sobe em cascata. */
    private fun animateEntry() {
        val bg = findViewById<View>(R.id.bg_image)
        bg.alpha = 0f
        bg.animate().alpha(1f).setDuration(500).start()
        for ((i, id) in listOf(R.id.logo, R.id.title, R.id.info, R.id.btn_play, R.id.description).withIndex()) {
            val v = findViewById<View>(id) ?: continue
            v.alpha = 0f
            v.translationY = dp(14).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(120L + 60L * i).setDuration(380).start()
        }
    }

    private fun showBackground(url: String) {
        val bg = findViewById<ImageView>(R.id.bg_image)
        val u = url.ifEmpty { if (meta.id.startsWith("tt")) "https://images.metahub.space/background/medium/${meta.id}/img" else "" }
        if (u.isNotEmpty()) bg.load(u) { crossfade(true) }
    }

    override fun onResume() {
        super.onResume()
        if (::btnPlay.isInitialized) {
            updatePlayButton()
            if (currentSeason >= 0) showSeason(currentSeason)
            schedulePreview()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (sourcesPanel.visibility == View.VISIBLE) { hideSources(); return }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun loadMeta() {
        lifecycleScope.launch {
            val f = try { Addons.meta(meta.type, meta.id) } catch (e: Exception) { null }
            full = f
            if (f != null) {
                titleView.text = f.optString("name").ifEmpty { meta.name }
                val parts = ArrayList<String>()
                f.optString("releaseInfo").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                f.optString("runtime").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                f.optString("imdbRating").takeIf { it.isNotEmpty() }?.let { parts.add("★ $it") }
                val g = f.optJSONArray("genres")
                val genres = if (g != null) (0 until g.length()).map { g.optString(it) } else emptyList()
                if (genres.isNotEmpty()) parts.add(genres.take(3).joinToString(", "))
                if (parts.isNotEmpty()) info.text = parts.joinToString("   ·   ")
                f.optString("description").takeIf { it.isNotEmpty() }?.let { desc.text = it }
                showBackground(f.optString("background").ifEmpty { meta.background })
                showLogo(f.optString("logo"))
                showCast(f)
                trailerId = findTrailer(f)
                findViewById<View>(R.id.btn_trailer).visibility = if (trailerId.isNotEmpty()) View.VISIBLE else View.GONE
                schedulePreview()
                episodes = Addons.episodes(f)
                loadSimilar(genres)
            } else {
                showLogo("")
            }
            if (episodes.isNotEmpty()) {
                seasons = episodes.map { it.season }.distinct().sortedBy { if (it == 0) 9999 else it }
                seasonAdapter.update(seasons.map { Row(if (it == 0) "Extras" else "Temporada $it", tag = it) })
                findViewById<View>(R.id.episodes_label).visibility = View.VISIBLE
                seasonsView.visibility = if (seasons.size > 1) View.VISIBLE else View.GONE
                episodesView.visibility = View.VISIBLE
                val cur = episodes.firstOrNull { it.id == currentVideo() }
                showSeason(cur?.season ?: seasons.first())
            }
            updatePlayButton()
            // Começa já a procurar as fontes do que vais ver, para o «Ver» arrancar sem espera.
            Addons.prefetch(meta.type, currentVideo())
            if (intent.getBooleanExtra("play", false) && !autoPlayed) {
                autoPlayed = true
                quickPlay()
            }
        }
    }

    private fun showLogo(url: String) {
        val logo = findViewById<ImageView>(R.id.logo)
        val u = url.ifEmpty { if (meta.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${meta.id}/img" else "" }
        if (u.isEmpty()) return
        logo.load(u) {
            listener(
                onSuccess = { _, _ -> logo.visibility = View.VISIBLE; titleView.visibility = View.GONE },
                onError = { _, _ -> logo.visibility = View.GONE; titleView.visibility = View.VISIBLE }
            )
        }
    }

    private fun showCast(f: JSONObject) {
        val lines = ArrayList<String>()
        fun names(key: String): List<String> {
            val a = f.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
        }
        val cast = names("cast")
        val dir = names("director")
        if (cast.isNotEmpty()) lines.add("Elenco: " + cast.take(6).joinToString(", "))
        if (dir.isNotEmpty()) lines.add("Realização: " + dir.take(3).joinToString(", "))
        val tv = findViewById<TextView>(R.id.cast)
        tv.text = lines.joinToString("\n")
        tv.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun findTrailer(f: JSONObject): String {
        f.optJSONArray("trailerStreams")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("ytId")?.takeIf { it.isNotEmpty() }?.let { return it } }
        f.optJSONArray("trailers")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("source")?.takeIf { it.isNotEmpty() }?.let { return it } }
        return ""
    }

    private fun openTrailer() {
        if (trailerId.isEmpty()) return
        stopPreview()
        try {
            Trailer.open(this, trailerId)
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailerId"))) }
            catch (e2: Exception) { toast("Não há nenhuma app para abrir o trailer.") }
        }
    }

    // ---------- Pré-visualização do trailer (fundo vivo, sem som) ----------

    private var previewOn = false
    private val previewStart = Runnable { startPreview() }

    private fun schedulePreview() {
        if (trailerId.isEmpty() || !Trailer.autoEnabled()) return
        val v = findViewById<View>(R.id.trailer_view)
        v.removeCallbacks(previewStart)
        // espera um pouco: só quem fica na página vê o trailer (e não gasta dados a quem passa rápido)
        v.postDelayed(previewStart, 2500)
    }

    private fun startPreview() {
        if (isFinishing || previewOn || trailerId.isEmpty()) return
        val w = findViewById<android.webkit.WebView>(R.id.trailer_view) ?: return
        try {
            previewOn = true
            Trailer.setup(w)
            w.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    // o YouTube demora a mostrar imagem: aparece suavemente só depois, sem ecrã preto
                    if (previewOn) w.postDelayed({
                        if (previewOn) {
                            w.animate().alpha(1f).setDuration(1200).start()
                            findViewById<View>(R.id.bg_image).animate().alpha(0f).setDuration(1200).start()
                        }
                    }, 2200)
                }
            }
            w.alpha = 0f
            w.visibility = View.VISIBLE
            Trailer.load(w, trailerId, preview = true)
        } catch (e: Throwable) {
            previewOn = false   // sem WebView no aparelho: fica a imagem de fundo
        }
    }

    private fun stopPreview() {
        val w = findViewById<android.webkit.WebView>(R.id.trailer_view) ?: return
        w.removeCallbacks(previewStart)
        if (!previewOn) return
        previewOn = false
        w.animate().cancel()
        w.alpha = 0f
        w.visibility = View.GONE
        try { w.loadUrl("about:blank") } catch (e: Throwable) {}
        findViewById<View>(R.id.bg_image).animate().alpha(1f).setDuration(300).start()
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    private fun sendToTv() {
        val v = currentVideo()
        toast("A enviar para a TV…")
        lifecycleScope.launch {
            val ok = com.hortencioflix.tv.data.RokuRemote.sendTitle(meta.id.substringBefore(':').ifEmpty { meta.id }, meta.type, v)
            toast(if (ok) "📺 A abrir na TV — a Roku escolhe a melhor fonte." else "A Roku não respondeu. Confirma que está ligada e na mesma rede (Definições › Roku › endereço).")
        }
    }

    private fun share() {
        val link = if (meta.id.startsWith("tt")) "https://www.imdb.com/title/${meta.id.substringBefore(':')}/" else ""
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${titleView.text}  $link".trim())
        try { startActivity(Intent.createChooser(i, "Partilhar")) } catch (e: Exception) {}
    }

    private fun loadSimilar(genres: List<String>) {
        lifecycleScope.launch {
            val list = try { Addons.similar(meta.type, meta.id, genres) } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) return@launch
            val rv = findViewById<RecyclerView>(R.id.similar)
            rv.layoutManager = LinearLayoutManager(this@DetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            rv.adapter = PosterAdapter(list) { open(this@DetailsActivity, it) }
            rv.visibility = View.VISIBLE
            findViewById<View>(R.id.similar_label).visibility = View.VISIBLE
        }
    }

    private fun episodeMenu(ep: Episode) {
        val watched = Prefs.isWatched(ep.id)
        val season = episodes.filter { it.season == ep.season }
        val before = episodes.filter { it.season == ep.season && it.episode <= ep.episode }
        val opts = listOf(
            if (watched) "Marcar como não visto" else "✓  Marcar como visto",
            "✓  Marcar até este episódio como visto",
            "✓  Marcar temporada ${ep.season} como vista",
            "Marcar temporada ${ep.season} como não vista"
        )
        choiceDialog("${ep.label} · ${ep.name}", opts) { w ->
            when (w) {
                0 -> Prefs.setWatched(listOf(ep.id), !watched)
                1 -> Prefs.setWatched(before.map { it.id }, true)
                2 -> Prefs.setWatched(season.map { it.id }, true)
                3 -> Prefs.setWatched(season.map { it.id }, false)
            }
            videoId = ""
            showSeason(ep.season)
            updatePlayButton()
        }
    }

    private fun showSeason(s: Int) {
        currentSeason = s
        val list = episodes.filter { it.season == s }
        episodeAdapter.update(list, currentVideo())
        if (seasons.size > 1) seasonAdapter.update(seasons.map { Row((if (it == s) "● " else "") + (if (it == 0) "Extras" else "Temporada $it"), tag = it) })
    }

    /** Vídeo a reproduzir: o episódio em curso (progresso) ou o primeiro. */
    private fun currentVideo(): String {
        if (videoId.isNotEmpty()) return videoId
        if (meta.type != "series" || episodes.isEmpty()) {
            if (meta.videoId.isNotEmpty()) return meta.videoId
            return meta.id
        }
        if (meta.videoId.isNotEmpty() && episodes.any { it.id == meta.videoId }) return meta.videoId
        val arr = Prefs.progressList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("m") == meta.id) {
                val v = o.optString("v")
                if (episodes.any { it.id == v }) return v
            }
        }
        val st = Stremio.libraryItem(meta.id)?.optJSONObject("state")
        val v = st?.optString("video_id") ?: ""
        val ordered = episodes.filter { it.season > 0 }.ifEmpty { episodes }
        // Depois do último episódio visto, o seguinte por ver.
        val lastWatched = ordered.indexOfLast { Prefs.isWatched(it.id) }
        if (lastWatched >= 0) ordered.drop(lastWatched + 1).firstOrNull { !Prefs.isWatched(it.id) }?.let { return it.id }
        if (v.isNotEmpty() && episodes.any { it.id == v }) return v
        return ordered.first().id
    }

    private fun updatePlayButton() {
        if (!::meta.isInitialized) return
        val v = currentVideo()
        val res = Prefs.resumeFor(v, meta.id)
        val ep = episodes.firstOrNull { it.id == v }
        val label = StringBuilder(if (res > 0) "▶  Continuar" else "▶  Ver")
        if (ep != null) label.append("  ").append(ep.label)
        if (res > 0) label.append("  ·  ").append(formatTime(res))
        btnPlay.text = label.toString()
    }

    private fun updateLibButton() {
        btnLib.text = if (Stremio.inLibrary(meta.id)) "✓  Na biblioteca" else "+  Biblioteca"
    }

    private fun toggleLibrary() {
        val add = !Stremio.inLibrary(meta.id)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Stremio.setInLibrary(meta, add) }
            updateLibButton()
            toast(if (!ok) "Não foi possível atualizar a conta Stremio." else if (add) "Adicionado à biblioteca" else "Removido da biblioteca")
        }
    }

    /** «Ver»: abre já o leitor — a escolha da fonte acontece lá, em segundo plano. */
    private fun quickPlay() {
        val v = currentVideo()
        if (streamsFor == v && streams.isNotEmpty() && SourcePicker.hasReady(streams)) {
            // com filtros escolhidos no painel, o «Ver» começa pela primeira fonte pronta que os cumpre
            val first = if (filters.isEmpty()) null else streams.indices.firstOrNull { streams[it].cached && passes(it) }
            play(first ?: 0); return
        }
        val m = meta.copy(
            name = titleView.text.toString(),
            poster = meta.poster.ifEmpty { full?.optString("poster") ?: "" },
            background = full?.optString("background") ?: meta.background
        )
        Session.prepare(m, v, if (streamsFor == v) subs else emptyList(), episodes, Prefs.resumeFor(v, meta.id))
        PlayerActivity.start(this)
    }

    private fun hideSources() {
        sourcesPanel.animate().alpha(0f).translationY(dp(60).toFloat()).setDuration(220).withEndAction { sourcesPanel.visibility = View.GONE }.start()
        loadJob?.cancel()
        btnPlay.requestFocus()
    }

    private fun loadStreams(vid: String, autoPlay: Boolean) {
        videoId = vid
        updatePlayButton()
        loadJob?.cancel()
        val ep = episodes.firstOrNull { it.id == vid }
        val label = titleView.text.toString() + (if (ep != null) "  ·  ${ep.label}" else "")
        streamsTitle.text = label
        if (autoPlay) { sourcesPanel.visibility = View.GONE; btnPlay.text = "A procurar fontes…" } else showPanel()
        streamsLoading.visibility = View.VISIBLE
        sourceAdapter.update(emptyList())
        filterAdapter.update(emptyList())
        filters.clear()
        notice = ""
        val type = meta.type
        loadJob = lifecycleScope.launch {
            val subsJob = async { try { Addons.subtitles(type, vid) } catch (e: Exception) { emptyList() } }
            val list = try { Addons.streams(type, vid) } catch (e: Exception) { emptyList() }
            subs = subsJob.await()
            streams = list
            infos = list.map { SourceParser.parse(it) }
            streamsFor = vid
            applyPinned()
            streamsLoading.visibility = View.GONE
            if (list.isEmpty()) {
                streamsTitle.text = "$label\nNenhuma fonte encontrada"
                if (Prefs.stremioAddons().isEmpty() && Addons.list().isEmpty())
                    toast("Sem fontes: liga a conta Stremio ou instala um add-on de fontes nas Definições.")
                else if (!Prefs.hasDebrid())
                    toast("Sem fontes diretas. Para torrents precisas de um serviço debrid (TorBox, Real-Debrid…) — configura-o nas Definições.")
                return@launch
            }
            if (autoPlay && SourcePicker.hasReady(list)) {
                sourcesPanel.visibility = View.GONE
                play(bestStart())
                return@launch
            }
            if (autoPlay) {
                // Nenhuma fonte está pronta no debrid: não tocar o vídeo «downloading to debrid».
                updatePlayButton()
                showPanel()
                streamsTitle.text = "$label\nAinda nenhuma fonte está pronta no teu serviço debrid. Escolhe uma (⬇) para o TorBox a descarregar — avisamos-te quando estiver pronta."
            }
            renderSources()
            streamsView.animateIn()
            streamsView.post { streamsView.getChildAt(0)?.requestFocus() }
        }
    }

    /** Testa as primeiras fontes em paralelo e começa pela primeira que responde (sem esperar pelas outras). */
    private suspend fun bestStart(): Int {
        val n = minOf(4, streams.takeWhile { it.cached }.size)
        if (n <= 1) return 0
        btnPlay.text = "A testar fontes…"
        val checks = (0 until n).map { i -> lifecycleScope.async(Dispatchers.IO) { Http.probe(streams[i].url, streams[i].headers) } }
        var pick = 0
        for (i in 0 until n) {
            val ok = checks[i].await()
            if (ok != false) { pick = i; break }   // true ou sem resposta a tempo (pode ser só lento): usar
            if (i == n - 1) pick = 0
        }
        checks.forEach { it.cancel() }
        updatePlayButton()
        return pick
    }

    // ---------------- Filtros das fontes ----------------
    // Lógica (igual no «Ver» e no leitor, ver SourceFilters): OU dentro do grupo, E entre grupos.

    private val FG = com.hortencioflix.tv.data.SourceFilters

    private fun passes(i: Int, except: String? = null): Boolean = FG.passes(streams[i], filters, except)

    /** Começa com os filtros fixados; se este título não tiver nenhuma fonte assim, mostra todas e avisa. */
    private fun applyPinned() {
        filters.clear()
        val pin = FG.pinned()
        if (pin.isEmpty()) return
        pin.forEach { (g, vs) -> filters[g] = vs.toMutableSet() }
        if (streams.indices.none { passes(it) }) {
            notice = "Sem fontes «${FG.describe(pin)}» neste título — a mostrar todas."
            filters.clear()
        }
    }

    private fun onFilterChip(tag: String) {
        notice = ""
        when {
            tag == "all" -> { filters.clear(); sortMode = "" }
            tag == "pin" -> {
                val cur = FG.encode(filters)
                if (cur.isEmpty() || cur == Prefs.str("pinFilters")) {
                    FG.setPinned(emptyMap()); toast("Filtros soltos — cada título volta a mostrar todas as fontes.")
                } else {
                    FG.setPinned(filters); toast("Filtros fixados para todos os títulos: ${FG.describe(filters)}. O «Ver» também os segue.")
                }
            }
            tag.startsWith("sort:") -> { val v = tag.substringAfter(':'); sortMode = if (sortMode == v) "" else v }
            else -> {
                val g = tag.substringBefore(':'); val v = tag.substringAfter(':')
                val set = filters.getOrPut(g) { LinkedHashSet() }
                if (!set.add(v)) set.remove(v)        // tocar de novo tira esse valor
                // «Qualquer HDR» e os tipos de HDR não fazem sentido juntos: o último escolhido ganha
                if (g == "img" && v == "anyhdr" && "anyhdr" in set) set.retainAll(setOf("anyhdr", "dv"))
                if (g == "img" && v != "anyhdr" && v != "dv") set.remove("anyhdr")
                if (set.isEmpty()) filters.remove(g)
            }
        }
        renderSources()
    }

    private fun renderSources() {
        val order = listOf("4K", "1440p", "1080p", "720p", "SD", "?")
        val VR = com.hortencioflix.tv.data.VideoRange
        val caps = com.hortencioflix.tv.data.DeviceCaps
        val chips = ArrayList<Row>()
        /**
         * Um chip só aparece se mudar alguma coisa: tem de haver fontes com esse valor (contando já com os
         * outros filtros) e não podem ser todas — um «4K (12)» quando as 12 são 4K só ocupava espaço.
         */
        fun chip(group: String, v: String, label: String, warn: Boolean = false) {
            val active = filters[group]?.contains(v) == true
            val base = streams.indices.count { passes(it, group) }
            val n = streams.indices.count { passes(it, group) && FG.facet(streams[it], group, v) }
            if (!active && (n == 0 || (n == base && filters[group].isNullOrEmpty()))) return
            chips.add(Row((if (active) "● " else "") + label + (if (warn) " ⚠" else "") + " ($n)", tag = "$group:$v"))
        }
        chips.add(Row((if (filters.isEmpty()) "● " else "") + "Todas (${streams.size})", tag = "all"))
        for (q in order) chip("q", q, FG.valueLabel("q", q))
        // Imagem: avisa (⚠) quando o ecrã não mostra bem esse tipo
        val noHdr = caps.known && !caps.hdr && !caps.dv
        chip("img", "anyhdr", "Qualquer HDR", warn = noHdr)
        chip("img", "dv", "Dolby Vision", warn = caps.known && !caps.dv)
        chip("img", "hdr10p", "HDR10+", warn = noHdr)
        chip("img", "hdr10", "HDR10", warn = noHdr)
        chip("img", "hlg", "HLG", warn = noHdr)
        chip("img", "sdr", "SDR")
        chip("src", "remux", "Remux")
        chip("src", "bluray", "Blu-ray")
        chip("src", "web", "WEB")
        chip("nocam", "1", "🚫 Sem CAM/TS")
        chip("lang", "br", FG.valueLabel("lang", "br"))
        chip("lang", "pt", FG.valueLabel("lang", "pt"))
        chip("aud", "atmos", "Atmos")
        chip("aud", "lossless", "Som sem perdas")
        chip("aud", "51", "Som 5.1+")
        chip("cached", "1", "⚡ Em cache")
        chip("compat", "1", "✅ Compatíveis")
        if (streams.count { FG.sizeGb(VR.text(it)) > 0 } > 1) {
            chips.add(Row((if (sortMode == "big") "● " else "") + "↓ Maior primeiro", tag = "sort:big"))
            chips.add(Row((if (sortMode == "small") "● " else "") + "↑ Mais leve primeiro", tag = "sort:small"))
        }
        if (filters.isNotEmpty() || Prefs.str("pinFilters").isNotEmpty()) {
            val pinnedNow = FG.encode(filters).let { it.isNotEmpty() && it == Prefs.str("pinFilters") }
            chips.add(Row(if (pinnedNow) "📌 Fixados (tocar para soltar)" else if (filters.isEmpty()) "📌 Soltar filtros fixos" else "📌 Fixar para todos os títulos", tag = "pin"))
        }
        filterAdapter.update(chips)

        val entries = ArrayList<Any>()
        if (notice.isNotEmpty()) entries.add(notice)
        var lastQ = ""
        val sizeOf = { i: Int -> FG.sizeGb(VR.text(streams[i])) }
        val idx = streams.indices.filter { passes(it) }.let { l ->
            when (sortMode) {
                "big" -> l.sortedWith(compareBy<Int>({ order.indexOf(infos[it].quality) }, { -sizeOf(it) }))
                "small" -> l.sortedWith(compareBy<Int>({ order.indexOf(infos[it].quality) }, { sizeOf(it).let { s -> if (s <= 0) Double.MAX_VALUE else s } }))
                else -> l.sortedBy { order.indexOf(infos[it].quality) }
            }
        }
        if (idx.isEmpty() && filters.isNotEmpty()) entries.add(emptyHint())
        for (i in idx) {
            val q = infos[i].quality
            if (q != lastQ) { entries.add(if (q == "?") "Outras" else q); lastQ = q }
            entries.add(i)
        }
        sourceAdapter.update(entries)
    }

    /** Lista vazia: diz qual filtro tirar para voltar a haver fontes (o que deixa mais). */
    private fun emptyHint(): String {
        val best = filters.keys.map { g -> g to streams.indices.count { passes(it, g) } }.maxByOrNull { it.second }
        return if (best != null && best.second > 0)
            "Nenhuma fonte com estes filtros. Sem «${filters[best.first]!!.joinToString(" ou ") { FG.valueLabel(best.first, it) }}» ficam ${best.second}."
        else "Nenhuma fonte com estes filtros — toca em «Todas»."
    }

    private fun play(index: Int) {
        if (streams.isEmpty()) return
        val v = streamsFor
        val start = Prefs.resumeFor(v, meta.id)
        val m = meta.copy(
            name = titleView.text.toString(),
            poster = meta.poster.ifEmpty { full?.optString("poster") ?: "" },
            background = full?.optString("background") ?: meta.background
        )
        // A fonte escolhida primeiro; depois as que cumprem os filtros ativos (se falhar, troca por outra
        // do mesmo tipo — ex.: outra Dolby Vision) e só no fim as restantes.
        val others = streams.indices.filter { it != index }
        val (same, rest) = if (filters.isEmpty()) others to emptyList() else others.partition { passes(it) }
        val ordered = listOf(streams[index]) + (same + rest).map { streams[it] }
        Session.vod(m, v, ordered, 0, subs, episodes, start)
        PlayerActivity.start(this)
    }

    // ---------------- Adaptadores ----------------

    private fun progressFor(videoId: String): Float {
        val arr = Prefs.progressList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("v") == videoId) {
                val d = o.optLong("d")
                return if (d > 0) o.optLong("t").toFloat() / d else 0f
            }
        }
        return 0f
    }

    private fun prettyDate(iso: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.take(10))
        if (d == null) "" else SimpleDateFormat("d MMM yyyy", Locale("pt", "PT")).format(d)
    } catch (e: Exception) { "" }

    inner class EpisodeAdapter(private val onClick: (Episode) -> Unit, private val onLong: (Episode) -> Unit) : RecyclerView.Adapter<EpisodeAdapter.VH>() {
        private var items: List<Episode> = emptyList()
        private var current = ""

        fun update(list: List<Episode>, cur: String) { items = list; current = cur; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val num: TextView = v.findViewById(R.id.ep_num)
            val thumb: ImageView = v.findViewById(R.id.ep_thumb)
            val prog: ProgressBar = v.findViewById(R.id.ep_progress)
            val title: TextView = v.findViewById(R.id.ep_title)
            val metaLine: TextView = v.findViewById(R.id.ep_meta)
            val overview: TextView = v.findViewById(R.id.ep_overview)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val ep = items[position]
            h.num.text = "${ep.episode}"
            h.thumb.setImageDrawable(null)
            if (ep.thumbnail.isNotEmpty()) h.thumb.load(ep.thumbnail) { crossfade(true); transformations(RoundedCornersTransformation(h.itemView.context.dp(8).toFloat())) }
            val name = ep.name.ifEmpty { "Episódio ${ep.episode}" }
            h.title.text = (if (ep.id == current) "▶ " else "") + "${ep.episode}. $name"
            val p = progressFor(ep.id)
            h.prog.visibility = if (p > 0f) View.VISIBLE else View.GONE
            h.prog.progress = (p * 100).toInt().coerceAtLeast(3)
            val res = Prefs.resumeFor(ep.id, "")
            val parts = ArrayList<String>()
            val seen = Prefs.isWatched(ep.id)
            if (seen) parts.add("✓ Visto")
            h.itemView.alpha = if (seen && ep.id != current) 0.6f else 1f
            prettyDate(ep.released).takeIf { it.isNotEmpty() }?.let { parts.add(it) }
            if (res > 0) parts.add("parou em ${formatTime(res)}")
            h.metaLine.text = parts.joinToString("  ·  ")
            h.overview.text = ep.overview
            h.overview.visibility = if (ep.overview.isEmpty()) View.GONE else View.VISIBLE
            h.itemView.setOnClickListener { onClick(ep) }
            h.itemView.setOnLongClickListener { onLong(ep); true }
            h.itemView.setOnFocusChangeListener { v, has ->
                // Parado num episódio por um instante: procura já as fontes dele.
                if (has) v.postDelayed({ if (v.hasFocus()) Addons.prefetch(meta.type, ep.id) }, 700)
            }
        }
    }

    inner class SourceAdapter(private val onClick: (Int) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var entries: List<Any> = emptyList()
        fun update(list: List<Any>) { entries = list; notifyDataSetChanged() }

        override fun getItemViewType(position: Int) = if (entries[position] is String) 0 else 1
        override fun getItemCount() = entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 0) object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_header, parent, false)) {}
            else object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_source, parent, false)) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val e = entries[position]
            val v = holder.itemView
            if (e is String) { (v as TextView).text = e; return }
            val i = e as Int
            val s = streams[i]
            val inf = infos[i]
            val q = v.findViewById<TextView>(R.id.src_quality)
            q.text = inf.quality
            q.setTextColor(if (inf.quality == "4K") v.context.themeColor(R.attr.hfAccent) else v.context.themeColor(R.attr.hfText))
            val name = s.filename.ifEmpty { s.title.lineSequence().firstOrNull() ?: s.name }
            v.findViewById<TextView>(R.id.src_title).text = name
            val flags = ArrayList<String>()
            flags.add(if (s.cached) "⚡" else "⬇ download")
            if (s.risky) flags.add("⚠ pode falhar")
            com.hortencioflix.tv.data.VideoRange.screenWarning(com.hortencioflix.tv.data.VideoRange.text(s))?.let { flags.add("⚠ $it") }
            v.findViewById<TextView>(R.id.src_badges).text = (flags + inf.badges).joinToString("   ")
            v.findViewById<TextView>(R.id.src_addon).text = s.addon + "  ·  " + s.name.replace("\n", " ")
            v.setOnClickListener { onClick(i) }
        }
    }
}
