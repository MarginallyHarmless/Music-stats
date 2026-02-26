package com.musicstats.app.service

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.musicstats.app.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class MediaSessionTracker @Inject constructor(
    private val repository: MusicRepository
) {
    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentAlbum: String? = null
    private var currentSourceApp: String? = null
    private var playStartTime: Long? = null
    private var isPlaying: Boolean = false

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
            if (isPlaying) {
                playStartTime = System.currentTimeMillis()
            }
        }
    }

    fun onPlaybackStateChanged(state: PlaybackState?, sourceApp: String, scope: CoroutineScope) {
        val wasPlaying = isPlaying
        isPlaying = state?.state == PlaybackState.STATE_PLAYING
        currentSourceApp = sourceApp

        if (isPlaying && !wasPlaying) {
            playStartTime = System.currentTimeMillis()
        } else if (!isPlaying && wasPlaying) {
            saveCurrentIfPlaying(scope)
        }
    }

    private fun saveCurrentIfPlaying(scope: CoroutineScope) {
        val title = currentTitle ?: return
        val artist = currentArtist ?: return
        val startTime = playStartTime ?: return
        val duration = System.currentTimeMillis() - startTime

        if (duration < 5_000) return // ignore <5s plays

        scope.launch {
            repository.recordPlay(
                title = title,
                artist = artist,
                album = currentAlbum,
                sourceApp = currentSourceApp ?: "unknown",
                startedAt = startTime,
                durationMs = duration,
                completed = duration > 30_000
            )
        }
        playStartTime = null
    }
}
