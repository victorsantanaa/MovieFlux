package com.example.movieflux.analytics

import com.example.movieflux.BuildConfig

private const val PROD_LOW_SAMPLE_RATE = 0.15f
private const val PROD_STANDARD_SAMPLE_RATE = 0.20f

data class SamplingPolicy(
    val errorRate: Float = 1.0f,
    val funnelRate: Float = 1.0f,
    val screenRenderRate: Float = if (BuildConfig.DEBUG) 1.0f else PROD_LOW_SAMPLE_RATE,
    val jankRate: Float = if (BuildConfig.DEBUG) 1.0f else PROD_LOW_SAMPLE_RATE,
    val networkRate: Float = if (BuildConfig.DEBUG) 1.0f else PROD_STANDARD_SAMPLE_RATE,
    val defaultRate: Float = if (BuildConfig.DEBUG) 1.0f else PROD_STANDARD_SAMPLE_RATE,
)
