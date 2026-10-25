package com.hortencioflix.tv.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

/**
 * «Continuar a ver» no ecrã inicial do Android TV / Google TV (fila «Play Next»).
 * Cada título fica com um único cartão, atualizado com a posição; sai quando acaba de ser visto.
 * Só corre em TV (com o fornecedor de programas do sistema); em telemóveis não faz nada.
 */
object WatchNext {
    private fun supported(ctx: Context) =
        android.os.Build.VERSION.SDK_INT >= 26 && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun key(metaId: String) = "wn:" + metaId

    @SuppressLint("RestrictedApi")
    fun update(ctx: Context, meta: MetaItem, videoId: String, posMs: Long, durMs: Long) {
        if (!supported(ctx)) return
        try {
            remove(ctx, meta.id)
            val episode = videoId.count { it == ':' } >= 2
            val open = Intent(ctx, com.hortencioflix.tv.ui.OpenActivity::class.java)
                .putExtra("id", meta.id).putExtra("type", meta.type).putExtra("name", meta.name)
                .putExtra("poster", meta.poster).putExtra("bg", meta.background)
            val b = WatchNextProgram.Builder()
                .setType(if (episode) TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE else TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setLastPlaybackPositionMillis(posMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setDurationMillis(durMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setTitle(meta.name)
                .setInternalProviderId(meta.id)
                .setIntent(open)
            if (episode) {
                val parts = videoId.split(':')
                b.setEpisodeTitle("T${parts.getOrNull(1)} E${parts.getOrNull(2)}")
            }
            val art = meta.background.ifEmpty { meta.poster }
            if (art.isNotEmpty()) b.setPosterArtUri(Uri.parse(art))
                .setPosterArtAspectRatio(if (meta.background.isNotEmpty()) TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9 else TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            val uri = ctx.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
            if (uri != null) Prefs.put(key(meta.id), android.content.ContentUris.parseId(uri).toString())
        } catch (e: Throwable) {
            // sem fornecedor de programas (algumas boxes): ignorar
        }
    }

    fun remove(ctx: Context, metaId: String) {
        if (!supported(ctx)) return
        val id = Prefs.str(key(metaId)).toLongOrNull() ?: return
        try { ctx.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) } catch (e: Throwable) {}
        Prefs.remove(key(metaId))
    }
}
