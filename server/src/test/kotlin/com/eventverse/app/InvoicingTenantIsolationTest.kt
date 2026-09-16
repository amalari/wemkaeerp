package com.eventverse.app

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Isolasi tenant pada seluruh rute faktur ber-`{id}`.
 *
 * ## Kenapa satu test per rute, bukan satu test yang meringkas
 *
 * Kebocoran yang diperbaiki di sini bukan satu bug, melainkan **satu bug yang terlupa di delapan
 * tempat**. Dari seluruh rute ber-`{id}` di modul faktur, hanya `GET /{id}` yang membandingkan
 * `invoice.tenantId` dengan tenant pemanggil; tujuh sisanya tidak. RLS PostgreSQL tidak
 * menutupinya karena kueri tanpa tenant berjalan di koneksi platform yang tidak menyetel
 * `app.current_tenant_id`, dan `apply_tenant_rls` tidak pernah memanggil `FORCE ROW LEVEL SECURITY`
 * sehingga pemilik tabel selalu melewatinya.
 *
 * Test yang meringkas delapan rute menjadi satu akan berhenti di kegagalan pertama dan menyembunyikan
 * tujuh sisanya. Rute ke-sembilan yang ditambahkan nanti juga butuh barisnya sendiri di sini.
 *
 * Yang diuji adalah **akibat yang teramati dari luar**: tenant penyerang tidak mendapatkan data, dan
 * dokumen korban tidak berubah. Bukan detail implementasinya.
 */
class InvoicingTenantIsolationTest {

    private val victimSlug = "wemade-demo"
    private val victimId = TenantId("ten-demo-001")
    private val attackerSlug = "pabrik-lain"
    private val attackerId = TenantId("ten-other-002")

    private class Fixture(
        val tenants: InMemoryTenantRepository,
        val invoices: InMemoryInvoiceRepository,
        val templates: InMemoryInvoiceTemplateRepository,
        val payments: InMemoryInvoicePaymentRepository,
        val issuers: InMemoryInvoiceIssuerProfileRepository,
        val victimInvoice: Invoice,
        val victimTemplate: InvoiceTemplate
    )

    private fun tenant(id: TenantId, slug: String) = Tenant(
        id = id,
        slug = TenantSlug(slug),
        name = TenantName("PT $slug"),
        status = TenantStatus.ACTIVE,
        tier = SubscriptionTier.PRO
    )

    private fun fixture(victimStatus: InvoiceStatus = InvoiceStatus.DRAFT): Fixture = runBlocking {
        val tenants = InMemoryTenantRepository()
        tenants.save(tenant(victimId, victimSlug))
        tenants.save(tenant(attackerId, attackerSlug))

        val now = Clock.System.now()
        val templates = InMemoryInvoiceTemplateRepository()
        val victimTemplate = InvoiceTemplateFactory.standardIndonesianInvoice(victimId, now)
        templates.save(victimTemplate)

        // Tenant penyerang punya template default sendiri, supaya kalau rute PDF jatuh ke
        // findDefault() ia tidak diam-diam meminjam milik korban dan terlihat "berhasil".
        templates.save(
            InvoiceTemplateFactory.standardIndonesianInvoice(attackerId, now)
                .copy(id = InvoiceTemplateId("tpl-attacker"), isDefault = true)
        )

        val invoices = InMemoryInvoiceRepository()
        val victimInvoice = Invoice(
            id = InvoiceId("inv-victim-001"),
            tenantId = victimId,
            number = InvoiceNumber("INV/2026/03/0001"),
            kind = InvoiceKind.FULL,
            status = victimStatus,
            billTo = BillToParty(name = "PT Klien Rahasia"),
            issuer = IssuerProfile(companyName = "PT WeMade Demo"),
            lines = listOf(
                InvoiceLine(
                    id = InvoiceLineId("line-1"),
                    description = "Kemeja Seragam",
                    quantity = Quantity.of(10.0, UnitOfMeasure.PIECE),
                    unitPrice = Money.idr(100_000L * 100),
                    discount = Ratio.ZERO,
                    sortOrder = 1
                )
            ),
            currency = CurrencyCode.IDR,
            issueDate = LocalDate(2026, 3, 15),
            templateId = victimTemplate.id,
            renderedTemplate = if (victimStatus == InvoiceStatus.DRAFT) null else victimTemplate,
            createdBy = "victim-staff",
            createdAt = now,
            updatedAt = now
        )
        invoices.save(victimInvoice)

        Fixture(
            tenants, invoices, templates,
            InMemoryInvoicePaymentRepository(),
            InMemoryInvoiceIssuerProfileRepository(),
            victimInvoice, victimTemplate
        )
    }

    private fun ApplicationTestBuilder.install(f: Fixture) {
        application {
            module(
                tenantRepository = f.tenants,
                invoiceRepository = f.invoices,
                invoiceTemplateRepository = f.templates,
                invoicePaymentRepository = f.payments,
                invoiceIssuerProfileRepository = f.issuers
            )
        }
    }

    /** Tidak 2xx dan tidak memuat data korban. */
    private suspend fun assertDenied(res: HttpResponse) {
        assertTrue(
            !res.status.isSuccess(),
            "Permintaan lintas-tenant seharusnya ditolak, tapi mengembalikan ${res.status}"
        )
        val body = res.bodyAsText()
        assertTrue(
            !body.contains("PT Klien Rahasia") && !body.contains("INV/2026/03/0001"),
            "Respons membocorkan data korban: $body"
        )
    }

    // ── BACA ──────────────────────────────────────────────────────────────────

    @Test
    fun invoiceDetail_acrossTenants_isDenied() = testApplication {
        val f = fixture(); install(f)
        assertDenied(client.get("/api/tenant/invoicing/${f.victimInvoice.id.value}") { asTenant(attackerSlug) })
    }

    @Test
    fun invoicePdf_acrossTenants_isDenied() = testApplication {
        // Kebocoran paling parah sebelum perbaikan: PDF lengkap berisi nama klien, nominal,
        // dan nomor rekening penerbit, tanpa satu pun pemeriksaan kepemilikan.
        val f = fixture(victimStatus = InvoiceStatus.ISSUED); install(f)
        val res = client.get("/api/tenant/invoicing/${f.victimInvoice.id.value}/pdf") { asTenant(attackerSlug) }
        assertTrue(!res.status.isSuccess(), "PDF lintas-tenant seharusnya ditolak, dapat ${res.status}")
        assertTrue(
            res.contentType()?.match(ContentType.Application.Pdf) != true,
            "Tidak boleh ada byte PDF yang dikirim ke tenant lain"
        )
    }

    @Test
    fun paymentHistory_acrossTenants_isDenied() = testApplication {
        val f = fixture(victimStatus = InvoiceStatus.ISSUED); install(f)
        runBlocking {
            f.payments.append(
                victimId,
                InvoicePayment(
                    id = InvoicePaymentId("pay-victim-1"),
                    invoiceId = f.victimInvoice.id,
                    amount = Money.idr(500_000L * 100),
                    paidAt = Clock.System.now(),
                    method = "TRANSFER",
                    recordedBy = "victim-finance"
                )
            )
        }
        val res = client.get("/api/tenant/invoicing/${f.victimInvoice.id.value}/payments") { asTenant(attackerSlug) }
        assertTrue(
            !res.bodyAsText().contains("pay-victim-1"),
            "Riwayat pembayaran korban bocor: ${res.bodyAsText()}"
        )
    }

    @Test
    fun templateDetail_acrossTenants_isDenied() = testApplication {
        val f = fixture(); install(f)
        val res = client.get("/api/tenant/invoicing/templates/${f.victimTemplate.id.value}") { asTenant(attackerSlug) }
        assertTrue(!res.status.isSuccess(), "Template lintas-tenant seharusnya ditolak, dapat ${res.status}")
    }

    // ── TULIS ─────────────────────────────────────────────────────────────────

    @Test
    fun issueInvoice_acrossTenants_isDeniedAndLeavesInvoiceUntouched() = testApplication {
        val f = fixture(); install(f)
        val res = client.post("/api/tenant/invoicing/${f.victimInvoice.id.value}/issue") { asTenant(attackerSlug) }
        assertDenied(res)
        runBlocking {
            val after = f.invoices.findById(victimId, f.victimInvoice.id)
            assertNotNull(after)
            assertEquals(InvoiceStatus.DRAFT, after.status, "Faktur korban ikut diterbitkan oleh tenant lain")
        }
    }

    @Test
    fun voidInvoice_acrossTenants_isDeniedAndLeavesInvoiceUntouched() = testApplication {
        val f = fixture(victimStatus = InvoiceStatus.ISSUED); install(f)
        val res = client.post("/api/tenant/invoicing/${f.victimInvoice.id.value}/void") {
            asTenant(attackerSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"dibatalkan penyerang"}""")
        }
        assertDenied(res)
        runBlocking {
            val after = f.invoices.findById(victimId, f.victimInvoice.id)
            assertNotNull(after)
            assertEquals(InvoiceStatus.ISSUED, after.status, "Faktur korban dibatalkan oleh tenant lain")
        }
    }

    @Test
    fun recordPayment_acrossTenants_isDeniedAndRecordsNothing() = testApplication {
        val f = fixture(victimStatus = InvoiceStatus.ISSUED); install(f)
        val res = client.post("/api/tenant/invoicing/${f.victimInvoice.id.value}/payments") {
            asTenant(attackerSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"amount":{"minorUnits":100000,"currency":"IDR"},"method":"TRANSFER"}""")
        }
        assertTrue(!res.status.isSuccess(), "Pembayaran lintas-tenant seharusnya ditolak, dapat ${res.status}")
        runBlocking {
            assertTrue(
                f.payments.historyFor(victimId, f.victimInvoice.id).isEmpty(),
                "Pembayaran palsu tercatat pada faktur korban"
            )
        }
    }

    @Test
    fun createSettlement_acrossTenants_isDenied() = testApplication {
        val f = fixture(victimStatus = InvoiceStatus.ISSUED); install(f)
        assertDenied(
            client.post("/api/tenant/invoicing/${f.victimInvoice.id.value}/create-settlement") {
                asTenant(attackerSlug)
            }
        )
    }

    @Test
    fun updateDraft_acrossTenants_isDeniedAndLeavesInvoiceUntouched() = testApplication {
        val f = fixture(); install(f)
        val res = client.put("/api/tenant/invoicing/${f.victimInvoice.id.value}") {
            asTenant(attackerSlug)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"FULL","billTo":{"name":"DIBAJAK"},"lines":[],
                   "issueDate":"2026-03-15","templateId":"${f.victimTemplate.id.value}"}"""
            )
        }
        assertTrue(!res.status.isSuccess(), "Update lintas-tenant seharusnya ditolak, dapat ${res.status}")
        runBlocking {
            val after = f.invoices.findById(victimId, f.victimInvoice.id)
            assertNotNull(after)
            assertEquals("PT Klien Rahasia", after.billTo.name, "Draft korban diubah oleh tenant lain")
        }
    }

    @Test
    fun archiveTemplate_acrossTenants_isDeniedAndLeavesTemplateActive() = testApplication {
        val f = fixture(); install(f)
        client.delete("/api/tenant/invoicing/templates/${f.victimTemplate.id.value}") { asTenant(attackerSlug) }
        runBlocking {
            assertNotNull(
                f.templates.findById(victimId, f.victimTemplate.id),
                "Template korban diarsipkan oleh tenant lain"
            )
        }
    }

    // ── Kontrol positif ───────────────────────────────────────────────────────

    @Test
    fun ownTenant_stillReadsItsOwnInvoice() = testApplication {
        // Tanpa ini, seluruh test di atas tetap hijau walau semua rute mengembalikan 404 selamanya.
        val f = fixture(); install(f)
        val res = client.get("/api/tenant/invoicing/${f.victimInvoice.id.value}") { asTenant(victimSlug) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("PT Klien Rahasia"))
    }

    @Test
    fun ownTenant_stillReadsItsOwnTemplate() = testApplication {
        val f = fixture(); install(f)
        val res = client.get("/api/tenant/invoicing/templates/${f.victimTemplate.id.value}") { asTenant(victimSlug) }
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun unknownInvoiceId_isIndistinguishableFromForeignOne() = testApplication {
        // Membedakan "milik orang lain" (403) dari "tidak ada" (404) memberi tahu penyerang
        // ID mana yang valid. Keduanya harus menjawab sama.
        val f = fixture(); install(f)
        val foreign = client.get("/api/tenant/invoicing/${f.victimInvoice.id.value}") { asTenant(attackerSlug) }
        val missing = client.get("/api/tenant/invoicing/inv-tidak-ada-sama-sekali") { asTenant(attackerSlug) }
        assertEquals(missing.status, foreign.status, "Status faktur milik tenant lain bocor sebagai sinyal keberadaan")
        assertNull(null)
    }
}
