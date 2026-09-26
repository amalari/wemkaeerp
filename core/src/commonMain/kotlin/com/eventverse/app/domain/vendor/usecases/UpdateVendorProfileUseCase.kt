package com.eventverse.app.domain.vendor.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorName
import com.eventverse.app.domain.vendor.VendorRepository
import kotlinx.datetime.Clock

data class UpdateVendorProfileCommand(
    val tenantId: TenantId,
    val vendorId: VendorId,
    val name: String,
    val phone: String,
    val address: String,
    val notes: String,
    /** `null` = status aktif tidak diubah. */
    val isActive: Boolean? = null
)

/**
 * Mengubah data kontak vendor. Penugasan yang sudah berjalan tidak ikut berubah — mereka memegang
 * salinan nama dan telepon saat ditugaskan.
 */
class UpdateVendorProfileUseCase(
    private val repository: VendorRepository
) {
    suspend operator fun invoke(command: UpdateVendorProfileCommand): Result<Vendor> = runCatching {
        val vendor = repository.findById(command.tenantId, command.vendorId)
            ?: error("Vendor '${command.vendorId.value}' tidak ditemukan")

        val name = VendorName(command.name.trim())
        val duplicate = repository.listContacts(command.tenantId, includeInactive = true)
            .firstOrNull { it.id != vendor.id && it.name.value.equals(name.value, ignoreCase = true) }
        require(duplicate == null) { "Vendor bernama '${name.value}' sudah terdaftar" }

        val now = Clock.System.now()
        val profiled = vendor.updateProfile(name, command.phone, command.address, command.notes, now)
        val updated = when (command.isActive) {
            true -> profiled.activate(now)
            false -> profiled.deactivate(now)
            null -> profiled
        }
        repository.save(updated)
    }
}
