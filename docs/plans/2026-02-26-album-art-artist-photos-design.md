# Album Art + Artist Photos Design

## Problem

1. The Top Artist card on the Home screen shows a generic icon instead of the artist's actual photo. Existing artists before the Deezer integration never had their photos fetched.
2. Songs have no album art displayed anywhere in the app, despite `Song.albumArtUrl` existing in the schema.

## Solution

### Album Art: 3-Tier Sourcing

Priority order for getting album art per song:

1. **Media session URI** — Extract `METADATA_KEY_ALBUM_ART_URI` from Android's `MediaMetadata`. Most apps (Spotify, YouTube Music, Tidal) provide this. Store the URL string directly in `Song.albumArtUrl`.
2. **Media session bitmap** — If no URI, extract `METADATA_KEY_ART` or `METADATA_KEY_ALBUM_ART` bitmap. Compress to JPEG, save to `app_internal_storage/album_art/<songId>.jpg`, store the `file:///` path in `Song.albumArtUrl`.
3. **Deezer API fallback** — If neither local source available, search `https://api.deezer.com/search/track?q=<artist> <title>&limit=1`. Store `album.cover_medium` URL.

### Artist Photos: Backfill

- Current: `ArtistImageFetcher` fetches from Deezer on `recordPlay()` for new/missing artists.
- Addition: On app startup, background job finds all artists with `imageUrl == null` and fetches from Deezer, throttled to ~2 requests/sec.

### Deezer API Details

- Free, no API key required
- Rate limit: 50 requests per 5 seconds per IP (per-device, not shared across users)
- Stable for 10+ years
- Returns artist photos (`picture_medium`, 250x250) and album art (`album.cover_medium`, 250x250)

### Database Changes

- No schema migration needed — `Song.albumArtUrl` already exists, `Artist.imageUrl` already exists
- New DAO query: `SongDao.updateAlbumArtUrl(songId: Long, url: String)`
- New fetcher method for track/album art lookup

### UI: Where Album Art Appears

1. **Home screen — Top Artist card**: Already implemented with `AsyncImage`, needs backfill to populate data
2. **Home screen — Top Songs This Week**: Circular thumbnail next to each song
3. **Library screen — Song list**: Thumbnail next to each song entry
4. **Song detail screen**: Large album art header

All use Coil 3 `AsyncImage` with `MusicNote` icon fallback.

### File Changes

1. `MediaSessionTracker.kt` — Extract album art URI/bitmap from metadata
2. `MusicRepository.kt` — Accept `albumArtUrl` in `recordPlay()`, trigger Deezer fallback, add backfill method
3. `ArtistImageFetcher.kt` — Add `fetchAlbumArtUrl(title, artist)` method (or rename class)
4. `SongDao.kt` — Add `updateAlbumArtUrl` query
5. `HomeScreen.kt` — Add thumbnails to Top Songs list
6. `HomeViewModel.kt` — Trigger artist backfill on init
7. `LibraryScreen.kt` — Add thumbnails to song list
8. `SongDetailScreen.kt` — Add large album art header
