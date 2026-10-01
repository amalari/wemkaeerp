package com.eventverse.app.domain.builder

/**
 * Status transaksi di sisi iPaymu. Enum **sistem** (Uji Variabilitas: nilainya ditentukan API
 * iPaymu, bukan tenant), dengan satu parser ketat — status yang tidak dikenal **ditolak**, tidak
 * pernah jatuh diam-diam ke default (tenant-variability-rules Kontrak 4: fallback senyap = data
 * berubah, mis. callback gagal dibaca sebagai lunas).
 *
 * Alias mengikuti dokumentasi resmi iPaymu (Callback + Check Transaction, diakses 2026-10-01):
 * - `status` callback berupa teks: `berhasil`, `pending`, `expired`.
 * - `status_code` callback: `1` Sukses, `0` Pending, `-2` Expired.
 * - `Status` pada cek transaksi (int): `0` Pending, `1` Success, `2` Cancelled, `3` Refund,
 *   `4` Error, `5` Failed, `6` Success-Unsettled, `7` Escrow, `-2` Expired.
 *
 * Keputusan pemetaan (ditulis eksplisit agar bisa di-review):
 * - `6` (uang diterima, settlement menyusul) tetap **PAID** — invoice platform hanya butuh
 *   bukti bayar, bukan status settlement bank.
 * - `7` (escrow) **PENDING** — uang belum bisa ditarik, jangan cepat melunasi.
 * - `3` refund / `2` cancelled / `4` error / `5` failed → **FAILED**.
 */
enum class IpaymuTransactionStatus {
    PENDING, PAID, EXPIRED, FAILED;

    companion object {
        private val ALIASES: Map<String, IpaymuTransactionStatus> = buildMap {
            listOf("berhasil", "success", "paid", "settlement", "settled", "1", "6").forEach {
                put(it.lowercase(), PAID)
            }
            listOf("pending", "unpaid", "escrow", "0", "7").forEach { put(it.lowercase(), PENDING) }
            listOf("expired", "kadaluarsa", "-2").forEach { put(it.lowercase(), EXPIRED) }
            listOf("failed", "gagal", "canceled", "cancelled", "refund", "error", "2", "3", "4", "5")
                .forEach { put(it.lowercase(), FAILED) }
        }

        fun fromApi(raw: String): IpaymuTransactionStatus =
            ALIASES[raw.trim().lowercase()]
                ?: error("Status iPaymu tidak dikenal: '$raw' — tolak, jangan ditebak")
    }
}

/**
 * Hasil cek status transaksi langsung ke API iPaymu. [sessionId] adalah `Data.SessionId`
 * dari respons `/api/v2/transaction` — wajib dicocokkan dengan `sid` tersimpan sebelum
 * status dipercaya, supaya `transactionId` milik transaksi orang lain tidak bisa dipakai
 * memvalidasi invoice kita.
 */
data class IpaymuTransactionCheck(
    val status: IpaymuTransactionStatus,
    val sessionId: String,
)

/** Hasil pembuatan transaksi Hosted Checkout di iPaymu. */
data class IpaymuCheckout(
    val trxId: String,
    val paymentUrl: String,
) {
    init {
        require(trxId.isNotBlank()) { "IpaymuCheckout.trxId kosong" }
        require(paymentUrl.startsWith("https://")) { "paymentUrl wajib https, dapat '$paymentUrl'" }
    }
}

/**
 * Notifikasi callback dari iPaymu, **sudah diparse ketat** dari payload mentah (form-urlencoded
 * atau JSON). Kegagalan parse = error di sini, bukan nilai `null` yang lolos diam-diam.
 *
 * Identitas (dokumentasi Callback iPaymu, 2026-10-01): callback membawa `trx_id` (ID numerik
 * internal iPaymu, dipakai untuk cek status) dan `sid` (= SessionID dari saat create — yang
 * tersimpan di invoice). Pencocokan invoice **wajib lewat `sid`**, bukan `reference_id` yang
 * bisa ditulis siapa pun.
 */
data class IpaymuNotification(
    /** `trx_id` numerik iPaymu — dipakai sebagai `transactionId` saat re-check status. */
    val trxId: String,
    /** `sid` = SessionID saat create transaction; kunci pencocokan invoice. */
    val sid: String,
    /** `reference_id` yang kita kirim saat create transaction = id invoice. */
    val referenceId: String,
    val status: IpaymuTransactionStatus,
    val amountIdr: Long,
) {
    init {
        require(trxId.isNotBlank()) { "IpaymuNotification.trxId kosong" }
        require(sid.isNotBlank()) { "IpaymuNotification.sid kosong" }
        require(amountIdr >= 0) { "IpaymuNotification.amount negatif" }
    }

    companion object {
        /**
         * Payload resmi memuat `status` (teks: berhasil/pending/expired) DAN `status_code`
         * (1/0/-2). Yang dipakai: `status`; kalau kosong, fallback `status_code` — keduanya
         * tetap lewat parser ketat.
         */
        fun fromFields(fields: Map<String, String>): IpaymuNotification {
            val trxId = fields["trx_id"]?.trim().orEmpty()
            require(trxId.isNotBlank()) { "callback tanpa trx_id" }
            val sid = fields["sid"]?.trim().orEmpty()
            require(sid.isNotBlank()) { "callback tanpa sid" }
            val statusRaw = fields["status"]?.trim().orEmpty()
                .ifEmpty { fields["status_code"]?.trim().orEmpty() }
            require(statusRaw.isNotBlank()) { "callback tanpa status" }
            val amountRaw = fields["amount"]?.trim().orEmpty()
            require(amountRaw.isNotBlank()) { "callback tanpa amount" }
            val amount = amountRaw.toLongOrNull() ?: error("amount bukan angka: '$amountRaw'")
            return IpaymuNotification(
                trxId = trxId,
                sid = sid,
                referenceId = fields["reference_id"]?.trim().orEmpty(),
                status = IpaymuTransactionStatus.fromApi(statusRaw),
                amountIdr = amount
            )
        }
    }
}

/**
 * Port keluar ke payment gateway (FR-PAY-3, TRD-PAY-001). Implementasi infrastruktur
 * (`IpaymuClient`) memegang detail HTTP/signature; domain hanya melihat operasi bisnis:
 * buat checkout, dan cek ulang status — cek ulang inilah syarat keamanan callback
 * (FR-PAY-3.3 butir 2: isi callback tidak pernah dipercaya).
 */
interface PaymentGateway {
    /** Membuat transaksi Hosted Checkout untuk satu invoice. */
    suspend fun createCheckout(invoice: SubscriptionInvoice): Result<IpaymuCheckout>

    /**
     * Cek status transaksi langsung ke API iPaymu berdasarkan `trx_id` numerik dari callback.
     * Hasil menyertakan `sessionId` untuk diverifikasi terhadap `sid` tersimpan.
     */
    suspend fun checkStatus(transactionId: String): Result<IpaymuTransactionCheck>
}
