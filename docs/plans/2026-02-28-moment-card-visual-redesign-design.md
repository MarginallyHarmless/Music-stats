# Moment Card Visual Redesign

**Date:** 2026-02-28

## Problem

The current `MomentCard` uses a horizontal row layout (small leading image/emoji + text column) with a flat `primaryContainer → tertiaryContainer` gradient. This underuses the album/artist art available on many cards and feels disconnected from the glassy aesthetic used elsewhere in the app.

## Goals

- More vertical layout with more visual breathing room
- Full-bleed image background for every card — album art or artist photo where available
- Dark placeholder (+ palette glow) for cards without an image, ready to accept custom imagery later
- No emoji anywhere on cards
- Consistent single layout for all moment types

## Design

### Single card variant — always full-bleed image background

All moment cards use the same structure regardless of type:

```
┌─────────────────────────────┐
│  [image fills background]    │
│  ░ AsyncImage (Crop) or      │
│  ░ dark placeholder          │
│  ░ + palette glow            │
│                              │
│  Artist Name (if present)    │  ← entityName, labelMedium, white 65%
│  Title                       │  ← titleMedium bold, white
│  Description two lines max   │  ← bodySmall, white 70%
│  73% of your listening       │  ← statLine, primary color (if non-null)
│  Feb 15, 2026           ●    │  ← date left, unseen dot right
└─────────────────────────────┘
```

### Image layer

- If `moment.imageUrl != null`: `AsyncImage(fillMaxSize, ContentScale.Crop)`
- Else: solid `Color(0xFF1A1A28)` (ElevatedSurface) + subtle radial glow using `LocalAlbumPalette.current.accent`

### Scrim

`Brush.verticalGradient(transparent → Color.Black.copy(alpha = 0.70f))` layered over the image. Ensures text readability on any image.

### Text layer

Anchored to the bottom of the card via `Box` + `Column(Alignment.BottomStart)`:

| Field | Style | Color |
|---|---|---|
| entityName (if present) | labelMedium | white 65% |
| title | titleMedium bold | white |
| description | bodySmall, maxLines=2 | white 70% |
| statLine (if non-null) | labelSmall | `colorScheme.primary` |
| date | labelSmall | white 40% |

Unseen dot: 8dp white circle, aligned to the right of the date row.

Unseen border: 1.5dp `colorScheme.primary` border around the card.

### Placeholder images

Cards without `imageUrl` (streak, total hours, discovery milestones, archetypes) show the dark placeholder. When the user provides custom imagery later, a `momentBackgroundDrawable(type: String): Int?` function will be added to map type strings to local drawable resource IDs. Coil can load both `imageUrl` (remote) and drawable resource IDs through the same `AsyncImage` call.

### Corner radius

20dp (up from 16dp, matches `MaterialTheme.shapes.extraLarge - 12dp` — actually using 20dp explicitly).

## What changes

- `app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt` — full rewrite

No other files change. `MomentsStrip`, `AllMomentsScreen`, and `MomentDetailBottomSheet` are untouched.
