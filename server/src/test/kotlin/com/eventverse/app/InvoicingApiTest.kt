package com.eventverse.app

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import com.eventverse.app.shared.invoicing.InvoiceCodec
import com.eventverse.app.shared.invoicing.InvoiceTemplateCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Test
import kotlin.test.*

class InvoicingApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")

    private fun setupTestTenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("PT WeMade Demo"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    private fun setupDefaultTemplate(templateRepo: InMemoryInvoiceTemplateRepository): InvoiceTemplate {
        val template = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, Clock.System.now())
        runBlocking {
            templateRepo.save(template)
        }
        return template
    }

    @Test
    fun getInvoices_initiallyEmpty_shouldReturnEmptyPage() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val invoiceRepo = InMemoryInvoiceRepository()
        val templateRepo = InMemoryInvoiceTemplateRepository()
        val paymentRepo = InMemoryInvoicePaymentRepository()
        val issuerRepo = InMemoryInvoiceIssuerProfileRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                invoiceRepository = invoiceRepo,
                invoiceTemplateRepository = templateRepo,
                invoicePaymentRepository = paymentRepo,
                invoiceIssuerProfileRepository = issuerRepo
            )
        }

        val res = client.get("/api/tenant/invoicing") {
            asTenant(tenantSlug)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"items\":[]"))
        assertTrue(body.contains("\"totalCount\":0"))
    }

    @Test
    fun fullInvoiceLifecycle_create_update_issue_pay_and_pdf() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val invoiceRepo = InMemoryInvoiceRepository()
        val templateRepo = InMemoryInvoiceTemplateRepository()
        val paymentRepo = InMemoryInvoicePaymentRepository()
        val issuerRepo = InMemoryInvoiceIssuerProfileRepository()

        val seedTemplate = setupDefaultTemplate(templateRepo)
        runBlocking {
            issuerRepo.save(
                tenantId,
                IssuerProfile(
                    companyName = "PT WeMade Garment Indonesia",
                    address = "Kawasan Industri Rancaekek Kav. 12, Bandung",
                    taxId = "02.345.678.9-429.000",
                    bankName = "BCA",
                    bankAccountNumber = "8420-123-999"
                )
            )
        }

        application {
            module(
                tenantRepository = tenantRepo,
                invoiceRepository = invoiceRepo,
                invoiceTemplateRepository = templateRepo,
                invoicePaymentRepository = paymentRepo,
                invoiceIssuerProfileRepository = issuerRepo
            )
        }

        // 1. Create Draft
        val createPayload = """
            {
                "kind": "DOWN_PAYMENT",
                "templateId": "${seedTemplate.id.value}",
                "billTo": {
                    "name": "PT Mitra Jaya",
                    "contactPerson": "Pak Hendra",
                    "address": "Jl. Asia Afrika No. 10, Bandung",
                    "phone": "08123456789",
                    "email": "hendra@mitrajaya.com",
                    "taxId": "01.222.333.4-000.000"
                },
                "lines": [
                    {
                        "id": "line-01",
                        "description": "Kemeja Seragam Lapangan Drill (DP 50%)",
                        "quantity": {"micros": 100000000, "uom": "pcs"},
                        "unitPrice": {"minor": 7500000, "currency": "IDR"},
                        "discount": {"numerator": 0, "denominator": 100},
                        "sortOrder": 1
                    }
                ],
                "taxRatio": {"numerator": 11, "denominator": 100},
                "globalDiscount": {"numerator": 0, "denominator": 100},
                "contractValue": {"minor": 1500000000, "currency": "IDR"},
                "currency": "IDR",
                "issueDate": "2026-03-15",
                "dueDate": "2026-03-29",
                "notes": "DP produksi seragam 100 pcs.",
                "terms": "Pembayaran via transfer bank."
            }
        """.trimIndent()

        val createRes = client.post("/api/tenant/invoicing") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(createPayload)
        }

        assertEquals(HttpStatusCode.OK, createRes.status)
        val createdJson = JsonParser.parseObject(createRes.bodyAsText())
        val invoiceId = createdJson.string("id")
        assertNotNull(invoiceId)
        assertEquals("DRAFT", createdJson.string("status"))
        assertTrue(createdJson.string("number")?.startsWith("INV/2026/03/") == true)

        // 2. Issue Invoice (Freeze Snapshot)
        val issueRes = client.post("/api/tenant/invoicing/$invoiceId/issue") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, issueRes.status)
        val issuedJson = JsonParser.parseObject(issueRes.bodyAsText())
        assertEquals("ISSUED", issuedJson.string("status"))
        assertNotNull(issuedJson.obj("renderedTemplate"), "Snapshot template harus dibekukan")

        // 3. Record Payment
        val payPayload = """
            {
                "amount": {"minor": 832500000, "currency": "IDR"},
                "method": "BANK_TRANSFER",
                "reference": "TRX-BCA-9921",
                "note": "Pembayaran DP lunas via transfer BCA"
            }
        """.trimIndent()

        val payRes = client.post("/api/tenant/invoicing/$invoiceId/payments") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(payPayload)
        }
        assertEquals(HttpStatusCode.OK, payRes.status)

        // 4. Verify Invoice Status changed to PAID
        val getRes = client.get("/api/tenant/invoicing/$invoiceId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, getRes.status)
        val updatedInvoiceJson = JsonParser.parseObject(getRes.bodyAsText())
        assertEquals("PAID", updatedInvoiceJson.string("status"))

        // 5. Download Real PDF
        val pdfRes = client.get("/api/tenant/invoicing/$invoiceId/pdf") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, pdfRes.status)
        assertEquals(ContentType.Application.Pdf, pdfRes.contentType())
        val pdfBytes = pdfRes.bodyAsBytes()
        assertTrue(pdfBytes.isNotEmpty())
        assertEquals("%PDF-", String(pdfBytes.sliceArray(0..4)))

        // 6. Generate Settlement Invoice from this Down Payment
        val settleRes = client.post("/api/tenant/invoicing/$invoiceId/create-settlement") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, settleRes.status)
        val settleJson = JsonParser.parseObject(settleRes.bodyAsText())
        assertEquals("SETTLEMENT", settleJson.string("kind"))
        assertEquals("DRAFT", settleJson.string("status"))
        assertEquals(invoiceId, settleJson.string("parentInvoiceId"))
    }

    @Test
    fun templatesApi_crud_shouldWork() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val invoiceRepo = InMemoryInvoiceRepository()
        val templateRepo = InMemoryInvoiceTemplateRepository()
        val paymentRepo = InMemoryInvoicePaymentRepository()
        val issuerRepo = InMemoryInvoiceIssuerProfileRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                invoiceRepository = invoiceRepo,
                invoiceTemplateRepository = templateRepo,
                invoicePaymentRepository = paymentRepo,
                invoiceIssuerProfileRepository = issuerRepo
            )
        }

        val template = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, Clock.System.now())
            .copy(id = InvoiceTemplateId("tmpl-custom-001"), name = "Template Modern Custom")

        val savePayload = InvoiceTemplateCodec.encode(template).encode()

        // Create
        val postRes = client.post("/api/tenant/invoicing/templates") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(savePayload)
        }
        assertEquals(HttpStatusCode.OK, postRes.status)

        // List
        val listRes = client.get("/api/tenant/invoicing/templates") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, listRes.status)
        val listBody = listRes.bodyAsText()
        assertTrue(listBody.contains("Template Modern Custom"))

        // Get detail
        val detailRes = client.get("/api/tenant/invoicing/templates/tmpl-custom-001") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, detailRes.status)
    }

    @Test
    fun issuerProfileApi_get_and_put_shouldPersist() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val invoiceRepo = InMemoryInvoiceRepository()
        val templateRepo = InMemoryInvoiceTemplateRepository()
        val paymentRepo = InMemoryInvoicePaymentRepository()
        val issuerRepo = InMemoryInvoiceIssuerProfileRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                invoiceRepository = invoiceRepo,
                invoiceTemplateRepository = templateRepo,
                invoicePaymentRepository = paymentRepo,
                invoiceIssuerProfileRepository = issuerRepo
            )
        }

        val updatePayload = """
            {
                "companyName": "PT Konveksi Maju Bersama",
                "address": "Jl. Industri No. 8, Cimahi",
                "taxId": "01.999.888.7-000.000",
                "phone": "+62 811-2233-4455",
                "email": "billing@konveksimaju.com",
                "bankName": "Bank Mandiri",
                "bankAccountNumber": "130-00-1234567-8",
                "bankAccountHolder": "PT KONVEKSI MAJU BERSAMA"
            }
        """.trimIndent()

        val putRes = client.put("/api/tenant/invoicing/issuer-profile") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(updatePayload)
        }
        assertEquals(HttpStatusCode.OK, putRes.status)

        val getRes = client.get("/api/tenant/invoicing/issuer-profile") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, getRes.status)
        val body = getRes.bodyAsText()
        assertTrue(body.contains("PT Konveksi Maju Bersama"))
        assertTrue(body.contains("130-00-1234567-8"))
    }
}
