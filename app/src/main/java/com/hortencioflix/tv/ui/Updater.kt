package com.hortencioflix.tv.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.BuildConfig
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.Http
import com.hortencioflix.tv.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** Atualização automática a partir do ramo «apk» do repositório GitHub. */
object Updater {
    private const val BASE = "https://raw.githubusercontent.com/jacobhortencio-droid/hortencioflix-app/apk/"

    private fun headers(): Map<String, String> {
        val t = Prefs.str("ghToken")
        return if (t.isEmpty()) emptyMap() else mapOf("Authorization" to "token $t")
    }

    /** Número da última versão publicada (o N de 1.0.N), ou null se não for possível ler. */
    var latestName = ""
        private set

    suspend fun latest(): Int? = withContext(Dispatchers.IO) {
        val txt = Http.getStringH(BASE + "version.txt?t=" + System.currentTimeMillis() / 60000, headers())?.trim()
        latestName = txt ?: ""
        txt?.substringAfterLast('.')?.toIntOrNull()
    }

    /** APK já descarregado em segundo plano (pela verificação diária), pronto a instalar. */
    fun readyApk(ctx: Context): Pair<Int, File>? {
        val v = Prefs.str("updReady").toIntOrNull() ?: return null
        val f = File(updatesDir(ctx), "update-$v.apk")
        return if (v > BuildConfig.VERSION_CODE && f.exists()) v to f else null
    }

    private fun updatesDir(ctx: Context): File {
        val d = if (Build.VERSION.SDK_INT >= 24) File(ctx.cacheDir, "updates") else File(ctx.externalCacheDir ?: ctx.cacheDir, "updates")
        d.mkdirs()
        return d
    }

    /** Descarrega e confirma o APK da versão v (usado no ecrã e na verificação diária). */
    suspend fun fetch(ctx: Context, v: Int, onPct: (Int) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        try {
            val dir = updatesDir(ctx)
            dir.listFiles()?.forEach { if (it.name != "update-$v.apk") it.delete() }
            val f = File(dir, "update-$v.apk")
            var expected = -1L
            val rb = Request.Builder().url(BASE + "Hortencioflix-android.apk?v=$v")
            for ((k, value) in headers()) rb.header(k, value)
            Http.client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body ?: return@withContext null
                val total = body.contentLength()
                expected = total
                body.byteStream().use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; onPct(pct) }
                            }
                        }
                    }
                }
            }
            if (expected > 0 && f.length() != expected) { f.delete(); return@withContext null }
            // Confirma que é mesmo um APK da Hortêncioflix antes de o oferecer para instalar.
            @Suppress("DEPRECATION")
            val info = try { ctx.packageManager.getPackageArchiveInfo(f.path, 0) } catch (e: Exception) { null }
            if (info == null || info.packageName != ctx.packageName) { f.delete(); return@withContext null }
            f
        } catch (e: Exception) {
            null
        }
    }

    fun checkOnStart(a: FragmentActivity) {
        // Já há uma versão descarregada em segundo plano: só falta instalar.
        readyApk(a)?.let { (v, f) ->
            if (Prefs.str("updSkip") != v.toString()) {
                a.dialog().setTitle("Nova versão pronta a instalar")
                    .setMessage("A versão ${Prefs.str("updReadyName").ifEmpty { "nº $v" }} já foi descarregada (tens a ${BuildConfig.VERSION_NAME}).\nAs tuas definições mantêm-se.")
                    .setPositiveButton("Instalar") { _, _ -> install(a, f) }
                    .setNegativeButton("Mais tarde", null)
                    .setNeutralButton("Ignorar esta versão") { _, _ -> Prefs.put("updSkip", v.toString()) }
                    .show()
                return
            }
        }
        val last = Prefs.str("updCheck", "0").toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        if (now - last < 3 * 3600 * 1000L) return
        Prefs.put("updCheck", now.toString())
        a.lifecycleScope.launch {
            val v = latest() ?: return@launch
            if (v > BuildConfig.VERSION_CODE && Prefs.str("updSkip") != v.toString()) prompt(a, v, true)
        }
    }

    fun checkNow(a: FragmentActivity) {
        a.toast("A procurar atualizações…")
        a.lifecycleScope.launch {
            val v = latest()
            when {
                v == null -> a.dialog().setTitle("Não foi possível verificar")
                    .setMessage("O repositório da app é privado, por isso a app não consegue ler as versões novas.\n\nPara ativar as atualizações automáticas, torna o repositório público no GitHub ou cola um token de acesso em «Token GitHub para atualizações».")
                    .setPositiveButton("OK", null).show()
                v > BuildConfig.VERSION_CODE -> prompt(a, v, false)
                else -> a.toast("Já tens a versão mais recente (${BuildConfig.VERSION_NAME}).")
            }
        }
    }

    private fun prompt(a: FragmentActivity, v: Int, allowSkip: Boolean) {
        if (a.isFinishing) return
        val b = a.dialog().setTitle("Nova versão disponível")
            .setMessage("Está disponível a versão ${latestName.ifEmpty { "nº $v" }} (tens a ${BuildConfig.VERSION_NAME}).\nQueres atualizar agora? As tuas definições mantêm-se.")
            .setPositiveButton("Atualizar") { _, _ -> download(a, v) }
            .setNegativeButton("Mais tarde", null)
        if (allowSkip) b.setNeutralButton("Ignorar esta versão") { _, _ -> Prefs.put("updSkip", v.toString()) }
        b.show()
    }

    private fun download(a: FragmentActivity, v: Int) {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(a.dp(24), a.dp(16), a.dp(24), a.dp(8))
        val label = TextView(a)
        label.text = "A descarregar…"
        label.setTextColor(a.themeColor(R.attr.hfText))
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        box.addView(label)
        box.addView(bar)
        val dlg: AlertDialog = a.dialog().setTitle("Atualizar para ${latestName.ifEmpty { "a versão nova" }}").setView(box).setCancelable(false).create()
        dlg.show()
        a.lifecycleScope.launch {
            val file = fetch(a, v) { pct -> a.runOnUiThread { bar.progress = pct; label.text = "A descarregar… $pct%" } }
            dlg.dismiss()
            if (file == null) { a.toast("Falhou a transferência (ou o ficheiro veio danificado). Tenta de novo mais tarde."); return@launch }
            install(a, file)
        }
    }

    private fun install(a: FragmentActivity, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            a.toast("Autoriza a Hortêncioflix a instalar apps e volta a tocar em Atualizar.")
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            } catch (e: ActivityNotFoundException) {
                a.toast("Ativa «Fontes desconhecidas» para a Hortêncioflix nas definições do aparelho.")
            }
            return
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(a, a.packageName + ".files", f) else Uri.fromFile(f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            a.startActivity(i)
        } catch (e: Exception) {
            a.toast("Não foi possível abrir o instalador: ${e.message}")
        }
    }
}
