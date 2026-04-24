package com.example.animewiki.data.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    fun scheduleWeekly() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<TopAnimeSyncWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(delayUntilNextMondayAt9am(), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // se já tiver, mantém — não reagenda
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /** Only for debug/testing — fires as soon as constraints are met. */
    fun scheduleNowForTesting() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<TopAnimeSyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueue(request)
    }

    private fun delayUntilNextMondayAt9am(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Move pro próximo segunda-feira
            while (
                get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY ||
                timeInMillis <= now.timeInMillis
            ) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    companion object {
        private const val WORK_NAME = "weekly_top_anime"
    }
}