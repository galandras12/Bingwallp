package com.galandras.bingwallpaper

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        when (val r = WallpaperUpdater(applicationContext).run(force = false)) {
            is UpdateResult.Applied, is UpdateResult.AlreadyApplied -> Result.success()
            is UpdateResult.Failed ->
                // Átmeneti hibánál exponenciális backoff-fal újrapróbál (max. 5 kísérlet),
                // különben ennek a periódusnak vége, a következő a szokásos időben jön.
                if (r.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "bing_wallpaper_periodic"
        private const val MAX_ATTEMPTS = 5

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
                if (intervalHours == 12) 12L else 24L, TimeUnit.HOURS,
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
