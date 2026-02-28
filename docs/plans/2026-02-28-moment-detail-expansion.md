# Moment Detail Expansion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the direct share-on-tap flow for moment cards with a bottom sheet that shows enriched moment detail and a Share button, add per-type stat lines to moments, and remove the noisy ARTIST_UNLOCKED card type.

**Architecture:** Add a nullable `statLine` field to `Moment` (DB migration 7→8), populate it at detection time in `MomentDetector`, create a `MomentDetailBottomSheet` composable, update `MomentShareCard` to show `statLine`, and rewire the tap handlers in `HomeScreen` and `AllMomentsScreen` to open the sheet instead of immediately sharing. Filter `ARTIST_UNLOCKED` out of all DAO queries.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3 `ModalBottomSheet`, Room migration, Hilt, existing `ShareCardRenderer` / `MomentShareCard`.

---

### Task 1: Add `statLine` to `Moment` entity and migrate DB 7→8

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/model/Moment.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`

**Step 1: Add field to `Moment`**

Open `Moment.kt`. The current last field is `artistId`. Add `statLine` after it:

```kotlin
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val entityKey: String,
    val triggeredAt: Long,
    val seenAt: Long? = null,
    val sharedAt: Long? = null,
    val title: String,
    val description: String,
    val songId: Long? = null,
    val artistId: Long? = null,
    val statLine: String? = null   // ← add this
)
```

**Step 2: Add `MIGRATION_7_8` to `MusicStatsDatabase`**

In `MusicStatsDatabase.kt`, bump `version` from `7` to `8` and add the migration in the `companion object` after `MIGRATION_6_7`:

```kotlin
@Database(
    entities = [Song::class, Artist::class, ListeningEvent::class, Moment::class],
    version = 8,   // ← bumped
    exportSchema = false
)
```

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE moments ADD COLUMN statLine TEXT DEFAULT NULL")
    }
}
```

**Step 3: Register `MIGRATION_7_8` in `DatabaseModule`**

In `DatabaseModule.kt`, add `MusicStatsDatabase.MIGRATION_7_8` to the `.addMigrations(...)` call:

```kotlin
.addMigrations(
    MusicStatsDatabase.MIGRATION_1_2,
    MusicStatsDatabase.MIGRATION_2_3,
    MusicStatsDatabase.MIGRATION_3_4,
    MusicStatsDatabase.MIGRATION_4_5,
    MusicStatsDatabase.MIGRATION_5_6,
    MusicStatsDatabase.MIGRATION_6_7,
    MusicStatsDatabase.MIGRATION_7_8,   // ← add
)
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/model/Moment.kt
git add app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt
git add app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt
git commit -m "feat: add statLine field to Moment + DB migration 7→8"
```

---

### Task 2: Update `MomentDetector` to populate `statLine` + remove `ARTIST_UNLOCKED`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Context:** `MomentDetector` is the source of truth for all moment creation. The `statLine` is a short computed fact added at detection time. Types that already have enough info in title/description get `statLine = null`. Only the most meaningful enrichments are added.

`formatDuration` lives at `com.musicstats.app.util.formatDuration` — import it.

**Step 1: Add import for `formatDuration`**

At the top of `MomentDetector.kt`, add:

```kotlin
import com.musicstats.app.util.formatDuration
```

**Step 2: Remove `detectArtistUnlocked` call and method**

In `detectAndPersistNewMoments()`, remove the line:
```kotlin
newMoments += detectArtistUnlocked(sevenDaysAgo, now)
```

Then delete the entire `detectArtistUnlocked` private method (lines ~347–361).

**Step 3: Populate `statLine` in `detectSongPlayMilestones`**

Inside the `persistIfNew(Moment(...))` call, add `statLine`:

```kotlin
persistIfNew(Moment(
    type = "SONG_PLAYS_$threshold",
    entityKey = "${song.songId}:$threshold",
    triggeredAt = System.currentTimeMillis(),
    title = "$threshold plays",
    description = "You've played ${song.title} $threshold times",
    songId = song.songId,
    statLine = "${formatDuration(song.totalDurationMs)} total"
))?.let { result += it }
```

**Step 4: Populate `statLine` in `detectArtistHourMilestones`**

```kotlin
persistIfNew(Moment(
    type = "ARTIST_HOURS_$hours",
    entityKey = "${artist.artist}:$hours",
    triggeredAt = System.currentTimeMillis(),
    title = "$humanHours of ${artist.artist}",
    description = "You've spent $humanHours listening to ${artist.artist}",
    artistId = artistEntity?.id,
    statLine = "${artist.playCount} total plays"
))?.let { result += it }
```

**Step 5: Populate `statLine` in `detectArchetypes`**

This function is the most complex. Add `statLine` to each archetype `Moment(...)` call. The variables `nightMs`, `totalMs`, `morningMs`, `commuteAmMs`, `commutePmMs`, `skipRate`, `deepCuts`, `topMs`, `allMs`, `newArtistsThisWeek` are all already computed in this function — use them.

Replace each archetype `persistIfNew` block as follows:

**NIGHT_OWL:**
```kotlin
val nightPct = (nightMs * 100 / totalMs).toInt()
persistIfNew(Moment(
    type = "ARCHETYPE_NIGHT_OWL", entityKey = yearMonth, triggeredAt = now,
    title = "Night Owl",
    description = "You do most of your listening after 10pm",
    statLine = "$nightPct% of your listening"
))?.let { result += it }
```

**MORNING_LISTENER:**
```kotlin
val morningPct = (morningMs * 100 / totalMs).toInt()
persistIfNew(Moment(
    type = "ARCHETYPE_MORNING_LISTENER", entityKey = yearMonth, triggeredAt = now,
    title = "Morning Listener",
    description = "You do most of your listening before 9am",
    statLine = "$morningPct% of your listening"
))?.let { result += it }
```

**COMMUTE_LISTENER:**
```kotlin
val commutePct = ((commuteAmMs + commutePmMs) * 100 / totalMs).toInt()
persistIfNew(Moment(
    type = "ARCHETYPE_COMMUTE_LISTENER", entityKey = yearMonth, triggeredAt = now,
    title = "Commute Listener",
    description = "Your listening peaks at 7–9am and 5–7pm",
    statLine = "$commutePct% during commute hours"
))?.let { result += it }
```

**COMPLETIONIST:**
```kotlin
val skipPct = "%.1f".format(skipRate * 100)
persistIfNew(Moment(
    type = "ARCHETYPE_COMPLETIONIST", entityKey = yearMonth, triggeredAt = now,
    title = "Completionist",
    description = "You skip less than 5% of songs — truly dedicated",
    statLine = "${skipPct}% skip rate"
))?.let { result += it }
```

**CERTIFIED_SKIPPER:**
```kotlin
val skipPct2 = "%.1f".format(skipRate * 100)
persistIfNew(Moment(
    type = "ARCHETYPE_CERTIFIED_SKIPPER", entityKey = yearMonth, triggeredAt = now,
    title = "Certified Skipper",
    description = "You skip more than 40% of songs. Nothing is good enough.",
    statLine = "${skipPct2}% skip rate"
))?.let { result += it }
```

Note: use a separate `val skipPct2` if `skipPct` is declared in the same scope, or restructure with `if/else` since COMPLETIONIST and CERTIFIED_SKIPPER are mutually exclusive — they are already in separate `if` blocks so `skipPct` can be reused with the same name in each block.

**DEEP_CUT_DIGGER:**
```kotlin
persistIfNew(Moment(
    type = "ARCHETYPE_DEEP_CUT_DIGGER", entityKey = yearMonth, triggeredAt = now,
    title = "Deep Cut Digger",
    description = "You've listened to ${deepCuts[0].title} over 50 times",
    statLine = "${deepCuts[0].playCount} plays"
))?.let { result += it }
```

**LOYAL_FAN:**
```kotlin
val topPct = (topMs * 100 / allMs).toInt()
persistIfNew(Moment(
    type = "ARCHETYPE_LOYAL_FAN", entityKey = yearMonth, triggeredAt = now,
    title = "Loyal Fan",
    description = "Over 50% of your listening is ${topArtists[0].artist}",
    artistId = artistEntity?.id,
    statLine = "$topPct% of your listening"
))?.let { result += it }
```

**EXPLORER:** leave `statLine = null` (description already captures the count).

**Step 6: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: populate statLine in MomentDetector, remove ARTIST_UNLOCKED detection"
```

---

### Task 3: Filter `ARTIST_UNLOCKED` from DAO queries

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt`

**Step 1: Update the two list queries**

`getAllMoments` and `getRecentMoments` both need a `WHERE type != 'ARTIST_UNLOCKED'` clause. Rewrite them:

```kotlin
@Query("SELECT * FROM moments WHERE type != 'ARTIST_UNLOCKED' ORDER BY triggeredAt DESC")
fun getAllMoments(): Flow<List<Moment>>

@Query("SELECT * FROM moments WHERE type != 'ARTIST_UNLOCKED' ORDER BY triggeredAt DESC LIMIT :limit")
fun getRecentMoments(limit: Int): Flow<List<Moment>>
```

Leave `getUnseenMoments` and `getUnseenCount` unchanged — they implicitly exclude already-seen cards, and unseen ARTIST_UNLOCKED cards will disappear on next app open once the filter is in place.

Actually, also update `getUnseenCount` to exclude ARTIST_UNLOCKED so the badge count is accurate:

```kotlin
@Query("SELECT COUNT(*) FROM moments WHERE seenAt IS NULL AND type != 'ARTIST_UNLOCKED'")
fun getUnseenCount(): Flow<Int>
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt
git commit -m "feat: filter ARTIST_UNLOCKED from moment queries"
```

---

### Task 4: Create `MomentDetailBottomSheet`

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/moments/MomentDetailBottomSheet.kt`

**Step 1: Create the file**

```kotlin
package com.musicstats.app.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicstats.app.data.model.Moment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentDetailBottomSheet(
    moment: Moment,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val isArchetype = moment.type.startsWith("ARCHETYPE_")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isArchetype) {
                Text(
                    text = momentEmoji(moment.type),
                    fontSize = 48.sp
                )
            }

            Text(
                text = moment.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = moment.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (moment.statLine != null) {
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = moment.statLine,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }
    }
}

private fun momentEmoji(type: String): String = when {
    type.contains("NIGHT_OWL") -> "🌙"
    type.contains("MORNING") -> "☀️"
    type.contains("COMMUTE") -> "🎧"
    type.contains("COMPLETIONIST") -> "✅"
    type.contains("SKIPPER") -> "⏭️"
    type.contains("DEEP_CUT") -> "💿"
    type.contains("LOYAL_FAN") -> "❤️"
    type.contains("EXPLORER") -> "🧭"
    else -> "🎵"
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/MomentDetailBottomSheet.kt
git commit -m "feat: add MomentDetailBottomSheet"
```

---

### Task 5: Update `MomentShareCard` to show `statLine`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt`

**Step 1: Add `statLine` display**

In `MomentShareCard`, `statLine` comes from `moment.statLine`. Add it between the description and the watermark. Find the description `Text(...)` block (around line 99) and add after the closing `}` of the hero `Column`:

Current hero section:
```kotlin
Column {
    if (isArchetype) { ... }
    Text(text = moment.title, ...)
    Spacer(Modifier.height(12.dp))
    Text(text = moment.description, ...)
}
```

Replace the hero `Column` with:
```kotlin
Column {
    if (isArchetype) {
        Text(
            text = archetypeEmoji(moment.type),
            fontSize = 72.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
    Text(
        text = moment.title,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        lineHeight = 52.sp
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = moment.description,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
    if (moment.statLine != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = moment.statLine,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt
git commit -m "feat: show statLine on MomentShareCard"
```

---

### Task 6: Wire `MomentDetailBottomSheet` into `HomeScreen`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Context:** Currently, tapping a moment card in `HomeScreen` sets `showMomentShareSheet = true`, which triggers an inline `if (showMomentShareSheet)` block that immediately renders the share bitmap. We need to replace this with showing `MomentDetailBottomSheet`, and move the share rendering into the `onShare` lambda.

**Step 1: Add import**

Add at the top:
```kotlin
import com.musicstats.app.ui.moments.MomentDetailBottomSheet
```

**Step 2: Rename the state variable**

Find:
```kotlin
var showMomentShareSheet by remember { mutableStateOf(false) }
```

Replace with:
```kotlin
var showMomentDetail by remember { mutableStateOf(false) }
```

**Step 3: Update the `onMomentTap` handler**

Find the `MomentsStrip(...)` call and update its `onMomentTap`:

```kotlin
onMomentTap = { moment ->
    viewModel.markMomentSeen(moment.id)
    selectedMoment = moment
    showMomentDetail = true
},
```

**Step 4: Replace the share sheet block**

Find the `if (showMomentShareSheet)` block (around lines 540–556) and replace the entire block with:

```kotlin
if (showMomentDetail) {
    selectedMoment?.let { moment ->
        MomentDetailBottomSheet(
            moment = moment,
            onShare = {
                val density = context.resources.displayMetrics.density
                val w = (360 * density).toInt()
                val h = (640 * density).toInt()
                ShareCardRenderer.renderComposable(context, w, h, {
                    MusicStatsTheme {
                        MomentShareCard(moment = moment)
                    }
                }) { bitmap ->
                    ShareCardRenderer.shareBitmap(context, bitmap)
                    viewModel.markMomentShared(moment.id)
                }
                showMomentDetail = false
                selectedMoment = null
            },
            onDismiss = {
                showMomentDetail = false
                selectedMoment = null
            }
        )
    }
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt
git commit -m "feat: open MomentDetailBottomSheet on moment tap in HomeScreen"
```

---

### Task 7: Wire `MomentDetailBottomSheet` into `AllMomentsScreen`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsScreen.kt`

**Context:** Same pattern as Task 6. Currently `AllMomentsScreen` sets `showShareSheet = true` on tap which triggers the share render inline.

**Step 1: Add import**

`MomentDetailBottomSheet` is in the same package (`ui.moments`), so no import needed.

**Step 2: Rename state variable**

Find:
```kotlin
var showShareSheet by remember { mutableStateOf(false) }
```

Replace with:
```kotlin
var showMomentDetail by remember { mutableStateOf(false) }
```

**Step 3: Update `MomentListCard` tap handler**

In the `LazyColumn` items block, find the `onTap` lambda and update it:

```kotlin
onTap = {
    viewModel.markSeen(moment.id)
    selectedMoment = moment
    showMomentDetail = true
}
```

**Step 4: Replace the share block**

Find the `if (showShareSheet)` block and replace entirely with:

```kotlin
if (showMomentDetail) {
    selectedMoment?.let { moment ->
        MomentDetailBottomSheet(
            moment = moment,
            onShare = {
                val density = context.resources.displayMetrics.density
                val w = (360 * density).toInt()
                val h = (640 * density).toInt()
                ShareCardRenderer.renderComposable(context, w, h, {
                    MusicStatsTheme {
                        MomentShareCard(moment = moment)
                    }
                }) { bitmap ->
                    ShareCardRenderer.shareBitmap(context, bitmap)
                    viewModel.markShared(moment.id)
                }
                showMomentDetail = false
                selectedMoment = null
            },
            onDismiss = {
                showMomentDetail = false
                selectedMoment = null
            }
        )
    }
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsScreen.kt
git commit -m "feat: open MomentDetailBottomSheet on moment tap in AllMomentsScreen"
```

---

### Task 8: Build and verify

**Step 1: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

**Step 2: Install**

```bash
./gradlew installDebug
```

**Step 3: Manual verification checklist**

1. App opens without crash — DB migrated cleanly
2. Home screen moments strip shows no `ARTIST_UNLOCKED` cards
3. All Moments screen shows no `ARTIST_UNLOCKED` cards
4. Tapping a moment card opens the bottom sheet (not the share dialog)
5. Bottom sheet shows: emoji (for archetypes), title, description, stat pill (for types that have `statLine`)
6. Tapping Share in the bottom sheet renders and opens the system share sheet
7. The shared image includes `statLine` text below the description for enriched cards
8. Dismissing the bottom sheet (swipe down) returns to the list without sharing
