# Music Stats App — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android app that passively tracks music listening across any streaming app and displays detailed stats via a dashboard UI.

**Architecture:** Local-first monolithic Android app. A `NotificationListenerService` captures media events, a foreground service keeps tracking alive, Room database stores all data, Jetpack Compose renders a dashboard UI with stats computed via DAO queries.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt (DI), MediaSession API, Kotlin Coroutines/Flow, Kotlinx Serialization (JSON export/import), Vico (charts)

---

## Task 1: Project Scaffolding

**Files:**
- Create: `app/build.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/musicstats/app/MusicStatsApp.kt`

**Step 1: Create Android project structure**

Initialize an Android project with the following directory layout:
```
music-stats/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/musicstats/app/
│       └── test/
│           └── java/com/musicstats/app/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    └── libs.versions.toml
```

**Step 2: Configure version catalog**

Create `gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
compose-bom = "2025.01.01"
room = "2.6.1"
hilt = "2.53.1"
hilt-navigation-compose = "1.2.0"
kotlinx-serialization = "1.7.3"
coroutines = "1.9.0"
vico = "2.0.2"
navigation-compose = "2.8.6"
ksp = "2.1.0-1.0.29"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

# Kotlinx Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# Charts
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
turbine = { group = "app.cash.turbine", name = "turbine", version = "1.2.0" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

**Step 3: Configure root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
```

**Step 4: Configure app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.musicstats.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.musicstats.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)

    // Charts
    implementation(libs.vico.compose.m3)

    // Navigation
    implementation(libs.navigation.compose)

    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
}
```

**Step 5: Configure settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MusicStats"
include(":app")
```

**Step 6: Create Application class**

Create `app/src/main/java/com/musicstats/app/MusicStatsApp.kt`:
```kotlin
package com.musicstats.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicStatsApp : Application()
```

**Step 7: Create minimal AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".MusicStatsApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Music Stats"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DynamicColors.DayNight">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Material3.DynamicColors.DayNight">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

**Step 8: Create stub MainActivity**

Create `app/src/main/java/com/musicstats/app/ui/MainActivity.kt`:
```kotlin
package com.musicstats.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("Music Stats")
        }
    }
}
```

**Step 9: Add Gradle wrapper and verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git init
git add .
git commit -m "feat: scaffold Android project with Compose, Room, Hilt dependencies"
```

---

## Task 2: Room Database — Entities & DAOs

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/model/Song.kt`
- Create: `app/src/main/java/com/musicstats/app/data/model/Artist.kt`
- Create: `app/src/main/java/com/musicstats/app/data/model/ListeningEvent.kt`
- Create: `app/src/main/java/com/musicstats/app/data/dao/SongDao.kt`
- Create: `app/src/main/java/com/musicstats/app/data/dao/ArtistDao.kt`
- Create: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`
- Create: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt`
- Create: `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/musicstats/app/data/dao/SongDaoTest.kt`
- Test: `app/src/test/java/com/musicstats/app/data/dao/ListeningEventDaoTest.kt`

**Step 1: Create Song entity**

```kotlin
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
    val firstHeardAt: Long, // epoch millis
    val genre: String? = null,
    val releaseYear: Int? = null,
    val albumArtUrl: String? = null
)
```

**Step 2: Create Artist entity**

```kotlin
package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = true)]
)
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val firstHeardAt: Long // epoch millis
)
```

**Step 3: Create ListeningEvent entity**

```kotlin
package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_events",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId"), Index("startedAt")]
)
data class ListeningEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startedAt: Long, // epoch millis
    val durationMs: Long,
    val sourceApp: String,
    val completed: Boolean
)
```

**Step 4: Create SongDao**

```kotlin
package com.musicstats.app.data.dao

import androidx.room.*
import com.musicstats.app.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song): Long

    @Update
    suspend fun update(song: Song)

    @Query("SELECT * FROM songs WHERE title = :title AND artist = :artist LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, artist: String): Song?

    @Query("SELECT * FROM songs ORDER BY firstHeardAt DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalSongCount(): Flow<Int>

    @Query("""
        SELECT * FROM songs WHERE id IN (
            SELECT songId FROM listening_events
            GROUP BY songId
            ORDER BY SUM(durationMs) DESC
            LIMIT :limit
        )
    """)
    fun getTopSongsByTime(limit: Int = 10): Flow<List<Song>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsSnapshot(): List<Song>
}
```

**Step 5: Create ArtistDao**

```kotlin
package com.musicstats.app.data.dao

import androidx.room.*
import com.musicstats.app.data.model.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: Artist): Long

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Artist?

    @Query("SELECT * FROM artists ORDER BY firstHeardAt DESC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT COUNT(*) FROM artists")
    fun getTotalArtistCount(): Flow<Int>

    @Query("SELECT * FROM artists")
    suspend fun getAllArtistsSnapshot(): List<Artist>
}
```

**Step 6: Create ListeningEventDao**

```kotlin
package com.musicstats.app.data.dao

import androidx.room.*
import com.musicstats.app.data.model.ListeningEvent
import kotlinx.coroutines.flow.Flow

data class SongPlayStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int
)

data class ArtistPlayStats(
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int
)

data class HourlyListening(
    val hour: Int,
    val totalDurationMs: Long
)

data class DailyListening(
    val date: String, // yyyy-MM-dd
    val totalDurationMs: Long
)

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insert(event: ListeningEvent): Long

    @Query("SELECT * FROM listening_events WHERE songId = :songId AND startedAt = :startedAt LIMIT 1")
    suspend fun findBySongAndTime(songId: Long, startedAt: Long): ListeningEvent?

    // Total listening time
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events")
    fun getTotalListeningTimeMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE startedAt >= :since")
    fun getListeningTimeSince(since: Long): Flow<Long>

    // Per-song stats
    @Query("""
        SELECT s.id as songId, s.title, s.artist,
               COALESCE(SUM(e.durationMs), 0) as totalDurationMs,
               COUNT(e.id) as playCount
        FROM songs s
        LEFT JOIN listening_events e ON s.id = e.songId
        WHERE e.startedAt >= :since OR :since = 0
        GROUP BY s.id
        ORDER BY totalDurationMs DESC
        LIMIT :limit
    """)
    fun getTopSongsByDuration(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>

    @Query("""
        SELECT s.id as songId, s.title, s.artist,
               COALESCE(SUM(e.durationMs), 0) as totalDurationMs,
               COUNT(e.id) as playCount
        FROM songs s
        LEFT JOIN listening_events e ON s.id = e.songId
        WHERE e.startedAt >= :since OR :since = 0
        GROUP BY s.id
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    fun getTopSongsByPlayCount(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>

    // Per-artist stats
    @Query("""
        SELECT s.artist,
               COALESCE(SUM(e.durationMs), 0) as totalDurationMs,
               COUNT(e.id) as playCount
        FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE e.startedAt >= :since OR :since = 0
        GROUP BY s.artist
        ORDER BY totalDurationMs DESC
        LIMIT :limit
    """)
    fun getTopArtistsByDuration(since: Long = 0, limit: Int = 10): Flow<List<ArtistPlayStats>>

    // Hourly heatmap
    @Query("""
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour,
               SUM(durationMs) as totalDurationMs
        FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        GROUP BY hour
        ORDER BY hour
    """)
    fun getHourlyListening(since: Long = 0): Flow<List<HourlyListening>>

    // Daily listening (for bar charts and streaks)
    @Query("""
        SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') as date,
               SUM(durationMs) as totalDurationMs
        FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        GROUP BY date
        ORDER BY date DESC
    """)
    fun getDailyListening(since: Long = 0): Flow<List<DailyListening>>

    // Song count since
    @Query("SELECT COUNT(DISTINCT songId) FROM listening_events WHERE startedAt >= :since")
    fun getSongCountSince(since: Long): Flow<Int>

    // New discoveries: songs whose firstHeardAt falls in range
    @Query("""
        SELECT COUNT(*) FROM songs
        WHERE firstHeardAt >= :since
    """)
    fun getNewSongsDiscoveredSince(since: Long): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM artists
        WHERE firstHeardAt >= :since
    """)
    fun getNewArtistsDiscoveredSince(since: Long): Flow<Int>

    // Average session duration
    @Query("SELECT COALESCE(AVG(durationMs), 0) FROM listening_events")
    fun getAverageSessionDuration(): Flow<Long>

    // Longest session
    @Query("SELECT COALESCE(MAX(durationMs), 0) FROM listening_events")
    fun getLongestSession(): Flow<Long>

    // Skip rate for a song
    @Query("""
        SELECT CAST(SUM(CASE WHEN completed = 0 THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*)
        FROM listening_events WHERE songId = :songId
    """)
    suspend fun getSkipRate(songId: Long): Float

    // Total play count and time for a specific song
    @Query("""
        SELECT COALESCE(SUM(durationMs), 0) as totalDurationMs, COUNT(*) as playCount
        FROM listening_events WHERE songId = :songId
    """)
    fun getSongStats(songId: Long): Flow<SongPlayStats>

    // Events for a song (listening history)
    @Query("SELECT * FROM listening_events WHERE songId = :songId ORDER BY startedAt DESC")
    fun getEventsForSong(songId: Long): Flow<List<ListeningEvent>>

    // All events (for export)
    @Query("SELECT * FROM listening_events")
    suspend fun getAllEventsSnapshot(): List<ListeningEvent>

    // Unique artist count
    @Query("""
        SELECT COUNT(DISTINCT s.artist)
        FROM listening_events e JOIN songs s ON e.songId = s.id
    """)
    fun getUniqueArtistCount(): Flow<Int>

    // Play count for "deep cuts" (songs with 50+ plays)
    @Query("""
        SELECT s.id as songId, s.title, s.artist,
               COALESCE(SUM(e.durationMs), 0) as totalDurationMs,
               COUNT(e.id) as playCount
        FROM songs s
        JOIN listening_events e ON s.id = e.songId
        GROUP BY s.id
        HAVING playCount >= :threshold
        ORDER BY playCount DESC
    """)
    fun getDeepCuts(threshold: Int = 50): Flow<List<SongPlayStats>>
}
```

**Step 7: Create Database class**

```kotlin
package com.musicstats.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song

@Database(
    entities = [Song::class, Artist::class, ListeningEvent::class],
    version = 1,
    exportSchema = true
)
abstract class MusicStatsDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun listeningEventDao(): ListeningEventDao
}
```

**Step 8: Create Hilt DatabaseModule**

```kotlin
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
        ).build()
    }

    @Provides
    fun provideSongDao(db: MusicStatsDatabase): SongDao = db.songDao()

    @Provides
    fun provideArtistDao(db: MusicStatsDatabase): ArtistDao = db.artistDao()

    @Provides
    fun provideListeningEventDao(db: MusicStatsDatabase): ListeningEventDao = db.listeningEventDao()
}
```

**Step 9: Write DAO tests**

Use `robolectric` + Room in-memory database for unit tests. Create `app/src/test/java/com/musicstats/app/data/dao/SongDaoTest.kt` and `ListeningEventDaoTest.kt` testing:
- Insert and retrieve a song
- Deduplication (insert same title+artist twice, second returns -1)
- Insert listening events and verify top songs query
- Daily listening aggregation
- Hourly listening aggregation

**Step 10: Run tests, verify pass**

Run: `./gradlew test`
Expected: All DAO tests pass.

**Step 11: Commit**

```bash
git add .
git commit -m "feat: add Room database with Song, Artist, ListeningEvent entities and DAOs"
```

---

## Task 3: Repository Layer

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt`
- Test: `app/src/test/java/com/musicstats/app/data/repository/MusicRepositoryTest.kt`

**Step 1: Create MusicRepository**

```kotlin
package com.musicstats.app.data.repository

import com.musicstats.app.data.dao.*
import com.musicstats.app.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val eventDao: ListeningEventDao
) {
    /**
     * Record a song play. Handles deduplication of songs and artists.
     * Returns the created ListeningEvent.
     */
    suspend fun recordPlay(
        title: String,
        artist: String,
        album: String?,
        sourceApp: String,
        startedAt: Long,
        durationMs: Long,
        completed: Boolean
    ): ListeningEvent {
        // Upsert artist
        val existingArtist = artistDao.findByName(artist)
        if (existingArtist == null) {
            artistDao.insert(Artist(name = artist, firstHeardAt = startedAt))
        }

        // Upsert song
        val existingSong = songDao.findByTitleAndArtist(title, artist)
        val songId = if (existingSong != null) {
            existingSong.id
        } else {
            songDao.insert(
                Song(
                    title = title,
                    artist = artist,
                    album = album,
                    firstHeardAt = startedAt
                )
            )
        }

        // Insert listening event
        val event = ListeningEvent(
            songId = songId,
            startedAt = startedAt,
            durationMs = durationMs,
            sourceApp = sourceApp,
            completed = completed
        )
        val eventId = eventDao.insert(event)
        return event.copy(id = eventId)
    }

    // Delegate stats queries to DAOs
    fun getTotalListeningTime(): Flow<Long> = eventDao.getTotalListeningTimeMs()
    fun getListeningTimeSince(since: Long): Flow<Long> = eventDao.getListeningTimeSince(since)
    fun getTopSongsByDuration(since: Long = 0, limit: Int = 10) = eventDao.getTopSongsByDuration(since, limit)
    fun getTopSongsByPlayCount(since: Long = 0, limit: Int = 10) = eventDao.getTopSongsByPlayCount(since, limit)
    fun getTopArtistsByDuration(since: Long = 0, limit: Int = 10) = eventDao.getTopArtistsByDuration(since, limit)
    fun getHourlyListening(since: Long = 0) = eventDao.getHourlyListening(since)
    fun getDailyListening(since: Long = 0) = eventDao.getDailyListening(since)
    fun getNewSongsDiscoveredSince(since: Long) = eventDao.getNewSongsDiscoveredSince(since)
    fun getNewArtistsDiscoveredSince(since: Long) = eventDao.getNewArtistsDiscoveredSince(since)
    fun getAverageSessionDuration() = eventDao.getAverageSessionDuration()
    fun getLongestSession() = eventDao.getLongestSession()
    fun getDeepCuts(threshold: Int = 50) = eventDao.getDeepCuts(threshold)
    fun getAllSongs() = songDao.getAllSongs()
    fun getTotalSongCount() = songDao.getTotalSongCount()
    fun getUniqueArtistCount() = eventDao.getUniqueArtistCount()
    fun getEventsForSong(songId: Long) = eventDao.getEventsForSong(songId)
    suspend fun getSkipRate(songId: Long) = eventDao.getSkipRate(songId)
}
```

**Step 2: Write tests for MusicRepository**

Test `recordPlay`:
- First play creates song + artist + event
- Second play of same song reuses existing song ID
- Different song by same artist reuses existing artist

**Step 3: Run tests, verify pass**

Run: `./gradlew test`

**Step 4: Commit**

```bash
git add .
git commit -m "feat: add MusicRepository with play recording and stats delegation"
```

---

## Task 4: Notification Listener Service

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/MusicNotificationListener.kt`
- Create: `app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add service declaration)

**Step 1: Create MediaSessionTracker**

This class handles the core logic of tracking media state changes and deciding when to write a listening event. Extracted from the service for testability.

```kotlin
package com.musicstats.app.service

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.musicstats.app.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class MediaSessionTracker @Inject constructor(
    private val repository: MusicRepository
) {
    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentAlbum: String? = null
    private var currentSourceApp: String? = null
    private var playStartTime: Long? = null
    private var isPlaying: Boolean = false

    fun onMetadataChanged(
        metadata: MediaMetadata?,
        sourceApp: String,
        scope: CoroutineScope
    ) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        if (title == null || artist == null) return

        // Song changed — save previous if playing
        if (title != currentTitle || artist != currentArtist) {
            saveCurrentIfPlaying(scope)
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentSourceApp = sourceApp
            if (isPlaying) {
                playStartTime = System.currentTimeMillis()
            }
        }
    }

    fun onPlaybackStateChanged(
        state: PlaybackState?,
        sourceApp: String,
        scope: CoroutineScope
    ) {
        val wasPlaying = isPlaying
        isPlaying = state?.state == PlaybackState.STATE_PLAYING

        currentSourceApp = sourceApp

        if (isPlaying && !wasPlaying) {
            // Resumed or started
            playStartTime = System.currentTimeMillis()
        } else if (!isPlaying && wasPlaying) {
            // Paused or stopped
            saveCurrentIfPlaying(scope)
        }
    }

    private fun saveCurrentIfPlaying(scope: CoroutineScope) {
        val title = currentTitle ?: return
        val artist = currentArtist ?: return
        val startTime = playStartTime ?: return
        val duration = System.currentTimeMillis() - startTime

        if (duration < 5_000) return // ignore <5 second plays

        scope.launch {
            repository.recordPlay(
                title = title,
                artist = artist,
                album = currentAlbum,
                sourceApp = currentSourceApp ?: "unknown",
                startedAt = startTime,
                durationMs = duration,
                completed = duration > 30_000 // rough heuristic: >30s = completed
            )
        }

        playStartTime = null
    }
}
```

**Step 2: Create MusicNotificationListener**

```kotlin
package com.musicstats.app.service

import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class MusicNotificationListener : NotificationListenerService() {

    @Inject lateinit var tracker: MediaSessionTracker

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeCallbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.addOnActiveSessionsChangedListener(
            { controllers -> onSessionsChanged(controllers) },
            ComponentName(this, MusicNotificationListener::class.java)
        )
        // Process already-active sessions
        val controllers = manager.getActiveSessions(
            ComponentName(this, MusicNotificationListener::class.java)
        )
        onSessionsChanged(controllers)
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        controllers?.forEach { controller ->
            if (activeCallbacks.containsKey(controller.sessionToken)) return@forEach

            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    tracker.onMetadataChanged(
                        metadata,
                        controller.packageName,
                        scope
                    )
                }

                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    tracker.onPlaybackStateChanged(
                        state,
                        controller.packageName,
                        scope
                    )
                }
            }

            controller.registerCallback(callback)
            activeCallbacks[controller.sessionToken] = callback

            // Process current state
            controller.metadata?.let {
                tracker.onMetadataChanged(it, controller.packageName, scope)
            }
            controller.playbackState?.let {
                tracker.onPlaybackStateChanged(it, controller.packageName, scope)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        activeCallbacks.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
```

**Step 3: Update AndroidManifest.xml**

Add inside `<application>`:
```xml
<service
    android:name=".service.MusicNotificationListener"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

**Step 4: Write unit tests for MediaSessionTracker**

Test with a mock MusicRepository:
- Metadata change with no previous song does not save
- Play then pause saves event with correct duration
- Song change while playing saves previous song
- Plays under 5 seconds are ignored

**Step 5: Run tests, verify pass**

Run: `./gradlew test`

**Step 6: Commit**

```bash
git add .
git commit -m "feat: add NotificationListenerService and MediaSessionTracker for music detection"
```

---

## Task 5: Foreground Service & Tracking Notification

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/TrackingService.kt`
- Create: `app/src/main/java/com/musicstats/app/service/TrackingNotification.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Create TrackingNotification helper**

```kotlin
package com.musicstats.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.musicstats.app.ui.MainActivity

object TrackingNotification {
    const val CHANNEL_ID = "music_stats_tracking"
    const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Music Stats is tracking your listening"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Music Stats")
            .setContentText("Tracking your listening")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
```

**Step 2: Create TrackingService**

```kotlin
package com.musicstats.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrackingService : Service() {

    override fun onCreate() {
        super.onCreate()
        TrackingNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                TrackingNotification.NOTIFICATION_ID,
                TrackingNotification.build(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                TrackingNotification.NOTIFICATION_ID,
                TrackingNotification.build(this)
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

**Step 3: Update AndroidManifest.xml**

Add inside `<application>`:
```xml
<service
    android:name=".service.TrackingService"
    android:foregroundServiceType="specialUse"
    android:exported="false" />
```

**Step 4: Commit**

```bash
git add .
git commit -m "feat: add foreground tracking service with persistent notification"
```

---

## Task 6: Export / Import

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/export/ExportImportManager.kt`
- Create: `app/src/main/java/com/musicstats/app/data/export/ExportModels.kt`
- Test: `app/src/test/java/com/musicstats/app/data/export/ExportImportManagerTest.kt`

**Step 1: Create serializable export models**

```kotlin
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
```

**Step 2: Create ExportImportManager**

```kotlin
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

        // Import artists
        for (artist in data.artists) {
            val existing = artistDao.findByName(artist.name)
            if (existing == null) {
                artistDao.insert(Artist(name = artist.name, firstHeardAt = artist.firstHeardAt))
            }
        }

        // Import songs
        for (song in data.songs) {
            val existing = songDao.findByTitleAndArtist(song.title, song.artist)
            if (existing == null) {
                songDao.insert(Song(
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    firstHeardAt = song.firstHeardAt,
                    genre = song.genre,
                    releaseYear = song.releaseYear
                ))
                songsImported++
            }
        }

        // Import events
        for (event in data.listeningEvents) {
            val song = songDao.findByTitleAndArtist(event.songTitle, event.songArtist) ?: continue
            val existing = eventDao.findBySongAndTime(song.id, event.startedAt)
            if (existing == null) {
                eventDao.insert(ListeningEvent(
                    songId = song.id,
                    startedAt = event.startedAt,
                    durationMs = event.durationMs,
                    sourceApp = event.sourceApp,
                    completed = event.completed
                ))
                eventsImported++
            }
        }

        return ImportResult(songsImported, eventsImported)
    }
}

data class ImportResult(val songsImported: Int, val eventsImported: Int)
```

**Step 3: Write tests**

Test export produces valid JSON, import restores data, duplicate events are skipped.

**Step 4: Run tests, verify pass**

**Step 5: Commit**

```bash
git add .
git commit -m "feat: add JSON export/import with deduplication"
```

---

## Task 7: Onboarding UI

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/onboarding/OnboardingViewModel.kt`

**Step 1: Create OnboardingViewModel**

```kotlin
package com.musicstats.app.ui.onboarding

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    fun nextStep() {
        _currentStep.value++
    }

    fun isNotificationListenerEnabled(): Boolean {
        val context = getApplication<Application>()
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(context, "com.musicstats.app.service.MusicNotificationListener")
        return flat?.contains(componentName.flattenToString()) == true
    }

    fun markOnboardingComplete() {
        val prefs = getApplication<Application>().getSharedPreferences("music_stats", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    companion object {
        fun isOnboardingComplete(context: Context): Boolean {
            return context.getSharedPreferences("music_stats", Context.MODE_PRIVATE)
                .getBoolean("onboarding_complete", false)
        }
    }
}
```

**Step 2: Create OnboardingScreen**

Build a 3-step pager:
1. Welcome — app description with icon
2. Notification access — button that opens system settings, shows status
3. Done — confirmation with "Start tracking" button

Use `HorizontalPager` from Compose Foundation. Each page is a full-screen card with centered content. The "Next" button advances, the notification step shows a "Grant Access" button that launches `ACTION_NOTIFICATION_LISTENER_SETTINGS`, and polls `isNotificationListenerEnabled()` on resume.

**Step 3: Commit**

```bash
git add .
git commit -m "feat: add onboarding flow with notification permission request"
```

---

## Task 8: Navigation & App Shell

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/navigation/NavGraph.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/navigation/BottomNavBar.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/MainActivity.kt`

**Step 1: Create Theme**

Material 3 dynamic colors theme wrapping the app. Dark/light mode support.

**Step 2: Create BottomNavBar**

Four items: Home, Stats, Library, Settings. Use Material 3 `NavigationBar` with `NavigationBarItem`. Icons: `Home`, `BarChart`, `LibraryMusic`, `Settings` from Material Icons Extended.

**Step 3: Create NavGraph**

Define routes: `home`, `stats`, `library`, `settings`, `onboarding`, `song/{songId}` (detail). Use `NavHost` with `composable` blocks.

**Step 4: Update MainActivity**

Check if onboarding is complete. If not, show onboarding. Otherwise show the main app shell with bottom nav + NavHost. Start `TrackingService` on launch.

**Step 5: Commit**

```bash
git add .
git commit -m "feat: add navigation shell with bottom bar and routing"
```

---

## Task 9: Home / Dashboard Screen

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/components/StatCard.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/components/ListeningTimeChart.kt`

**Step 1: Create HomeViewModel**

Exposes as StateFlows:
- `todayListeningTimeMs` — from `getListeningTimeSince(startOfToday)`
- `songsToday` — from `getSongCountSince(startOfToday)`
- `topArtistToday` — from `getTopArtistsByDuration(startOfToday, 1)`
- `weeklyDailyListening` — from `getDailyListening(7 days ago)` for bar chart
- `topSongsThisWeek` — from `getTopSongsByDuration(startOfWeek, 5)`
- `currentStreak` — computed from `getDailyListening(0)` by counting consecutive days

Use helper function to get epoch millis for start of today and start of week.

**Step 2: Create reusable StatCard composable**

A Material 3 `ElevatedCard` with a label, a large value, and an optional icon. Used throughout the dashboard.

**Step 3: Create ListeningTimeChart**

Bar chart of the last 7 days using Vico. X-axis = day names (Mon, Tue...), Y-axis = hours listened.

**Step 4: Build HomeScreen**

Scrollable column:
- Hero card with today's listening time (formatted as Xh Ym)
- Row of 3 `StatCard`s: songs today, top artist, streak
- `ListeningTimeChart` for the week
- "Top songs this week" list (top 5, each showing title, artist, play count)

**Step 5: Commit**

```bash
git add .
git commit -m "feat: add home dashboard with stats cards and weekly chart"
```

---

## Task 10: Stats Screen

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/stats/StatsScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/stats/StatsViewModel.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/stats/TimeStatsTab.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/stats/DiscoveryStatsTab.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/stats/TopListsTab.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/components/HourlyHeatmap.kt`

**Step 1: Create StatsViewModel**

Holds a `selectedTimeRange` state (Today / Week / Month / All Time) that drives all queries. Exposes:
- Time tab: total time, avg session, longest session, hourly data, daily data
- Discovery tab: new songs/artists counts, total unique counts, deep cuts, replay ratio (computed as `totalPlays - uniqueSongs / totalPlays`)
- Top lists: top songs and artists by duration or play count

**Step 2: Create TimeStatsTab**

- Total listening time (big number)
- Average session duration, longest session
- `HourlyHeatmap` — a grid of 24 cells colored by intensity (hour of day)
- Daily listening bar chart

**Step 3: Create DiscoveryStatsTab**

- New songs discovered (big number)
- New artists discovered
- Total unique songs / artists
- Replay ratio (percentage)
- Deep cuts list (songs with 50+ plays)
- Artist diversity: top 5 artists' share of total listening (horizontal stacked bar or pie)

**Step 4: Create TopListsTab**

- Toggle: by Duration / by Play Count
- Time range filter chips
- Numbered list of top songs with rank, title, artist, metric value
- Top artists list below

**Step 5: Assemble StatsScreen**

`TabRow` with 3 tabs, each rendering its tab composable. Time range filter as `FilterChip` row above the tab content.

**Step 6: Commit**

```bash
git add .
git commit -m "feat: add stats screen with time, discovery, and top lists tabs"
```

---

## Task 11: Library Screen

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/library/LibraryScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/library/SongDetailViewModel.kt`

**Step 1: Create LibraryViewModel**

Exposes:
- `songs` — all songs as Flow, joined with play count and total time
- `searchQuery` — filters the list
- `sortMode` — enum: MostPlayed, MostRecent, Alphabetical

**Step 2: Build LibraryScreen**

- Search bar at top (`TextField` with search icon)
- Sort chips: Most Played, Recent, A-Z
- `LazyColumn` of song items, each showing: title, artist, play count, total time
- Tap navigates to `song/{id}` route

**Step 3: Create SongDetailViewModel**

For a given `songId`, exposes:
- Song info (title, artist, album)
- Total play count, total listening time
- Skip rate
- Listening history (list of events with dates)

**Step 4: Build SongDetailScreen**

- Song title + artist as header
- Stats row: total plays, total time, skip rate
- "Listening history" — `LazyColumn` of events with date, duration, source app

**Step 5: Commit**

```bash
git add .
git commit -m "feat: add library screen with search, sort, and song detail view"
```

---

## Task 12: Settings Screen

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/settings/SettingsViewModel.kt`

**Step 1: Create SettingsViewModel**

Handles export/import logic:
- `exportData()` — calls `ExportImportManager.exportToJson()`, returns JSON string
- `importData(jsonString)` — calls `importFromJson()`, returns `ImportResult`
- `isNotificationListenerEnabled()` — checks permission status

**Step 2: Build SettingsScreen**

- Section: "Data"
  - "Export Stats" row — launches `ACTION_CREATE_DOCUMENT` with `application/json` MIME type, writes JSON to selected URI
  - "Import Stats" row — launches `ACTION_OPEN_DOCUMENT`, reads JSON, shows confirmation dialog ("Import X songs, Y events?"), then imports
- Section: "Permissions"
  - Notification listener status (green check or red X) with button to open settings
- Section: "About"
  - App version, link to project

Use `rememberLauncherForActivityResult` for the document picker intents.

**Step 3: Commit**

```bash
git add .
git commit -m "feat: add settings screen with export/import and permission status"
```

---

## Task 13: Polish & Final Integration

**Files:**
- Modify: various UI files
- Create: `app/src/main/java/com/musicstats/app/util/TimeFormatters.kt`

**Step 1: Create time formatting utilities**

```kotlin
package com.musicstats.app.util

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

fun formatDurationLong(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
```

**Step 2: Add app icon**

Create a simple adaptive icon using Android Studio's Image Asset tool or a vector drawable placeholder.

**Step 3: Verify end-to-end flow**

1. Install on device/emulator
2. Grant notification listener permission
3. Play a song on any music app
4. Verify it appears in the library and dashboard updates
5. Export JSON, uninstall, reinstall, import — verify data restored

**Step 4: Commit**

```bash
git add .
git commit -m "feat: add time formatters, app icon, and polish"
```

---

## Task Summary

| Task | Description | Dependencies |
|------|-------------|--------------|
| 1 | Project scaffolding | None |
| 2 | Room database entities & DAOs | 1 |
| 3 | Repository layer | 2 |
| 4 | Notification listener service | 3 |
| 5 | Foreground service | 4 |
| 6 | Export / Import | 2 |
| 7 | Onboarding UI | 1 |
| 8 | Navigation & app shell | 7 |
| 9 | Home dashboard screen | 3, 8 |
| 10 | Stats screen | 3, 8 |
| 11 | Library screen | 3, 8 |
| 12 | Settings screen | 6, 8 |
| 13 | Polish & integration | 9, 10, 11, 12 |

Tasks 6 and 7 can be worked on in parallel with tasks 4-5. Tasks 9-12 can be worked on in parallel once task 8 is done.
