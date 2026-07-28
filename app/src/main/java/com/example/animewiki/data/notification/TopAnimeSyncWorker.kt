package com.example.animewiki.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.graphql.TopAnimeQuery
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class TopAnimeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apolloClient: ApolloClient,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun doWork(): Result {
        return try {
            val data = apolloClient.query(
                TopAnimeQuery(
                    page = 1,
                    perPage = 1,
                    isAdult = Optional.present(false)
                )
            ).execute().dataOrAniListError()
            val top = data.Page?.media.orEmpty()
                .firstNotNullOfOrNull { it?.animeCacheFields?.toDomain() }
                ?: return Result.retry()
            val score = top.score?.let { "%.2f".format(it) } ?: "—"

            notificationHelper.showWeeklyTopAnime(
                animeId = top.id,
                title = top.title,
                score = score
            )

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network might be flaky, WorkManager will retry with exponential backoff
            Result.retry()
        }
    }
}
