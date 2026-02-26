package com.musicstats.app.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val TAG = "MediaSessionTracker"

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
    private var playStartTime: Long? = null
    private var isPlaying: Boolean = false

    @Synchronized
    fun onMetadataChanged(metadata: MediaMetadata?, sourceApp: String, scope: CoroutineScope) {
        Log.d(TAG, "onMetadataChanged from $sourceApp, metadata=${metadata != null}")
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        Log.d(TAG, "  title=$title, artist=$artist")
        if (title == null || artist == null) return

        if (title != currentTitle || artist != currentArtist) {
            Log.d(TAG, "  NEW track: $title by $artist")
            saveCurrentIfPlaying(scope)
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentSourceApp = sourceApp

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
                playStartTime = System.currentTimeMillis()
            }
        }
    }

    @Synchronized
    fun onPlaybackStateChanged(state: PlaybackState?, sourceApp: String, scope: CoroutineScope) {
        val wasPlaying = isPlaying
        isPlaying = state?.state == PlaybackState.STATE_PLAYING
        currentSourceApp = sourceApp
        Log.d(TAG, "onPlaybackStateChanged from $sourceApp: state=${state?.state}, wasPlaying=$wasPlaying, isPlaying=$isPlaying, currentTitle=$currentTitle")

        if (isPlaying && !wasPlaying) {
            playStartTime = System.currentTimeMillis()
            Log.d(TAG, "  Started playing at $playStartTime")
        } else if (!isPlaying && wasPlaying) {
            saveCurrentIfPlaying(scope)
        } else if (isPlaying && wasPlaying && playStartTime != null) {
            val positionMs = state?.position ?: return
            if (positionMs < REWIND_POSITION_THRESHOLD_MS) {
                val elapsed = System.currentTimeMillis() - playStartTime!!
                if (elapsed >= 5_000) {
                    saveCurrentIfPlaying(scope)
                    playStartTime = System.currentTimeMillis()
                }
            }
        }
    }

    companion object {
        private const val REWIND_POSITION_THRESHOLD_MS = 3_000L
    }

    @Synchronized
    private fun saveCurrentIfPlaying(scope: CoroutineScope) {
        val title = currentTitle ?: run { Log.d(TAG, "  saveCurrentIfPlaying: no title"); return }
        val artist = currentArtist ?: run { Log.d(TAG, "  saveCurrentIfPlaying: no artist"); return }
        val startTime = playStartTime ?: run { Log.d(TAG, "  saveCurrentIfPlaying: no startTime"); return }
        val duration = System.currentTimeMillis() - startTime

        if (duration < 5_000) { Log.d(TAG, "  saveCurrentIfPlaying: duration too short (${duration}ms)"); return }

        val albumArtUrl = currentAlbumArtUri ?: saveBitmapToFile(currentAlbumArtBitmap, title, artist)

        Log.d(TAG, "  SAVING play: $title by $artist, duration=${duration}ms")
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
        playStartTime = null
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
