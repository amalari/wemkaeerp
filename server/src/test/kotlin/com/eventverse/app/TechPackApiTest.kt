package com.eventverse.app

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.techpack.TechPackStatus
import com.eventverse.app.domain.techpack.usecases.CreateBlankTechPackCommand
import com.eventverse.app.domain.techpack.usecases.CreateTechPackDraftUseCase
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.techpack.BomCostPreviewCodec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.*

class TechPackApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T00:00:00Z")

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
    fun getTechPacks_initiallyEmpty_shouldReturnEmptyPage() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val techPackRepo = InMemoryTechPackRepository()
        val samplingRepo = InMemorySamplingOrderRepository()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                techPackRepository = techPackRepo,
                samplingOrderRepository = samplingRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo
            )
        }

        val res = client.get("/api/tenant/tech-pack") {
            asTenant(tenantSlug)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"items\":[]"))
        assertTrue(body.contains("\"totalCount\":0"))
    }

    @Test
    fun createBlankDraft_andFetchDetail_shouldSucceed() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val techPackRepo = InMemoryTechPackRepository()
        val samplingRepo = InMemorySamplingOrderRepository()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                techPackRepository = techPackRepo,
                samplingOrderRepository = samplingRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo
            )
        }

        val createRes = client.post("/api/tenant/tech-pack/blank") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"styleName":"Oversized Knit Sweater","clientName":"Brand Distro"}""")
        }

        assertEquals(HttpStatusCode.OK, createRes.status)
        val createdJson = (JsonParser.parse(createRes.bodyAsText()) as JsonValue.Obj)
        val tpId = createdJson.string("id")!!
        val styleCode = createdJson.string("styleCode")!!
        assertEquals("Oversized Knit Sweater", createdJson.string("styleName"))
        assertEquals("DRAFT", createdJson.string("status"))
        assertEquals(1, createdJson.int("version"))

        // Fetch detail
        val detailRes = client.get("/api/tenant/tech-pack/$tpId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, detailRes.status)
        val detailJson = (JsonParser.parse(detailRes.bodyAsText()) as JsonValue.Obj)
        assertEquals(tpId, detailJson.string("id"))
        assertEquals(styleCode, detailJson.string("styleCode"))
    }

    @Test
    fun createFromSampling_whenApproved_shouldInheritKnitSpecsAndMilestones() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val techPackRepo = InMemoryTechPackRepository()
        val samplingRepo = InMemorySamplingOrderRepository()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        // Seed an ACC_APPROVED sampling order
        val samplingOrder = SamplingOrder(
            id = SamplingOrderId("smp-001"),
            tenantId = tenantId,
            spkNumber = SpkNumber("SPK-SMP-0099"),
            clientName = "Misty Garden",
            styleName = "Cardigan Rajut V-Neck",
            status = SamplingStatus.ACC_APPROVED,
            knitSpec = KnitSpec(yarnType = "Katun Combed 30s"),
            yieldAndTiming = YieldAndTiming(
                panelWeights = PanelWeightGrams(front = 120.0, back = 110.0, sleeve = 80.0, collar = 20.0, placket = 15.0),
                panelMinutes = PanelKnittingMinutes(front = 25, back = 22, sleeve = 15, collar = 5, placket = 4),
                additionalProcess = "Kancing Batok 5 pcs"
            ),
            createdAt = now,
            updatedAt = now
        )
        samplingRepo.save(samplingOrder)

        application {
            module(
                tenantRepository = tenantRepo,
                techPackRepository = techPackRepo,
                samplingOrderRepository = samplingRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo
            )
        }

        val res = client.post("/api/tenant/tech-pack/from-sampling") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"samplingOrderId":"smp-001"}""")
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val createdJson = (JsonParser.parse(res.bodyAsText()) as JsonValue.Obj)
        assertEquals("SPK-SMP-0099", createdJson.string("sourceSpkNumber"))
        assertEquals("smp-001", createdJson.string("sourceSampleSpecId"))

        val bomLines = createdJson.objectArray("bomLines")
        assertTrue(bomLines.isNotEmpty(), "Should have populated initial BOM lines from sample")
        val laborOps = createdJson.objectArray("laborOperations")
        assertTrue(laborOps.isNotEmpty(), "Should have populated initial labor operations from sample")
    }

    @Test
    fun bomLifecycle_updateBom_resolveMaterials_previewCost_release_andRevise() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val techPackRepo = InMemoryTechPackRepository()
        val samplingRepo = InMemorySamplingOrderRepository()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()

        // Seed master data material and standard price
        val yarnItem = MaterialItem(
            id = MaterialId("mat-yarn-1"),
            tenantId = tenantId,
            code = MaterialCode("YRN-0001"),
            name = "Benang Katun 30s Navy",
            category = MaterialCategory.YARN,
            baseUom = UnitOfMeasure.KILOGRAM,
            defaultOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
            createdAt = now,
            updatedAt = now
        )
        materialRepo.save(yarnItem)
        priceRepo.append(
            MaterialPrice(
                id = MaterialPriceId("prc-yarn-1"),
                tenantId = tenantId,
                materialId = yarnItem.id,
                source = PriceSource.STANDARD,
                unitPrice = UnitPrice(
                    amount = Money.idr(120_000),
                    per = Quantity.of(1.0, UnitOfMeasure.KILOGRAM)
                ),
                effectiveFrom = now,
                recordedAt = now
            )
        )

        application {
            module(
                tenantRepository = tenantRepo,
                techPackRepository = techPackRepo,
                samplingOrderRepository = samplingRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo
            )
        }

        // 1. Create Blank Tech Pack
        val createRes = client.post("/api/tenant/tech-pack/blank") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"styleName":"Cardigan Navy"}""")
        }
        assertEquals(HttpStatusCode.OK, createRes.status)
        val tpId = (JsonParser.parse(createRes.bodyAsText()) as JsonValue.Obj).string("id")!!

        // 2. Put BOM with free text matching the master data name
        val bomPayload = """
            {
                "lines": [
                    {
                        "lineId": "line-1",
                        "material": {
                            "freeText": "Benang Katun 30s Navy"
                        },
                        "category": "yarn",
                        "netQuantityPerGarment": {
                            "micros": 200000000,
                            "uom": "g"
                        },
                        "wasteAllowance": {
                            "numerator": 5,
                            "denominator": 100
                        },
                        "ownership": "owned_raw_material",
                        "notes": "Badan dan lengan"
                    }
                ]
            }
        """.trimIndent()

        val putBomRes = client.put("/api/tenant/tech-pack/$tpId/bom") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody(bomPayload)
        }
        assertEquals(HttpStatusCode.OK, putBomRes.status)

        // 3. Resolve Materials
        val resolveRes = client.post("/api/tenant/tech-pack/$tpId/resolve-materials") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, resolveRes.status)
        val resolvedJson = (JsonParser.parse(resolveRes.bodyAsText()) as JsonValue.Obj)
        val lineAfterResolve = resolvedJson.objectArray("bomLines").first()
        val matRefObj = lineAfterResolve.obj("material")!!
        assertEquals("mat-yarn-1", matRefObj.string("materialId"))
        assertEquals("YRN-0001", matRefObj.string("resolvedCode"))

        // 4. Preview Cost
        val costRes = client.get("/api/tenant/tech-pack/$tpId/cost-preview?orderQuantity=100") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, costRes.status)
        val costJson = (JsonParser.parse(costRes.bodyAsText()) as JsonValue.Obj)
        val preview = BomCostPreviewCodec.decode(costJson)
        assertTrue(preview.isComplete)
        // 200g net + 5% waste = 210g = 0.21 kg per garment. Rate Rp 120.000 / kg -> Rp 25.200 per garment. Total for 100 = Rp 2.520.000
        assertEquals(Money.idr(25_200), preview.materialCostPerGarment)
        assertEquals(Money.idr(2_520_000), preview.materialCostTotal)

        // 5. Release
        val releaseRes = client.post("/api/tenant/tech-pack/$tpId/release") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, releaseRes.status)
        val releasedJson = (JsonParser.parse(releaseRes.bodyAsText()) as JsonValue.Obj)
        assertEquals("RELEASED", releasedJson.string("status"))
        assertNotNull(releasedJson.string("releasedAt"))

        // 6. Revise (creates v2)
        val reviseRes = client.post("/api/tenant/tech-pack/$tpId/revise") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, reviseRes.status)
        val revisedJson = (JsonParser.parse(reviseRes.bodyAsText()) as JsonValue.Obj)
        assertEquals("DRAFT", revisedJson.string("status"))
        assertEquals(2, revisedJson.int("version"))
        assertNotEquals(tpId, revisedJson.string("id"))

        // 7. Check versions endpoint
        val styleCode = revisedJson.string("styleCode")!!
        val versionsRes = client.get("/api/tenant/tech-pack/by-style/$styleCode/versions") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, versionsRes.status)
        val versionsArray = (JsonParser.parse(versionsRes.bodyAsText()) as JsonValue.Arr)
        assertEquals(2, versionsArray.items.size)
    }

    @Test
    fun costPreview_whenUserHasNoMasterDataAccess_shouldReturnForbidden() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val techPackRepo = InMemoryTechPackRepository()
        val samplingRepo = InMemorySamplingOrderRepository()
        val materialRepo = InMemoryMaterialItemRepository()
        val priceRepo = InMemoryMaterialPriceRepository()
        val roleRepo = InMemoryRoleRepository()
        val assignmentRepo = InMemoryModuleAssignmentRepository()

        // Operator role with no master data access
        val roleId = RoleId("role-sewing-operator")
        val customRole = CustomRole(
            id = roleId,
            tenantId = tenantId,
            name = "Operator Jahit",
            description = "Operator",
            modulePermissions = mapOf(
                GarmentModules.TECH_PACK_BOM to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                GarmentModules.MASTER_DATA to ModuleAccessConfig(AccessLevel.NONE, DataScope.ALL_TENANT_DATA)
            )
        )
        roleRepo.save(customRole)

        // Seed a draft tech pack
        val blankCommand = CreateBlankTechPackCommand(tenantId = tenantId, styleName = "Test Shirt")
        val draft = CreateTechPackDraftUseCase(techPackRepo).createBlank(blankCommand).getOrThrow()

        application {
            module(
                tenantRepository = tenantRepo,
                techPackRepository = techPackRepo,
                samplingOrderRepository = samplingRepo,
                materialItemRepository = materialRepo,
                materialPriceRepository = priceRepo,
                roleRepository = roleRepo,
                moduleAssignmentRepository = assignmentRepo
            )
        }

        // Token with role-sewing-operator
        val res = client.get("/api/tenant/tech-pack/${draft.id.value}/cost-preview") {
            asStaff(
                tenantSlug = tenantSlug,
                customRoleId = roleId.value,
                departmentId = "dept-sewing"
            )
        }

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertTrue(res.bodyAsText().contains("wewenang membaca data harga"))
    }
}
