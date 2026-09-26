package com.eventverse.app.domain.crm

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrmLeadTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun lead(stage: LeadStage = LeadStage.NEW_LEAD) = CrmLead(
        id = LeadId("lead-1"),
        tenantId = TenantId("ten-demo-001"),
        brandName = BrandName("PT Sinar Jaya"),
        stage = stage,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun transitionTo_qualifyLead_shouldSucceed() {
        val result = lead(stage = LeadStage.NEW_LEAD).transitionTo(LeadStage.QUALIFIED, now)
        assertTrue(result.isSuccess)
        assertEquals(LeadStage.QUALIFIED, result.getOrThrow().stage)
    }

    @Test
    fun transitionTo_unqualifyLead_shouldSucceed() {
        val result = lead(stage = LeadStage.NEW_LEAD).transitionTo(LeadStage.UNQUALIFIED, now)
        assertTrue(result.isSuccess)
        assertEquals(LeadStage.UNQUALIFIED, result.getOrThrow().stage)
    }

    @Test
    fun transitionTo_reopenUnqualifiedLead_shouldSucceed() {
        val result = lead(stage = LeadStage.UNQUALIFIED).transitionTo(LeadStage.QUALIFIED, now)
        assertTrue(result.isSuccess)
        assertEquals(LeadStage.QUALIFIED, result.getOrThrow().stage)
    }

    @Test
    fun transitionTo_sameStage_shouldFail() {
        val result = lead(stage = LeadStage.QUALIFIED).transitionTo(LeadStage.QUALIFIED, now)
        assertTrue(result.isFailure)
    }

    @Test
    fun archive_shouldSetArchivedAtAndIsArchivedTrue() {
        val archived = lead().archive(now)
        assertTrue(archived.isArchived)
        assertEquals(now, archived.archivedAt)
    }

    @Test
    fun whatsappNumber_parsesLocalIndonesianFormats() {
        assertEquals(WhatsappNumber("6281234567890"), WhatsappNumber.parse("081234567890"))
        assertEquals(WhatsappNumber("6281234567890"), WhatsappNumber.parse("+6281234567890"))
        assertEquals(WhatsappNumber("6281234567890"), WhatsappNumber.parse("6281234567890"))
    }

    @Test
    fun whatsappNumber_waLink_hasNoPlusPrefix() {
        val number = WhatsappNumber("6281234567890")
        assertEquals("https://wa.me/6281234567890", number.waLink)
        assertFalse(number.waLink.contains("+"))
    }

    @Test
    fun whatsappNumber_unparsableInput_returnsNull() {
        assertEquals(null, WhatsappNumber.parse("not-a-number"))
        // Indonesian phone validation: too short (< 10 digits in local format) or wrong prefix
        assertFalse(WhatsappNumber.isValidIndonesianPhone("08123"))
        assertFalse(WhatsappNumber.isValidIndonesianPhone("0211234567")) // landline, not 08 mobile
        assertTrue(WhatsappNumber.isValidIndonesianPhone("08123456789")) // 11 digits
        assertTrue(WhatsappNumber.isValidIndonesianPhone("+6281234567890")) // 12 digits
    }

    @Test
    fun brandName_canBeEmpty() {
        val emptyBrand = BrandName("")
        assertTrue(emptyBrand.isBlank)
        assertEquals("Tanpa Nama Brand", emptyBrand.display())
    }

    @Test
    fun crmLead_title_fallsBackGracefully() {
        val leadWithBrand = CrmLead(
            id = LeadId("l-1"),
            tenantId = TenantId("t-1"),
            brandName = BrandName("Brand A"),
            contactPerson = "Budi",
            createdAt = now,
            updatedAt = now
        )
        assertEquals("Brand A", leadWithBrand.title)

        val leadWithoutBrand = CrmLead(
            id = LeadId("l-2"),
            tenantId = TenantId("t-1"),
            brandName = BrandName(""),
            contactPerson = "Budi",
            createdAt = now,
            updatedAt = now
        )
        assertEquals("Budi", leadWithoutBrand.title)

        val leadOnlyPhone = CrmLead(
            id = LeadId("l-3"),
            tenantId = TenantId("t-1"),
            brandName = BrandName(""),
            whatsappNumber = WhatsappNumber("6281234567890"),
            createdAt = now,
            updatedAt = now
        )
        assertEquals("081234567890", leadOnlyPhone.title)
    }
}
