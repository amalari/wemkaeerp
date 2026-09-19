package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit test murni domain: gerbang validasi ACC lembar sampling dari deal CRM.
 *
 * Aturan bisnis:
 * 1. Foto mockup Tampak Depan wajib; Tampak Belakang opsional.
 * 2. Size chart minimal 1 ukuran dengan SELURUH baris POM terisi lengkap.
 * 3. Jumlah sampel minimal 1 pcs.
 * 4. Catatan opsional — tidak boleh divalidasi.
 * 5. Revisi otomatis mewarisi data revisi sebelumnya (carry-over).
 */
class SamplingApprovalReadinessTest {

    private val now = Instant.parse("2026-09-17T05:00:00Z")
    private val tenantId = TenantId("ten-demo-001")

    /** Matriks lengkap: ukuran S lengkap (qty 2 + kedua POM terisi); M dan L sengaja kosong. */
    private fun completeMatrix(qty: Int = 2) = listOf(
        SizeChartRow(
            id = SAMPLING_QTY_ROW_ID,
            pomName = SAMPLING_QTY_ROW_NAME,
            values = mapOf("S" to qty.toString(), "M" to "", "L" to "")
        ),
        SizeChartRow(
            id = "pom_lebar_dada",
            pomName = "Lebar Dada",
            values = mapOf("S" to "50", "M" to "53", "L" to "55")
        ),
        SizeChartRow(
            id = "pom_panjang_baju",
            pomName = "Panjang Baju",
            values = mapOf("S" to "70", "M" to "72", "L" to "74")
        )
    )

    private fun newOrder(
        mockupUrls: List<String> = emptyList(),
        matrix: List<SizeChartRow> = completeMatrix(),
        notes: String = "",
        deadlineDelivery: LocalDate? = LocalDate(2026, 9, 30)
    ) = SamplingOrder(
        id = SamplingOrderId("smp_acc_001"),
        tenantId = tenantId,
        spkNumber = SpkNumber("SPK-SMP-0009"),
        clientName = "ACME FASHION",
        styleName = "KNIT CARDIGAN",
        knitSpec = KnitSpec(mockupImageUrls = mockupUrls),
        sizeMatrix = matrix,
        deadlineDelivery = deadlineDelivery,
        sampleQuantity = 2,
        notes = notes,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun missingApprovalRequirements_whenDesignIsBlank_shouldListAllThreeIssues() {
        val issues = newOrder(matrix = defaultSamplingSizeMatrix()).missingApprovalRequirements()

        assertEquals(3, issues.size)
        assertTrue(issues[0].contains("Tampak Depan"))
        assertTrue(issues[1].contains("Size chart"))
        assertTrue(issues[2].contains("Jumlah sampel"))
    }

    @Test
    fun missingApprovalRequirements_whenFrontMockupAndCompleteMatrix_shouldReturnEmptyList() {
        val order = newOrder(mockupUrls = listOf("front:mockups/depan.jpg", "back:mockups/belakang.jpg"))

        assertTrue(order.missingApprovalRequirements().isEmpty())
        assertTrue(order.isReadyForAcc)
    }

    @Test
    fun missingApprovalRequirements_whenOnlyBackMockupUploaded_shouldStillRequireFrontMockup() {
        // Tampak Belakang opsional — mengunggahnya saja TIDAK memenuhi syarat Tampak Depan.
        val order = newOrder(mockupUrls = listOf("back:mockups/belakang.jpg"))

        val issues = order.missingApprovalRequirements()
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("Tampak Depan"))
        assertFalse(order.isReadyForAcc)
    }

    @Test
    fun missingApprovalRequirements_whenOnePomRowIsEmpty_shouldFailSizeChartCompletenessRule() {
        // Dua baris POM ada di tabel; hanya Lebar Dada yang diisi — ukuran S belum lengkap.
        val halfFilledMatrix = listOf(
            SizeChartRow(
                id = SAMPLING_QTY_ROW_ID,
                pomName = SAMPLING_QTY_ROW_NAME,
                values = mapOf("S" to "2")
            ),
            SizeChartRow(
                id = "pom_lebar_dada",
                pomName = "Lebar Dada",
                values = mapOf("S" to "50")
            ),
            SizeChartRow(
                id = "pom_panjang_baju",
                pomName = "Panjang Baju",
                values = mapOf("S" to "") // baris kedua kosong!
            )
        )
        val order = newOrder(mockupUrls = listOf("front:mockups/depan.jpg"), matrix = halfFilledMatrix)

        val issues = order.missingApprovalRequirements()
        // Dua issue sekaligus: tidak ada ukuran lengkap, dan total sampel jadi 0 karena
        // qty hanya dihitung untuk kolom ukuran yang POM-nya lengkap.
        assertEquals(2, issues.size)
        assertTrue(issues.any { it.contains("Size chart") })
        assertTrue(issues.any { it.contains("Jumlah sampel") })
    }

    @Test
    fun missingApprovalRequirements_whenQuantityIsZeroOrBlank_shouldFailMinimumQuantityRule() {
        val zeroQtyMatrix = completeMatrix().map { row ->
            if (row.isQtyRow) row.copy(values = mapOf("S" to "0", "M" to "", "L" to "")) else row
        }
        val order = newOrder(mockupUrls = listOf("front:mockups/depan.jpg"), matrix = zeroQtyMatrix)

        val issues = order.missingApprovalRequirements()
        // Dua issue sekaligus: qty 0 membuat tidak ada ukuran yang "lengkap" (syarat >= 1 pcs)
        // sekaligus melanggar aturan jumlah sampel minimal.
        assertEquals(2, issues.size)
        assertTrue(issues.any { it.contains("Size chart") })
        assertTrue(issues.any { it.contains("Jumlah sampel") })
    }

    @Test
    fun missingApprovalRequirements_whenNotesEmpty_shouldNotAddAnyIssue() {
        // Catatan bersifat opsional: desain lengkap tanpa catatan harus tetap lolos validasi.
        val order = newOrder(mockupUrls = listOf("front:mockups/depan.jpg"), notes = "")

        assertTrue(order.missingApprovalRequirements().isEmpty())
    }

    @Test
    fun missingApprovalRequirements_whenMatrixOverrideGiven_shouldValidateAgainstOverride() {
        // UI mengirim input terkini (mungkin belum ter-autosave) — validasi wajib ikut nilainya.
        val order = newOrder(mockupUrls = listOf("front:mockups/depan.jpg"))
        assertFalse(order.missingApprovalRequirements(emptyList()).isEmpty())
        assertTrue(order.missingApprovalRequirements(completeMatrix()).isEmpty())
    }

    @Test
    fun firstCompleteSizeColumn_whenPartialRows_shouldReturnNullAndCompleteColumnWhenWhole() {
        assertNull(firstCompleteSizeColumn(defaultSamplingSizeMatrix())) // semua POM masih kosong
        assertEquals("S", firstCompleteSizeColumn(completeMatrix()))
    }

    @Test
    fun requestRevision_whenCalled_shouldCarryOverPreviousRevisionDataIntoNextRevision() {
        val order = newOrder(
            mockupUrls = listOf("front:mockups/depan_v0.jpg", "back:mockups/belakang_v0.jpg"),
            notes = "Handfeel lembut"
        )

        val revised = order.requestRevision(notes = "Kerah terlalu lebar", updatedAt = now)

        // Seluruh data revisi sebelumnya ikut ke revisi berikutnya — user tinggal mengubah yang perlu.
        assertEquals(SamplingStatus.REVISION, revised.status)
        assertEquals("mockups/depan_v0.jpg", revised.mockupFrontKey)
        assertEquals("mockups/belakang_v0.jpg", revised.mockupBackKey)
        assertEquals(order.sizeMatrix, revised.sizeMatrix)
        assertEquals(order.samplingFeeIdr, revised.samplingFeeIdr)
        assertEquals("Handfeel lembut", revised.notes)

        // Snapshot lama tetap terarsip untuk arsip Rev 0 (read-only).
        val snapshot0 = revised.snapshotFor(0)
        assertEquals(order.sizeMatrix, snapshot0?.sizeMatrix)
        assertEquals(order.samplingFeeIdr, snapshot0?.samplingFeeIdr)
    }

    @Test
    fun missingSpkRequirements_whenValidOrder_shouldPass() {
        val order = newOrder(
            mockupUrls = listOf("front:mockup.png"),
            matrix = completeMatrix(qty = 2)
        )
        assertTrue(order.missingSpkRequirements().isEmpty())
        assertTrue(order.isReadyForSpk)
    }

    @Test
    fun missingSpkRequirements_whenFrontMockupIsMissing_shouldReportError() {
        val order = newOrder(
            mockupUrls = emptyList(),
            matrix = completeMatrix(qty = 2)
        )
        val errors = order.missingSpkRequirements()
        assertTrue(errors.any { it.contains("Tampak Depan") })
        assertFalse(order.isReadyForSpk)
    }

    @Test
    fun missingSpkRequirements_whenSizeChartIncomplete_shouldReportError() {
        val order = newOrder(
            mockupUrls = listOf("front:mockup.png"),
            matrix = defaultSamplingSizeMatrix() // semua baris POM masih kosong
        )
        val errors = order.missingSpkRequirements()
        assertTrue(errors.any { it.contains("Size chart") })
        assertFalse(order.isReadyForSpk)
    }

    @Test
    fun missingSpkRequirements_whenStyleNameIsBlank_shouldReportError() {
        val order = newOrder(
            mockupUrls = listOf("front:mockup.png"),
            matrix = completeMatrix(qty = 2)
        ).copy(styleName = "   ")
        val errors = order.missingSpkRequirements()
        assertTrue(errors.any { it.contains("Nama desain") })
        assertFalse(order.isReadyForSpk)
    }

    @Test
    fun missingSpkRequirements_whenQtyIsZero_shouldReportError() {
        val order = newOrder(
            mockupUrls = listOf("front:mockup.png"),
            matrix = completeMatrix(qty = 0)
        ).copy(sampleQuantity = 0)
        val errors = order.missingSpkRequirements()
        assertTrue(errors.any { it.contains("Jumlah sampel") })
        assertFalse(order.isReadyForSpk)
    }

    @Test
    fun missingSpkRequirements_whenDeadlineDeliveryIsMissing_shouldReportError() {
        val order = newOrder(
            mockupUrls = listOf("front:mockup.png"),
            matrix = completeMatrix(qty = 2),
            deadlineDelivery = null
        )
        val errors = order.missingSpkRequirements()
        assertTrue(errors.any { it.contains("deadline") || it.contains("Deadline") })
        assertFalse(order.isReadyForSpk)
    }

    @Test
    fun spkValidationWarnings_whenNoBackMockup_shouldWarn() {
        val orderWithoutBack = newOrder(mockupUrls = listOf("front:mockup.png"))
        val warnings = orderWithoutBack.spkValidationWarnings()
        assertTrue(warnings.any { it.contains("Tampak Belakang") })

        val orderWithBoth = newOrder(mockupUrls = listOf("front:depan.png", "back:belakang.png"))
        assertTrue(orderWithBoth.spkValidationWarnings().isEmpty())
    }
}

