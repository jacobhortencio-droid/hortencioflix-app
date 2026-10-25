package com.hortencioflix.tv.data

import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Velocidade real da Internet deste aparelho e o peso (bitrate) de cada fonte.
 *
 * Porque há pausas: um filme 4K Remux de 60 GB precisa de ~70 Mbps; com 10–25 Mbps só pode parar.
 * O peso de cada fonte calcula-se pelo tamanho do ficheiro e pela duração do título; as que cabem na
 * ligação (com folga) passam à frente das que não cabem.
 */
object NetSpeed {
    private const val KEY = "netKbps"
    private const val AT = "netKbpsAt"

    /** Velocidade estimada em kbit/s (0 = ainda não se sabe). */
    fun kbps(): Int = Prefs.str(KEY).toIntOrNull() ?: 0

    fun mbpsText(): String = kbps().let { if (it <= 0) "?" else String.format("%.1f Mbps", it / 1000.0) }

    /** Junta uma medição nova (média que dá mais peso às recentes; medições absurdas ignoradas). */
    fun record(kbps: Int, weight: Double = 0.4) {
        if (kbps < 200 || kbps > 2_000_000) return
        val old = kbps()
        val v = if (old <= 0) kbps else (old * (1 - weight) + kbps * weight).toInt()
        Prefs.put(KEY, v.toString())
        Prefs.put(AT, System.currentTimeMillis().toString())
    }

    fun ageMs(): Long = System.currentTimeMillis() - (Prefs.str(AT).toLongOrNull() ?: 0L)

    /** Teste rápido (~8 MB do Cloudflare). Corre no máximo de 6 em 6 h, ou quando forçado. */
    fun measure(force: Boolean = false): Int {
        if (!force && kbps() > 0 && ageMs() < 6 * 3600_000L) return kbps()
        return try {
            val c = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
            val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=8000000").header("User-Agent", "Hortencioflix").build()
            c.newCall(req).execute().use { r ->
                val src = r.body?.byteStream() ?: return kbps()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                var t0 = 0L
                var counted = 0L
                val deadline = SystemClock.elapsedRealtime() + 12_000
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    // ignora o primeiro meio segundo (arranque da ligação)
                    if (t0 == 0L && total > 256 * 1024) { t0 = SystemClock.elapsedRealtime(); counted = total }
                    if (SystemClock.elapsedRealtime() > deadline) break
                }
                val ms = SystemClock.elapsedRealtime() - t0
                val bytes = total - counted
                if (t0 > 0 && ms > 300 && bytes > 0) {
                    val k = (bytes * 8 / ms).toInt()   // bits/ms = kbit/s
                    record(k, if (force) 0.7 else 0.5)
                    PlayLog.add("📶 Velocidade medida: ${String.format("%.1f", k / 1000.0)} Mbps")
                }
                kbps()
            }
        } catch (e: Exception) { kbps() }
    }

    // ---------- Peso das fontes ----------

    /** Duração provável em minutos (filme 120, episódio 45, se não se souber). */
    fun runtimeMin(type: String, runtime: Int = 0): Int = when {
        runtime in 5..400 -> runtime
        type == "series" || type == "anime" -> 45
        else -> 120
    }

    /** Bitrate médio da fonte em kbit/s, pelo tamanho do ficheiro (0 = tamanho desconhecido). */
    fun sourceKbps(s: StreamItem, type: String, runtime: Int = 0): Int {
        val gb = SourceFilters.sizeGb(VideoRange.text(s))
        if (gb <= 0.05) return 0
        // um pack de temporada (muitos GB num «episódio») não diz nada do episódio: ignora
        if ((type == "series" || type == "anime") && gb > 25) return 0
        val secs = runtimeMin(type, runtime) * 60.0
        return (gb * 8_000_000 / secs).toInt()   // GB → kbit
    }

    /** A fonte cabe na ligação? (null = não se sabe). Folga de 25 % para os picos do vídeo. */
    fun fits(s: StreamItem, type: String, runtime: Int = 0): Boolean? {
        val net = kbps()
        val need = sourceKbps(s, type, runtime)
        if (net <= 0 || need <= 0) return null
        return need <= net * 0.75
    }

    /** Aviso para a lista de fontes. */
    fun warning(s: StreamItem, type: String): String? {
        if (fits(s, type) != false) return null
        return "pesada para a tua Internet (~${(sourceKbps(s, type) + 500) / 1000} Mbps, tens ${mbpsText()}): vai parar para carregar"
    }
}
