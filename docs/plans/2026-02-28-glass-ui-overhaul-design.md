# Glass UI Overhaul — Design Doc

## Problem

The dark theme redesign shipped with three issues:
1. **Dark text invisible on dark backgrounds** — `primaryContainer` (derived from `albumPalette.darkVibrant`) is used as both backgrounds and foreground fills. Heatmap cells, selected pill chips, and gradient cards all blend into the near-black aurora background.
2. **Stat card labels truncated** — 4 stat cards squeezed into one row on detail screens. Labels like "TOTAL TIME" and "SKIP RATE" get clipped. Large `headlineSmall` values and `1.sp` letter-spacing on labels waste space.
3. **Plain layouts** — Screens are functional but flat. Cards are opaque dark rectangles. No visual depth, no layering, no personality.

## Direction

Apple Music / Marvis Pro inspired. Frosted glass surfaces, subtle borders, floating depth, stronger aurora glow. The album-art color theming should feel alive without being garish.

---

## Part 1: Bug Fixes

### Dark Text — Root Cause & Fixes

The theme maps `primaryContainer = albumPalette.darkVibrant`. Anything using `primaryContainer` as a *fill* creates dark-on-dark. Fix every usage:

**HourlyHeatmap.kt**
- Current: `baseColor = MaterialTheme.colorScheme.primaryContainer` (dark purple cells, invisible)
- Fix: `baseColor = LocalAlbumPalette.current.accent` — uses the brighter vibrant color
- Text color: always `Color.White` regardless of intensity (the accent color cells are bright enough)

**PillChip.kt**
- Current selected: `background = primaryContainer`, `text = onPrimaryContainer`
- Fix selected: `background = accent.copy(alpha = 0.2f)`, `text = accent` — tinted highlight style
- Unselected stays: `surfaceVariant.copy(alpha = 0.5f)` background, `onSurfaceVariant` text

**AppPillTabs.kt**
- Current selected: `background = primaryContainer`, `text = onPrimaryContainer`
- Fix selected: `background = accent.copy(alpha = 0.2f)`, `text = accent`
- The outer row container: bump from `surfaceVariant.copy(alpha = 0.4f)` to `Color.White.copy(alpha = 0.06f)` for glass consistency

**GradientCard.kt** — see Part 2 (full glass rework)

**ElevatedCard throughout** — `surfaceVariant.copy(alpha = 0.3f)` is too transparent. Each usage gets the glass treatment (Part 2).

### Stat Card Truncation Fix

**StatCard.kt changes:**
- Padding: 16dp → 12dp
- Value text: `headlineSmall` (24sp) → `titleLarge` (22sp) — still bold, slightly smaller
- Label text: remove `letterSpacing = 1.sp`, keep `labelSmall` but allow wrapping (remove `maxLines = 1`)
- Add `minHeight` to card so short labels don't collapse

**Detail screens (SongDetailScreen, ArtistDetailScreen):**
- Change 4-across stat row → **2x2 grid** (two rows of two cards)
- This gives each card ~50% width instead of ~25%, eliminating truncation entirely

---

## Part 2: Glass Card System

### Design Language

All cards share a frosted glass surface:
- Fill: `Color.White.copy(alpha = 0.06f)`
- Border: `1dp` stroke at `Color.White.copy(alpha = 0.08f)`
- Shape: existing `MaterialTheme.shapes.large` (24dp radius) for hero cards, `.medium` (16dp) for stat/list cards
- No elevation shadow (remove `ElevatedCard`, use `Card` or `Surface`)

### GradientCard → GlassCard

Replace the opaque `darkVibrant → darkMuted` gradient:
- Glass fill as above
- Top-edge accent glow: a vertical gradient from `accent.copy(alpha = 0.1f)` to `Color.Transparent` covering the top ~30% of the card — creates a subtle colored reflection
- Content text: `Color.White` for values, `Color.White.copy(alpha = 0.7f)` for labels (guaranteed visible on glass)

### StatCard

- Glass surface instead of opaque `surfaceVariant`
- Icon tint: `LocalAlbumPalette.current.lightVibrant` (brighter than `primary`)
- Value: `Color.White`
- Label: `onSurfaceVariant` (0xFFB0B0C0)

### List Item Cards (Library, Top Songs, Deep Cuts)

- Glass fill + border instead of `ElevatedCard` with `surfaceVariant` container
- Library accent bar: use `LocalAlbumPalette.current.accent` instead of `MaterialTheme.colorScheme.primary`
- Text colors unchanged (already White / onSurfaceVariant)

### Artist Spotlight Card (Home)

- Keep album art background
- Replace flat `Color(0xFF0A0A0F).copy(alpha = 0.6f)` overlay with a bottom-heavy gradient: `Color.Transparent` at top → `Color(0xFF0A0A0F).copy(alpha = 0.85f)` at bottom
- Stat pills inside: glass pill style (`Color.White.copy(alpha = 0.1f)` fill, `Color.White.copy(alpha = 0.15f)` border)
- Increase card height: 160dp → 200dp to show more art

---

## Part 3: Aurora Background Enhancement

Current glow is too subtle to notice:
- Primary glow radius: 900 → 1800
- Secondary glow radius: 1100 → 2200
- Primary alpha: 0.15f → 0.20f
- Secondary alpha: 0.10f → 0.15f
- Shift center positions to create a diagonal sweep: primary at `Offset(0.15f, 0.05f)`, secondary at `Offset(0.85f, 0.95f)`

---

## Part 4: Screen-Specific Layout Changes

### Home Screen
- Hero glass card: value uses `displayMedium` (45sp, down from 57sp) for better proportion in glass card. Accent-colored 1dp line above "LISTENED TODAY" label.
- Top songs: replace per-song `ElevatedCard` with flat rows on the aurora background. Thin `outline.copy(alpha = 0.3f)` divider between items. Cleaner, less card-heavy.

### Song Detail / Artist Detail
- Stat cards: 4x1 row → 2x2 grid
- Album art glow: draw a blurred circle of `accent.copy(alpha = 0.15f)` behind the album art image (Box with larger size behind the image, radial gradient from accent to transparent)
- Per-song palette override already exists (CompositionLocalProvider) — no change needed

### Stats Screen
- TimeStatsTab hero gradient cards → glass cards
- All stat cards get glass treatment
- Heatmap gets the accent color fix

### Library Screen
- Search field: `Color.White.copy(alpha = 0.06f)` fill + `Color.White.copy(alpha = 0.08f)` border, matching glass language
- List cards: glass style

### Settings Screen
- Minimal: ensure all text uses visible colors. SettingsRow icon tint and description text are already `onSurfaceVariant` which is fine.

### Onboarding
- Sub-page backgrounds (`WelcomePage`, `NotificationAccessPage`, `AllSetPage`): these use `MaterialTheme.colorScheme.background`/`onBackground` implicitly. The parent `AuroraBackground` already wraps them, so they just need transparent backgrounds (no `Surface` fill overrides).

---

## Files Changed

| File | Changes |
|------|---------|
| `ui/theme/Theme.kt` | Adjust `primaryContainer` to use brighter color |
| `ui/theme/AlbumPalette.kt` | Add `glassBackground`, `glassBorder` computed props |
| `ui/components/GradientCard.kt` | Rewrite as glass card with accent top glow |
| `ui/components/StatCard.kt` | Glass surface, smaller text, allow label wrapping |
| `ui/components/AuroraBackground.kt` | Larger radius, stronger alpha, diagonal sweep |
| `ui/components/PillChip.kt` | Accent tint selected state |
| `ui/components/AppPillTabs.kt` | Accent tint selected state, glass container |
| `ui/components/HourlyHeatmap.kt` | Use accent color, fix text visibility |
| `ui/home/HomeScreen.kt` | Glass cards, flat top-song rows, artist spotlight gradient, smaller hero text |
| `ui/library/SongDetailScreen.kt` | 2x2 stat grid, album art glow |
| `ui/library/ArtistDetailScreen.kt` | 2x2 stat grid, artist image glow |
| `ui/library/LibraryScreen.kt` | Glass search field, glass list cards |
| `ui/stats/TimeStatsTab.kt` | Glass hero cards |
| `ui/stats/DiscoveryStatsTab.kt` | Glass hero card, glass deep cut cards |
| `ui/settings/SettingsScreen.kt` | Minor text color verification |
| `ui/onboarding/OnboardingScreen.kt` | Transparent sub-page backgrounds |

---

## Not Changed

- Navigation graph, ViewModels, DAOs, database, repository — no data layer changes
- Bottom nav bar — already themed correctly
- Share cards — rendered offscreen, keep existing style
- Typography — Inter font family stays
