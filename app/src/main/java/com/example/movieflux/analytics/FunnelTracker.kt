package com.example.movieflux.analytics

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FunnelTracker @Inject constructor(private val tracker: AnalyticsTracker) {

    private val startTimes = mutableMapOf<String, Long>()
    private val stepTimes = mutableMapOf<String, Long>()

    fun start(flow: String) {
        val now = System.currentTimeMillis()
        startTimes[flow] = now
        stepTimes[flow] = now
    }

    fun step(flow: String, step: String) {
        val now = System.currentTimeMillis()
        val start = startTimes[flow] ?: return
        val prev = stepTimes[flow] ?: start
        stepTimes[flow] = now
        tracker.trackEvent("funnel_step", mapOf(
            "flow" to flow,
            "step" to step,
            "ms_since_start" to (now - start),
            "ms_since_previous_step" to (now - prev)
        ))
    }

    fun complete(flow: String) {
        val now = System.currentTimeMillis()
        val start = startTimes.remove(flow) ?: return
        stepTimes.remove(flow)
        tracker.trackEvent("funnel_complete", mapOf(
            "flow" to flow,
            "total_ms" to (now - start)
        ))
    }

    fun abandon(flow: String, reason: String) {
        val now = System.currentTimeMillis()
        val start = startTimes.remove(flow) ?: return
        stepTimes.remove(flow)
        tracker.trackEvent("funnel_abandoned", mapOf(
            "flow" to flow,
            "reason" to reason,
            "total_ms" to (now - start)
        ))
    }
}
