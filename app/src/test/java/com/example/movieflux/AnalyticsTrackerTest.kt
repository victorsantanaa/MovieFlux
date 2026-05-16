package com.example.movieflux

import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.analytics.CompositeAnalyticsTracker
import com.example.movieflux.analytics.SampledAnalyticsTracker
import com.example.movieflux.analytics.SamplingPolicy
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AnalyticsTrackerTest {

    @Test
    fun `CompositeAnalyticsTracker forwards trackScreen to all sinks`() {
        val a = mockk<AnalyticsTracker>(relaxed = true)
        val b = mockk<AnalyticsTracker>(relaxed = true)
        val composite = CompositeAnalyticsTracker(listOf(a, b))

        composite.trackScreen("home")

        verify { a.trackScreen("home") }
        verify { b.trackScreen("home") }
    }

    @Test
    fun `CompositeAnalyticsTracker continues if one sink throws`() {
        val failing = mockk<AnalyticsTracker> {
            io.mockk.every { trackScreen(any()) } throws RuntimeException("sink down")
        }
        val good = mockk<AnalyticsTracker>(relaxed = true)
        val composite = CompositeAnalyticsTracker(listOf(failing, good))

        composite.trackScreen("home") // must not throw

        verify { good.trackScreen("home") }
    }

    @Test
    fun `SampledAnalyticsTracker always forwards trackError regardless of rate`() {
        val delegate = mockk<AnalyticsTracker>(relaxed = true)
        val policy = SamplingPolicy(errorRate = 0.0f) // drop everything — except errors
        val sampled = SampledAnalyticsTracker(delegate, policy)

        sampled.trackError("[TEST]", RuntimeException("boom"))

        verify { delegate.trackError("[TEST]", any(), any()) }
    }

    @Test
    fun `SampledAnalyticsTracker drops events when rate is zero`() {
        val delegate = mockk<AnalyticsTracker>(relaxed = true)
        val policy = SamplingPolicy(defaultRate = 0.0f, networkRate = 0.0f, jankRate = 0.0f)
        val sampled = SampledAnalyticsTracker(delegate, policy)

        repeat(100) { sampled.trackEvent("some_event") }

        verify(exactly = 0) { delegate.trackEvent(any(), any()) }
    }

    @Test
    fun `SampledAnalyticsTracker always forwards funnel events`() {
        val delegate = mockk<AnalyticsTracker>(relaxed = true)
        val policy = SamplingPolicy(funnelRate = 1.0f)
        val sampled = SampledAnalyticsTracker(delegate, policy)

        sampled.trackEvent("funnel_step", mapOf("flow" to "auth"))

        verify { delegate.trackEvent("funnel_step", any()) }
    }
}
