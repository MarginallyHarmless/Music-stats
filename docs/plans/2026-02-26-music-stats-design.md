# Music Stats App — Design Document

## Overview

An Android app that passively tracks what music you listen to across any streaming app and presents detailed stats about your listening patterns. Local-first, no backend required.

**Tech stack:** Kotlin + Jetpack Compose, Room database, NotificationListenerService

**Target:** Play Store publication

---

## Song Detection & Data Capture

A `NotificationListenerService` runs as a foreground service (persistent notification: "Music Stats is tracking"). It listens for `MediaSession` callbacks from any music app and extracts:

- Song title, artist name, album name (if available)
- Source app (package name)
- Timestamp (start time)
- Duration listened (calculated on song change / pause / stop)

Play, pause, and song-change events are tracked to calculate actual listening time accurately.

**Phase 2 — API enrichment:** Optional Spotify/YouTube Music account connection to fetch genre, release year, album art URL, BPM. Core app works without it.

---

## Data Model

### Song (deduplicated registry)
- `id` (auto-generated)
- `title`, `artist`, `album`
- `firstHeardAt` (timestamp)
- `genre`, `releaseYear`, `albumArtUrl` (nullable — Phase 2)

### ListeningEvent (one row per play session)
- `id` (auto-generated)
- `songId` (FK → Song)
- `startedAt` (timestamp)
- `durationMs` (actual time listened)
- `sourceApp` (package name)
- `completed` (boolean — full listen or skip?)

### Artist (deduplicated registry)
- `id`, `name`, `firstHeardAt`

**Deduplication:** Match incoming notifications by `(title, artist)` to find or create a Song. Exact match for v1.

**Key principle:** `ListeningEvent` is the workhorse. Every stat is a query over listening events joined with songs.

---

## Stats

All computed via Room DAO queries with date range filters powering today/week/month/all-time views.

### Time-based
- Total listening time (all time, this week, today)
- Total listening time per song / artist / album
- Average listening session length
- Listening time by hour of day (heatmap)
- Listening time by day of week
- Daily/weekly/monthly streaks
- Longest single listening session

### Discovery & Variety
- New songs/artists discovered per week/month
- Total unique songs / artists / albums
- Replay ratio (re-listens vs new discoveries)
- Artist diversity score (entropy-based or top-N concentration)
- Genre distribution (Phase 2, after API enrichment)
- "Deep cuts" — songs listened to 50+ times
- Skip rate per song

### Top Lists
- Top 10 songs / artists / albums
- Toggleable by play count or total time
- Filterable by time range

---

## UI Structure

Bottom navigation with four screens:

### 1. Home / Dashboard
- Hero card: today's listening time (progress ring)
- "Currently playing" card
- Quick stats row: songs today, top artist today, streak count
- Charts: 7-day listening time (bar chart), top 5 artists this week (horizontal bars)
- "Your top song right now" spotlight card

### 2. Stats
- Tabs: **Time** | **Discovery** | **Top Lists**
- Time: heatmap, total time breakdowns, session stats
- Discovery: new songs/artists graphs, diversity score, replay ratio
- Top Lists: songs/artists/albums with time range + metric toggles
- Tap any item to drill into detail screen

### 3. Library
- Searchable list of all tracked songs and artists
- Sort by: most played, most recent, alphabetical
- Each entry shows play count + total listening time
- Tap for full listening history

### 4. Settings
- Export / import stats (JSON)
- Connected apps management (Phase 2)
- Notification listener permission status
- About / version info

---

## Background Service & Permissions

### Services
- `ForegroundService` with persistent notification keeps the app alive
- `NotificationListenerService` (system-managed) forwards media events to the foreground service

### Permissions
- `NOTIFICATION_LISTENER` — granted via Settings > Notification Access
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS` (Android 13+)

### Battery
- Event-driven (not polling) — low battery impact
- DB writes only on song change / pause / stop
- No background network calls

### Onboarding
1. Welcome screen explaining what the app does
2. Request notification access (opens system settings)
3. Request notification permission (Android 13+)
4. Confirmation: "You're all set, start playing music!"

---

## Export / Import

### Export
- Serializes full database to JSON via `ACTION_CREATE_DOCUMENT`
- Schema:
```json
{
  "version": 1,
  "exportedAt": "2026-02-26T19:30:00Z",
  "songs": [...],
  "listeningEvents": [...],
  "artists": [...]
}
```

### Import
- `ACTION_OPEN_DOCUMENT` to pick a JSON file
- Validates structure and `version` field
- Merge via upsert: songs matched by `(title, artist)`, events matched by `(songId, startedAt)`
- Confirmation dialog before importing
