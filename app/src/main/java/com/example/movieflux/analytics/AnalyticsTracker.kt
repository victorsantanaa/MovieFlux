package com.example.movieflux.analytics

interface AnalyticsTracker {
    fun trackScreen(name: String)
    fun trackEvent(name: String, params: Map<String, Any> = emptyMap())
    fun trackError(tag: String, throwable: Throwable, isFatal: Boolean = false)
}
