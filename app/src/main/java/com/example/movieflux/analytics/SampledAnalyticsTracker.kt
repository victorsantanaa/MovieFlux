package com.example.movieflux.analytics

import kotlin.random.Random

class SampledAnalyticsTracker(
    private val delegate: AnalyticsTracker,
    private val policy: SamplingPolicy
) : AnalyticsTracker {

    override fun trackScreen(name: String) {
        if (sample(policy.screenRenderRate)) delegate.trackScreen(name)
    }

    override fun trackEvent(name: String, params: Map<String, Any>) {
        val rate = when {
            name.startsWith("funnel") -> policy.funnelRate
            name == "frame_jank" -> policy.jankRate
            name == "network_request" -> policy.networkRate
            name == "screen_rendered" -> policy.screenRenderRate
            else -> policy.defaultRate
        }
        if (sample(rate)) delegate.trackEvent(name, params)
    }

    override fun trackError(tag: String, throwable: Throwable, isFatal: Boolean) =
        delegate.trackError(tag, throwable, isFatal)

    private fun sample(rate: Float): Boolean = rate >= 1.0f || Random.nextFloat() < rate
}
