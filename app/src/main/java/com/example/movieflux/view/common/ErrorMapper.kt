package com.example.movieflux.view.common

import androidx.annotation.StringRes
import com.example.movieflux.R
import retrofit2.HttpException
import java.io.IOException

/**
 * Maps a [Throwable] to a friendly, localized [StringRes] for display. The raw exception message is
 * intentionally never surfaced to the user — it's technical, untranslated, and may leak internal
 * detail; callers should log the throwable separately.
 */
@StringRes
fun Throwable.toUserMessageRes(): Int = when (this) {
    is IOException -> R.string.error_network
    is HttpException -> R.string.error_server
    else -> R.string.error_generic
}
