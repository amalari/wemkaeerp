package com.eventverse.app.presentation.invoicing

import com.eventverse.app.domain.crm.CrmLead
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
    val notes: String = "",
    val openDesignerDirectly: Boolean = true
) {
    companion object {

        /**
         * Memetakan satu lead CRM nyata menjadi prefill faktur.
         *
         * Pemetaannya sengaja identik dengan tombol "Generate Invoice" di [com.eventverse.app.presentation.crm.components.LeadInspectorPane]
         * supaya preview kanvas desainer menampilkan data yang sama persis dengan yang akan
         * diterbitkan lewat alur CRM — bukan contoh data bawaan desainer.
         */
        fun fromCrmLead(
            lead: CrmLead,
            kind: InvoiceKind = InvoiceKind.DOWN_PAYMENT
        ): InvoicePrefillData = InvoicePrefillData(
            kind = kind,
            clientName = lead.brandName.display(fallback = lead.contactPerson),
            contactPerson = lead.contactPerson,
            phone = lead.whatsappNumber?.value ?: "",
            email = lead.email,
            sourceKind = InvoiceSourceKind.CRM_LEAD,
            sourceRef = lead.id.value,
            openDesignerDirectly = true
        )

        /**
         * Memetakan deal + contact menjadi prefill faktur — jalur baru setelah invoicing
         * dipindah dari CRM ke Deal. `sourceKind = DEAL` dan `sourceRef = dealId`, sehingga
         * riwayat penagihan deal bisa direkonstruksi dari invoice mana pun.
         */
        fun fromDeal(
            deal: com.eventverse.app.domain.deal.Deal,
            contact: com.eventverse.app.domain.crm.Contact?,
            kind: InvoiceKind = InvoiceKind.DOWN_PAYMENT
        ): InvoicePrefillData = InvoicePrefillData(
            kind = kind,
            clientName = contact?.brandName?.takeIf { !it.isBlank }?.value
                ?: contact?.displayName
                ?: deal.title.value,
            contactPerson = contact?.name ?: "",
            phone = contact?.phone?.value ?: "",
            email = contact?.email ?: "",
            address = contact?.address ?: "",
            sourceKind = InvoiceSourceKind.DEAL,
            sourceRef = deal.id.value,
            openDesignerDirectly = true
        )
    }
}

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
