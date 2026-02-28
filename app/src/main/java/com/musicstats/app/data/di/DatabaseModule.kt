package com.musicstats.app.data.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicStatsDatabase {
        return Room.databaseBuilder(
            context,
            MusicStatsDatabase::class.java,
            "music_stats.db"
        )
            .addMigrations(
                MusicStatsDatabase.MIGRATION_1_2,
                MusicStatsDatabase.MIGRATION_2_3,
                MusicStatsDatabase.MIGRATION_3_4,
                MusicStatsDatabase.MIGRATION_4_5,
                MusicStatsDatabase.MIGRATION_5_6,
                MusicStatsDatabase.MIGRATION_6_7,
                MusicStatsDatabase.MIGRATION_7_8,
                MusicStatsDatabase.MIGRATION_8_9,
                MusicStatsDatabase.MIGRATION_9_10,
                MusicStatsDatabase.MIGRATION_10_11,
            )
            .build()
    }

    @Provides
    fun provideSongDao(database: MusicStatsDatabase): SongDao = database.songDao()

    @Provides
    fun provideArtistDao(database: MusicStatsDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideListeningEventDao(database: MusicStatsDatabase): ListeningEventDao =
        database.listeningEventDao()

    @Provides
    fun provideMomentDao(database: MusicStatsDatabase): MomentDao = database.momentDao()

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        SingletonImageLoader.get(context)
}
