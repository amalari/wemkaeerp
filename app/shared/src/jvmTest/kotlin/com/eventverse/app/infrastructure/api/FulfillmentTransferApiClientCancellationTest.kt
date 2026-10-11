package com.eventverse.app.infrastructure.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FulfillmentTransferApiClientCancellationTest {

    @Test
    fun `transport cancellation propagates instead of becoming failed result`() = runTest {
        val client = FulfillmentTransferApiClient(
            httpClient = HttpClient(MockEngine { throw CancellationException("request dibatalkan") }),
            tokenProvider = FixedSessionTokenProvider("test-token")
        )

        assertFailsWith<CancellationException> {
            client.routes("bordir-uji")
        }
    }
}
