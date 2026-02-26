# Music Stats — Android App

## Project Overview
Android app that passively tracks music listening across any streaming app (Spotify, YouTube Music, Tidal, Apple Music, etc.) via Android's NotificationListenerService. Displays detailed stats about listening patterns. Local-first, no backend.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 (dynamic colors)
- **Database:** Room (SQLite)
- **DI:** Hilt
- **Serialization:** Kotlinx Serialization (JSON export/import)
- **Charts:** Vico 2.x (compose-m3)
- **Navigation:** Jetpack Navigation Compose
- **Async:** Kotlin Coroutines + Flow
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

## Package Structure
```
com.musicstats.app/
├── data/
│   ├── model/          # Room entities: Song, Artist, ListeningEvent
│   ├── dao/            # Room DAOs + query result data classes
│   ├── di/             # Hilt DatabaseModule
│   ├── export/         # ExportImportManager, ExportModels
│   ├── repository/     # MusicRepository
│   └── MusicStatsDatabase.kt
├── service/
│   ├── MusicNotificationListener.kt  # NotificationListenerService
│   ├── MediaSessionTracker.kt        # Media state tracking logic
│   ├── TrackingService.kt            # Foreground service
│   └── TrackingNotification.kt       # Notification builder
├── ui/
│   ├── MainActivity.kt
│   ├── navigation/     # NavGraph, BottomNavBar
│   ├── theme/          # Material 3 theme
│   ├── onboarding/     # Onboarding flow
│   ├── home/           # Dashboard screen
│   ├── stats/          # Stats screen (Time, Discovery, Top Lists tabs)
│   ├── library/        # Library + Song detail screens
│   ├── settings/       # Settings screen (export/import)
│   └── components/     # Reusable composables (StatCard, charts)
├── util/               # Time formatters, helpers
└── MusicStatsApp.kt    # @HiltAndroidApp Application class
```

## Key Architecture Decisions
- **NotificationListenerService** reads media sessions from any app — no API keys needed
- **MediaSessionTracker** is extracted from the service for testability
- **ListeningEvent** is the core table — every stat is a query over events joined with songs
- Songs deduplicated by exact `(title, artist)` match
- Plays under 5 seconds are ignored; >30 seconds counts as "completed"
- Export/import uses JSON with a `version` field for future schema migrations
- Import uses upsert strategy to avoid duplicates

## Conventions
- Use `Flow<T>` for reactive data from DAOs, collect in ViewModels as `StateFlow`
- ViewModels use `@HiltViewModel` with `@Inject constructor`
- Time stored as epoch millis (`Long`), formatted only at the UI layer
- Composables: one file per screen, reusable components in `ui/components/`
- Tests use Room in-memory database + Turbine for Flow testing

## Build & Run
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew installDebug           # Install on connected device
```

## Implementation Plan
See `docs/plans/2026-02-26-music-stats-implementation.md` for the full task breakdown.
