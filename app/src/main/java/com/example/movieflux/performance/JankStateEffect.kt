package com.example.movieflux.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState

@Composable
fun JankStateEffect(vararg states: Pair<String, String>) {
    val view = LocalView.current
    DisposableEffect(states.toList()) {
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        states.forEach { (k, v) -> holder.state?.putState(k, v) }
        onDispose { states.forEach { (k, _) -> holder.state?.removeState(k) } }
    }
}
