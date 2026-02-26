package com.musicstats.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.musicstats.app.data.dao.DailyListening
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ListeningTimeChart(dailyData: List<DailyListening>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val sortedData = remember(dailyData) {
        val today = LocalDate.now()
        val dayMap = dailyData.associate { it.day to it.totalDurationMs }
        (6 downTo 0).map { daysBack ->
            val date = today.minusDays(daysBack.toLong())
            val key = date.toString()
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val hours = (dayMap[key] ?: 0L) / 3_600_000.0
            label to hours
        }
    }

    LaunchedEffect(sortedData) {
        modelProducer.runTransaction {
            columnSeries {
                series(sortedData.map { it.second })
            }
        }
    }

    val dayLabels = sortedData.map { it.first }

    val bottomAxisValueFormatter = CartesianValueFormatter { context, value, position ->
        dayLabels.getOrElse(value.toInt()) { "" }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisValueFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}
