package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.OrgChartVisibility
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.TShapeHierarchyResult
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
        isOwnerOrSuperAdmin = principal.isPlatformSuperadmin && role == null,
        isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
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
 * Batas pemanggil: divisi dan karyawan mana yang boleh ia sentuh — baik dibaca satu-satu lewat
 * `GET /{id}`, maupun ditulis lewat POST/PUT/DELETE.
 *
 * Semula bernama `OrgChartWriteReach` dan hanya menjaga sisi tulis. Ternyata `GET /{id}` punya
 * lubang yang sebentuk: `GET /employees` menyaring dengan benar, tetapi seseorang bisa melewatinya
 * begitu saja dengan menebak id (`emp-joko`, `emp-budi`, ...) dan memanggil `GET /employees/{id}`
 * langsung — endpoint itu memeriksa *level* tapi tidak pernah memeriksa *scope*. Aturannya satu
 * kalimat, berlaku untuk baca maupun tulis: **yang boleh disentuh adalah yang boleh dilihat di
 * daftarnya.** Himpunan di sini diturunkan dari daftar karyawan yang sudah tersaring, jadi baca
 * satu-satu, tulis, dan list mustahil menyimpang satu sama lain.
 */
internal class OrgChartDataReach private constructor(
    /** True bila pemanggil berjangkauan `ALL_TENANT_DATA` — tidak ada batasan sama sekali. */
    val isUnrestricted: Boolean,
    private val departmentIds: Set<String>,
    private val employeeIds: Set<String>
) {
    /**
     * Divisi `null` berarti direksi — orang di puncak bagan yang tidak berada di divisi mana pun.
     * Hanya pemanggil tak terbatas yang boleh menyentuhnya; jangkauan sempit apa pun tidak punya
     * dasar untuk mengangkat seseorang ke luar struktur divisi.
     */
    fun allowsDepartment(departmentId: String?): Boolean = when {
        isUnrestricted -> true
        departmentId == null -> false
        else -> departmentId in departmentIds
    }

    fun allowsEmployee(employeeId: String): Boolean =
        isUnrestricted || employeeId in employeeIds

    companion object {
        fun from(
            decision: AccessDecision?,
            scopedEmployees: List<OrgNode>,
            viewerDepartmentId: String?
        ): OrgChartDataReach {
            val scope = decision?.config?.scope
            if (scope == null || scope == DataScope.ALL_TENANT_DATA) {
                return OrgChartDataReach(isUnrestricted = true, emptySet(), emptySet())
            }

            // Divisi penonton ikut disertakan meski divisinya sedang kosong. Tanpa itu, kepala
            // divisi baru yang belum punya anggota tidak akan pernah bisa menambahkan orang
            // pertamanya — jangkauannya kosong, jadi setiap penambahan ditolak.
            val departments = scopedEmployees
                .mapNotNull { it.department?.id?.value }
                .toMutableSet()
            viewerDepartmentId?.let { departments += it }

            return OrgChartDataReach(
                isUnrestricted = false,
                departmentIds = departments,
                employeeIds = scopedEmployees.map { it.id.value }.toSet()
            )
        }
    }
}

/**
 * Menghitung jangkauan pemanggil dari daftar karyawan **penuh** satu tenant.
 *
 * Penyaringannya dilakukan di sini, bukan diminta dari pemanggil, supaya jangkauan baca-satu-per-
 * satu, tulis, dan list tidak mungkin dihitung dengan aturan yang berbeda — ketiganya melewati
 * [applyOrgChartScope] yang sama.
 */
internal fun ApplicationCall.orgChartDataReach(
    decision: AccessDecision?,
    allEmployees: List<OrgNode>
): OrgChartDataReach {
    val viewerDepartmentId = callerPrincipalOrNull?.departmentId
    val scoped = applyOrgChartScope(
        employees = allEmployees,
        decision = decision,
        viewerEmail = callerPrincipalOrNull?.email,
        viewerDepartmentId = viewerDepartmentId
    )
    return OrgChartDataReach.from(decision, scoped, viewerDepartmentId)
}

/** Menolak dengan 403 bila divisi tujuan berada di luar jangkauan pemanggil. */
internal suspend fun ApplicationCall.requireWritableDepartment(
    reach: OrgChartDataReach,
    departmentId: String?
): Boolean {
    if (reach.allowsDepartment(departmentId)) return true
    respond(
        HttpStatusCode.Forbidden,
        "Divisi tujuan berada di luar jangkauan data Anda. Jangkauan wewenang Anda atas modul " +
            "\"${BusinessModule.ORG_CHART.displayName}\" tidak mencakup divisi tersebut."
    )
    return false
}

/**
 * Menolak dengan 403 bila seorang karyawan berada di luar jangkauan pemanggil.
 *
 * Dipakai dua arah: sebelum menulis (create/update/archive/restore) dan sebelum membaca satu
 * karyawan langsung lewat id (`GET /{id}`, `GET /{id}/t-shape`). Keduanya menanyakan pertanyaan
 * yang sama — "apakah karyawan ini termasuk yang boleh dilihat pemanggil" — sehingga satu fungsi
 * cukup untuk keduanya.
 */
internal suspend fun ApplicationCall.requireReachableEmployee(
    reach: OrgChartDataReach,
    employeeId: String
): Boolean {
    if (reach.allowsEmployee(employeeId)) return true
    // 403, bukan 404. Menyamarkannya sebagai "tidak ditemukan" memang menyembunyikan keberadaan
    // baris itu, tetapi juga membuat admin yang sah mengira datanya hilang. Di dalam satu tenant,
    // keberadaan seorang karyawan bukan rahasia — yang dijaga adalah datanya.
    respond(
        HttpStatusCode.Forbidden,
        "Karyawan ini berada di luar jangkauan data Anda."
    )
    return false
}

/**
 * Menyaring hasil T-Shape agar hanya membawa karyawan yang termasuk jangkauan pemanggil.
 *
 * `focusNode` tidak diperiksa lagi di sini — itu tugas [requireReachableEmployee] yang sudah
 * dijalankan sebelum use case ini dipanggil. Yang justru perlu disaring adalah **penumpang gelap**
 * di sekitarnya: `superior` (bisa jadi direksi di luar divisi mana pun), `peerHeads` (kepala
 * divisi lain), dan `subordinates`/`peersInDepartment` bila hierarkinya melebar ke luar jangkauan.
 *
 * Tanpa ini, memvalidasi fokusnya saja memberi rasa aman yang keliru: seorang kepala Penjualan
 * yang dilarang membuka `GET /employees/emp-joko` (403) tetap menerima nama, email, dan telepon
 * Joko secara lengkap lewat `peerHeads` saat membuka T-Shape dirinya sendiri.
 */
internal fun TShapeHierarchyResult.restrictToReach(reach: OrgChartDataReach): TShapeHierarchyResult {
    if (reach.isUnrestricted) return this
    return copy(
        superior = superior?.takeIf { reach.allowsEmployee(it.id.value) },
        peerHeads = peerHeads.filter { reach.allowsEmployee(it.id.value) },
        subordinates = subordinates.filter { reach.allowsEmployee(it.id.value) },
        peersInDepartment = peersInDepartment.filter { reach.allowsEmployee(it.id.value) },
        orderedDepartmentMembers = orderedDepartmentMembers.filter { reach.allowsEmployee(it.id.value) }
    )
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
