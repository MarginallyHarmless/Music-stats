# High-Res Artwork via Deezer XL

**Date:** 2026-03-01
**Status:** Approved

## Problem

Share cards display artwork at full width (1080px+ on modern phones), but the app fetches medium-resolution images from Deezer (~250x250px). The result looks blurry and low-quality when shared on social media, across all card types (moment cards, song/artist spotlight cards).

## Solution

Upgrade Deezer API image fields from `medium` (250x250) to `xl` (1000x1000). Backfill existing library entries with high-res URLs. Re-extract palette colors from the higher-res images.

## Changes

### 1. ArtistImageFetcher

Switch the Deezer response models to parse XL fields instead of medium:

- `DeezerArtist`: `picture_medium` -> `picture_xl`
- `DeezerAlbum`: `cover_medium` -> `cover_xl`

Both fields are returned in the same API response — no additional API calls needed.

### 2. Backfill Existing Library

MusicRepository already has `backfillAlbumArt()` and `backfillArtistImages()` that re-fetch for entries with null URLs. Add a one-time upgrade pass:

- Clear existing Deezer medium URLs (those containing `deezer` in the domain) so the standard backfill re-fetches them at XL resolution
- Media session URLs (from Spotify, YouTube Music, etc.) are left untouched — they're already the best available from the source app
- Local file:// bitmaps are left untouched

### 3. Re-extract Palettes

After URLs are upgraded, re-run palette extraction for affected songs to get more accurate colors from the higher-resolution source images.

## Scope

- **Files modified:** ArtistImageFetcher.kt, MusicRepository.kt
- **No schema changes** — same `albumArtUrl` / `imageUrl` string fields
- **No new dependencies** — same Deezer API, same Coil loader
- **No new APIs** — just reading a different field from existing responses
