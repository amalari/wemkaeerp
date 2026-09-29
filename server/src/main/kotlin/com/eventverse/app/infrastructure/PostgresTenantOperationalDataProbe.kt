package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DealsTable
import com.eventverse.app.infrastructure.tables.SamplingOrdersTable
import org.jetbrains.exposed.sql.selectAll

/**
 * Data operasional = deal atau SPK sampling. Keduanya dokumen yang membawa kode modul/tahap vertikal lama; pindah pack
 * di atasnya membuat dokumen itu yatim (B7 §5). Dibaca dalam konteks RLS tenant itu.
 */
class PostgresTenantOperationalDataProbe : TenantOperationalDataProbe {
    override suspend fun hasOperationalData(tenantId: TenantId): Boolean = DatabaseFactory.dbQuery(tenantId) {
        DealsTable.selectAll().where { DealsTable.tenantId eq tenantId.value }.limit(1).any() ||
            SamplingOrdersTable.selectAll().where { SamplingOrdersTable.tenantId eq tenantId.value }.limit(1).any()
    }
}
