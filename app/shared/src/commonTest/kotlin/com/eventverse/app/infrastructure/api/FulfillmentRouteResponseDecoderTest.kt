package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FulfillmentRouteResponseDecoderTest {

    @Test
    fun `route settings decoder preserves non-default tenant routes`() {
        val payload = JsonParser.parseObject(
            """
            {
              "routes": [
                {
                  "route": "DIGITIZING_TO_HOOPING",
                  "routeLabel": "Digitizing ke Pembidangan",
                  "mode": "DIRECT",
                  "isExplicit": true,
                  "active": true,
                  "sortOrder": 4
                }
              ]
            }
            """.trimIndent()
        )

        val result = FulfillmentTransferApiClient.decodeRouteSettingsView(payload, TenantId("bordir-uji"))

        assertEquals("bordir-uji", result.tenantId.value)
        assertEquals(1, result.settings.size)
        assertEquals("DIGITIZING_TO_HOOPING", result.settings.single().route.code.value)
        assertEquals("Digitizing ke Pembidangan", result.settings.single().route.label)
        assertEquals(4, result.settings.single().route.sortOrder)
        assertEquals(HandoverMode.DIRECT, result.settings.single().mode)
        assertFalse(result.hasAdminHubRoute)
    }

    @Test
    fun `route settings decoder rejects unknown mode instead of falling back`() {
        val payload = JsonParser.parseObject(
            """
            {
              "routes": [
                {
                  "route": "DIGITIZING_TO_HOOPING",
                  "routeLabel": "Digitizing ke Pembidangan",
                  "mode": "MAGIC_MODE",
                  "isExplicit": true,
                  "active": true,
                  "sortOrder": 0
                }
              ]
            }
            """.trimIndent()
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            FulfillmentTransferApiClient.decodeRouteSettingsView(payload, TenantId("bordir-uji"))
        }

        assertEquals("\$.routes[0].mode: tidak dikenal 'MAGIC_MODE'", failure.message)
    }

    @Test
    fun `route settings decoder rejects malformed rows instead of skipping them`() {
        val payload = JsonParser.parseObject(
            """
            {
              "routes": [
                {
                  "routeLabel": "Tanpa kode",
                  "mode": "DIRECT",
                  "isExplicit": true,
                  "active": true,
                  "sortOrder": 0
                }
              ]
            }
            """.trimIndent()
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            FulfillmentTransferApiClient.decodeRouteSettingsView(payload, TenantId("bordir-uji"))
        }

        assertTrue(failure.message.orEmpty().startsWith("\$.routes[0].route:"))
    }
}
