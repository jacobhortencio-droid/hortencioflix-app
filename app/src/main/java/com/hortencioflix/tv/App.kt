package com.hortencioflix.tv

import android.app.Application
import com.hortencioflix.tv.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.hortencioflix.tv.data.DeviceCaps.init(this)
        // A app morreu a meio do arranque de uma fonte (sem passar por onStop): essa fonte fica marcada como má.
        Prefs.str("playGuard").takeIf { it.isNotEmpty() }?.let { sig ->
            Prefs.remove("playGuard")
            if (sig != "1") com.hortencioflix.tv.data.SourceStats.crashed(sig)
            com.hortencioflix.tv.data.PlayLog.add("💥 A app fechou ao arrancar a fonte «$sig» — passa a ficar no fim da lista")
        }
        Thread { trimCache() }.start()
        // Guardar o erro para o mostrar (e poder enviar) na próxima abertura.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "Hortêncioflix ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" +
                        "Thread: ${t.name}\n\n" + sw.toString().take(8000)
                )
            } catch (x: Exception) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Mantém a cache pequena: apaga APKs de atualizações antigas e o que passa de ~300 MB (o mais antigo primeiro). */
    private fun trimCache() {
        try {
            java.io.File(cacheDir, "updates").listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete() }
            val files = cacheDir.walkTopDown().filter { it.isFile && !it.path.contains("/image_cache") }.toList()
            var total = files.sumOf { it.length() }
            val limit = 300L * 1024 * 1024
            if (total <= limit) return
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= limit) break
                total -= f.length()
                f.delete()
            }
        } catch (e: Exception) {
        }
    }

    companion object {
        @JvmStatic var mainTaskId: Int = -1
    }
}
