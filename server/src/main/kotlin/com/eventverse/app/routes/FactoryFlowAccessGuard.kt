package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.grantedModulesOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Penjagaan wewenang untuk konfigurasi lokasi pabrik, bergerbang pada
 * [BusinessModule.FACTORY_FLOW].
 *
 * ## Kenapa FACTORY_FLOW, bukan modul baru
 *
 * Pemetaan simpul alur ke gedung adalah properti *topologi alur* — sekelas dengan urutan modul
 * dan bypass yang memang sudah jadi isi modul itu. Menambah nilai baru ke [BusinessModule]
 * merembet ke matriks RBAC, penugasan divisi, entitlement, seed tiap tenant, dan `when`
 * exhaustive di layar workspace; `AppNavScreen.TRACEABILITY` dan `SURAT_JALAN` sudah memilih
 * jalan menumpang ini lebih dulu, dengan alasan yang sama.
 *
 * Konsekuensi wewenangnya sudah ter-seed di V19 dan tidak perlu migrasi baru: Owner dan PPIC
 * `MANAGE`; Sales, Kepala Sales, Gudang, dan Operator `NONE`. Itu pembagian yang tepat — PPIC
 * yang tahu mesin mana ada di gedung mana.
 *
 * `MASTER_DATA` sempat jadi kandidat dan ditolak: di sana Staff Gudang punya `OPERATE`, jadi
 * menumpang di situ ikut memberi mereka hak menulis kecuali ditambah pemeriksaan `MANAGE`
 * tersendiri. Di FACTORY_FLOW mereka sudah `NONE`, jadi batasnya benar tanpa pengecualian.
 *
 * ## Kenapa penjagaannya di server
 *
 * Menyembunyikan tombol bukan penjagaan. Dan di sini taruhannya lebih dari biasanya: topologi
 * lokasi inilah yang menggerakkan gerbang perpindahan tahap, sehingga siapa pun yang bisa
 * menulisnya bisa mematikan gerbang itu dari luar.
 */
internal suspend fun ApplicationCall.factoryFlowDecision(
    tenant: TenantContext,
    roleRepository: RoleRepository?,
    moduleAssignmentRepository: ModuleAssignmentRepository?
): AccessDecision? {
    val principal = callerPrincipalOrNull ?: return null
    if (roleRepository == null || moduleAssignmentRepository == null) return null
    if (principal.customRoleId == null && principal.departmentId == null) return null

    val role = principal.customRoleId
        ?.let { runCatching { RoleId(it) }.getOrNull() }
        ?.let { roleRepository.findById(tenant.tenantId, it) }

    val persona = TestingPersona(
        userId = principal.userId.ifBlank { "unknown" },
        name = principal.email ?: principal.userId.ifBlank { "unknown" },
        tenantId = tenant.tenantId,
        tenantSlug = tenant.slug.value,
        departmentId = principal.departmentId,
        departmentName = "",
        roleId = role?.id,
        roleTitle = role?.name ?: "",
        isOwnerOrSuperAdmin = principal.isPlatformSuperadmin && role == null,
        isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
    )

    val assignments = moduleAssignmentRepository.findAllByTenant(tenant.tenantId)
    return AccessDecisionEngine.explain(
        persona = persona,
        module = BusinessModule.FACTORY_FLOW,
        role = role,
        assignments = assignments[BusinessModule.FACTORY_FLOW].orEmpty(),
        grantedModules = grantedModulesOrNull
    )
}

/**
 * Memastikan pemanggil berwenang setidaknya [required] atas Alur Pabrik.
 *
 * Mengembalikan `false` **dan sudah menjawab 403**; pemanggil tinggal `return@put`.
 *
 * Wewenang yang tidak dapat dihitung (`decision == null`) dibiarkan lewat, mengikuti pola
 * [orgChartDecision]: pemasangan route lama dan sebagian pengujian tidak menyuntikkan
 * repository wewenang sama sekali, dan menutup total di situ mematikan fitur alih-alih
 * menjaganya.
 */
internal suspend fun ApplicationCall.requireFactoryFlowAccess(
    decision: AccessDecision?,
    required: AccessLevel
): Boolean {
    val effective = decision ?: return true
    if (effective.config.level.isAtLeast(required)) return true

    respond(
        HttpStatusCode.Forbidden,
        "Butuh wewenang ${required.displayName} atas modul " +
            "\"${BusinessModule.FACTORY_FLOW.displayName}\"; wewenang Anda saat ini " +
            "${effective.config.level.displayName} (${effective.source.label})."
    )
    return false
}
