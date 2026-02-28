package com.musicstats.app.ui.moments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.Moment
import com.musicstats.app.ui.components.AppPillTabs
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.MomentCard
import com.musicstats.app.ui.components.MomentShareCard
import com.musicstats.app.ui.share.ShareCardRenderer
import com.musicstats.app.ui.theme.MusicStatsTheme

@Composable
fun AllMomentsScreen(
    viewModel: AllMomentsViewModel = hiltViewModel()
) {
    val moments by viewModel.moments.collectAsState()
    val previewMoments by viewModel.previewMoments.collectAsState()
    val context = LocalContext.current
    var selectedMoment by remember { mutableStateOf<Moment?>(null) }
    var showMomentDetail by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val displayMoments = if (selectedTab == 1) previewMoments else moments

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

            AppPillTabs(
                tabs = listOf("All", "Preview"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (displayMoments.isEmpty()) {
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
                    items(displayMoments, key = { it.id }) { moment ->
                        MomentCard(
                            moment = moment,
                            onTap = {
                                if (selectedTab == 0) viewModel.markSeen(moment.id)
                                selectedMoment = moment
                                showMomentDetail = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

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
                            MomentShareCard(moment = moment, imageUrl = moment.imageUrl)
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
}
