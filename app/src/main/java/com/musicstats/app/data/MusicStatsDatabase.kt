package com.musicstats.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song

@Database(
    entities = [Song::class, Artist::class, ListeningEvent::class],
    version = 5,
    exportSchema = false
)
abstract class MusicStatsDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun listeningEventDao(): ListeningEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artists ADD COLUMN imageUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN albumArtUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(songs)")
                var hasColumn = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "albumArtUrl") {
                        hasColumn = true
                        break
                    }
                }
                cursor.close()
                if (!hasColumn) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN albumArtUrl TEXT DEFAULT NULL")
                }
            }
        }

        // Cap inflated durations from missed track transitions
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE listening_events SET durationMs = 600000 WHERE durationMs > 600000")
                db.execSQL("UPDATE listening_events SET completed = 0 WHERE durationMs < 30000 AND completed = 1")
            }
        }

        // Remove YouTube video events and deduplicate
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Delete events from regular YouTube (not YouTube Music)
                db.execSQL("DELETE FROM listening_events WHERE sourceApp = 'com.google.android.youtube'")
                // Delete orphaned songs (no remaining events)
                db.execSQL("DELETE FROM songs WHERE id NOT IN (SELECT DISTINCT songId FROM listening_events)")
                // Delete orphaned artists (no remaining songs)
                db.execSQL("DELETE FROM artists WHERE name NOT IN (SELECT DISTINCT artist FROM songs)")
                // Remove duplicate events: keep the one with the longest duration per (songId, rounded startedAt)
                db.execSQL("""
                    DELETE FROM listening_events WHERE id NOT IN (
                        SELECT id FROM (
                            SELECT id, ROW_NUMBER() OVER (
                                PARTITION BY songId, startedAt / 10000
                                ORDER BY durationMs DESC
                            ) as rn
                            FROM listening_events
                        ) WHERE rn = 1
                    )
                """)
            }
        }
    }
}
