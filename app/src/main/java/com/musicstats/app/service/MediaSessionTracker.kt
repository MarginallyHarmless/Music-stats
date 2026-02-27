package com.musicstats.app.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaSessionTracker"

@Singleton
class MediaSessionTracker @Inject constructor(
    private val repository: MusicRepository,
    @ApplicationContext private val context: Context
) {
    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentAlbum: String? = null
    private var currentAlbumArtUri: String? = null
    private var currentAlbumArtBitmap: Bitmap? = null
    private var currentSourceApp: String? = null
    private var playStartTime: Long? = null       // wall-clock epoch ms, used for startedAt
    private var isPlaying: Boolean = false

    // Exposed state for UI live ticker
    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()
    private val _currentSessionStartMs = MutableStateFlow(0L)
    val currentSessionStartMs: StateFlow<Long> = _currentSessionStartMs.asStateFlow()
    private var currentMediaDurationMs: Long? = null

    // Position-based tracking
    private var playStartPositionMs: Long? = null  // media position when play started
    private var lastKnownPositionMs: Long? = null  // most recent reported position
    private var lastPositionUpdateRealtime: Long? = null // SystemClock.elapsedRealtime() of last position update
    private var lastPlaybackSpeed: Float = 1.0f

    private fun extractTitle(metadata: MediaMetadata): String? {
        return metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
    }

    private fun extractArtist(metadata: MediaMetadata): String? {
        return metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
    }

    private fun updatePositionFromState(state: PlaybackState?) {
        if (state == null) return
        val pos = state.position
        if (pos >= 0) {
            lastKnownPositionMs = pos
            lastPositionUpdateRealtime = state.lastPositionUpdateTime
            lastPlaybackSpeed = if (state.playbackSpeed > 0) state.playbackSpeed else 1.0f
        }
    }

    /**
     * Estimate current media position by extrapolating from last known position.
     */
    private fun estimateCurrentPositionMs(): Long? {
        val lastPos = lastKnownPositionMs ?: return null
        val lastUpdate = lastPositionUpdateRealtime ?: return lastPos
        val elapsed = SystemClock.elapsedRealtime() - lastUpdate
        return lastPos + (elapsed * lastPlaybackSpeed).toLong()
    }

    /**
     * Calculate played duration using position data when available, wall-clock as fallback.
     */
    private fun calculateDuration(): Long {
        val startPos = playStartPositionMs
        val currentPos = estimateCurrentPositionMs()

        // Position-based: use media positions if both are available
        if (startPos != null && currentPos != null && currentPos >= startPos) {
            val positionDuration = currentPos - startPos
            Log.d(TAG, "  Duration from position: ${positionDuration}ms (startPos=$startPos, currentPos=$currentPos)")
            DebugLog.log(DebugEventType.TRACKING, "Duration: ${positionDuration}ms (position-based, start=$startPos, cur=$currentPos)")
            return positionDuration
        }

        // Fallback: wall-clock time
        val startTime = playStartTime ?: return 0
        val wallClockDuration = System.currentTimeMillis() - startTime
        Log.d(TAG, "  Duration from wall-clock (fallback): ${wallClockDuration}ms")
        DebugLog.log(DebugEventType.TRACKING, "Duration: ${wallClockDuration}ms (wall-clock fallback)")

        // Apply cap to wall-clock fallback
        val mediaDur = currentMediaDurationMs
        return if (mediaDur != null && mediaDur > 0 && wallClockDuration > (mediaDur * 1.1).toLong()) {
            (mediaDur * 1.1).toLong()
        } else if (wallClockDuration > MAX_DURATION_MS) {
            MAX_DURATION_MS
        } else {
            wallClockDuration
        }
    }

    private fun startTracking(state: PlaybackState?) {
        playStartTime = System.currentTimeMillis()
        updatePositionFromState(state)
        playStartPositionMs = lastKnownPositionMs ?: 0L
        _isPlayingFlow.value = true
        _currentSessionStartMs.value = playStartTime!!
        Log.d(TAG, "  Started tracking: startTime=$playStartTime, startPosition=$playStartPositionMs")
        DebugLog.log(DebugEventType.TRACKING, "Started | startPos=$playStartPositionMs | lastKnown=$lastKnownPositionMs")
    }

    private fun resetTracking() {
        playStartTime = null
        playStartPositionMs = null
        lastKnownPositionMs = null
        lastPositionUpdateRealtime = null
        lastPlaybackSpeed = 1.0f
        _isPlayingFlow.value = false
        _currentSessionStartMs.value = 0L
    }

    private val ignoredApps = setOf(
        "com.google.android.youtube",  // Regular YouTube (videos, not music)
    )

    @Synchronized
    fun onMetadataChanged(metadata: MediaMetadata?, sourceApp: String, scope: CoroutineScope) {
        Log.d(TAG, "onMetadataChanged from $sourceApp, metadata=${metadata != null}")
        if (metadata == null) return
        if (sourceApp in ignoredApps) return

        val title = extractTitle(metadata)
        val artist = extractArtist(metadata)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

        Log.d(TAG, "  title=$title, artist=$artist")
        DebugLog.log(DebugEventType.METADATA, "$sourceApp | $title by $artist | album=$album")
        if (title == null || artist == null) return

        // Different app taking over — save current play and switch
        if (sourceApp != currentSourceApp && currentSourceApp != null && isPlaying) {
            Log.d(TAG, "  App switch: $currentSourceApp -> $sourceApp, saving current play")
            DebugLog.log(DebugEventType.METADATA, "App switch: $currentSourceApp -> $sourceApp")
            saveCurrentIfPlaying(scope)
        }

        if (title != currentTitle || artist != currentArtist) {
            Log.d(TAG, "  NEW track: $title by $artist")
            DebugLog.log(DebugEventType.METADATA, "NEW track: $title by $artist")
            saveCurrentIfPlaying(scope)
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentSourceApp = sourceApp

            // Extract media duration for completed detection
            val mediaDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            currentMediaDurationMs = if (mediaDuration > 0) mediaDuration else null

            // Extract album art: prefer URI, fall back to bitmap
            try {
                currentAlbumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                currentAlbumArtBitmap = if (currentAlbumArtUri == null) {
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                } else null
            } catch (_: Exception) {
                currentAlbumArtUri = null
                currentAlbumArtBitmap = null
            }

            if (isPlaying) {
                startTracking(null)
            }
        }
    }

    @Synchronized
    fun onPlaybackStateChanged(state: PlaybackState?, sourceApp: String, scope: CoroutineScope) {
        if (sourceApp in ignoredApps) return
        val wasPlaying = isPlaying
        isPlaying = state?.state == PlaybackState.STATE_PLAYING

        // Different app taking over — save current play and switch
        if (sourceApp != currentSourceApp && currentSourceApp != null && isPlaying) {
            Log.d(TAG, "  App switch via playback: $currentSourceApp -> $sourceApp, saving current play")
            DebugLog.log(DebugEventType.STATE, "App switch via playback: $currentSourceApp -> $sourceApp")
            saveCurrentIfPlaying(scope)
            currentSourceApp = sourceApp
            startTracking(state)
        } else {
            currentSourceApp = sourceApp
        }

        Log.d(TAG, "onPlaybackStateChanged from $sourceApp: state=${state?.state}, wasPlaying=$wasPlaying, isPlaying=$isPlaying, currentTitle=$currentTitle, position=${state?.position}")
        DebugLog.log(DebugEventType.STATE, "$sourceApp | state=${state?.state} | pos=${state?.position} | $wasPlaying->$isPlaying | $currentTitle")

        if (isPlaying && !wasPlaying) {
            startTracking(state)
        } else if (!isPlaying && wasPlaying) {
            // Only update position if it advances — don't accept pos=0 resets during skip transitions
            val incomingPos = state?.position ?: -1
            if (incomingPos > (lastKnownPositionMs ?: 0)) {
                updatePositionFromState(state)
                DebugLog.log(DebugEventType.TRACKING, "Pause: accepted pos=$incomingPos (was $lastKnownPositionMs)")
            } else {
                DebugLog.log(DebugEventType.TRACKING, "Pause: REJECTED pos=$incomingPos (keeping $lastKnownPositionMs)")
            }
            saveCurrentIfPlaying(scope)
        } else if (isPlaying && wasPlaying && playStartTime != null) {
            // Detect track restart: position jumped backward significantly
            val currentPos = state?.position ?: return
            val prevPos = lastKnownPositionMs
            if (prevPos != null && currentPos < prevPos - REWIND_THRESHOLD_MS && currentPos < REWIND_POSITION_THRESHOLD_MS) {
                val duration = calculateDuration()
                if (duration >= 5_000) {
                    Log.d(TAG, "  Track restart detected: position jumped from ${prevPos}ms to ${currentPos}ms")
                    DebugLog.log(DebugEventType.TRACKING, "Track restart: pos ${prevPos}ms -> ${currentPos}ms")
                    saveCurrentIfPlaying(scope)
                    updatePositionFromState(state)
                    startTracking(state)
                }
            } else {
                updatePositionFromState(state)  // normal position update during playback
            }
        }
    }

    companion object {
        private const val REWIND_POSITION_THRESHOLD_MS = 3_000L  // position must be near start
        private const val REWIND_THRESHOLD_MS = 1_000L           // backward jump threshold
        private const val MAX_DURATION_MS = 30L * 60 * 1_000     // 30 minutes hard cap for wall-clock fallback
    }

    @Synchronized
    private fun saveCurrentIfPlaying(scope: CoroutineScope) {
        val title = currentTitle ?: run { Log.d(TAG, "  saveCurrentIfPlaying: no title"); DebugLog.log(DebugEventType.REJECT, "No title"); return }
        val artist = currentArtist ?: run { Log.d(TAG, "  saveCurrentIfPlaying: no artist"); DebugLog.log(DebugEventType.REJECT, "No artist"); return }
        val startTime = playStartTime ?: run { Log.d(TAG, "  saveCurrentIfPlaying: no startTime"); DebugLog.log(DebugEventType.REJECT, "No startTime | $title by $artist"); return }
        val duration = calculateDuration()

        if (duration < 5_000) { Log.d(TAG, "  saveCurrentIfPlaying: duration too short (${duration}ms)"); DebugLog.log(DebugEventType.REJECT, "Too short: ${duration}ms | $title by $artist | startPos=$playStartPositionMs lastKnown=$lastKnownPositionMs"); return }

        val albumArtUrl = currentAlbumArtUri ?: saveBitmapToFile(currentAlbumArtBitmap, title, artist)

        Log.d(TAG, "  SAVING play: $title by $artist, duration=${duration}ms")
        DebugLog.log(DebugEventType.SAVE, "$title by $artist | ${duration}ms | completed=${duration > 30_000}")
        scope.launch {
            try {
                repository.recordPlay(
                    title = title,
                    artist = artist,
                    album = currentAlbum,
                    sourceApp = currentSourceApp ?: "unknown",
                    startedAt = startTime,
                    durationMs = duration,
                    completed = duration > 30_000,
                    albumArtUrl = albumArtUrl
                )
                Log.d(TAG, "  recordPlay SUCCESS for $title")
            } catch (e: Exception) {
                Log.e(TAG, "  recordPlay FAILED for $title", e)
            }
        }
        resetTracking()
    }

    private fun saveBitmapToFile(bitmap: Bitmap?, title: String, artist: String): String? {
        bitmap ?: return null
        return try {
            val dir = File(context.filesDir, "album_art")
            dir.mkdirs()
            val safeName = "$artist-$title".replace(Regex("[^a-zA-Z0-9-]"), "_").take(100)
            val file = File(dir, "$safeName.jpg")
            if (!file.exists()) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            }
            file.toURI().toString()
        } catch (_: Exception) {
            null
        }
    }
}
