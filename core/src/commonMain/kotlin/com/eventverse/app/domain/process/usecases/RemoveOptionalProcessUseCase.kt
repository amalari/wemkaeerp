package com.eventverse.app.domain.process.usecases

import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.tenant.TenantId

data class RemoveOptionalProcessCommand(
    val tenantId: TenantId,
    val processId: String
)

/**
 * Mengeluarkan tahapan opsional dari flow tenant (tombol `×` pada chip proses).
 * Proses bisa dipasang kembali di celah lain kapan saja.
 */
class RemoveOptionalProcessUseCase(
    private val repository: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(command: RemoveOptionalProcessCommand): Result<TenantProcessCatalog> = runCatching {
        val catalog = repository.findByTenantId(command.tenantId)
            ?: error("Katalog proses tenant tidak ditemukan: ${command.tenantId.value}")
        repository.save(catalog.removeProcess(command.processId)).getOrThrow()
    }
}