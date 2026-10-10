package com.eventverse.app.routes

import com.eventverse.app.domain.crm.LeadScope
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.RecordOwnerSource
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.isHierarchical
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FieldFileRecordGate")

/**
 * Gerbang record + jangkauan data untuk rute FILE generik (TRD-FIELD-004 FR-1.1/1.2), satu fungsi dipakai
 * unggah **dan** unduh supaya keduanya tidak bisa menyimpang. Dipanggil SETELAH RBAC modul induk, SEBELUM
 * storage/`fileName`/MIME/body disentuh. Urutan (kode status sengaja tidak membedakan sebab di luar yang perlu):
 *
 * 1. Modul `HIERARCHICAL` wajib punya sumber pemilik ([rows] yang juga [RecordOwnerSource]) — tidak ada = **403**
 *    fail-closed (Q2). Modul `GLOBAL_ONLY` tanpa penyimpan baris = **404** "Record tidak ditemukan".
 * 2. Record ada pada tenant pemanggil — tidak ada = **404** (record tenant lain pun "tidak ada").
 * 3. Modul hierarkis: pemilik record dalam jangkauan `DataScope` pemanggil atas modul ini — di luar = **403**.
 *
 * Mengembalikan baris bila lolos; `null` bila sudah menjawab.
 */
internal suspend fun ApplicationCall.requireReachableRecord(
    tenant: TenantContext,
    module: ModuleId,
    recordId: String,
    decision: AccessDecision,
    rows: PrototypeRowRepository?,
    employeeRepository: EmployeeRepository
): PrototypeRow? {
    val hierarchical = module.isHierarchical
    val owners = rows as? RecordOwnerSource
    if (rows == null || (hierarchical && owners == null)) {
        if (hierarchical) {
            log.warn("FIELD FILE ditolak 403: modul hierarkis '{}' tanpa sumber pemilik record", module.value)
            respond(HttpStatusCode.Forbidden, "Modul ini belum menyediakan sumber pemilik record untuk berkas.")
        } else {
            respond(HttpStatusCode.NotFound, "Record tidak ditemukan")
        }
        return null
    }
    val row = rows.find(tenant.tenantId, recordId)
    if (row == null) {
        respond(HttpStatusCode.NotFound, "Record tidak ditemukan")
        return null
    }
    if (hierarchical && owners != null) {
        val reach = callerOwnerReach(tenant, decision.config.sanitizeFor(module).scope, employeeRepository)
        val owner = owners.ownerOf(tenant.tenantId, recordId)
        val allowed = reach == null || (owner != null && owner in reach)
        if (!allowed) {
            log.warn("FIELD FILE ditolak 403: record di luar jangkauan data pemanggil (modul '{}')", module.value)
            respond(HttpStatusCode.Forbidden, "Record berada di luar jangkauan data Anda untuk modul ini.")
            return null
        }
    }
    return row
}

/**
 * Himpunan pemilik yang boleh dijangkau pemanggil di bawah [scope] (pola `crmOwnerReach`): `null` =
 * `ALL_TENANT_DATA` (tanpa predicate), selain itu himpunan id karyawan. Dipakai bersama gerbang record dan
 * otorisasi target rujukan supaya tidak ada salinan ketiga perhitungan jangkauan.
 */
internal suspend fun ApplicationCall.callerOwnerReach(
    tenant: TenantContext,
    scope: DataScope,
    employeeRepository: EmployeeRepository
): Set<OrgNodeId>? {
    if (scope == DataScope.ALL_TENANT_DATA) return null
    val principal = callerPrincipalOrNull
    val viewerEmployeeId = principal?.email
        ?.let { email -> employeeRepository.findByEmail(tenant.tenantId, email) }
        ?.id
    val employees = employeeRepository.findAllByTenant(tenant.tenantId)
    return LeadScope.reachableOwnerIds(scope, employees, viewerEmployeeId, principal?.departmentId)
}
