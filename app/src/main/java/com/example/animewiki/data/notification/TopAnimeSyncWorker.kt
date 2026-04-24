package com.example.animewiki.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.animewiki.data.remote.JikanApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TopAnimeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: JikanApi,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            val response = api.getTopAnime(page = 1, limit = 1)
            val top = response.data?.firstOrNull() ?: return Result.retry()

            val title = top.titleEnglish?.takeIf { it.isNotBlank() } ?: top.title
            val score = top.score?.let { "%.2f".format(it) } ?: "—"
            val id = top.malId

            notificationHelper.showWeeklyTopAnime(
                animeId = id,
                title = title,
                score = score
            )

            Result.success()
        } catch (e: Exception) {
            // Network might be flaky, WorkManager will retry with exponential backoff
            Result.retry()
        }
    }
}