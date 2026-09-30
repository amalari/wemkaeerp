package com.eventverse.app.domain.builder.print

import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceStatus
import com.eventverse.app.domain.printing.IdrFormat

/**
 * Satu baris modul di dokumen tagihan, **sudah berlabel rupiah**.
 *
 * Harganya disalin dari `SubscriptionInvoiceLine.monthlyPrice` — angka yang dibekukan saat invoice
 * terbit. Dokumen ini tidak pernah menghitung ulang dari katalog: kalau ia menghitung, PDF yang
 * dikirim bulan lalu akan berubah isinya sendiri setiap kali harga katalog dinaikkan.
 */
data class SubscriptionInvoicePdfLine(
    val moduleId: String,
    val displayName: String,
    /** "Langganan" / "Kustomisasi" — jenis baris tagihan, bukan kode enum mentah. */
    val kindLabel: String,
    val priceLabel: String
)

/**
 * Isi dokumen tagihan langganan platform (FR-M2-5b) — **bukan** geometri: [SubscriptionInvoiceSheetLayout]
 * yang mengurus posisi. Pemisahan yang sama dengan `BlueprintPdfDocument`/`BlueprintSheetLayout`, dan
 * alasannya sama: isi diuji sebagai teks ("apakah PDF-nya menyebut nomor invoice dan totalnya?"),
 * geometri diuji sebagai bidang.
 *
 * ## Kenapa ini dokumen platform, bukan dokumen tenant
 *
 * Renderer invoice tenant (`InvoicePdfRenderer`) mencetak **penjualan tenant**: kop profil tenant,
 * NPWP tenant, prefiks nomor dokumen tenant. Tagihan ini arahnya terbalik — platform menagih tenant —
 * sehingga memakai renderer itu akan mencetak dokumen yang salah pemiliknya. Karena itu isi dan
 * renderernya berdiri sendiri, meniru pola dokumen platform pertama (lembar blueprint, Fase D).
 *
 * ## Watermark menyebut apa yang **belum** terjadi
 *
 * Sama seperti blueprint: dokumen ini berpindah tangan (WhatsApp/email) dan bisa difoto lalu dirujuk
 * sebagai "sudah dibayar". Karena itu statusnya dicetak besar dan diagonal: [WATERMARK_UNPAID] pada
 * invoice terbit, [WATERMARK_PAID] pada yang lunas, [WATERMARK_VOID] pada yang dibatalkan — yang
 * terakhir justru yang paling berbahaya bila tidak terlihat, karena orang bisa mentransfer ke tagihan
 * yang sudah dibatalkan.
 */
data class SubscriptionInvoicePdfDocument(
    val title: String,
    val number: String,
    val period: String,
    val tenantName: String,
    val tenantId: String,
    val statusLabel: String,
    /** Kata yang tercetak diagonal di setiap halaman; sumbernya [watermarkFor]. */
    val watermark: String,
    val issuedAtLabel: String?,
    val paidAtLabel: String?,
    val paidNote: String?,
    val generatedAtLabel: String,
    val footerNote: String,
    /** Catatan tambahan di akhir dokumen (kunci harga & cara pembayaran). */
    val notes: List<String>,
    val lines: List<SubscriptionInvoicePdfLine>,
    val totalLabel: String,
    /** Total beku dari invoice — dicetak apa adanya, tidak dihitung ulang dari [lines]. */
    val totalIdr: Long
) {

    val lineCount: Int get() = lines.size

    companion object {
        const val DEFAULT_TITLE = "Tagihan Langganan"

        const val WATERMARK_UNPAID = "BELUM DIBAYAR"
        const val WATERMARK_PAID = "LUNAS"
        const val WATERMARK_VOID = "DIBATALKAN"
        const val WATERMARK_DRAFT = "DRAF — BELUM DITERBITKAN"

        /**
         * Catatan kaki setiap halaman. Menyebut **kapan angkanya berlaku**, karena itulah pertanyaan
         * pertama penerima tagihan ketika harga jual naik setelah dokumen terbit.
         */
        const val DEFAULT_FOOTER =
            "Angka pada dokumen ini dibekukan saat invoice diterbitkan dan tidak berubah oleh perubahan harga katalog berikutnya."

        /** Langkah pembayaran pada MVP: manual, dikonfirmasi tim platform. */
        const val PAYMENT_NOTE =
            "Pembayaran dicatat manual oleh tim platform. Status pada dokumen ini berubah menjadi LUNAS hanya setelah dana diterima dan dikonfirmasi."

        fun watermarkFor(status: SubscriptionInvoiceStatus): String = when (status) {
            SubscriptionInvoiceStatus.DRAFT -> WATERMARK_DRAFT
            SubscriptionInvoiceStatus.ISSUED -> WATERMARK_UNPAID
            SubscriptionInvoiceStatus.PAID -> WATERMARK_PAID
            SubscriptionInvoiceStatus.VOID -> WATERMARK_VOID
        }

        fun statusLabelFor(status: SubscriptionInvoiceStatus): String = when (status) {
            SubscriptionInvoiceStatus.DRAFT -> "Draf"
            SubscriptionInvoiceStatus.ISSUED -> "Menunggu pembayaran"
            SubscriptionInvoiceStatus.PAID -> "Lunas"
            SubscriptionInvoiceStatus.VOID -> "Dibatalkan"
        }

        /**
         * Jenis baris tagihan → label yang bisa dibaca penerima tagihan.
         *
         * Nilai tak dikenal dicetak apa adanya, **tidak** jatuh ke "Langganan": baris uang yang salah
         * kategori lebih membingungkan daripada satu label teknis yang jujur
         * (`tenant-variability-rules` Kontrak 4).
         */
        fun kindLabelFor(kind: String): String = when (kind) {
            "SUBSCRIPTION" -> "Langganan"
            "CUSTOMIZATION" -> "Kustomisasi"
            else -> kind
        }

        /**
         * Merakit isi dari invoice. [tenantName] datang dari repositori tenant (invoice hanya menyimpan
         * id-nya), dan label waktu datang dari pemanggil — dokumen domain tidak pernah membaca jam
         * sendiri supaya isinya deterministik saat diuji.
         */
        fun of(
            invoice: SubscriptionInvoice,
            tenantName: String,
            generatedAtLabel: String,
            issuedAtLabel: String? = null,
            paidAtLabel: String? = null,
            title: String = DEFAULT_TITLE,
            footerNote: String = DEFAULT_FOOTER,
            notes: List<String> = listOf(PAYMENT_NOTE)
        ): SubscriptionInvoicePdfDocument = SubscriptionInvoicePdfDocument(
            title = title,
            number = invoice.number,
            period = invoice.period,
            tenantName = tenantName,
            tenantId = invoice.tenantId.value,
            statusLabel = statusLabelFor(invoice.status),
            watermark = watermarkFor(invoice.status),
            issuedAtLabel = issuedAtLabel,
            paidAtLabel = paidAtLabel,
            paidNote = invoice.paidNote?.takeIf { it.isNotBlank() },
            generatedAtLabel = generatedAtLabel,
            footerNote = footerNote,
            notes = notes,
            lines = invoice.lines.map { line ->
                SubscriptionInvoicePdfLine(
                    moduleId = line.moduleId,
                    displayName = line.displayName,
                    kindLabel = kindLabelFor(line.kind),
                    priceLabel = IdrFormat.format(line.monthlyPrice.amount)
                )
            },
            totalLabel = IdrFormat.format(invoice.totalIdr.amount),
            totalIdr = invoice.totalIdr.amount
        )
    }
}
