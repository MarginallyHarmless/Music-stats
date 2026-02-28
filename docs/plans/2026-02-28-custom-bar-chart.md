# Custom Canvas Bar Chart Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the unstyled Vico bar chart on the Home screen with a custom Canvas-drawn bar chart that matches the app's dark glass aesthetic.

**Architecture:** A new `WeeklyBarChart` composable draws 7 rounded bars directly on a `Canvas`, normalised to the tallest day, using the dynamic `palette.accent` colour. The existing `ListeningTimeChart.kt` file is replaced. The Vico dependency is unchanged (still used on the Stats tab).

**Tech Stack:** Kotlin, Jetpack Compose `Canvas`, `DrawScope`, `LocalAlbumPalette`

---

### Task 1: Replace ListeningTimeChart with custom Canvas implementation

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/ListeningTimeChart.kt`

**Step 1: Rewrite the file**

Replace the entire file content with:

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicstats.app.data.dao.DailyListening
import com.musicstats.app.ui.theme.LocalAlbumPalette
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ListeningTimeChart(dailyData: List<DailyListening>, modifier: Modifier = Modifier) {
    val palette = LocalAlbumPalette.current
    val accentColor = palette.accent
    val labelColor = Color.White.copy(alpha = 0.5f)

    val today = LocalDate.now()

    val entries = remember(dailyData) {
        val dayMap = dailyData.associate { it.day to it.totalDurationMs }
        (6 downTo 0).map { daysBack ->
            val date = today.minusDays(daysBack.toLong())
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val ms = dayMap[date.toString()] ?: 0L
            val isToday = daysBack == 0
            Triple(label, ms, isToday)
        }
    }

    val maxMs = remember(entries) { entries.maxOfOrNull { it.second } ?: 1L }

    Canvas(modifier = modifier) {
        val labelTextSizePx = 10.sp.toPx()
        val labelAreaHeight = labelTextSizePx + 8.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        val minBarHeight = 3.dp.toPx()
        val cornerRadius = 6.dp.toPx()
        val totalGap = 8.dp.toPx() * 6 // 6 gaps between 7 bars
        val barWidth = (size.width - totalGap) / 7f
        val gapWidth = 8.dp.toPx()

        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        entries.forEachIndexed { index, (label, ms, isToday) ->
            val left = index * (barWidth + gapWidth)
            val centerX = left + barWidth / 2f

            // Draw label
            drawContext.canvas.nativeCanvas.drawText(
                label,
                centerX,
                size.height - 2.dp.toPx(),
                paint
            )

            // Compute bar height
            val fraction = if (maxMs > 0) ms.toFloat() / maxMs.toFloat() else 0f
            val barHeight = (fraction * chartHeight).coerceAtLeast(minBarHeight)
            val top = chartHeight - barHeight

            val color = when {
                ms == 0L -> Color.White.copy(alpha = 0.07f)
                isToday -> accentColor
                else -> accentColor.copy(alpha = 0.6f)
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        }
    }
}
```

**Step 2: Build to verify it compiles**

```bash
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL with no errors.

**Step 3: Install and visually verify**

```bash
./gradlew installDebug
```

Check the Home screen "This Week" section shows 7 rounded bars with day labels, accent colour, and a dimmer style for past days vs today.

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/ListeningTimeChart.kt
git commit -m "Replace Vico bar chart with custom Canvas rounded bar chart"
```
