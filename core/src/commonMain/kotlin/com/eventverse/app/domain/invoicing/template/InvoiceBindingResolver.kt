package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.InvoiceLine

/**
 * Nilai ter-resolve dari token binding.
 */
sealed interface ResolvedBindingValue {
    data class Text(val value: String) : ResolvedBindingValue
    data class Image(val assetUrl: String?) : ResolvedBindingValue
    data object Empty : ResolvedBindingValue
}

/**
 * Resolver murni untuk mengonversi [BindingToken] menjadi nilai teks atau gambar yang siap digambar
 * pada layar preview Compose maupun PDFBox server.
 *
 * Menjamin 100% konsistensi representasi teks, angka uang, dan tanggal antar client dan server.
 */
object InvoiceBindingResolver {

    fun resolve(
        token: BindingToken,
        invoice: Invoice,
        line: InvoiceLine? = null,
        paidAmount: Money = Money.zero(invoice.currency)
    ): ResolvedBindingValue {
        return when (token.value) {
            // ── Dokumen ──
            "invoice.number" -> ResolvedBindingValue.Text(invoice.number.value)
            "invoice.kind" -> ResolvedBindingValue.Text(invoice.kind.displayName)
            "invoice.status" -> ResolvedBindingValue.Text(invoice.status.displayName)
            "invoice.issueDate" -> ResolvedBindingValue.Text(formatLocalDate(invoice.issueDate))
            "invoice.dueDate" -> ResolvedBindingValue.Text(invoice.dueDate?.let { formatLocalDate(it) } ?: "-")
            "invoice.terms" -> ResolvedBindingValue.Text(invoice.terms)
            "invoice.notes" -> ResolvedBindingValue.Text(invoice.notes)
            "invoice.sourceRef" -> ResolvedBindingValue.Text(invoice.sourceRef ?: "-")

            // ── Finansial Dokumen ──
            "invoice.subtotal" -> ResolvedBindingValue.Text(formatMoney(invoice.subtotal))
            "invoice.discountAmount" -> ResolvedBindingValue.Text(formatMoney(invoice.discountAmount))
            "invoice.taxableBase" -> ResolvedBindingValue.Text(formatMoney(invoice.taxableBase))
            "invoice.taxRate" -> ResolvedBindingValue.Text(invoice.taxRatio.asPercentageString())
            "invoice.taxAmount" -> ResolvedBindingValue.Text(formatMoney(invoice.taxAmount))
            "invoice.total" -> ResolvedBindingValue.Text(formatMoney(invoice.total))
            "invoice.totalInWords" -> ResolvedBindingValue.Text(TerbilangRupiah.konversi(invoice.total.toWholeUnits()))
            "invoice.paidAmount" -> ResolvedBindingValue.Text(formatMoney(paidAmount))
            "invoice.outstandingAmount" -> {
                val outstanding = (invoice.total - paidAmount).coerceAtLeastZero()
                ResolvedBindingValue.Text(formatMoney(outstanding))
            }
            "invoice.contractValue" -> ResolvedBindingValue.Text(invoice.contractValue?.let { formatMoney(it) } ?: "-")

            // ── Klien (Bill To) ──
            "billTo.name" -> ResolvedBindingValue.Text(invoice.billTo.name)
            "billTo.contactPerson" -> ResolvedBindingValue.Text(invoice.billTo.contactPerson)
            "billTo.address" -> ResolvedBindingValue.Text(invoice.billTo.address)
            "billTo.phone" -> ResolvedBindingValue.Text(invoice.billTo.phone)
            "billTo.email" -> ResolvedBindingValue.Text(invoice.billTo.email)
            "billTo.taxId" -> ResolvedBindingValue.Text(invoice.billTo.taxId)

            // ── Penerbit (Issuer) ──
            "issuer.companyName" -> ResolvedBindingValue.Text(invoice.issuer.companyName)
            "issuer.address" -> ResolvedBindingValue.Text(invoice.issuer.address)
            "issuer.taxId" -> ResolvedBindingValue.Text(invoice.issuer.taxId)
            "issuer.phone" -> ResolvedBindingValue.Text(invoice.issuer.phone)
            "issuer.email" -> ResolvedBindingValue.Text(invoice.issuer.email)
            "issuer.bankName" -> ResolvedBindingValue.Text(invoice.issuer.bankName)
            "issuer.bankAccountNumber" -> ResolvedBindingValue.Text(invoice.issuer.bankAccountNumber)
            "issuer.bankAccountHolder" -> ResolvedBindingValue.Text(invoice.issuer.bankAccountHolder)
            "issuer.logoAssetUrl" -> ResolvedBindingValue.Image(invoice.issuer.logoAssetUrl)

            // ── Baris Item (Line Scope) ──
            "line.no" -> ResolvedBindingValue.Text(line?.let { (it.sortOrder + 1).toString() } ?: "")
            "line.description" -> ResolvedBindingValue.Text(line?.description ?: "")
            "line.quantity" -> ResolvedBindingValue.Text(line?.quantity?.formatted() ?: "")
            "line.uom" -> ResolvedBindingValue.Text(line?.quantity?.uom?.code ?: "")
            "line.unitPrice" -> ResolvedBindingValue.Text(line?.unitPrice?.let { formatMoney(it) } ?: "")
            "line.discount" -> ResolvedBindingValue.Text(line?.discount?.asPercentageString() ?: "")
            "line.grossAmount" -> ResolvedBindingValue.Text(line?.grossAmount?.let { formatMoney(it) } ?: "")
            "line.amount" -> ResolvedBindingValue.Text(line?.amount?.let { formatMoney(it) } ?: "")

            else -> ResolvedBindingValue.Empty
        }
    }

    fun formatMoney(money: Money): String {
        val whole = money.toWholeUnits()
        val frac = kotlin.math.abs(money.minorUnits % money.currency.minorFactor)
        val isNeg = whole < 0
        val absStr = kotlin.math.abs(whole).toString()
        val formattedWhole = absStr.reversed().chunked(3).joinToString(".").reversed()
        val sign = if (isNeg) "-" else ""

        return if (frac == 0L) {
            "${money.currency.symbol} $sign$formattedWhole"
        } else {
            val fracPadded = frac.toString().padStart(money.currency.minorDigits, '0')
            "${money.currency.symbol} $sign$formattedWhole,$fracPadded"
        }
    }

    fun formatLocalDate(date: kotlinx.datetime.LocalDate): String {
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val monthNames = arrayOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )
        val month = monthNames[(date.monthNumber - 1).coerceIn(0, 11)]
        return "$day $month ${date.year}"
    }

    private fun Money.coerceAtLeastZero(): Money =
        if (this.isNegative) Money.zero(this.currency) else this
}

/**
 * Pengubah angka nominal uang menjadi teks terbilang bahasa Indonesia.
 */
object TerbilangRupiah {
    private val SATUAN = arrayOf(
        "", "Satu", "Dua", "Tiga", "Empat", "Lima",
        "Enam", "Tujuh", "Delapan", "Sembilan", "Sepuluh", "Sebelas"
    )

    fun konversi(nilai: Long): String {
        if (nilai == 0L) return "Nol Rupiah"
        val isNegative = nilai < 0
        val angka = kotlin.math.abs(nilai)
        val hasil = sebut(angka).trim()
        val teks = if (isNegative) "Minus $hasil" else hasil
        return "$teks Rupiah"
    }

    private fun sebut(n: Long): String = when {
        n < 12L -> SATUAN[n.toInt()]
        n < 20L -> sebut(n - 10L) + " Belas"
        n < 100L -> sebut(n / 10L) + " Puluh " + sebut(n % 10L)
        n < 200L -> "Seratus " + sebut(n - 100L)
        n < 1000L -> sebut(n / 100L) + " Ratus " + sebut(n % 100L)
        n < 2000L -> "Seribu " + sebut(n - 1000L)
        n < 1_000_000L -> sebut(n / 1000L) + " Ribu " + sebut(n % 1000L)
        n < 1_000_000_000L -> sebut(n / 1_000_000L) + " Juta " + sebut(n % 1_000_000L)
        n < 1_000_000_000_000L -> sebut(n / 1_000_000_000L) + " Miliar " + sebut(n % 1_000_000_000L)
        else -> sebut(n / 1_000_000_000_000L) + " Triliun " + sebut(n % 1_000_000_000_000L)
    }.replace("\\s+".toRegex(), " ")
}
