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
    version = 3,
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
    }
}
