package com.eventverse.app

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MasterDataApiTest {

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

    @Test
    fun getMaterials_initiallyEmpty_shouldReturnEmptyCatalogPage() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo,
                customFieldDefinitionRepository = InMemoryCustomFieldDefinitionRepository()
            )
        }

        val res = client.get("/api/tenant/master-data/materials") {
            asTenant(tenantSlug)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"items\":[]"))
        assertTrue(body.contains("\"totalCount\":0"))
    }

    @Test
    fun createMaterial_andGetDetail_shouldPersistAndReserveCode() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo,
                customFieldDefinitionRepository = InMemoryCustomFieldDefinitionRepository()
            )
        }

        val createPayload = """
            {
                "name": "Benang Cotton Combed 30s Reaktif",
                "category": "YARN",
                "baseUom": "KILOGRAM",
                "description": "Benang katun 30s premium untuk rajutan sweater",
                "alternateUoms": [
                    {
                        "from": "cone",
                        "equivalent": {
                            "micros": 1200000,
                            "uom": "kg"
                        }
                    }
                ]
            }
        """.trimIndent()

        val createRes = client.post("/api/tenant/master-data/materials") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(createPayload)
        }

        assertEquals(HttpStatusCode.OK, createRes.status)
        val createBody = createRes.bodyAsText()
        assertTrue(createBody.contains("Benang Cotton Combed 30s Reaktif"))
        assertTrue(createBody.contains("YRN-0001"))
        assertTrue(createBody.contains("\"baseUom\":\"kg\""))

        val materialId = createBody.substringAfter("\"id\":\"").substringBefore("\"")
        assertFalse(materialId.isBlank())

        // Fetch detail
        val detailRes = client.get("/api/tenant/master-data/materials/$materialId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, detailRes.status)
        val detailBody = detailRes.bodyAsText()
        assertTrue(detailBody.contains(materialId))
        assertTrue(detailBody.contains("YRN-0001"))
    }

    @Test
    fun updateMaterial_andArchive_shouldModifyAndSoftDelete() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo,
                customFieldDefinitionRepository = InMemoryCustomFieldDefinitionRepository()
            )
        }

        val createPayload = """
            {
                "name": "Kancing Batok Kelapa 18L",
                "category": "TRIM",
                "baseUom": "PIECE"
            }
        """.trimIndent()

        val createRes = client.post("/api/tenant/master-data/materials") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(createPayload)
        }
        val matId = createRes.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        // Update
        val updatePayload = """
            {
                "name": "Kancing Batok Kelapa 18L Natural Finishing",
                "description": "Kancing ramah lingkungan 4 lubang"
            }
        """.trimIndent()

        val updateRes = client.put("/api/tenant/master-data/materials/$matId") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(updatePayload)
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        assertTrue(updateRes.bodyAsText().contains("Kancing Batok Kelapa 18L Natural Finishing"))

        // Archive
        val archiveRes = client.post("/api/tenant/master-data/materials/$matId/archive") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, archiveRes.status)

        // Verify it disappears from active catalog by default
        val catalogRes = client.get("/api/tenant/master-data/materials?activeOnly=true") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, catalogRes.status)
        assertFalse(catalogRes.bodyAsText().contains("Kancing Batok Kelapa 18L Natural Finishing"))
    }

    @Test
    fun priceManagement_andPriceResolution_shouldWorkEndToEnd() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo,
                customFieldDefinitionRepository = InMemoryCustomFieldDefinitionRepository()
            )
        }

        // 1. Create material with cone conversion (1 cone = 1.2 kg)
        val matPayload = """
            {
                "name": "Benang Wool Acryl 2/32",
                "category": "YARN",
                "baseUom": "kg",
                "alternateUoms": [
                    {
                        "from": "cone",
                        "equivalent": {
                            "micros": 1200000,
                            "uom": "kg"
                        }
                    }
                ]
            }
        """.trimIndent()

        val matRes = client.post("/api/tenant/master-data/materials") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(matPayload)
        }
        val matId = matRes.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        // 2. Set standard price: Rp 120.000 / 1 kg
        val pricePayload = """
            {
                "unitPrice": {
                    "amount": {
                        "minor": 12000000,
                        "currency": "IDR"
                    },
                    "per": {
                        "micros": 1000000,
                        "uom": "kg"
                    }
                },
                "note": "Tarif kontrak Q1 2026"
            }
        """.trimIndent()

        val setPriceRes = client.post("/api/tenant/master-data/materials/$matId/prices") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(pricePayload)
        }
        assertEquals(HttpStatusCode.OK, setPriceRes.status)

        // 3. Check price history
        val historyRes = client.get("/api/tenant/master-data/materials/$matId/prices") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, historyRes.status)
        assertTrue(historyRes.bodyAsText().contains("Tarif kontrak Q1 2026"))

        // 4. Resolve price in base unit (kg)
        val resolveRes = client.get("/api/tenant/master-data/price-resolution?materialId=$matId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, resolveRes.status)
        assertTrue(resolveRes.bodyAsText().contains("12000000"))

        // 5. Resolve price for consigned client material -> Contract 3 & 4: must be Rp 0
        val consignedRes = client.get("/api/tenant/master-data/price-resolution?materialId=$matId&ownership=CONSIGNED_CLIENT_MATERIAL") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, consignedRes.status)
        val consignedBody = consignedRes.bodyAsText()
        assertTrue(consignedBody.contains("\"minor\":0"))
        assertTrue(consignedBody.contains("CLIENT_SUPPLIED_ZERO"))
    }
}
