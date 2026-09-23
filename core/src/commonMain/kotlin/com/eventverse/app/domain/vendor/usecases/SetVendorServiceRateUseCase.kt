package com.eventverse.app.domain.vendor.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorRepository
import com.eventverse.app.domain.vendor.VendorServiceRate
import kotlinx.datetime.Clock

data class SetVendorServiceRateCommand(
    val tenantId: TenantId,
    val vendorId: VendorId,
    val rate: VendorServiceRate
)

/** Mencatat harga layanan vendor; harga lama pada jalur yang sama ditutup, bukan ditimpa. */
class SetVendorServiceRateUseCase(
    private val repository: VendorRepository
) {
    suspend operator fun invoke(command: SetVendorServiceRateCommand): Result<Vendor> = runCatching {
        val vendor = repository.findById(command.tenantId, command.vendorId)
            ?: error("Vendor '${command.vendorId.value}' tidak ditemukan")
        repository.save(vendor.setRate(command.rate, Clock.System.now()))
    }
}
