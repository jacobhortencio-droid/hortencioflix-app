package com.hortencioflix.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hortencioflix.tv.data.HomeData
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.ui.MainActivity
import java.util.concurrent.TimeUnit

/** Verifica de 12 em 12 horas se saíram episódios novos das séries que acompanhas. */
class EpisodeWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    companion object {
        private const val CHANNEL = "new_episodes"

        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (!Prefs.notifyEpisodes) { wm.cancelUniqueWork("newEpisodes"); return }
            val cons = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<EpisodeWorker>(12, TimeUnit.HOURS).setConstraints(cons).build()
            wm.enqueueUniquePeriodicWork("newEpisodes", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    override fun doWork(): Result {
        if (!Prefs.notifyEpisodes) return Result.success()
        return try {
            Prefs.put("newEpAt", "0")
            val eps = HomeData.newEpisodes()
            val notified = Prefs.array("notifiedEp")
            val seen = (0 until notified.length()).map { notified.optString(it) }.toMutableSet()
            val fresh = eps.filter { it.videoId !in seen }
            if (fresh.isNotEmpty()) {
                notify(fresh.map { "${it.name} · ${it.info.substringBefore(" · novo")}" })
                seen.addAll(fresh.map { it.videoId })
                Prefs.putArray("notifiedEp", org.json.JSONArray(seen.toList().takeLast(300)))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(lines: List<String>) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Novos episódios", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val title = if (lines.size == 1) "Novo episódio disponível" else "${lines.size} episódios novos"
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(1001, n) } catch (e: SecurityException) {}
    }
}
