package com.eventverse.app.routes

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

import com.eventverse.app.domain.rbac.RoleRepository

import com.eventverse.app.domain.rbac.ModuleAssignmentRepository

import com.eventverse.app.domain.rbac.BusinessModule

import com.eventverse.app.domain.rbac.AccessLevel

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.masterdata.usecases.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.masterdata.MaterialCatalogPageCodec
import com.eventverse.app.shared.masterdata.MaterialItemCodec
import com.eventverse.app.shared.masterdata.MaterialPriceCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun Route.masterDataRoutes(
    materialRepository: MaterialItemRepository,
    priceRepository: MaterialPriceRepository,
    customFieldRepository: CustomFieldDefinitionRepository,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository
) {
    val createMaterialUseCase = CreateMaterialItemUseCase(materialRepository, customFieldRepository)
    val updateMaterialUseCase = UpdateMaterialItemUseCase(materialRepository, priceRepository, customFieldRepository)
    val setStandardPriceUseCase = SetMaterialStandardPriceUseCase(materialRepository, priceRepository)
    val archiveMaterialUseCase = ArchiveMaterialItemUseCase(materialRepository)
    val priceResolver = PriceSourceResolver(
        strategies = mapOf(PriceSource.STANDARD to StandardPriceSource(priceRepository)),
        materialRepository = materialRepository
    )
    val resolvePriceUseCase = ResolveMaterialPriceUseCase(priceResolver, priceRepository)

    route("/api/tenant/master-data") {
        // B5. Daftar/detail bahan juga dibaca dropdown bahan di Sampling & Tech Pack; harga = data keuangan.
        moduleGate(GarmentModules.MASTER_DATA, roleRepository, moduleAssignmentRepository, write = AccessLevel.OPERATE) { method, path ->
            val isPrice = path.endsWith("/prices") || path.contains("/price-policy") || path.contains("/price-resolution")
            when {
                method == io.ktor.http.HttpMethod.Get && isPrice ->
                    GateRule(AccessLevel.VIEW, listOf(GarmentModules.MASTER_DATA, GarmentModules.COSTING_HPP))
                method == io.ktor.http.HttpMethod.Get ->
                    GateRule(AccessLevel.VIEW, listOf(GarmentModules.MASTER_DATA, GarmentModules.SAMPLING_ORDER, GarmentModules.TECH_PACK_BOM))
                path.contains("/price-policy") -> GateRule(AccessLevel.MANAGE, listOf(GarmentModules.MASTER_DATA))
                else -> null
            }
        }

        // GET /api/tenant/master-data/materials (Search & List)
        get("/materials") {
            val tenant = call.requireTenant() ?: return@get
            val q = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() }
            val category = call.request.queryParameters["category"]?.let {
                runCatching { MaterialCategory.valueOf(it.uppercase()) }.getOrNull()
            }
            val ownership = call.request.queryParameters["ownership"]?.let {
                runCatching { StockOwnershipSemantics.valueOf(it.uppercase()) }.getOrNull()
            }
            val activeOnly = call.request.queryParameters["activeOnly"]?.toBooleanStrictOrNull() ?: true
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val query = MaterialCatalogQuery(
                queryText = q,
                category = category,
                ownership = ownership,
                includeArchived = !activeOnly,
                page = page,
                pageSize = pageSize
            )

            val resultPage = materialRepository.searchCatalog(tenant.tenantId, query)
            call.respondJson(MaterialCatalogPageCodec.encode(resultPage).encode())
        }

        // POST /api/tenant/master-data/materials (Create)
        post("/materials") {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val name = json.string("name")?.trim() ?: ""
            val code = json.string("code")?.trim()
            val category = MaterialCategory.fromCode(json.string("category"))
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing or invalid category")

            val baseUom = json.string("baseUom")?.let {
                UnitOfMeasure.fromCode(it)
            }

            val alternateUoms = json.objectArray("alternateUoms").mapNotNull { obj ->
                runCatching { MaterialItemCodec.decodeConversion(obj) }.getOrNull()
            }

            val defaultOwnership = json.string("defaultOwnership")?.let {
                runCatching { StockOwnershipSemantics.valueOf(it.uppercase()) }.getOrNull()
            } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

            val description = json.string("description") ?: ""

            val customValues = mutableMapOf<CustomFieldId, JsonValue.Obj?>()
            val caObj = json.obj("customAttributes")
            if (caObj != null) {
                caObj.entries.forEach { (key, value) ->
                    customValues[CustomFieldId(key)] = value as? JsonValue.Obj
                }
            }

            val command = CreateMaterialCommand(
                tenantId = tenant.tenantId,
                code = code,
                name = name,
                category = category,
                baseUom = baseUom,
                alternateUoms = alternateUoms,
                defaultOwnership = defaultOwnership,
                description = description,
                customValues = customValues
            )

            createMaterialUseCase(command)
                .onSuccess { item ->
                    call.respondJson(MaterialItemCodec.encodeMaterial(item).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // GET /api/tenant/master-data/materials/{id} (Detail)
        get("/materials/{id}") {
            val tenant = call.requireTenant() ?: return@get
            val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val item = materialRepository.findById(tenant.tenantId, MaterialId(idParam))
            if (item == null) {
                call.respond(HttpStatusCode.NotFound, "Material '$idParam' tidak ditemukan")
            } else {
                call.respondJson(MaterialItemCodec.encodeMaterial(item).encode())
            }
        }

        // PUT /api/tenant/master-data/materials/{id} (Update)
        put("/materials/{id}") {
            val tenant = call.requireTenant() ?: return@put
            val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val name = json.string("name")
            val description = json.string("description")
            val category = json.string("category")?.let { MaterialCategory.fromCode(it) }
            val alternateUoms = if (json.has("alternateUoms")) {
                json.objectArray("alternateUoms").mapNotNull { obj ->
                    runCatching { MaterialItemCodec.decodeConversion(obj) }.getOrNull()
                }
            } else null

            val defaultOwnership = json.string("defaultOwnership")?.let {
                runCatching { StockOwnershipSemantics.valueOf(it.uppercase()) }.getOrNull()
            }

            val customValues = mutableMapOf<CustomFieldId, JsonValue.Obj?>()
            val caObj = json.obj("customAttributes")
            if (caObj != null) {
                caObj.entries.forEach { (key, value) ->
                    customValues[CustomFieldId(key)] = value as? JsonValue.Obj
                }
            }

            val command = UpdateMaterialCommand(
                tenantId = tenant.tenantId,
                materialId = MaterialId(idParam),
                name = name,
                description = description,
                category = category,
                alternateUoms = alternateUoms,
                defaultOwnership = defaultOwnership,
                customValues = customValues
            )

            updateMaterialUseCase(command)
                .onSuccess { item ->
                    call.respondJson(MaterialItemCodec.encodeMaterial(item).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // POST /api/tenant/master-data/materials/{id}/archive (Archive / Soft Delete)
        post("/materials/{id}/archive") {
            val tenant = call.requireTenant() ?: return@post
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            archiveMaterialUseCase(tenant.tenantId, MaterialId(idParam))
                .onSuccess {
                    val updated = materialRepository.findById(tenant.tenantId, MaterialId(idParam))
                    if (updated != null) {
                        call.respondJson(MaterialItemCodec.encodeMaterial(updated).encode())
                    } else {
                        call.respond(HttpStatusCode.OK, "Material archived")
                    }
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // GET /api/tenant/master-data/materials/{id}/prices (Price History)
        get("/materials/{id}/prices") {
            val tenant = call.requireTenant() ?: return@get
            val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val history = priceRepository.historyFor(tenant.tenantId, MaterialId(idParam))
            call.respondJson(MaterialPriceCodec.encodeHistory(history).encode())
        }

        // POST /api/tenant/master-data/materials/{id}/prices (Append Standard Price)
        post("/materials/{id}/prices") {
            val tenant = call.requireTenant() ?: return@post
            val idParam = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val unitPriceObj = json.obj("unitPrice")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing unitPrice")
            val unitPrice = MeasureCodec.decodeUnitPrice(unitPriceObj)

            val effectiveFrom = json.string("effectiveFrom")?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: Clock.System.now()

            val note = json.string("note") ?: ""
            val recordedByUserId = json.string("recordedByUserId") ?: ""

            val command = SetMaterialStandardPriceCommand(
                tenantId = tenant.tenantId,
                materialId = MaterialId(idParam),
                unitPrice = unitPrice,
                effectiveFrom = effectiveFrom,
                note = note,
                recordedByUserId = recordedByUserId
            )

            setStandardPriceUseCase(command)
                .onSuccess { price ->
                    call.respondJson(MaterialPriceCodec.encodePrice(price).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        // GET /api/tenant/master-data/price-resolution (Point-in-time Price Resolver)
        get("/price-resolution") {
            val tenant = call.requireTenant() ?: return@get
            val materialIdParam = call.request.queryParameters["materialId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing materialId parameter")

            val atInstant = call.request.queryParameters["at"]?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: Clock.System.now()

            val ownership = call.request.queryParameters["ownership"]?.let {
                runCatching { StockOwnershipSemantics.valueOf(it.uppercase()) }.getOrNull()
            } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

            val targetUom = call.request.queryParameters["targetUom"]?.let {
                UnitOfMeasure.fromCode(it)
            }

            val query = PriceQuery(
                tenantId = tenant.tenantId,
                materialId = MaterialId(materialIdParam),
                at = atInstant,
                ownership = ownership,
                targetUom = targetUom
            )

            resolvePriceUseCase(query)
                .onSuccess { resolved ->
                    call.respondJson(MaterialPriceCodec.encodeResolvedPrice(resolved).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.NotFound, it) }
        }

        // GET /api/tenant/master-data/price-policy
        get("/price-policy") {
            val tenant = call.requireTenant() ?: return@get
            val policy = priceRepository.policyFor(tenant.tenantId)
            call.respondJson(MaterialPriceCodec.encodePolicy(policy).encode())
        }

        // PUT /api/tenant/master-data/price-policy
        put("/price-policy") {
            val tenant = call.requireTenant() ?: return@put
            val body = call.receiveText()
            val json = JsonParser.parse(body) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val policy = MaterialPriceCodec.decodePolicy(json).copy(tenantId = tenant.tenantId)
            priceRepository.savePolicy(policy)
            call.respondJson(MaterialPriceCodec.encodePolicy(policy).encode())
        }
    }
}

private suspend fun ApplicationCall.requireTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Unknown error")
}
