package com.example.movieflux.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import timber.log.Timber

private const val MIN_DRAW_TIME_MS = 1f
private const val NANOS_PER_MS = 1_000_000f

@Composable
fun LogRecompositions(name: String) {
    val count = remember { mutableIntStateOf(0) }
    SideEffect {
        count.intValue++
        Timber.tag("[RECOMPOSE]").d("%s — recomposition #%d", name, count.intValue)
    }
}

// Measures how long draw takes for this composable each frame.
fun Modifier.logDrawTime(name: String): Modifier = drawWithContent {
    val start = System.nanoTime()
    drawContent()
    val durationMs = (System.nanoTime() - start) / NANOS_PER_MS
    if (durationMs > MIN_DRAW_TIME_MS) { // only log if draw took more than 1ms
        Timber.tag("[DRAW]").d("%s — draw=%.2fms", name, durationMs)
    }
}
