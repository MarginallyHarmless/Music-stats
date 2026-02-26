package com.musicstats.app.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class MusicNotificationListener : NotificationListenerService() {

    @Inject lateinit var tracker: MediaSessionTracker

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeCallbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.addOnActiveSessionsChangedListener(
            { controllers -> onSessionsChanged(controllers) },
            ComponentName(this, MusicNotificationListener::class.java)
        )
        val controllers = manager.getActiveSessions(
            ComponentName(this, MusicNotificationListener::class.java)
        )
        onSessionsChanged(controllers)
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        controllers?.forEach { controller ->
            if (activeCallbacks.containsKey(controller.sessionToken)) return@forEach

            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    tracker.onMetadataChanged(metadata, controller.packageName, scope)
                }
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    tracker.onPlaybackStateChanged(state, controller.packageName, scope)
                }
            }

            controller.registerCallback(callback)
            activeCallbacks[controller.sessionToken] = callback

            controller.metadata?.let { tracker.onMetadataChanged(it, controller.packageName, scope) }
            controller.playbackState?.let { tracker.onPlaybackStateChanged(it, controller.packageName, scope) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        activeCallbacks.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
