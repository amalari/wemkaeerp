package com.eventverse.app.presentation.orgchart

import com.eventverse.app.infrastructure.api.OrgChartRestoreException
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrgChartErrorMessagesTest {

    private val proxyText = "Error occurred while trying to proxy: localhost:3001/api/tenant/departments"

    @Test
    fun friendly_proxyNoiseInMessage_isReplacedAndNeverLeaks() {
        val text = OrgChartErrorMessages.friendly(RuntimeException("Gagal memuat divisi (HTTP 500): $proxyText"))
        assertEquals(OrgChartErrorMessages.UNREACHABLE, text)
        assertFalse(text.contains("proxy", ignoreCase = true))
    }

    @Test
    fun friendly_gatewayStatus_isUnreachable() {
        assertEquals(
            OrgChartErrorMessages.UNREACHABLE,
            OrgChartErrorMessages.friendly(RuntimeException("Gagal memuat karyawan (HTTP 502): Bad Gateway"))
        )
    }

    @Test
    fun friendly_connectExceptionInCauseChain_isUnreachable() {
        assertEquals(
            OrgChartErrorMessages.UNREACHABLE,
            OrgChartErrorMessages.friendly(RuntimeException("wrapper", ConnectException("x")))
        )
    }

    @Test
    fun friendly_businessMessage_isKept() {
        assertEquals("Email sudah dipakai.", OrgChartErrorMessages.friendly(RuntimeException("Email sudah dipakai.")))
    }

    @Test
    fun friendly_blankOrNull_usesFallback() {
        assertEquals("cadangan", OrgChartErrorMessages.friendly(RuntimeException(""), "cadangan"))
        assertEquals("cadangan", OrgChartErrorMessages.friendly(null, "cadangan"))
    }

    @Test
    fun restoreFailure_409_isWarningNotSuccess() {
        val text = OrgChartErrorMessages.restoreFailure(OrgChartRestoreException(409, "x", "Tidak ada contoh."))
        assertEquals("Peringatan: Tidak ada contoh.", text)
        assertEquals(OrgChartToastSeverity.WARNING, OrgChartErrorMessages.severityOf(text))
    }

    @Test
    fun restoreFailure_403WithServerText_isError() {
        val text = OrgChartErrorMessages.restoreFailure(OrgChartRestoreException(403, "x", "Khusus Owner."))
        assertEquals(OrgChartToastSeverity.ERROR, OrgChartErrorMessages.severityOf(text))
    }

    @Test
    fun restoreFailure_proxyBodyOr502_isUnreachable() {
        assertEquals(OrgChartErrorMessages.UNREACHABLE, OrgChartErrorMessages.restoreFailure(OrgChartRestoreException(500, "x", proxyText)))
        assertEquals(OrgChartErrorMessages.UNREACHABLE, OrgChartErrorMessages.restoreFailure(OrgChartRestoreException(502, "x", "")))
    }

    @Test
    fun severityOf_classifiesByMessage() {
        assertEquals(OrgChartToastSeverity.SUCCESS, OrgChartErrorMessages.severityOf("Contoh struktur organisasi berhasil dimuat."))
        assertEquals(OrgChartToastSeverity.ERROR, OrgChartErrorMessages.severityOf("Gagal menyimpan: x"))
        assertEquals(OrgChartToastSeverity.ERROR, OrgChartErrorMessages.severityOf(OrgChartErrorMessages.UNREACHABLE))
        assertEquals(OrgChartToastSeverity.WARNING, OrgChartErrorMessages.severityOf("Nama divisi tidak boleh kosong."))
        assertTrue(OrgChartErrorMessages.isTransportNoise("ECONNREFUSED 127.0.0.1"))
    }

    @Test
    fun loadState_failedWithProxyNoise_carriesFriendlyMessage() {
        val state = OrgChartLoadState.from(Result.failure(RuntimeException(proxyText)), Result.success(emptyList()))
        assertEquals(OrgChartLoadState.Failed(OrgChartErrorMessages.UNREACHABLE), state)
    }
}
