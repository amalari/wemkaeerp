package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.invoicing.InvoicePage
import com.eventverse.app.domain.invoicing.InvoiceQuery
import com.eventverse.app.domain.invoicing.InvoiceRepository

class GetInvoiceListUseCase(
    private val invoiceRepository: InvoiceRepository
) {
    suspend operator fun invoke(query: InvoiceQuery): Result<InvoicePage> = runCatching {
        invoiceRepository.search(query)
    }
}
