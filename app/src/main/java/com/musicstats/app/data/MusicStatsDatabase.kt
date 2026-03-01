package com.musicstats.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.musicstats.app.data.Converters
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.model.Song

@TypeConverters(Converters::class)
@Database(
    entities = [Song::class, Artist::class, ListeningEvent::class, Moment::class],
    version = 15,
    exportSchema = false
)
abstract class MusicStatsDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun momentDao(): MomentDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN paletteDominant INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN paletteVibrant INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN paletteMuted INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN paletteDarkVibrant INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN paletteDarkMuted INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN paletteLightVibrant INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS moments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        entityKey TEXT NOT NULL,
                        triggeredAt INTEGER NOT NULL,
                        seenAt INTEGER,
                        sharedAt INTEGER,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        songId INTEGER,
                        artistId INTEGER
                    )
                """)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_moments_type_entityKey` ON `moments` (`type`, `entityKey`)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moments ADD COLUMN statLine TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clear existing moments so they are re-detected with statLine populated
                db.execSQL("DELETE FROM moments")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moments ADD COLUMN imageUrl TEXT DEFAULT NULL")
                // Clear so moments are re-detected with imageUrl populated
                db.execSQL("DELETE FROM moments")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moments ADD COLUMN entityName TEXT DEFAULT NULL")
                db.execSQL("DELETE FROM moments")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate moments table without the old statLine column and with the new statLines column.
                // ALTER TABLE ... DROP COLUMN isn't supported on Android < 12, so we use the copy approach.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS moments_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        entityKey TEXT NOT NULL,
                        triggeredAt INTEGER NOT NULL,
                        seenAt INTEGER,
                        sharedAt INTEGER,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        songId INTEGER,
                        artistId INTEGER,
                        statLines TEXT NOT NULL DEFAULT '[]',
                        imageUrl TEXT,
                        entityName TEXT
                    )
                """)
                db.execSQL("DROP TABLE moments")
                db.execSQL("ALTER TABLE moments_new RENAME TO moments")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_moments_type_entityKey` ON `moments` (`type`, `entityKey`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clear content:// URIs — they belong to other apps and aren't accessible.
                // Songs will get proper artwork on their next play via bitmap save.
                db.execSQL("UPDATE songs SET albumArtUrl = NULL WHERE albumArtUrl LIKE 'content://%'")
                // Re-detect moments so they pick up corrected imageUrls
                db.execSQL("DELETE FROM moments")
            }
        }

        // Remove Audible audiobook entries
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM listening_events WHERE sourceApp = 'com.audible.application'")
                db.execSQL("DELETE FROM songs WHERE id NOT IN (SELECT DISTINCT songId FROM listening_events)")
                db.execSQL("DELETE FROM artists WHERE name NOT IN (SELECT DISTINCT artist FROM songs)")
                db.execSQL("DELETE FROM moments")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moments ADD COLUMN tier TEXT NOT NULL DEFAULT 'BRONZE'")
                db.execSQL("ALTER TABLE moments ADD COLUMN isPersonalBest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE moments ADD COLUMN copyVariant INTEGER NOT NULL DEFAULT 0")
                // Wipe moments so they re-detect with new copy, tiers, and personal bests
                db.execSQL("DELETE FROM moments")
            }
        }
    }
}
