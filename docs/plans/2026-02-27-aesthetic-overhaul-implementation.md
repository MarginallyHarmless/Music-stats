# Aesthetic Overhaul Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Full visual redesign of every screen with editorial typography, gradient accents, generous spacing, and a share card system for social media.

**Architecture:** Layer custom Inter font family and accent gradients on top of existing M3 dynamic colors. Rewrite each screen composable with new layout patterns. Add a share card rendering system using `drawToBitmap` on offscreen composables. No data layer changes needed — only UI/theme.

**Tech Stack:** Jetpack Compose, Material 3, Vico 2.x, Coil 3, existing Room/Hilt stack unchanged.

---

### Task 1: Add Inter font and update theme

**Files:**
- Create: `app/src/main/res/font/inter_variable.ttf` (download)
- Create: `app/src/main/java/com/musicstats/app/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/theme/Theme.kt`

**Step 1: Download Inter font**

Download Inter variable font TTF and place it at `app/src/main/res/font/inter_variable.ttf`. The `res/font/` directory doesn't exist yet — create it.

Run:
```bash
mkdir -p app/src/main/res/font
curl -L -o app/src/main/res/font/inter_variable.ttf "https://github.com/rsms/inter/raw/master/extras/ttf/Inter-Regular.ttf"
curl -L -o app/src/main/res/font/inter_medium.ttf "https://github.com/rsms/inter/raw/master/extras/ttf/Inter-Medium.ttf"
curl -L -o app/src/main/res/font/inter_semibold.ttf "https://github.com/rsms/inter/raw/master/extras/ttf/Inter-SemiBold.ttf"
curl -L -o app/src/main/res/font/inter_bold.ttf "https://github.com/rsms/inter/raw/master/extras/ttf/Inter-Bold.ttf"
```

**Step 2: Create Type.kt**

```kotlin
package com.musicstats.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.musicstats.app.R

val InterFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val MusicStatsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

**Step 3: Update Theme.kt**

Add typography and shapes to the theme:

```kotlin
package com.musicstats.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val MusicStatsShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MusicStatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MusicStatsTypography,
        shapes = MusicStatsShapes,
        content = content
    )
}
```

**Step 4: Build to verify fonts compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — font resources found, Type.kt compiles, theme applies.

**Step 5: Commit**

```bash
git add app/src/main/res/font/ app/src/main/java/com/musicstats/app/ui/theme/
git commit -m "Add Inter font family and custom typography/shapes to theme"
```

---

### Task 2: Create shared UI primitives (GradientCard, PillChip, SectionHeader, etc.)

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/components/GradientCard.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/components/PillChip.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/components/SectionHeader.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/components/AppPillTabs.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/components/StatCard.kt`

**Step 1: Create GradientCard.kt**

A card with a diagonal gradient background using M3 primary→tertiary colors.

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    startColor: Color = MaterialTheme.colorScheme.primaryContainer,
    endColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(startColor, endColor),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .padding(24.dp),
        content = content
    )
}
```

**Step 2: Create PillChip.kt**

A fully rounded filter chip with optional gradient fill when selected.

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

@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
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

**Step 3: Create SectionHeader.kt**

Uppercase tracked section label used throughout the app.

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
```

**Step 4: Create AppPillTabs.kt**

Custom pill-shaped tab row replacing the default M3 TabRow.

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppPillTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

**Step 5: Redesign StatCard.kt**

Update the existing StatCard with new shape language and optional tint color for alternating backgrounds on detail pages.

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.unit.sp

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

**Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/
git commit -m "Add shared UI primitives: GradientCard, PillChip, SectionHeader, AppPillTabs; redesign StatCard"
```

---

### Task 3: Redesign HomeScreen

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt`

**Step 1: Add greeting helper to HomeViewModel**

Add a `greeting` property that returns time-based text and a formatted date string:

```kotlin
// Add to HomeViewModel
val greeting: String
    get() {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

val todayFormatted: String
    get() {
        val today = java.time.LocalDate.now()
        return today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
```

**Step 2: Rewrite HomeScreen.kt**

Full rewrite with new layout. The composable now includes:
1. Greeting header (no card, editorial text)
2. Hero gradient card with big listening time
3. Notification warning banner (keep existing, restyle into the new design language)
4. Today's stat pills row
5. Top artist spotlight with blurred background image
6. Weekly chart with SectionHeader
7. Top songs list with album art on every row
8. Share FAB (placeholder — implemented in Task 8)

Key patterns:
- Use `GradientCard` for the hero
- Use `SectionHeader` for section labels
- 24dp horizontal padding throughout
- 20dp vertical spacing between sections
- Top songs become subtle cards, not plain rows
- Top artist card uses `Box` with background `AsyncImage` (blurred + scrim overlay)
- Share FAB with `Icons.Default.Share`

Copy the notification listener check from the existing code (the `isNotificationListenerEnabled` function and the `LaunchedEffect` lifecycle check stay the same, just restyle the banner to use rounded shape and match the new design).

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/
git commit -m "Redesign HomeScreen with editorial layout, gradient hero, artist spotlight"
```

---

### Task 4: Redesign StatsScreen + all 3 tabs

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/stats/StatsScreen.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/stats/TimeStatsTab.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/stats/DiscoveryStatsTab.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/stats/TopListsTab.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/components/HourlyHeatmap.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/components/ListeningTimeChart.kt`

**Step 1: Redesign StatsScreen.kt shell**

- Replace `TabRow` with `AppPillTabs`
- Replace `FilterChip` time range selectors with `PillChip` in a `Row`
- Add "Stats" title in `headlineMedium` at top
- 24dp horizontal padding

**Step 2: Redesign TimeStatsTab.kt**

- Two side-by-side `GradientCard` hero cards (total time, total plays) with `displaySmall` numbers
- Three `StatCard` pills in a row (avg session, longest, skips) — no icons, just numbers + labels
- Refined `HourlyHeatmap`: 44dp cells, 8dp corners, primaryContainer gradient scale
- Refined chart: SectionHeader labels
- 24dp padding, 20dp vertical spacing

**Step 3: Redesign DiscoveryStatsTab.kt**

- Hero `GradientCard` for new songs discovered with `displaySmall` number
- 2x2 grid (two `Row`s of two `StatCard`s) for new artists, unique songs, unique artists, deep cuts count
- Deep cuts list with album art (48dp) on each row
- SectionHeader for "Deep Cuts"

**Step 4: Redesign TopListsTab.kt**

- Replace `FilterChip` metric toggle with two `PillChip`s
- Top songs: each row is a subtle `ElevatedCard` with album art (48dp), ranking number in `titleLarge` primary, title/artist, metric value. Top 3 get `headlineSmall` rank and 56dp art.
- Top artists: circular artist images via `AsyncImage` with `Icons.Default.Person` fallback. Top 3 get 64dp images, rest get 40dp.
- Need artist images in the data — add `imageUrl` to `ArtistPlayStats` query or join with `artists` table.

**Step 5: Update HourlyHeatmap.kt**

- Cell size 44dp (up from 40dp)
- Corner radius 8dp (up from 6dp)
- Use `primaryContainer` color range instead of raw primary alpha
- Add subtle legend strip beneath (low → high gradient bar with "Less" / "More" labels)

**Step 6: Update ListeningTimeChart.kt (optional polish)**

- If Vico supports rounded bar caps and gradient column fills, apply them
- Otherwise keep current — the chart is already functional

**Step 7: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/stats/ app/src/main/java/com/musicstats/app/ui/components/
git commit -m "Redesign Stats screen with gradient heroes, pill tabs, refined heatmap and lists"
```

---

### Task 5: Redesign LibraryScreen

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/LibraryScreen.kt`

**Step 1: Rewrite LibraryScreen.kt**

- Replace `TabRow` with `AppPillTabs` for Songs/Artists
- Replace `OutlinedTextField` search with filled rounded search bar: `surfaceVariant` background, no border, `RoundedCornerShape(28.dp)`, `Icons.Default.Search` leading icon
- Replace `FilterChip` sort chips with `PillChip` components
- `SongListItem`: wrap in subtle `ElevatedCard` with `surfaceVariant.copy(alpha=0.3f)`, 16dp vertical padding, thin primary accent `drawBehind` line on left edge (4dp wide, rounded)
- `ArtistListItem`: same card treatment, circular image, add song count label if available (or skip if not in data)
- "Library" title in `headlineMedium`
- 24dp horizontal padding
- 12dp spacing between list cards

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/library/LibraryScreen.kt
git commit -m "Redesign Library screen with pill tabs, rounded search bar, card-style list items"
```

---

### Task 6: Redesign SongDetailScreen + ArtistDetailScreen

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/library/ArtistDetailScreen.kt`

**Step 1: Redesign SongDetailScreen.kt**

- Album art 200dp (up from 160dp) centered, with `Modifier.shadow(8.dp, RoundedCornerShape(16.dp))`
- Title in `headlineMedium`, artist in `titleMedium` onSurfaceVariant, album in `bodyMedium`
- 4 stat boxes with alternating tints:
  - Plays: `primaryContainer.copy(alpha = 0.5f)`
  - Total Time: `secondaryContainer.copy(alpha = 0.5f)`
  - Skips: `tertiaryContainer.copy(alpha = 0.5f)`
  - Skip Rate: `surfaceVariant.copy(alpha = 0.5f)`
- Each stat box uses `headlineSmall` for value, `labelSmall` uppercase tracked for label
- History section: `SectionHeader("Listening History")`
- Each event row: source app as a small colored pill (`PillChip`-like badge), formatted date, duration
- 24dp padding, 20dp spacing
- Add share icon at top (`Icons.Default.Share`) — placeholder for Task 8

**Step 2: Redesign ArtistDetailScreen.kt**

- Artist image 200dp circular (up from 160dp), centered, with subtle shadow
- `Icons.Default.Person` fallback at 200dp if no image
- Artist name in `headlineMedium` centered
- Same 4 alternating-tint stat boxes as song detail
- History rows show album art mini-thumbnail (32dp rounded) + song title + source app pill + duration
- `SectionHeader("Listening History")`
- 24dp padding, 20dp spacing
- Add share icon at top — placeholder for Task 8

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt app/src/main/java/com/musicstats/app/ui/library/ArtistDetailScreen.kt
git commit -m "Redesign Song and Artist detail screens with larger art, alternating stat tints, editorial layout"
```

---

### Task 7: Polish Settings screen + BottomNavBar

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/navigation/BottomNavBar.kt`

**Step 1: Polish SettingsScreen.kt**

Light touch — match the new design language:
- 24dp horizontal padding
- Section headers become `SectionHeader` composable
- Setting rows get 16dp corners on cards
- Pill-shaped buttons

**Step 2: Polish BottomNavBar.kt**

- If using standard `NavigationBar`, keep it — M3 bottom nav is already good
- Optional: make the selected indicator use a pill shape if not already

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/settings/ app/src/main/java/com/musicstats/app/ui/navigation/
git commit -m "Polish Settings and BottomNav to match new design language"
```

---

### Task 8: Share card system

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/share/ShareCardRenderer.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/share/ShareCards.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/share/ShareBottomSheet.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt` (wire FAB)
- Modify: `app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt` (wire share button)
- Modify: `app/src/main/java/com/musicstats/app/ui/library/ArtistDetailScreen.kt` (wire share button)

**Step 1: Create ShareCardRenderer.kt**

Utility that renders a composable to a bitmap and triggers Android share intent:

```kotlin
package com.musicstats.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import java.io.File

object ShareCardRenderer {

    fun renderAndShare(
        context: Context,
        width: Int = 1080,
        height: Int = 1920,
        content: @Composable () -> Unit
    ) {
        // Create a ComposeView, measure/layout/draw it offscreen, drawToBitmap, share
        // Implementation uses ComposeView + ViewTreeLifecycleOwner approach
        // or the simpler approach of saving composable state to a Canvas
    }

    fun shareBitmap(context: Context, bitmap: Bitmap) {
        val file = File(context.cacheDir, "share_card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your stats"))
    }
}
```

Note: Need to register a `FileProvider` in AndroidManifest.xml and create `res/xml/file_paths.xml`. Check if these already exist; if not, create them.

**Step 2: Create ShareCards.kt**

Composables that render the styled share card content (not interactive — pure visual):

- `DailyRecapCard(listeningTime, topArtist, topSongs)` — gradient background, large time number, top artist + top 3 songs, "Music Stats" watermark
- `SongSpotlightCard(title, artist, albumArtUrl, playCount, totalTime)` — album art large, stats overlay
- `ArtistSpotlightCard(name, imageUrl, playCount, totalTime)` — artist image, stats

Each card is a fixed-size `Box` (1080×1920dp scaled or 360×640dp) with gradient background, bold typography, and the app watermark.

**Step 3: Create ShareBottomSheet.kt**

A `ModalBottomSheet` listing available card types:
- "Daily Recap" — generates DailyRecapCard
- "Song Spotlight" — only available from song detail
- "Artist Spotlight" — only available from artist detail

Each option shows a small preview thumbnail and label.

**Step 4: Wire FAB on HomeScreen**

Add `FloatingActionButton` with `Icons.Default.Share` in the `Scaffold` (or as an overlay at the bottom of the scroll). Tapping opens `ShareBottomSheet` → selecting "Daily Recap" renders and shares.

**Step 5: Wire share buttons on detail screens**

Add a `IconButton(onClick = { /* render spotlight card */ })` with `Icons.Default.Share` to both detail screens. Directly renders the appropriate spotlight card (no bottom sheet needed — there's only one option per detail screen).

**Step 6: Register FileProvider**

If not already in AndroidManifest:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="share_cards" path="/" />
</paths>
```

**Step 7: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/share/ app/src/main/java/com/musicstats/app/ui/home/ app/src/main/java/com/musicstats/app/ui/library/ app/src/main/res/xml/
git commit -m "Add share card system with daily recap, song and artist spotlight cards"
```

---

### Task 9: Final integration test + push

**Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Install and manual test**

Run: `./gradlew installDebug`

Manual checks:
- [ ] Inter font visible across all screens
- [ ] Gradient hero cards render on Home and Stats
- [ ] Pill tabs work on Library and Stats
- [ ] Rounded search bar on Library
- [ ] Artist spotlight on Home shows blurred image background
- [ ] Song detail has 200dp art, alternating stat tints
- [ ] Artist detail has 200dp circular art, alternating stat tints
- [ ] Heatmap has larger cells and legend
- [ ] Share FAB on Home → bottom sheet → generates card → share intent
- [ ] Share button on song detail → generates song spotlight card
- [ ] Share button on artist detail → generates artist spotlight card
- [ ] Settings matches new spacing/shapes
- [ ] No visual regressions on dark mode

**Step 3: Commit any fixes and push**

```bash
git push
```
