package com.eventverse.app.domain.vendor.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorName
import com.eventverse.app.domain.vendor.VendorRepository
import kotlinx.datetime.Clock

data class RegisterVendorCommand(
    val tenantId: TenantId,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val idGenerator: () -> String = { "vnd-${Clock.System.now().toEpochMilliseconds()}" }
)

class RegisterVendorUseCase(
    private val repository: VendorRepository
) {
    suspend operator fun invoke(command: RegisterVendorCommand): Result<Vendor> = runCatching {
        val name = VendorName(command.name.trim())

        // Nama vendor menjadi vendorRef di alur dan Surat Jalan; dua vendor bernama sama akan
        // tampil identik di dokumen jalan dan tidak bisa dibedakan kurir maupun gudang.
        val duplicate = repository.listContacts(command.tenantId, includeInactive = true)
            .firstOrNull { it.name.value.equals(name.value, ignoreCase = true) }
        require(duplicate == null) { "Vendor bernama '${name.value}' sudah terdaftar" }

        val now = Clock.System.now()
        repository.save(
            Vendor(
                id = VendorId(command.idGenerator()),
                tenantId = command.tenantId,
                name = name,
                phone = command.phone.trim(),
                address = command.address.trim(),
                notes = command.notes.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
