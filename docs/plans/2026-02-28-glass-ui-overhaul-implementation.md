# Glass UI Overhaul — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix dark-on-dark text visibility, fix stat card truncation, and transform the UI from flat opaque cards to a frosted-glass Apple Music / Marvis Pro aesthetic.

**Architecture:** Pure UI layer changes. No data model, DAO, repository, or ViewModel changes. All modifications are to Composable components, the theme, and screen layouts. The glass design language uses `Color.White.copy(alpha = 0.06f)` fills with `Color.White.copy(alpha = 0.08f)` borders everywhere, replacing opaque dark surfaces.

**Tech Stack:** Jetpack Compose, Material 3, Compose `Border`/`Brush` APIs, existing AlbumPalette system.

---

### Task 1: AlbumPalette — Add Glass Helper Properties

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/theme/AlbumPalette.kt`

**Step 1: Add glass properties to AlbumPalette**

Add two computed properties after the existing `glowSecondary`:

```kotlin
val glassBackground: Color
    get() = Color.White.copy(alpha = 0.06f)

val glassBorder: Color
    get() = Color.White.copy(alpha = 0.08f)
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: add glass helper properties to AlbumPalette
```

---

### Task 2: AuroraBackground — Stronger, Wider Glow

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/AuroraBackground.kt`

**Step 1: Update glow parameters**

Change the three `.background()` modifiers:

```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .background(Color(0xFF0A0A0F))
        .background(
            Brush.radialGradient(
                colors = listOf(
                    palette.glowPrimary.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(0.15f, 0.05f),
                radius = 1800f
            )
        )
        .background(
            Brush.radialGradient(
                colors = listOf(
                    palette.glowSecondary.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(0.85f, 0.95f),
                radius = 2200f
            )
        ),
    content = content
)
```

Changes from current:
- Primary alpha: `0.15f` → `0.20f`
- Primary center: `Offset(0.1f, 0.1f)` → `Offset(0.15f, 0.05f)`
- Primary radius: `900f` → `1800f`
- Secondary alpha: `0.10f` → `0.15f`
- Secondary center: `Offset(0.9f, 0.9f)` → `Offset(0.85f, 0.95f)`
- Secondary radius: `1100f` → `2200f`

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: strengthen aurora glow radius and alpha
```

---

### Task 3: GradientCard → Glass Card

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/GradientCard.kt`

**Step 1: Rewrite as glass card**

Replace the entire file contents (keep package/imports, rewrite composable):

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicstats.app.ui.theme.LocalAlbumPalette

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalAlbumPalette.current
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.glassBackground)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    endY = 150f
                )
            )
            .border(1.dp, palette.glassBorder, shape)
            .padding(24.dp),
        content = content
    )
}
```

Key changes:
- Remove `startColor`/`endColor` parameters (no longer needed)
- Glass fill + accent top-edge glow gradient + border
- The `endY = 150f` makes the accent glow cover roughly the top 30% of the card

**Step 2: Fix callers that pass startColor/endColor**

Search all files for `GradientCard(` calls. The callers in `TimeStatsTab.kt`, `DiscoveryStatsTab.kt`, and `HomeScreen.kt` only use the default colors — none pass explicit `startColor`/`endColor`, so no callers need updating.

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: rewrite GradientCard as frosted glass card
```

---

### Task 4: StatCard — Glass Surface + Truncation Fix

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/StatCard.kt`

**Step 1: Rewrite StatCard**

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicstats.app.ui.theme.LocalAlbumPalette

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalAlbumPalette.current.glassBackground
) {
    val palette = LocalAlbumPalette.current

    Card(
        modifier = modifier
            .border(1.dp, palette.glassBorder, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = palette.lightVibrant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
```

Key changes:
- `ElevatedCard` → `Card` (no elevation shadow)
- Glass `containerColor` default + glass border
- Padding: `16.dp` → `12.dp`
- Value: `headlineSmall` → `titleLarge`
- Value color: explicit `Color.White`
- Icon: `primary` → `palette.lightVibrant`
- Label: removed `letterSpacing = 1.sp`, removed `maxLines = 1` (allows wrapping)

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
fix: glass StatCard with truncation fix and visible text
```

---

### Task 5: PillChip + AppPillTabs — Accent Tint Style

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/PillChip.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/components/AppPillTabs.kt`

**Step 1: Rewrite PillChip selected state**

In `PillChip.kt`, add import and change the color logic:

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicstats.app.ui.theme.LocalAlbumPalette

@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAlbumPalette.current
    val shape = RoundedCornerShape(50)
    val backgroundColor = if (selected) {
        palette.accent.copy(alpha = 0.2f)
    } else {
        palette.glassBackground
    }
    val textColor = if (selected) {
        palette.accent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
```

**Step 2: Rewrite AppPillTabs selected state + glass container**

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicstats.app.ui.theme.LocalAlbumPalette

@Composable
fun AppPillTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAlbumPalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) palette.accent.copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) palette.accent
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
fix: accent-tint selected state for PillChip and AppPillTabs
```

---

### Task 6: HourlyHeatmap — Accent Color Fix

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/HourlyHeatmap.kt`

**Step 1: Use accent color and fix text visibility**

Add import for `LocalAlbumPalette` and `Color`:

```kotlin
import androidx.compose.ui.graphics.Color
import com.musicstats.app.ui.theme.LocalAlbumPalette
```

Change line 34 from:
```kotlin
val baseColor = MaterialTheme.colorScheme.primaryContainer
```
to:
```kotlin
val baseColor = LocalAlbumPalette.current.accent
```

Change lines 56-64 (the text color logic) from:
```kotlin
Text(
    text = String.format("%02d", hour),
    style = MaterialTheme.typography.labelSmall,
    color = if (intensity > 0.5f) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
)
```
to:
```kotlin
Text(
    text = String.format("%02d", hour),
    style = MaterialTheme.typography.labelSmall,
    color = Color.White
)
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
fix: use accent color in HourlyHeatmap for visibility
```

---

### Task 7: HomeScreen — Glass Cards + Flat Song Rows + Artist Spotlight

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Hero card — update text style**

Inside the `GradientCard` block (the hero card), change the Text composables:

Change `displayLarge` → `displayMedium` for the duration text.

Change the "LISTENED TODAY" label color from `onPrimaryContainer.copy(alpha = 0.7f)` → `Color.White.copy(alpha = 0.7f)`.

Change the duration text color from `onPrimaryContainer` → `Color.White`.

Add an accent divider line above the label. Between the duration Text and the "LISTENED TODAY" Text, add:
```kotlin
Box(
    modifier = Modifier
        .padding(vertical = 8.dp)
        .fillMaxWidth(0.3f)
        .height(1.dp)
        .background(LocalAlbumPalette.current.accent.copy(alpha = 0.4f))
)
```

Requires import: `import com.musicstats.app.ui.theme.LocalAlbumPalette`

**Step 2: Artist spotlight — bottom gradient overlay + taller card**

Change the artist spotlight card height from `160.dp` to `200.dp` (in both the image and the "no image" fallback Box).

Replace the flat overlay:
```kotlin
Box(
    modifier = Modifier
        .matchParentSize()
        .background(Color(0xFF0A0A0F).copy(alpha = 0.6f))
)
```
with a bottom-heavy gradient:
```kotlin
Box(
    modifier = Modifier
        .matchParentSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF0A0A0F).copy(alpha = 0.85f)
                ),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY
            )
        )
)
```

Change the stat pills inside the artist spotlight from:
```kotlin
.background(
    Color.White.copy(alpha = 0.15f),
    RoundedCornerShape(50)
)
```
to glass pills with border:
```kotlin
.background(
    Color.White.copy(alpha = 0.10f),
    RoundedCornerShape(50)
)
.border(
    1.dp,
    Color.White.copy(alpha = 0.15f),
    RoundedCornerShape(50)
)
```

(Add `import androidx.compose.foundation.border` if not present.)

**Step 3: Top songs — flat rows instead of cards**

Replace the `topSongs.forEachIndexed` block. Change each song from `ElevatedCard` to a simple `Row` with a bottom divider:

Replace:
```kotlin
ElevatedCard(
    onClick = { onSongClick(song.songId) },
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ),
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
```
with:
```kotlin
Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick(song.songId) }
            .padding(vertical = 12.dp),
```

Close the `Column` after the Row's closing brace and add a divider:
```kotlin
    )
    if (index < topSongs.lastIndex) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}
```

Add imports: `import androidx.compose.foundation.clickable`, `import androidx.compose.material3.HorizontalDivider` (if not present).

Remove the `ElevatedCard` closing brace that was wrapping the Row.

**Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: glass hero card, gradient artist spotlight, flat song rows on Home
```

---

### Task 8: SongDetailScreen — 2x2 Stat Grid + Album Art Glow

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt`

**Step 1: Replace 4x1 stat row with 2x2 grid**

Find the stats `item { Row(...) }` block. Replace the single `Row` containing 4 `StatCard`s with two rows:

```kotlin
// Stats grid
item {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Plays",
                value = "$totalPlayCount",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Total Time",
                value = formatDuration(totalListeningTime),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Skips",
                value = "$skipCount",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Skip Rate",
                value = "${(skipRate * 100).toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}
```

Note: remove the custom `containerColor` parameters (each was `primaryContainer.copy(0.5f)` etc.) — StatCard now defaults to glass background.

**Step 2: Add album art glow**

In the hero header `item`, wrap the `AsyncImage` with a `Box` that has a radial glow behind it:

```kotlin
if (currentSong.albumArtUrl != null) {
    Box(contentAlignment = Alignment.Center) {
        // Glow behind art
        Box(
            modifier = Modifier
                .size(230.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LocalAlbumPalette.current.accent.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        AsyncImage(
            model = currentSong.albumArtUrl,
            contentDescription = "Album art",
            modifier = Modifier
                .size(200.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}
```

Add imports: `import androidx.compose.foundation.shape.CircleShape`, `import androidx.compose.ui.graphics.Brush`, `import com.musicstats.app.ui.theme.LocalAlbumPalette` (if not present).

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: 2x2 stat grid and album art glow on SongDetailScreen
```

---

### Task 9: ArtistDetailScreen — 2x2 Stat Grid + Image Glow

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/ArtistDetailScreen.kt`

**Step 1: Replace 4x1 stat row with 2x2 grid**

Same pattern as Task 8. Replace the single Row with:

```kotlin
// Stats grid
item {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Plays",
                value = "$totalEvents",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Total Time",
                value = formatDuration(totalTime),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Skips",
                value = "$skipCount",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Skip Rate",
                value = "$skipRate%",
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}
```

Remove custom `containerColor` parameters.

**Step 2: Add artist image glow**

Wrap the `AsyncImage` for the artist (the `if (imageUrl != null)` branch) with a glow box:

```kotlin
if (imageUrl != null) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LocalAlbumPalette.current.accent.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        AsyncImage(
            model = imageUrl,
            contentDescription = "Artist image",
            modifier = Modifier
                .size(200.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}
```

Add imports: `import androidx.compose.ui.graphics.Brush`, `import com.musicstats.app.ui.theme.LocalAlbumPalette` (if not present).

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: 2x2 stat grid and artist image glow on ArtistDetailScreen
```

---

### Task 10: LibraryScreen — Glass Search + Glass List Cards

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/LibraryScreen.kt`

**Step 1: Glass search field styling**

In both `SongsTab` and `ArtistsTab`, change the `TextField` colors from:
```kotlin
colors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)
```
to:
```kotlin
colors = TextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.06f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)
```

**Step 2: Glass list cards**

In `SongListItem`, change from `ElevatedCard` to `Card` with glass styling:

```kotlin
val palette = LocalAlbumPalette.current
Card(
    modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, palette.glassBorder, MaterialTheme.shapes.medium),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(
        containerColor = palette.glassBackground
    )
)
```

Change the accent bar color from:
```kotlin
val accentColor = MaterialTheme.colorScheme.primary
```
to:
```kotlin
val accentColor = LocalAlbumPalette.current.accent
```

Repeat the same `ElevatedCard` → `Card` + glass treatment for `ArtistListItem`.

Add imports: `import androidx.compose.foundation.border`, `import com.musicstats.app.ui.theme.LocalAlbumPalette` (if not present).

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: glass search field and glass list cards in Library
```

---

### Task 11: Stats Tabs — Glass Cards + Text Color Fixes

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/stats/TimeStatsTab.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/stats/DiscoveryStatsTab.kt`

**Step 1: TimeStatsTab — fix GradientCard text colors**

The GradientCard is now glass (from Task 3). Fix the text colors inside both GradientCards from `onPrimaryContainer` → `Color.White`:

For both hero GradientCards, change:
```kotlin
color = MaterialTheme.colorScheme.onPrimaryContainer
```
to:
```kotlin
color = Color.White
```

And change:
```kotlin
color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
```
to:
```kotlin
color = Color.White.copy(alpha = 0.7f)
```

Add import: `import androidx.compose.ui.graphics.Color`

**Step 2: DiscoveryStatsTab — same text color fix**

Same change in the hero GradientCard text colors:
- `onPrimaryContainer` → `Color.White`
- `onPrimaryContainer.copy(alpha = 0.7f)` → `Color.White.copy(alpha = 0.7f)`

Add import: `import androidx.compose.ui.graphics.Color`

**Step 3: DiscoveryStatsTab — glass deep cut cards**

Change deep cut `ElevatedCard` from:
```kotlin
ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )
)
```
to:
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, LocalAlbumPalette.current.glassBorder, MaterialTheme.shapes.medium),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(
        containerColor = LocalAlbumPalette.current.glassBackground
    )
)
```

Add imports: `import androidx.compose.foundation.border`, `import androidx.compose.material3.Card`, `import com.musicstats.app.ui.theme.LocalAlbumPalette` (if not present).

**Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: glass cards and visible text colors in Stats tabs
```

---

### Task 12: HomeScreen — Fix Remaining Text Colors

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Fix ElevatedCard references for top songs**

After Task 7, the top songs use flat rows. Verify there are no remaining `ElevatedCard` imports that are now unused and remove them if so.

Also check the "no listening data" fallback card — if it still uses `ElevatedCard`, convert to `Card` with glass:

```kotlin
Card(
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = LocalAlbumPalette.current.glassBackground
    )
)
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
fix: remaining card and text color cleanup on HomeScreen
```

---

### Task 13: Onboarding — Transparent Backgrounds

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/onboarding/OnboardingScreen.kt`

**Step 1: Fix sub-page background colors**

The `WelcomePage`, `NotificationAccessPage`, and `AllSetPage` composables use `MaterialTheme.colorScheme.onBackground` and `MaterialTheme.colorScheme.background` for text colors. The `onBackground` is `Color.White` in our theme so these should be fine. But verify:

- `WelcomePage` line with `color = MaterialTheme.colorScheme.onBackground` → keep as-is (White)
- `AllSetPage` same → keep as-is

No changes needed here — the parent `AuroraBackground` provides the dark base and the `onBackground` is White. The `Surface` wrapper was already removed in the previous phase.

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit (if any changes)**

```
fix: verify onboarding text visibility
```

---

### Task 14: Final Build + Install + Verify

**Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Install on device**

Run: `./gradlew installDebug`
Expected: Installed on 1 device.

**Step 3: Visual verification checklist**

On device, verify:
- [ ] Home screen: glass hero card with accent divider line, White text visible
- [ ] Home screen: artist spotlight shows art at top, gradient fades to dark at bottom
- [ ] Home screen: top songs are flat rows with dividers, not cards
- [ ] Home screen: stat pills (Songs/Skips/Streak) are glass with visible labels
- [ ] Stats screen: pills and tabs use accent-tint selection (not dark primaryContainer)
- [ ] Stats screen: TimeStatsTab hero cards are glass with White text
- [ ] Stats screen: HourlyHeatmap uses vibrant accent color, all hour numbers visible
- [ ] Library screen: search field has glass styling
- [ ] Library screen: song/artist list items are glass cards with accent bar
- [ ] Song detail: 2x2 stat grid, no truncated labels
- [ ] Song detail: album art has colored glow behind it
- [ ] Artist detail: 2x2 stat grid, artist image has glow
- [ ] Settings: all text visible
- [ ] Onboarding: aurora shows through, all text visible
- [ ] Bottom nav: accent-colored selected item (unchanged from previous)
- [ ] Aurora glow is visibly stronger and wider than before

**Step 4: Commit all remaining changes**

```
chore: glass UI overhaul complete
```
