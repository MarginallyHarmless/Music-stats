# Aesthetic Overhaul Design — Full Visual Redesign

## Goal
Significantly improve the app's visual design so every screen is social-media-screenshot-worthy, and add dedicated share card generation for Instagram stories / posts.

## Direction
**Clean & editorial** aesthetic. Large hero typography, generous whitespace, subtle gradient accents layered on M3 dynamic colors. Think Apple Music recap, not Spotify Wrapped neon.

## Design Language

### Typography
- Add **Inter** font family (clean, editorial, great for numbers)
- Hero numbers: oversized display treatment (48-72sp), tight letter spacing
- Section labels: uppercase, tracked-out text
- Keep M3 type scale but with Inter as the base family

### Shape Language
- Hero cards: 24dp corners
- Stat cards: 16dp corners
- Album art: 12dp corners (songs), circular (artists)
- Chips/pills: fully rounded (50% radius)
- Move away from uniform rounded rectangles

### Accent Gradients
- Primary -> Tertiary diagonal gradient on hero cards
- Subtle gradient overlays on stat card backgrounds (primaryContainer at 0.6 opacity -> transparent)
- Soft washes, not neon or harsh

### Spacing
- 24dp horizontal padding (up from 16dp)
- 20-32dp vertical gaps between sections
- 20-24dp internal card padding

### Color System
- Keep M3 dynamic colors (adapts to wallpaper)
- Layer custom accent gradients on top
- Stat cards get alternating tints: primaryContainer, secondaryContainer, tertiaryContainer, surfaceVariant

---

## Screen Designs

### Home Dashboard
Layout (top to bottom):
1. **Greeting header** - time-based greeting ("Good evening") + today's date. Left-aligned, no card. headlineLarge + bodyMedium uppercase tracked date.
2. **Hero listening card** - full-width, primary->tertiary gradient background. displayLarge (57sp) bold centered time. "listened today" as small uppercase tracked label.
3. **Now/Last played strip** - album art (56dp) + title/artist + source app pill badge. surfaceVariant background.
4. **Today's stats row** - three stat "pills" (Songs, Skips, Streak). Rounded surface, titleLarge number, labelSmall uppercase label.
5. **Top artist spotlight** - full-width card, artist image as large blurred background with dimmed overlay, artist name in headlineSmall white, play count + duration as small overlaid pills.
6. **Weekly chart** - uppercase tracked section title. Vico bars with rounded caps, gradient fills.
7. **Top songs this week** - album art (48dp) on every row, large primary-colored ranking number. Each row is a subtle card.
8. **Share FAB** - bottom-right, generates share card from dashboard state.

### Stats Screen
- **Tab row**: custom pill-shaped tabs (selected = filled primary + rounded, unselected = text only)
- **Time range chips**: rounded pills with gradient fill for selected

**Time Tab:**
1. Two hero gradient cards side by side (total time, total plays) - displaySmall numbers
2. Three horizontal pill stats (avg session, longest, skips)
3. Hourly heatmap: 44dp cells, 8dp rounded, primaryContainer gradient color scale, legend bar
4. Daily chart: rounded bars, gradient fills

**Discovery Tab:**
1. Hero card with "X new songs" in display text, gradient
2. 2x2 grid of stat cards (new artists, unique songs, unique artists, deep cuts count) with icons
3. Deep cuts list with album art

**Top Lists Tab:**
1. Two pill toggle (Duration / Play Count)
2. Top songs: card-rows with large rank number, album art (48dp), title/artist, metric. Top 3 get larger treatment (bigger art, bolder rank)
3. Top artists: circular images, top 3 get 64dp images vs 40dp

### Library Screen
- **Tabs**: same pill-style tabs as Stats
- **Search bar**: filled rounded (surfaceVariant background, no outline, 28dp corners)
- **Sort chips**: rounded pills
- **Song items**: subtle card (surfaceVariant low opacity), thin primary accent line on left edge, 16dp vertical padding, album art 48dp
- **Artist items**: same card treatment, circular images, "X songs" label beneath name

### Song Detail Screen
1. **Hero**: album art 200dp centered with drop shadow. Title headlineMedium, artist titleMedium, album bodyMedium.
2. **Stats row**: four rounded cards with alternating tints (primaryContainer, secondaryContainer, tertiaryContainer, surfaceVariant). Number in headlineSmall, label in labelSmall uppercase.
3. **History**: uppercase tracked section header. Source app as colored pill badge. Mini album art thumbnail (32dp) on each row.

### Artist Detail Screen
1. **Hero**: artist image 200dp circular centered. Name headlineMedium below.
2. **Stats row**: same alternating-tint treatment
3. **History**: album art (32dp) + song title + source app pill + duration per row.

### Settings Screen
- Light polish only: match new shape language (24dp card corners), updated spacing, pill-shaped buttons

---

## Share Cards (New Feature)

### Card Types
1. **Daily recap** - "I listened to X hours today" + top artist + top 3 songs. Gradient background.
2. **Song spotlight** - large album art, song stats, "on repeat" vibe.
3. **Artist spotlight** - artist image, play count, "my top artist" treatment.
4. **Weekly/monthly recap** - condensed stats summary.

### Card Design
- Fixed aspect ratios: 1080x1920 (story), 1080x1080 (square post)
- App branding watermark in corner ("Music Stats" small text)
- Gradient backgrounds using dynamic theme colors
- Large typography, minimal text, album art as focal point
- Rendered via drawToBitmap on a composable

### Share Flow
- Home FAB -> bottom sheet with card type picker
- Detail screens get share icon -> generates spotlight card for that item
- Preview card before sharing
- "Save to gallery" or "Share" buttons via Android share intent

---

## Out of Scope
- Animations/transitions (can be added later)
- Onboarding redesign
- Settings screen major changes
- Tablet/foldable layouts
