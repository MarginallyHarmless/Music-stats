package com.musicstats.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

private const val FADE_THROUGH_DURATION = 300
private const val DETAIL_DURATION = 300

// Fade through — Material 3 standard for peer destinations (bottom nav)
val FadeThroughEnter: EnterTransition =
    fadeIn(tween(FADE_THROUGH_DURATION / 2, delayMillis = FADE_THROUGH_DURATION / 2)) +
        scaleIn(tween(FADE_THROUGH_DURATION / 2, delayMillis = FADE_THROUGH_DURATION / 2), initialScale = 0.92f)

val FadeThroughExit: ExitTransition =
    fadeOut(tween(FADE_THROUGH_DURATION / 2))

// Shared z-axis — for drill-in detail screens
val DetailEnter: EnterTransition =
    fadeIn(tween(DETAIL_DURATION / 2, delayMillis = DETAIL_DURATION / 2)) +
        scaleIn(tween(DETAIL_DURATION), initialScale = 0.80f)

val DetailExit: ExitTransition =
    fadeOut(tween(DETAIL_DURATION / 2)) +
        scaleOut(tween(DETAIL_DURATION), targetScale = 1.1f)

val DetailPopEnter: EnterTransition =
    fadeIn(tween(DETAIL_DURATION / 2, delayMillis = DETAIL_DURATION / 2)) +
        scaleIn(tween(DETAIL_DURATION), initialScale = 1.1f)

val DetailPopExit: ExitTransition =
    fadeOut(tween(DETAIL_DURATION / 2)) +
        scaleOut(tween(DETAIL_DURATION), targetScale = 0.80f)
