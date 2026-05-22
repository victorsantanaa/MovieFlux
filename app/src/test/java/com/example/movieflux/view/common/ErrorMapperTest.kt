package com.example.movieflux.view.common

import com.example.movieflux.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Verifies #10: exception types map to friendly, localized resources — the raw exception message is
 * never returned for display.
 */
class ErrorMapperTest {

    @Test
    fun `IOException maps to network error`() {
        assertEquals(R.string.error_network, IOException("host unreachable").toUserMessageRes())
    }

    @Test
    fun `HttpException maps to server error`() {
        val http = HttpException(
            Response.error<Any>(404, "not found".toResponseBody("text/plain".toMediaType()))
        )
        assertEquals(R.string.error_server, http.toUserMessageRes())
    }

    @Test
    fun `unknown throwable maps to generic error`() {
        assertEquals(R.string.error_generic, RuntimeException("boom").toUserMessageRes())
    }
}
