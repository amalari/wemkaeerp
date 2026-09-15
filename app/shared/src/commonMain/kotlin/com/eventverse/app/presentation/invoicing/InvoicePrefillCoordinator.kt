package com.eventverse.app.presentation.invoicing

import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind

/**
 * Data model untuk menginisialisasi form pembuatan invoice custom secara otomatis
 * ketika pengguna dialihkan dari alur CRM (Sampling atau Order Langsung).
 */
data class InvoicePrefillData(
    val kind: InvoiceKind = InvoiceKind.SAMPLE,
    val clientName: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val sourceKind: InvoiceSourceKind = InvoiceSourceKind.CRM_LEAD,
    val sourceRef: String = "",
    val lineDescription: String = "",
    val lineQty: Double = 1.0,
    val linePrice: Long = 0L,
    val notes: String = ""
)

/**
 * State coordinator untuk menjembatani intent pembuatan invoice antar-layar/modul
 * (misalnya dari CRM Leads ke Invoicing Workspace) tanpa kopling langsung.
 */
object InvoicePrefillCoordinator {
    private var pending: InvoicePrefillData? = null

    fun setPending(data: InvoicePrefillData) {
        pending = data
    }

    fun hasPending(): Boolean = pending != null

    fun peek(): InvoicePrefillData? = pending

    fun consumePending(): InvoicePrefillData? {
        val current = pending
        pending = null
        return current
    }

    fun clear() {
        pending = null
    }
}
