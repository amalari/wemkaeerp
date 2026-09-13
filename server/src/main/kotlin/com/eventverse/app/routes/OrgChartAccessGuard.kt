package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.OrgChartVisibility
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Penjagaan wewenang untuk rute Bagan Organisasi (karyawan & divisi).
 *
 * Ada karena menyembunyikan menu **bukan** penjagaan. Sebelum berkas ini, seorang operator jahit
 * yang matriksnya menutup modul ORG_CHART tetap menerima seluruh daftar karyawan pabrik —
 * lengkap dengan email dan nomor telepon — hanya dengan memanggil `GET /api/tenant/employees`
 * memakai token miliknya sendiri. Layarnya memang tidak muncul di drawer; datanya tetap terkirim.
 *
 * Sumber bug-nya halus dan layak diingat: [com.eventverse.app.domain.rbac.ModuleAccessConfig]
 * memakai `scope = ALL_TENANT_DATA` sebagai nilai bawaan, termasuk ketika `level = NONE`. Kode
 * yang hanya membaca *scope* dan lupa memeriksa *level* karenanya membaca "tanpa akses" sebagai
 * "seluruh data pabrik" — kebalikan persis dari maksudnya.
 */

/**
 * Wewenang efektif pemanggil atas [BusinessModule.ORG_CHART], atau `null` bila tidak dapat
 * dihitung (repository wewenang tidak dipasang, atau pemanggil tanpa identitas pabrik).
 *
 * `null` berarti *"tidak diketahui"*, dan pemanggil memperlakukannya seperti sebelum penjagaan ini
 * ada. Itu disengaja: pemasangan route lama dan sebagian pengujian tidak menyuntikkan repository
 * wewenang sama sekali, dan menutup total di situ akan mematikan fitur alih-alih menjaganya.
 */
internal suspend fun ApplicationCall.orgChartDecision(
    tenant: TenantContext,
    roleRepository: RoleRepository?,
    moduleAssignmentRepository: ModuleAssignmentRepository?
): AccessDecision? {
    val principal = callerPrincipalOrNull ?: return null
    if (roleRepository == null || moduleAssignmentRepository == null) return null

    // Tanpa jabatan dan tanpa divisi, tidak ada satu pun sumbu yang bisa memberi maupun
    // mempersempit wewenang. Menanyakannya ke database hanya menghasilkan dua query untuk jawaban
    // yang sudah pasti — dan ini pula yang menjaga token layanan tanpa identitas pabrik tetap
    // berperilaku seperti sebelumnya.
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
        // Invarian TestingPersona melarang bypass bagi persona berjabatan: memilih sebuah jabatan
        // berarti minta dilihat persis sebagai jabatan itu.
        isOwnerOrSuperAdmin = principal.isPlatformSuperadmin && role == null
    )

    val assignments = moduleAssignmentRepository.findAllByTenant(tenant.tenantId)
    return AccessDecisionEngine.explain(
        persona = persona,
        module = BusinessModule.ORG_CHART,
        role = role,
        assignments = assignments[BusinessModule.ORG_CHART].orEmpty()
    )
}

/**
 * Memastikan pemanggil berwenang setidaknya [required] atas Bagan Organisasi.
 *
 * Mengembalikan `false` **dan sudah menjawab 403** bila tidak; pemanggil tinggal `return@get`.
 * Pola "sudah menjawab" dipilih supaya setiap handler tidak perlu menyusun pesan penolakannya
 * sendiri — pesan yang berbeda-beda di tiap endpoint adalah cara termudah membuat satu di
 * antaranya bocor.
 */
internal suspend fun ApplicationCall.requireOrgChartAccess(
    decision: AccessDecision?,
    required: AccessLevel
): Boolean {
    // Wewenang tak terhitung: pertahankan perilaku lama (lihat KDoc orgChartDecision).
    val effective = decision ?: return true

    if (effective.config.level.isAtLeast(required)) return true

    respond(
        HttpStatusCode.Forbidden,
        "Butuh wewenang ${required.displayName} atas modul " +
            "\"${BusinessModule.ORG_CHART.displayName}\"; wewenang Anda saat ini " +
            "${effective.config.level.displayName} (${effective.source.label})."
    )
    return false
}

/**
 * Mempersempit daftar karyawan menurut jangkauan data pemanggil.
 *
 * Dipanggil **setelah** [requireOrgChartAccess] memastikan levelnya memadai, sehingga di sini
 * scope-nya sudah pasti bermakna. Memanggilnya tanpa pemeriksaan level itulah bug aslinya.
 *
 * Inilah sisi penentu dari `ScopeCapability.HIERARCHICAL`. Klien menjalankan penyaringan yang sama
 * agar layarnya konsisten seketika, tetapi penyaringan yang hanya hidup di klien tidak
 * menyembunyikan apa pun — payload-nya tetap utuh dan terbaca di panel jaringan.
 */
internal fun applyOrgChartScope(
    employees: List<OrgNode>,
    decision: AccessDecision?,
    viewerEmail: String?,
    viewerDepartmentId: String?
): List<OrgNode> {
    val scope = decision?.config?.scope ?: return employees
    if (scope == DataScope.ALL_TENANT_DATA) return employees

    // Baris karyawan milik penonton dicocokkan lewat email: `users` menyimpan divisi dan jabatan
    // sejak V17, tetapi tidak menyimpan tautan ke baris `employees`. Email unik per tenant, jadi
    // pencocokan ini deterministik — dan bila tidak ketemu, penonton memang bukan karyawan
    // terdaftar sehingga hanya sumbu divisi yang berlaku baginya.
    val viewerEmployeeId = viewerEmail
        ?.let { email -> employees.firstOrNull { it.email.equals(email, ignoreCase = true) }?.id }

    return OrgChartVisibility.visibleTo(
        nodes = employees,
        scope = scope,
        viewerEmployeeId = viewerEmployeeId,
        viewerDepartmentId = viewerDepartmentId
    )
}
