package com.example.movieflux.analytics

class CompositeAnalyticsTracker(
    private val trackers: List<AnalyticsTracker>
) : AnalyticsTracker {

    override fun trackScreen(name: String) =
        trackers.forEach { runCatching { it.trackScreen(name) } }

    override fun trackEvent(name: String, params: Map<String, Any>) =
        trackers.forEach { runCatching { it.trackEvent(name, params) } }

    override fun trackError(tag: String, throwable: Throwable, isFatal: Boolean) =
        trackers.forEach { runCatching { it.trackError(tag, throwable, isFatal) } }
}
