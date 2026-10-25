package com.hortencioflix.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.RokuUpdater
import com.hortencioflix.tv.ui.MainActivity
import com.hortencioflix.tv.ui.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Uma vez por dia, mesmo com a app fechada: descarrega a versão nova do Android (fica pronta
 * a instalar) e, se configurado, atualiza a app da Roku pela rede de casa.
 */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object {
        private const val CHANNEL = "updates"

        fun schedule(ctx: Context) {
            val cons = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS).setConstraints(cons).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("dailyUpdate", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            val v = Updater.latest()
            if (v != null && v > BuildConfig.VERSION_CODE && Prefs.str("updSkip") != v.toString() && Prefs.str("updReady") != v.toString()) {
                val f = Updater.fetch(ctx, v)
                if (f != null) {
                    Prefs.put("updReady", v.toString())
                    Prefs.put("updReadyName", Updater.latestName)
                    notify(ctx, 1101, "Nova versão da Hortêncioflix pronta", "Versão ${Updater.latestName.ifEmpty { "nº $v" }} descarregada — toca para instalar.")
                }
            }
        } catch (e: Exception) {
        }
        if (Prefs.str("rokuAuto") == "1" && RokuUpdater.configured) {
            Prefs.put("rokuAutoRunning", "1")
            val r = try { withContext(Dispatchers.IO) { RokuUpdater.update(ctx) } } finally { Prefs.put("rokuAutoRunning", "0") }
            if (r.ok && r.message.startsWith("Roku atualizada")) notify(ctx, 1102, "Roku atualizada", r.message)
        }
        return Result.success()
    }

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) {}
    }
}
