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
import androidx.compose.ui.platform.LocalDensity
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

    val entries = remember(dailyData) {
        val today = LocalDate.now()
        val dayMap = dailyData.associate { it.day to it.totalDurationMs }
        (6 downTo 0).map { daysBack ->
            val date = today.minusDays(daysBack.toLong())
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val ms = dayMap[date.toString()] ?: 0L
            val isToday = daysBack == 0
            Triple(label, ms, isToday)
        }
    }

    val maxMs = remember(entries) { entries.maxOfOrNull { it.second }?.takeIf { it > 0L } ?: 1L }

    val labelTextSizePx = with(LocalDensity.current) { 10.sp.toPx() }
    val paint = remember(labelColor, labelTextSizePx) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val labelAreaHeight = labelTextSizePx + 8.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        val minBarHeight = 3.dp.toPx()
        val cornerRadius = 6.dp.toPx()
        val totalGap = 8.dp.toPx() * 6
        val barWidth = (size.width - totalGap) / 7f
        val gapWidth = 8.dp.toPx()

        entries.forEachIndexed { index, (label, ms, isToday) ->
            val left = index * (barWidth + gapWidth)
            val centerX = left + barWidth / 2f

            drawContext.canvas.nativeCanvas.drawText(
                label,
                centerX,
                size.height - 2.dp.toPx(),
                paint
            )

            val fraction = if (maxMs > 0) ms.toFloat() / maxMs.toFloat() else 0f
            val barHeight = if (ms == 0L) {
                minBarHeight
            } else {
                (fraction * chartHeight).coerceAtLeast(minBarHeight)
            }
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
