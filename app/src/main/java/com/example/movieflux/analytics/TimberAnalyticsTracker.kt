package com.example.movieflux.analytics

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimberAnalyticsTracker @Inject constructor() : AnalyticsTracker {

    override fun trackScreen(name: String) {
        Timber.tag("[NAV]").d("screen=%s", name)
    }

    override fun trackEvent(name: String, params: Map<String, Any>) {
        Timber.tag("[EVENT]").d("%s %s", name, params)
    }

    override fun trackError(tag: String, throwable: Throwable, isFatal: Boolean) {
        Timber.tag(tag).e(throwable, if (isFatal) "FATAL" else "non-fatal")
    }
}
