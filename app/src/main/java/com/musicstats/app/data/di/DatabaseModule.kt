package com.musicstats.app.data.di

import android.content.Context
import androidx.room.Room
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
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
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideSongDao(database: MusicStatsDatabase): SongDao = database.songDao()

    @Provides
    fun provideArtistDao(database: MusicStatsDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideListeningEventDao(database: MusicStatsDatabase): ListeningEventDao =
        database.listeningEventDao()
}
