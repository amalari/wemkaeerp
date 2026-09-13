package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonWriter
import com.eventverse.app.shared.pipeline.TenantEntitlementGrantsCodec
import com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Pandangan superadmin atas satu tenant: identitas, paket, dan modul yang disambungkan.
 *
 * `grantedModules` di sini sudah **dipadatkan** menjadi himpunan eksplisit. Di wire format,
 * `null` berarti "apa pun yang diberikan paket"; membiarkan arti itu merembes ke UI akan memaksa
 * setiap toggle menerjemahkannya ulang, dan cukup satu tempat yang lupa untuk membuat toggle
 * pertama mencabut delapan modul sekaligus.
 */
data class TenantAdminView(
    val slug: String,
    val name: String,
    val grantedModules: Set<BusinessModule>,
    val grantedCustomModuleIds: Set<String>,
    val catalog: TenantModuleCatalogSnapshot
) {
    /** Modul produksi yang sedang aktif di kanvas — angka yang dibatasi kuota paket. */
    val activeOperationalModules: Int get() = catalog.activeModuleCount

    val maxActiveModules: Int get() = catalog.maxActiveModules

    fun toGrants(): TenantEntitlementGrants = TenantEntitlementGrants(
        grantedModules = grantedModules.takeIf { it != BusinessModule.entries.toSet() },
        grantedCustomModuleIds = grantedCustomModuleIds
    )
}

/**
 * Klien untuk rute platform di bawah `/api/admin/`.
 *
 * Seluruh rute di bawahnya sudah dibatasi `TenantResolutionPlugin.platformRoutePrefixes` untuk
 * `PLATFORM_SUPERADMIN` saja, jadi klien ini tidak perlu — dan tidak boleh — memeriksa ulang
 * wewenangnya sendiri. Menyembunyikan tombolnya di UI adalah kenyamanan, bukan penjagaan.
 */
class AdminApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) {
    private fun resolveUrl(slug: String, suffix: String = ""): String {
        val path = "/api/admin/tenants/$slug$suffix"
        return if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path
    }

    /** GET /api/admin/tenants/{slug} */
    suspend fun getTenantAdminView(tenantSlug: String): Result<TenantAdminView> = runCatching {
        val response = httpClient.get(resolveUrl(tenantSlug)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat data tenant (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseAdminView(response.bodyAsText())
    }

    /** PUT /api/admin/tenants/{slug}/entitlement */
    suspend fun setEntitlement(
        tenantSlug: String,
        grants: TenantEntitlementGrants
    ): Result<TenantAdminView> = runCatching {
        val response = httpClient.put(resolveUrl(tenantSlug, "/entitlement")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(JsonWriter.write(TenantEntitlementGrantsCodec.encode(grants)))
        }
        if (!response.status.isSuccess()) {
            // Pesan server diteruskan apa adanya: penolakan di sini biasanya bukan kesalahan teknis
            // melainkan kalimat yang menjelaskan alur tenant mana yang akan menjadi tidak valid.
            error(response.bodyAsText().ifBlank { "Gagal menyimpan entitlement (HTTP ${response.status.value})" })
        }
        parseAdminView(response.bodyAsText())
    }

    private fun parseAdminView(rawJson: String): TenantAdminView {
        val root = JsonParser.parseObject(rawJson)
        val grants = TenantEntitlementGrantsCodec.decode(root)

        return TenantAdminView(
            slug = root.string("slug") ?: "",
            name = root.string("name") ?: "",
            grantedModules = grants.grantedModules ?: BusinessModule.entries.toSet(),
            grantedCustomModuleIds = grants.grantedCustomModuleIds,
            catalog = TenantModuleCatalogCodec.decode(rawJson)
        )
    }
}
