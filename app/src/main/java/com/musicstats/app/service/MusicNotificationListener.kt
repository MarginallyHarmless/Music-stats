package com.musicstats.app.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
    private val activeControllers = mutableMapOf<MediaSession.Token, MediaController>()
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MusicNotifListener", "onListenerConnected called")
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MusicNotificationListener::class.java)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.d("MusicNotifListener", "onActiveSessionsChanged: ${controllers?.size} sessions")
            onSessionsChanged(controllers)
        }
        sessionsChangedListener = listener
        manager.addOnActiveSessionsChangedListener(listener, componentName)
        val controllers = manager.getActiveSessions(componentName)
        Log.d("MusicNotifListener", "Initial active sessions: ${controllers.size}")
        onSessionsChanged(controllers)
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        Log.d("MusicNotifListener", "onSessionsChanged: ${controllers?.size} controllers")
        controllers?.forEach { controller ->
            if (activeCallbacks.containsKey(controller.sessionToken)) return@forEach
            Log.d("MusicNotifListener", "  Registering callback for ${controller.packageName}")

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
            activeControllers[controller.sessionToken] = controller

            controller.metadata?.let { tracker.onMetadataChanged(it, controller.packageName, scope) }
            controller.playbackState?.let { tracker.onPlaybackStateChanged(it, controller.packageName, scope) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        activeCallbacks.forEach { (token, callback) ->
            activeControllers[token]?.unregisterCallback(callback)
        }
        activeCallbacks.clear()
        activeControllers.clear()
        sessionsChangedListener?.let { listener ->
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.removeOnActiveSessionsChangedListener(listener)
        }
        sessionsChangedListener = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
