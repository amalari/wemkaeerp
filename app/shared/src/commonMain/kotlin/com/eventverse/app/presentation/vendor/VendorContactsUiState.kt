package com.eventverse.app.presentation.vendor

import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorQueueItem
import com.eventverse.app.domain.vendor.VendorServiceRate
import com.eventverse.app.infrastructure.api.VendorAssignmentInput
import com.eventverse.app.infrastructure.api.VendorProfileInput
import kotlinx.datetime.LocalDate

enum class VendorTab(val displayName: String) {
    QUEUE("Menunggu Vendor"),
    CONTACTS("Kontak Vendor");
}

/** Dialog yang sedang terbuka — satu saja pada satu waktu. */
sealed interface VendorDialog {
    /** [resumeAssign] terisi bila dibuka dari dialog penunjukan — setelah tersimpan, dialog itu dibuka lagi. */
    data class CreateVendor(val resumeAssign: VendorQueueItem? = null) : VendorDialog
    data class EditVendor(val vendorId: VendorId) : VendorDialog
    data class AddRate(val vendorId: VendorId) : VendorDialog
    data class Assign(val item: VendorQueueItem) : VendorDialog
}

/** Satu pilihan vendor di dialog penunjukan, beserta harga daftarnya untuk layanan itu. */
data class VendorOption(val vendor: Vendor, val listedRate: VendorServiceRate?)

data class VendorContactsUiState(
    val tab: VendorTab = VendorTab.QUEUE,
    val vendors: List<Vendor> = emptyList(),
    val queue: List<VendorQueueItem> = emptyList(),
    val selectedVendorId: VendorId? = null,
    val selectedVendorHistory: List<VendorAssignment> = emptyList(),
    val searchQuery: String = "",
    val showInactive: Boolean = false,
    val dialog: VendorDialog? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false
) {
    val pendingCount: Int get() = queue.count { it.isPending }

    val selectedVendor: Vendor?
        get() = selectedVendorId?.let { id -> vendors.firstOrNull { it.id == id } } ?: filteredVendors.firstOrNull()

    val filteredVendors: List<Vendor>
        get() = vendors.filter { vendor ->
            (showInactive || vendor.isActive) && (
                searchQuery.isBlank() ||
                    vendor.name.value.contains(searchQuery, ignoreCase = true) ||
                    vendor.phone.contains(searchQuery) ||
                    vendor.rates.any { it.serviceName.contains(searchQuery, ignoreCase = true) }
                )
        }

    fun vendorById(id: VendorId): Vendor? = vendors.firstOrNull { it.id == id }

    /**
     * Vendor aktif untuk dialog penunjukan: yang punya harga layanan ini di depan (termurah dulu)
     * supaya admin langsung bisa membandingkan, sisanya tetap bisa dipilih dengan harga manual.
     */
    fun optionsFor(processCode: String, today: LocalDate): List<VendorOption> =
        vendors.filter { it.isActive }
            .map { vendor -> VendorOption(vendor, vendor.ratesFor(processCode, today).minByOrNull { it.priceIdr }) }
            .sortedWith(compareBy<VendorOption> { it.listedRate == null }.thenBy { it.listedRate?.priceIdr ?: 0L })
}

sealed interface VendorContactsUiEvent {
    data object Load : VendorContactsUiEvent
    data class SelectTab(val tab: VendorTab) : VendorContactsUiEvent
    data class UpdateSearch(val query: String) : VendorContactsUiEvent
    data class SetShowInactive(val show: Boolean) : VendorContactsUiEvent
    data class SelectVendor(val vendorId: VendorId) : VendorContactsUiEvent
    data class OpenDialog(val dialog: VendorDialog) : VendorContactsUiEvent
    data object CloseDialog : VendorContactsUiEvent
    /** [vendorId] `null` = vendor baru. */
    data class SaveVendor(val vendorId: VendorId?, val input: VendorProfileInput) : VendorContactsUiEvent
    data class SaveRate(val vendorId: VendorId, val rate: VendorServiceRate) : VendorContactsUiEvent
    data class Assign(val input: VendorAssignmentInput) : VendorContactsUiEvent
    data class CancelAssignment(val assignment: VendorAssignment) : VendorContactsUiEvent
    data object DismissMessage : VendorContactsUiEvent
}
