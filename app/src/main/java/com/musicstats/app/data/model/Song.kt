package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [Index(value = ["title", "artist"], unique = true)]
)
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String? = null,
    val firstHeardAt: Long,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val albumArtUrl: String? = null,
    val paletteDominant: Int? = null,
    val paletteVibrant: Int? = null,
    val paletteMuted: Int? = null,
    val paletteDarkVibrant: Int? = null,
    val paletteDarkMuted: Int? = null,
    val paletteLightVibrant: Int? = null
)
