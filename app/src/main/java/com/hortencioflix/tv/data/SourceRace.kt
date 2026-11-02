package com.hortencioflix.tv.data

import android.os.SystemClock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * «Corrida» de fontes: antes de dar uma fonte ao leitor, testa as melhores em paralelo e escolhe a primeira
 * (pela ordem de preferência) que é mesmo um vídeo.
 *
 * Porque é preciso: muitas fontes «⚡ prontas» não estão — o serviço debrid devolve um vídeo de espera
 * de poucos MB («downloading to debrid»), uma página de erro, ou demora 20 s a responder. Antes, o leitor
 * só descobria isso depois de tentar (30 s de cada vez) e, ao fim de várias, desistia com «fontes falharam».
 * Aqui, o teste pede só 1 byte (seguindo os redirecionamentos do debrid) e lê o tamanho total do ficheiro:
 * um filme verdadeiro tem centenas de MB; o vídeo de espera tem poucos.
 */
object SourceRace {
    data class Check(val ok: Boolean, val verdict: String, val finalUrl: String, val sizeBytes: Long, val ms: Long)

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true)
            .build()
    }

    /** Menos do que isto num filme/episódio = vídeo de espera ou de erro do debrid. */
    private fun minBytes(type: String) = when (type) { "movie" -> 60L * 1024 * 1024; "series", "anime" -> 30L * 1024 * 1024; else -> 0L }

    /** Testa uma fonte (bloqueante). */
    fun check(s: StreamItem, type: String): Check {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val b = Request.Builder().url(s.url).header("User-Agent", Http.UA).header("Range", "bytes=0-0")
            for ((k, v) in s.headers) b.header(k, v)
            client.newCall(b.build()).execute().use { r ->
                val ms = SystemClock.elapsedRealtime() - t0
                val ct = (r.header("Content-Type") ?: "").lowercase()
                val total = r.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    ?: if (r.code == 200) (r.header("Content-Length")?.toLongOrNull() ?: -1L) else -1L
                val final = r.request.url.toString()
                when {
                    r.code !in 200..299 -> Check(false, "HTTP ${r.code}", final, total, ms)
                    ct.startsWith("text/") || ct.contains("json") || ct.contains("html") -> Check(false, "página de erro", final, total, ms)
                    total in 1 until minBytes(type) -> Check(false, "vídeo de espera (${total / 1048576} MB)", final, total, ms)
                    else -> Check(true, "ok", final, total, ms)
                }
            }
        } catch (e: java.io.InterruptedIOException) {
            Check(false, "não respondeu a tempo", s.url, -1, SystemClock.elapsedRealtime() - t0)
        } catch (e: Exception) {
            Check(false, e.javaClass.simpleName, s.url, -1, SystemClock.elapsedRealtime() - t0)
        }
    }

    data class Pick(val index: Int, val check: Check?, val failed: Map<Int, Check>)

    // Os testes correm fora de quem pede: quando a escolha está feita, não se espera pelos mais lentos.
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Escolhe a fonte: testa até [parallel] ao mesmo tempo, pela ordem de [order]. Fica com a primeira da ordem
     * que passa; se uma mais abaixo já passou e as de cima continuam sem resposta 2,5 s depois, fica com essa
     * (não se espera por uma fonte lenta quando há uma boa pronta). Desiste ao fim de [budgetMs].
     */
    suspend fun pick(streams: List<StreamItem>, order: List<Int>, type: String, parallel: Int = 3, budgetMs: Long = 20_000,
                     onProgress: (String) -> Unit = {}): Pick {
        val cand = order.take(8)
        if (cand.isEmpty()) return Pick(-1, null, emptyMap())
        val jobs = LinkedHashMap<Int, Deferred<Check>>()
        val doneAt = HashMap<Int, Long>()
        val failed = LinkedHashMap<Int, Check>()
        val t0 = SystemClock.elapsedRealtime()
        var next = 0
        fun launchMore() {
            while (next < cand.size && jobs.count { !it.value.isCompleted } < parallel) {
                val i = cand[next++]
                jobs[i] = bg.async { check(streams[i], type) }
            }
        }
        launchMore()
        onProgress("A testar as melhores fontes…")
        while (true) {
            val now = SystemClock.elapsedRealtime()
            for ((i, j) in jobs) if (j.isCompleted && i !in doneAt) {
                doneAt[i] = now
                val c = j.await()
                if (!c.ok) failed[i] = c
            }
            // a primeira da ordem que passou, se todas as de cima já falharam
            for (i in cand) {
                val j = jobs[i] ?: break
                if (!j.isCompleted) break
                val c = j.await()
                if (c.ok) { jobs.values.forEach { if (!it.isCompleted) it.cancel() }; return Pick(i, c, failed) }
            }
            // uma boa mais abaixo, à espera há 2,5 s de outras mais lentas acima
            val good = cand.firstOrNull { i -> jobs[i]?.isCompleted == true && failed[i] == null && now - (doneAt[i] ?: now) >= 2500 }
            if (good != null) {
                jobs.values.forEach { if (!it.isCompleted) it.cancel() }
                return Pick(good, jobs[good]!!.await(), failed)
            }
            launchMore()
            val allDone = next >= cand.size && jobs.values.all { it.isCompleted }
            if (allDone || now - t0 > budgetMs) break
            if (failed.isNotEmpty()) onProgress("A testar fontes… (${failed.size} sem vídeo, a passar à seguinte)")
            delay(120)
        }
        jobs.values.forEach { if (!it.isCompleted) it.cancel() }
        return Pick(-1, null, failed)
    }
}
