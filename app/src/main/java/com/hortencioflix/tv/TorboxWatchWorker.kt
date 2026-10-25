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
import com.hortencioflix.tv.data.MetaItem
import com.hortencioflix.tv.data.Prefs
import com.hortencioflix.tv.data.TorboxApi
import com.hortencioflix.tv.ui.DetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** De 15 em 15 minutos vê se os downloads pedidos ao TorBox já estão prontos e avisa. */
class TorboxWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object {
        private const val CHANNEL = "torbox_ready"
        private const val NAME = "torboxWatch"

        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (TorboxApi.watched().length() == 0) { wm.cancelUniqueWork(NAME); return }
            val cons = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
            val req = PeriodicWorkRequestBuilder<TorboxWatchWorker>(15, TimeUnit.MINUTES).setConstraints(cons).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val items = TorboxApi.watched()
        if (items.length() == 0 || Prefs.torboxKey.isEmpty()) { WorkManager.getInstance(ctx).cancelUniqueWork(NAME); return Result.success() }
        val list = withContext(Dispatchers.IO) { TorboxApi.list() }
        if (list.isEmpty()) return Result.success()   // sem resposta: tenta na próxima volta
        val keep = JSONArray()
        val now = System.currentTimeMillis()
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val t = list[it.optString("h")]
            when {
                t != null && TorboxApi.isReady(t) -> notifyReady(ctx, it, i)
                now - it.optLong("at") > 48 * 3600_000L -> {}            // desiste ao fim de 2 dias
                else -> keep.put(it)
            }
        }
        TorboxApi.setWatched(keep)
        if (keep.length() == 0) WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        return Result.success()
    }

    private fun notifyReady(ctx: Context, it: org.json.JSONObject, n: Int) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads do TorBox prontos", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val meta = MetaItem(it.optString("m"), it.optString("t"), it.optString("title"), it.optString("p"), videoId = it.optString("v"))
        val intent = Intent(ctx, DetailsActivity::class.java).putExtra("meta", meta).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 2000 + n, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val note = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("Pronto para ver: ${meta.name}")
            .setContentText("O TorBox já descarregou ${it.optString("n").ifEmpty { "a fonte que pediste" }}.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(3000 + (it.optString("h").hashCode() and 0xfff), note) } catch (e: SecurityException) {}
    }
}
