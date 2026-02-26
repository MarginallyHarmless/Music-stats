package com.musicstats.app.data.export

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: String, // ISO 8601
    val songs: List<ExportSong>,
    val artists: List<ExportArtist>,
    val listeningEvents: List<ExportListeningEvent>
)

@Serializable
data class ExportSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val firstHeardAt: Long,
    val genre: String? = null,
    val releaseYear: Int? = null
)

@Serializable
data class ExportArtist(
    val name: String,
    val firstHeardAt: Long
)

@Serializable
data class ExportListeningEvent(
    val songTitle: String,
    val songArtist: String,
    val startedAt: Long,
    val durationMs: Long,
    val sourceApp: String,
    val completed: Boolean
)
