package com.musicstats.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrackingService : Service() {
    override fun onCreate() {
        super.onCreate()
        TrackingNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                TrackingNotification.NOTIFICATION_ID,
                TrackingNotification.build(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                TrackingNotification.NOTIFICATION_ID,
                TrackingNotification.build(this)
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
