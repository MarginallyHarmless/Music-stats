package com.musicstats.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.musicstats.app.R
import com.musicstats.app.data.model.Moment
import com.musicstats.app.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MomentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val detector: MomentDetector
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val newMoments = detector.detectAndPersistNewMoments()
            newMoments.forEach { fireNotification(it) }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fireNotification(moment: Moment) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        ensureChannel(nm)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notifId = (moment.id and 0x7FFFFFFFL).toInt()
        val pi = PendingIntent.getActivity(
            applicationContext, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(moment.title)
            .setContentText(moment.description)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .build()
        nm.notify(notifId, notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Moments", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Music listening milestones and insights" }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "moments"
        const val GROUP_KEY = "com.musicstats.moments"
        const val WORK_NAME = "moment_detection_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MomentWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
