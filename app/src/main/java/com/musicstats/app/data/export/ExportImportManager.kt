package com.musicstats.app.data.export

import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportManager @Inject constructor(
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val eventDao: ListeningEventDao
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportToJson(): String {
        val songs = songDao.getAllSongsSnapshot()
        val artists = artistDao.getAllArtistsSnapshot()
        val events = eventDao.getAllEventsSnapshot()
        val songIdToSong = songs.associateBy { it.id }

        val data = ExportData(
            exportedAt = Instant.now().toString(),
            songs = songs.map { ExportSong(it.title, it.artist, it.album, it.firstHeardAt, it.genre, it.releaseYear) },
            artists = artists.map { ExportArtist(it.name, it.firstHeardAt) },
            listeningEvents = events.mapNotNull { event ->
                val song = songIdToSong[event.songId] ?: return@mapNotNull null
                ExportListeningEvent(song.title, song.artist, event.startedAt, event.durationMs, event.sourceApp, event.completed)
            }
        )
        return json.encodeToString(ExportData.serializer(), data)
    }

    suspend fun importFromJson(jsonString: String): ImportResult {
        val data = json.decodeFromString(ExportData.serializer(), jsonString)
        var songsImported = 0
        var eventsImported = 0

        for (artist in data.artists) {
            if (artistDao.findByName(artist.name) == null) {
                artistDao.insert(Artist(name = artist.name, firstHeardAt = artist.firstHeardAt))
            }
        }

        for (song in data.songs) {
            if (songDao.findByTitleAndArtist(song.title, song.artist) == null) {
                songDao.insert(Song(
                    title = song.title, artist = song.artist, album = song.album,
                    firstHeardAt = song.firstHeardAt, genre = song.genre, releaseYear = song.releaseYear
                ))
                songsImported++
            }
        }

        for (event in data.listeningEvents) {
            val song = songDao.findByTitleAndArtist(event.songTitle, event.songArtist) ?: continue
            if (eventDao.findBySongAndTime(song.id, event.startedAt) == null) {
                eventDao.insert(ListeningEvent(
                    songId = song.id, startedAt = event.startedAt, durationMs = event.durationMs,
                    sourceApp = event.sourceApp, completed = event.completed
                ))
                eventsImported++
            }
        }

        return ImportResult(songsImported, eventsImported)
    }
}

data class ImportResult(val songsImported: Int, val eventsImported: Int)
