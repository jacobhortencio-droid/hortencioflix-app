package com.hortencioflix.tv.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hortencioflix.tv.TorboxWatchWorker
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.SourcePicker
import com.hortencioflix.tv.data.StreamItem
import com.hortencioflix.tv.data.TorboxApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «Adicionar ao TorBox e avisar-me»: envia o torrent e fica a vigiar até estar pronto. */
object TorboxQueue {
    /** A melhor fonte por descarregar que dá para pedir ao TorBox (a lista já vem ordenada). */
    fun best(streams: List<StreamItem>): StreamItem? = streams.firstOrNull { TorboxApi.canQueue(it) && it.quality in 1..1080 }
        ?: streams.firstOrNull { TorboxApi.canQueue(it) }

    fun queue(a: FragmentActivity, s: StreamItem, meta: MetaItem, videoId: String, after: (() -> Unit)? = null) {
        val hash = TorboxApi.hashOf(s.url) ?: return a.toast("Esta fonte não pode ser enviada ao TorBox.")
        a.toast("A enviar para o TorBox…")
        a.lifecycleScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) { TorboxApi.queue(hash) }
            if (a.isFinishing) return@launch
            if (ok) {
                TorboxApi.watch(hash, meta, videoId, SourcePicker.qualityLabel(s.quality))
                TorboxWatchWorker.schedule(a)
                a.dialog().setTitle("A descarregar no TorBox")
                    .setMessage("$msg\n\nAvisamos-te com uma notificação quando «${meta.name}» estiver pronto a ver (normalmente minutos; torrents com poucas fontes podem demorar mais).")
                    .setPositiveButton("OK") { _, _ -> after?.invoke() }
                    .setOnCancelListener { after?.invoke() }
                    .show()
            } else {
                a.dialog().setTitle("Não foi possível adicionar").setMessage(msg).setPositiveButton("OK", null).show()
            }
        }
    }

    /** Pergunta ao tocar numa fonte por descarregar. */
    fun ask(a: FragmentActivity, s: StreamItem, meta: MetaItem, videoId: String, playAnyway: () -> Unit) {
        a.dialog().setTitle("Esta fonte ainda não está no TorBox")
            .setMessage("Se a abrires agora, aparece só um vídeo de espera. Queres que o TorBox a descarregue e te avise quando estiver pronta?")
            .setPositiveButton("Descarregar e avisar-me") { _, _ -> queue(a, s, meta, videoId) }
            .setNeutralButton("Abrir mesmo assim") { _, _ -> playAnyway() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Ao abrir a app: avisa (no máximo uma vez por dia) se a conta TorBox tem algum problema. */
    fun checkAccount(a: FragmentActivity) {
        if (com.hortencioflix.tv.data.Prefs.torboxKey.isEmpty()) return
        a.lifecycleScope.launch {
            val acc = withContext(Dispatchers.IO) { TorboxApi.account() }
            val w = TorboxApi.warning(acc) ?: return@launch
            val key = "tbWarn:" + w.take(20) + ":" + (System.currentTimeMillis() / 86_400_000L)
            if (com.hortencioflix.tv.data.Prefs.str(key) == "1" || a.isFinishing) return@launch
            com.hortencioflix.tv.data.Prefs.put(key, "1")
            a.dialog().setTitle("Conta TorBox").setMessage(w).setPositiveButton("OK", null).show()
        }
    }
}
