package com.musicstats.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Minimal NotificationListenerService — exists solely to hold the
 * BIND_NOTIFICATION_LISTENER_SERVICE permission. The actual media-session
 * tracking lives in [TrackingService] which uses our ComponentName to
 * call MediaSessionManager.getActiveSessions().
 *
 * This avoids ColorOS OplusAppStartupManager blocking the service from
 * binding in the background.
 */
class MusicNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
