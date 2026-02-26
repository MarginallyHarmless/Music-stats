package com.musicstats.app.service

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.musicstats.app.data.repository.MusicRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaSessionTrackerTest {

    private lateinit var repository: MusicRepository
    private lateinit var tracker: MediaSessionTracker
    private lateinit var scope: TestScope

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        tracker = MediaSessionTracker(repository)
        scope = TestScope(UnconfinedTestDispatcher())
    }

    private fun createMetadata(title: String, artist: String, album: String? = null): MediaMetadata {
        val metadata = mockk<MediaMetadata>()
        every { metadata.getString(MediaMetadata.METADATA_KEY_TITLE) } returns title
        every { metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) } returns artist
        every { metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) } returns album
        return metadata
    }

    private fun createPlaybackState(state: Int): PlaybackState {
        val playbackState = mockk<PlaybackState>()
        every { playbackState.state } returns state
        return playbackState
    }

    @Test
    fun `ignores metadata with null title`() = runTest {
        val metadata = mockk<MediaMetadata>()
        every { metadata.getString(MediaMetadata.METADATA_KEY_TITLE) } returns null
        every { metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) } returns "Artist"
        every { metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) } returns null

        tracker.onMetadataChanged(metadata, "com.spotify", scope)

        coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ignores metadata with null artist`() = runTest {
        val metadata = mockk<MediaMetadata>()
        every { metadata.getString(MediaMetadata.METADATA_KEY_TITLE) } returns "Song"
        every { metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) } returns null
        every { metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) } returns null

        tracker.onMetadataChanged(metadata, "com.spotify", scope)

        coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `play then pause records a play`() = runTest {
        val metadata = createMetadata("Song", "Artist")
        tracker.onMetadataChanged(metadata, "com.spotify", scope)
        tracker.onPlaybackStateChanged(createPlaybackState(PlaybackState.STATE_PLAYING), "com.spotify", scope)

        // Simulate time passing
        Thread.sleep(50)

        tracker.onPlaybackStateChanged(createPlaybackState(PlaybackState.STATE_PAUSED), "com.spotify", scope)

        // Duration < 5s so it should be ignored
        coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `track change while playing saves previous track`() = runTest {
        coEvery { repository.recordPlay(any(), any(), any(), any(), any(), any(), any()) } returns mockk()

        val metadata1 = createMetadata("Song 1", "Artist 1")
        tracker.onPlaybackStateChanged(createPlaybackState(PlaybackState.STATE_PLAYING), "com.spotify", scope)
        tracker.onMetadataChanged(metadata1, "com.spotify", scope)

        // Change track - since duration < 5s, previous won't be saved
        val metadata2 = createMetadata("Song 2", "Artist 2")
        tracker.onMetadataChanged(metadata2, "com.spotify", scope)

        // No recording because duration was < 5s
        coVerify(exactly = 0) { repository.recordPlay(eq("Song 1"), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `null metadata is ignored`() = runTest {
        tracker.onMetadataChanged(null, "com.spotify", scope)

        coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `null playback state transitions to not playing`() = runTest {
        tracker.onPlaybackStateChanged(createPlaybackState(PlaybackState.STATE_PLAYING), "com.spotify", scope)
        tracker.onPlaybackStateChanged(null, "com.spotify", scope)

        // Should not crash, and should handle gracefully
        coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any()) }
    }
}
