package com.example.movieflux.performance

import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.example.movieflux.analytics.AnalyticsTracker
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JankReporter @Inject constructor(
    private val tracker: AnalyticsTracker
) : JankStats.OnFrameListener {

    override fun onFrame(frameData: FrameData) {
        if (!frameData.isJank) return
        val durationMs = frameData.frameDurationUiNanos / 1_000_000f
        val states = frameData.states.joinToString { "${it.key}=${it.value}" }
        Timber.tag("[JANK]").w("frame=%.1fms states=[%s]", durationMs, states)
        val statesMap = frameData.states.associate { it.key to it.value }
        tracker.trackEvent(
            "frame_jank",
            statesMap + mapOf(
                "duration_ms" to frameData.frameDurationUiNanos / 1_000_000L,
                "is_jank" to true
            )
        )
    }
}
