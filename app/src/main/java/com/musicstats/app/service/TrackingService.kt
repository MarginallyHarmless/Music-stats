package com.musicstats.app.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

private const val TAG = "TrackingService"

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var tracker: MediaSessionTracker

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeCallbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()
    private val activeControllers = mutableMapOf<MediaSession.Token, MediaController>()
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var isSessionTrackingActive = false

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

        startMediaSessionTracking()

        return START_STICKY
    }

    private fun startMediaSessionTracking() {
        if (isSessionTrackingActive) return
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MusicNotificationListener::class.java)

        try {
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                Log.d(TAG, "onActiveSessionsChanged: ${controllers?.size} sessions")
                DebugLog.log(DebugEventType.TRACKING, "Sessions changed: ${controllers?.size} controllers")
                onSessionsChanged(controllers)
            }
            sessionsChangedListener = listener
            manager.addOnActiveSessionsChangedListener(listener, componentName)

            val controllers = manager.getActiveSessions(componentName)
            Log.d(TAG, "Initial active sessions: ${controllers.size} — ${controllers.map { it.packageName }}")
            DebugLog.log(DebugEventType.TRACKING, "Initial sessions: ${controllers.size} — ${controllers.map { it.packageName }}")
            onSessionsChanged(controllers)
            isSessionTrackingActive = true
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification listener permission yet", e)
            DebugLog.log(DebugEventType.TRACKING, "No listener permission: ${e.message}")
        }
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        // Clean up sessions that are no longer active
        val currentTokens = controllers?.map { it.sessionToken }?.toSet() ?: emptySet()
        val staleTokens = activeCallbacks.keys - currentTokens
        staleTokens.forEach { token ->
            DebugLog.log(DebugEventType.TRACKING, "Removing stale session: ${activeControllers[token]?.packageName}")
            activeControllers[token]?.let { ctrl ->
                activeCallbacks[token]?.let { cb -> ctrl.unregisterCallback(cb) }
            }
            activeCallbacks.remove(token)
            activeControllers.remove(token)
        }

        controllers?.forEach { controller ->
            if (activeCallbacks.containsKey(controller.sessionToken)) return@forEach
            DebugLog.log(DebugEventType.TRACKING, "Registering session: ${controller.packageName}")

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
            // Don't replay existing state — just wait for new callbacks
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(this, TrackingService::class.java)
        startService(restartIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        activeCallbacks.forEach { (token, callback) ->
            activeControllers[token]?.unregisterCallback(callback)
        }
        activeCallbacks.clear()
        activeControllers.clear()
        sessionsChangedListener?.let { listener ->
            try {
                val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                manager.removeOnActiveSessionsChangedListener(listener)
            } catch (_: Exception) {}
        }
        sessionsChangedListener = null
        isSessionTrackingActive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
