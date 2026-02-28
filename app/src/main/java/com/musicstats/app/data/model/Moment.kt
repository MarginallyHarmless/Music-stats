package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moments",
    indices = [Index(value = ["type", "entityKey"], unique = true)]
)
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,             // e.g. "SONG_PLAYS_100", "ARCHETYPE_NIGHT_OWL"
    val entityKey: String,        // idempotency key, unique per (type, key) pair
    val triggeredAt: Long,        // epoch millis
    val seenAt: Long? = null,     // null = unseen; set when user taps card
    val sharedAt: Long? = null,   // set when user shares
    val title: String,            // short hero label, e.g. "100 plays"
    val description: String,      // full punchy line, e.g. "You've played Blinding Lights 100 times"
    val songId: Long? = null,     // for album art + palette colors (nullable)
    val artistId: Long? = null,   // for artist image (nullable)
    val statLines: List<String> = emptyList(),
    val imageUrl: String? = null,
    val entityName: String? = null
)
