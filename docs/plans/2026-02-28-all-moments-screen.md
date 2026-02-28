# All Moments Screen Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire the "See all" button in the home screen Moments strip to a new full-list All Moments screen.

**Architecture:** Add a `"moments"` route to `NavGraph`, create `AllMomentsViewModel` backed by `MomentsRepository.getAllMoments()`, and create `AllMomentsScreen` with a `LazyColumn` of moment cards and the same share flow as the home screen. `HomeScreen` gets a new `onSeeAllMoments` param wired through `NavGraph`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room/Flow, existing `MomentsRepository`, `MomentShareCard`, `ShareCardRenderer`.

---

### Task 1: Add `getAllMoments` to `MomentsRepository`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/repository/MomentsRepository.kt`

Note: `MomentDao.getAllMoments()` already exists. We just need to expose it from the repository.

**Step 1: Add the method**

Open `MomentsRepository.kt` and add after the existing `getRecentMoments` method:

```kotlin
fun getAllMoments(): Flow<List<Moment>> = momentDao.getAllMoments()
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/repository/MomentsRepository.kt
git commit -m "feat: expose getAllMoments in MomentsRepository"
```

---

### Task 2: Create `AllMomentsViewModel`

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt`

**Step 1: Create the file**

```kotlin
package com.musicstats.app.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.repository.MomentsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllMomentsViewModel @Inject constructor(
    private val momentsRepository: MomentsRepository
) : ViewModel() {

    val moments: StateFlow<List<Moment>> =
        momentsRepository.getAllMoments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markSeen(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            momentsRepository.markSeen(id)
        }
    }

    fun markShared(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            momentsRepository.markShared(id)
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt
git commit -m "feat: add AllMomentsViewModel"
```

---

### Task 3: Create `AllMomentsScreen`

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsScreen.kt`

**Step 1: Create the file**

```kotlin
package com.musicstats.app.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.Moment
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.MomentShareCard
import com.musicstats.app.ui.share.ShareCardRenderer
import com.musicstats.app.ui.theme.MusicStatsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AllMomentsScreen(
    viewModel: AllMomentsViewModel = hiltViewModel()
) {
    val moments by viewModel.moments.collectAsState()
    val context = LocalContext.current
    var selectedMoment by remember { mutableStateOf<Moment?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Moments",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            if (moments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No moments yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(moments, key = { it.id }) { moment ->
                        MomentListCard(
                            moment = moment,
                            onTap = {
                                viewModel.markSeen(moment.id)
                                selectedMoment = moment
                                showShareSheet = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showShareSheet) {
        selectedMoment?.let { moment ->
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
            showShareSheet = false
            selectedMoment = null
        }
    }
}

@Composable
private fun MomentListCard(
    moment: Moment,
    onTap: () -> Unit
) {
    val isUnseen = moment.seenAt == null
    val isArchetype = moment.type.startsWith("ARCHETYPE_")
    val dateStr = remember(moment.triggeredAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(moment.triggeredAt))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
            .clickable(onClick = onTap)
            .then(
                if (isUnseen) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isArchetype) {
                Text(archetypeEmoji(moment.type), fontSize = 28.sp)
            } else {
                Text("🎵", fontSize = 28.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = moment.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
            if (isUnseen) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun archetypeEmoji(type: String): String = when {
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
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsScreen.kt
git commit -m "feat: add AllMomentsScreen with full moments list"
```

---

### Task 4: Add `onSeeAllMoments` param to `HomeScreen` and wire it

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Add the parameter**

In `HomeScreen.kt`, change the function signature from:

```kotlin
fun HomeScreen(
    onSongClick: (Long) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
)
```

to:

```kotlin
fun HomeScreen(
    onSongClick: (Long) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSeeAllMoments: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
)
```

**Step 2: Pass it to `MomentsStrip`**

Find the `MomentsStrip(...)` call at line ~399 and replace:

```kotlin
onSeeAll = { /* TODO: future - navigate to full moments list */ }
```

with:

```kotlin
onSeeAll = onSeeAllMoments
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt
git commit -m "feat: add onSeeAllMoments param to HomeScreen"
```

---

### Task 5: Register `"moments"` route in `NavGraph` and wire navigation

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/navigation/NavGraph.kt`

**Step 1: Add the import**

Add at the top of the import block:

```kotlin
import com.musicstats.app.ui.moments.AllMomentsScreen
```

**Step 2: Update `HomeScreen` call to pass `onSeeAllMoments`**

Find the existing `composable("home")` block:

```kotlin
composable("home") {
    HomeScreen(
        onSongClick = { songId -> navController.navigate("song/$songId") },
        onArtistClick = { artistName -> navController.navigate("artist/${Uri.encode(artistName)}") }
    )
}
```

Replace with:

```kotlin
composable("home") {
    HomeScreen(
        onSongClick = { songId -> navController.navigate("song/$songId") },
        onArtistClick = { artistName -> navController.navigate("artist/${Uri.encode(artistName)}") },
        onSeeAllMoments = { navController.navigate("moments") }
    )
}
```

**Step 3: Add the `"moments"` composable route**

Add this block after the `composable("home")` block:

```kotlin
composable("moments") {
    AllMomentsScreen()
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/navigation/NavGraph.kt
git commit -m "feat: add moments route and wire See All navigation"
```

---

### Task 6: Build and verify

**Step 1: Build the debug APK**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

**Step 2: Install and manually test**

```bash
./gradlew installDebug
```

Verify:
1. Home screen shows Moments strip
2. Tapping "See all" navigates to All Moments screen
3. All moments are listed with title, description, date, emoji, unseen dot where applicable
4. Tapping a moment card triggers share sheet and marks it seen
5. System back returns to home screen
