package com.example.movieflux.view.common

import androidx.annotation.StringRes
import com.example.movieflux.R
import retrofit2.HttpException
import java.io.IOException

/**
 * Mapeia um [Throwable] para um [StringRes] amigável e localizado para exibição. A mensagem crua da
 * exceção intencionalmente nunca é mostrada ao usuário — é técnica, não traduzida e pode vazar
 * detalhes internos; os chamadores devem logar o throwable separadamente.
 */
@StringRes
fun Throwable.toUserMessageRes(): Int = when (this) {
    is IOException -> R.string.error_network
    is HttpException -> R.string.error_server
    else -> R.string.error_generic
}
