package com.example.movieflux.analytics.di

import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.analytics.CompositeAnalyticsTracker
import com.example.movieflux.analytics.SampledAnalyticsTracker
import com.example.movieflux.analytics.SamplingPolicy
import com.example.movieflux.analytics.TimberAnalyticsTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsTracker(timber: TimberAnalyticsTracker): AnalyticsTracker =
        SampledAnalyticsTracker(
            CompositeAnalyticsTracker(listOf(timber)),
            SamplingPolicy()
        )
}
