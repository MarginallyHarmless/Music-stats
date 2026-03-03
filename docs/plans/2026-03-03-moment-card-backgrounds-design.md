# Moment Card Background Images Design

**Date:** 2026-03-03
**Goal:** Add bundled photo/texture backgrounds to all moment cards that don't have dynamic entity artwork (album art or artist image).

## Approach

- **Source:** Bundled assets shipped in `res/drawable/` (compressed JPEGs)
- **Style:** Atmospheric photography/textures that evoke the mood of each card
- **Integration:** Populate `momentBackgroundDrawable()` in `MomentCard.kt` to map moment types to drawable resource IDs
- **Naming convention:** `moment_<snake_case_type>.jpg` (e.g., `moment_streak_7.jpg`)

## Cards With Dynamic Artwork (no action needed)

These 32 cards already load album art or artist images at runtime:

| Type | Title | Artwork Source |
|------|-------|---------------|
| SONG_PLAYS_50 | Ride or Die | Album art |
| SONG_PLAYS_100 | Certified Obsession | Album art |
| SONG_PLAYS_250 | Down Bad | Album art |
| SONG_PLAYS_500 | Beyond Help | Album art |
| ARTIST_HOURS_5 | Getting Serious | Artist image |
| ARTIST_HOURS_10 | Dedicated Fan | Artist image |
| ARTIST_HOURS_24 | A Full Day | Artist image |
| OBSESSION_DAILY | Today's Fixation | Album art |
| DAILY_RITUAL | Daily Ritual | Album art |
| FAST_OBSESSION | Instant Classic | Album art |
| QUICK_OBSESSION | Love at First Listen | Album art |
| RESURRECTION | Back from the Dead | Album art |
| SLOW_BURN | Slow Burn | Album art |
| COMFORT_ZONE | Comfort Zone | Album art |
| BREAKUP_CANDIDATE | It's Complicated | Artist image |
| REDISCOVERY | The Comeback | Artist image |
| ARCHETYPE_DEEP_CUT_DIGGER | Deep Cut Digger | Album art |
| ARCHETYPE_LOYAL_FAN | Loyal Fan | Artist image |
| ARCHETYPE_REPEAT_OFFENDER | Repeat Offender | Album art |
| ARCHETYPE_ALBUM_LISTENER | Album Listener | Album art |
| NARRATIVE_ORIGIN_STORY | Origin Story | Artist image |
| NARRATIVE_GATEWAY | The Gateway | Artist image |
| NARRATIVE_COLLECTION | The Collection | Artist image |
| NARRATIVE_TAKEOVER | The Takeover | Album art |
| NARRATIVE_SLOW_BUILD | The Slow Build | Album art |
| NARRATIVE_BINGE_AND_FADE | Burned Bright | Album art |
| NARRATIVE_FULL_CIRCLE | Full Circle | Artist image |
| NARRATIVE_ONE_THAT_GOT_AWAY | The One That Got Away | Artist image |
| NARRATIVE_SOUNDTRACK | The Soundtrack | Album art |
| NARRATIVE_RABBIT_HOLE | The Rabbit Hole | Artist image |
| NARRATIVE_NIGHT_AND_DAY | Night & Day | Artist image |
| NARRATIVE_PARALLEL_LIVES | Parallel Lives | Artist image |

## Cards Needing Bundled Backgrounds (21 cards)

### Streak Milestones — fire, persistence, endurance

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| STREAK_7 | Full Week | `moment_streak_7` | Burning matchstick trail — a line of lit matches in darkness, evoking consecutive days lit up |
| STREAK_14 | Two Weeks Strong | `moment_streak_14` | Campfire embers at night — warm orange coals glowing in darkness, steady and enduring |
| STREAK_30 | Unstoppable | `moment_streak_30` | Lava flow at night — molten rock glowing against black basalt, raw unstoppable energy |
| STREAK_100 | Legendary | `moment_streak_100` | Aurora borealis over mountains — epic, rare, legendary natural phenomenon |

### Total Hours Milestones — time, depth, accumulation

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| TOTAL_HOURS_24 | First Full Day | `moment_total_hours_24` | Golden hour sunrise over water — the first full day, warm and promising |
| TOTAL_HOURS_100 | Triple Digits | `moment_total_hours_100` | Deep ocean blue — a deep-sea texture, suggesting depth and immersion |
| TOTAL_HOURS_500 | Half a Thousand | `moment_total_hours_500` | Star field / galaxy — vast, countless hours like countless stars |
| TOTAL_HOURS_1000 | The Thousand Hour Club | `moment_total_hours_1000` | Nebula close-up — cosmic, awe-inspiring, a monumental achievement |

### Discovery Milestones — exploration, new territory

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| SONGS_DISCOVERED_100 | Century Club | `moment_songs_discovered_100` | Vinyl record collection close-up — crate digging through stacked records |
| SONGS_DISCOVERED_250 | Music Collector | `moment_songs_discovered_250` | Colorful cassette tape wall — a grid of vintage tapes, variety and quantity |
| SONGS_DISCOVERED_500 | Walking Jukebox | `moment_songs_discovered_500` | Neon jukebox close-up — glowing neon tubes of a retro jukebox in a dark room |

### Time-of-Day Archetypes — atmosphere of that time of day

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| ARCHETYPE_NIGHT_OWL | Night Owl | `moment_night_owl` | City skyline at 2am — moody blue-purple city lights, quiet nighttime streets |
| ARCHETYPE_MORNING_LISTENER | Early Bird | `moment_morning_listener` | Misty sunrise through a window — warm light breaking through blinds, fresh morning |
| ARCHETYPE_COMMUTE_LISTENER | Road Warrior | `moment_commute_listener` | Car dashboard at dusk with highway lights — headphones-on-the-road vibe |

### Engagement Archetypes — listening behavior

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| ARCHETYPE_COMPLETIONIST | Completionist | `moment_completionist` | Equalizer bars / sound waves — clean, full audio waveform in neon on dark background |
| ARCHETYPE_CERTIFIED_SKIPPER | Certified Skipper | `moment_certified_skipper` | Blurred motion / speed lines — abstract fast-forward motion blur, restless energy |

### Taste Archetypes — musical identity

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| ARCHETYPE_EXPLORER | Explorer | `moment_explorer` | Compass on a map — or a winding forest trail, charting new territory |
| ARCHETYPE_WEEKEND_WARRIOR | Weekend Warrior | `moment_weekend_warrior` | Festival crowd at sunset — outdoor festival silhouettes, weekend energy |
| ARCHETYPE_WIDE_TASTE | Wide Taste | `moment_wide_taste` | Paint palette / paint splatters — colorful abstract paint splashes representing variety |

### Abstract Behavioral Moments — the specific behavior

| Type | Title | Drawable Name | Photo Description |
|------|-------|---------------|-------------------|
| DISCOVERY_WEEK | New Ears Week | `moment_discovery_week` | Headphones on a world map — new sounds from everywhere, exploration |
| NIGHT_BINGE | 3am Vibes | `moment_night_binge` | Neon-lit empty street at 3am — rain-slicked road with neon reflections, late-night atmosphere |
| LONGEST_SESSION | Marathon Mode | `moment_longest_session` | Winding road disappearing into fog — an endless path, marathon session |
| MARATHON_WEEK | Marathon Week | `moment_marathon_week` | Long-exposure highway light trails at night — pushing limits, sustained intensity |

## Implementation Notes

1. **Image specs:** JPEG, ~720x480px minimum, compressed to keep each file under 150KB
2. **Color tone:** All photos should lean dark/moody so white text remains readable even before the scrim
3. **Scrim:** The existing vertical gradient scrim (transparent -> 70% black) in `MomentCard.kt` handles text readability
4. **`momentBackgroundDrawable()`:** Update the stub in `MomentCard.kt` to return `R.drawable.moment_*` for each type
5. **Fallback:** Cards whose type isn't mapped still get the dark placeholder with radial palette glow
