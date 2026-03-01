package com.musicstats.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImageFetcher @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches an artist image URL from the Deezer API.
     * Returns the medium-size picture URL, or null if not found.
     */
    suspend fun fetchImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val url = URL("https://api.deezer.com/search/artist?q=$encoded&limit=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "MusicStats/1.0")

            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = json.decodeFromString<DeezerSearchResponse>(body)
            response.data.firstOrNull()?.pictureXl
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches album art URL from the Deezer API by searching for a track.
     * Returns the album cover URL, or null if not found.
     */
    suspend fun fetchAlbumArtUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("https://api.deezer.com/search/track?q=$query&limit=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "MusicStats/1.0")

            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = json.decodeFromString<DeezerTrackSearchResponse>(body)
            response.data.firstOrNull()?.album?.coverXl
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
private data class DeezerSearchResponse(
    val data: List<DeezerArtist> = emptyList()
)

@Serializable
private data class DeezerArtist(
    val name: String = "",
    @kotlinx.serialization.SerialName("picture_xl")
    val pictureXl: String? = null
)

@Serializable
private data class DeezerTrackSearchResponse(
    val data: List<DeezerTrack> = emptyList()
)

@Serializable
private data class DeezerTrack(
    val title: String = "",
    val album: DeezerAlbum? = null
)

@Serializable
private data class DeezerAlbum(
    @kotlinx.serialization.SerialName("cover_xl")
    val coverXl: String? = null
)
