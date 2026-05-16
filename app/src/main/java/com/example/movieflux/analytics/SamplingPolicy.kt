package com.example.movieflux.analytics

import com.example.movieflux.BuildConfig

data class SamplingPolicy(
    val errorRate: Float = 1.0f,
    val funnelRate: Float = 1.0f,
    val screenRenderRate: Float = if (BuildConfig.DEBUG) 1.0f else 0.15f,
    val jankRate: Float = if (BuildConfig.DEBUG) 1.0f else 0.15f,
    val networkRate: Float = if (BuildConfig.DEBUG) 1.0f else 0.20f,
    val defaultRate: Float = if (BuildConfig.DEBUG) 1.0f else 0.20f
)
