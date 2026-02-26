package com.musicstats.app.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

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
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        if (title == null || artist == null) return

        if (title != currentTitle || artist != currentArtist) {
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

        if (isPlaying && !wasPlaying) {
            playStartTime = System.currentTimeMillis()
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
        val title = currentTitle ?: return
        val artist = currentArtist ?: return
        val startTime = playStartTime ?: return
        val duration = System.currentTimeMillis() - startTime

        if (duration < 5_000) return

        val albumArtUrl = currentAlbumArtUri ?: saveBitmapToFile(currentAlbumArtBitmap, title, artist)

        scope.launch {
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
