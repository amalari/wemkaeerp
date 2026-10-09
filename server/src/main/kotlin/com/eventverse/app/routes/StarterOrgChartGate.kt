package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.StarterOrgChartPolicy
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.displayName
import com.eventverse.app.domain.tenant.TenantContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Gerbang pack untuk `POST …/restore-presets` Org Chart (TRD-PLAT-010 K3/FR-4).
 *
 * Dipanggil **setelah** pemeriksaan wewenang (403 fail-closed), jadi peran tak berwenang tidak pernah belajar
 * apakah pack tenant punya contoh. Mengembalikan `false` **dan sudah menjawab 409** bila pack tenant tak
 * menyediakan contoh bagan organisasi; tidak ada tulisan ke database dalam kasus itu. Pack yang tak bisa
 * di-resolve diperlakukan sama (tidak ada contoh) — tidak jatuh ke contoh garment (Kontrak 4).
 */
internal suspend fun ApplicationCall.requireStarterOrgChart(tenant: TenantContext): Boolean {
    val pack = runCatching { tenant.pack }.getOrNull()
    if (pack != null && StarterOrgChartPolicy.isAvailableFor(pack)) return true

    respond(
        HttpStatusCode.Conflict,
        pack?.let { StarterOrgChartPolicy.unavailableMessage(it) }
            ?: "Jenis usaha ini belum punya contoh bagan organisasi. Tambahkan divisi dan karyawan secara manual."
    )
    return false
}

/**
 * Penutup celah `restore-presets` karyawan: [orgChartDecision] mengembalikan `null` ("tidak diketahui", dibaca
 * permisif) untuk pemanggil tanpa jabatan dan tanpa divisi, sehingga token berperan lemah (mis. `SALES` tanpa
 * `customRoleId`) lolos `requireOrgChartAccess`. Route divisi tertutup oleh `moduleGate`; route karyawan tidak
 * memasangnya. Untuk **tulisan massal yang menimpa seluruh bagan** ini tidak boleh permisif: bila repository wewenang
 * terpasang, pemanggil wajib MANAGE atas Bagan Organisasi menurut [moduleDecision] — sama dengan gerbang divisi
 * (Owner/superadmin tanpa jabatan tetap lolos). Menjawab 403 dan mengembalikan `false` bila tidak.
 */
internal suspend fun ApplicationCall.requireManageOrgChartForBulkWrite(
    tenant: TenantContext,
    roleRepository: RoleRepository?,
    moduleAssignmentRepository: ModuleAssignmentRepository?
): Boolean {
    if (roleRepository == null || moduleAssignmentRepository == null) return true
    val level = moduleDecision(GarmentModules.ORG_CHART, tenant, roleRepository, moduleAssignmentRepository).config.level
    if (level.isAtLeast(AccessLevel.MANAGE)) return true
    respond(
        HttpStatusCode.Forbidden,
        "Butuh wewenang ${AccessLevel.MANAGE.displayName} atas modul \"${GarmentModules.ORG_CHART.displayName}\"."
    )
    return false
}
