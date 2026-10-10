package com.eventverse.app.domain.transfer

/**
 * Satu perpindahan barang yang tersirat di antara dua simpul alur.
 *
 * Leg **tidak pernah diketik manusia** — ia diturunkan dari [TenantLocationConfig] dan mode
 * pengerjaan tiap simpul. Itulah alasan pengiriman dimodelkan sebagai sambungan, bukan sebagai
 * simpul yang bisa diseret ke celah mana pun: sebuah simpul punya kebebasan untuk diletakkan di
 * tempat yang salah, sedangkan sambungan hanya bisa ada di antara dua simpul yang memang
 * berjauhan.
 *
 * [legKey] adalah jembatan ke dokumen: [SuratJalanManifest] menyimpan kunci yang sama, sehingga
 * status sebuah leg dapat dicari tanpa mencocok-cocokkan tuple asal/tujuan — yang langsung
 * ambigu begitu satu SPK melewati gedung yang sama dua kali.
 */
data class FlowTransferLeg(
    val legKey: String,
    val fromNode: FlowNodeRef,
    val toNode: FlowNodeRef,
    val origin: LegEndpoint,
    val destination: LegEndpoint,
    val transferType: TransferType
) {
    init {
        require(legKey.isNotBlank()) { "legKey tidak boleh kosong" }
        require(legKey.length <= MAX_LEG_KEY_LENGTH) {
            "legKey maksimal $MAX_LEG_KEY_LENGTH karakter, ada ${legKey.length}"
        }
        require(origin != destination) {
            "Leg harus menghubungkan dua ujung yang berbeda, keduanya: $origin"
        }
    }

    /** Ringkasan satu baris untuk konektor di panel alur, mis. `Gedung A → Gedung B`. */
    val summary: String get() = "${origin.displayLabel} -> ${destination.displayLabel}"

    companion object {
        /** Sepadan dengan lebar kolom `surat_jalan_manifests.leg_key`. */
        const val MAX_LEG_KEY_LENGTH = 120

        /**
         * Kunci deterministik dari pasangan simpul dan jenis perpindahannya.
         *
         * Jenis ikut masuk karena satu pasangan simpul yang sama bisa melahirkan dua leg
         * berlawanan arah pada kasus makloon (berangkat lalu pulang).
         */
        fun keyFor(from: FlowNodeRef, to: FlowNodeRef, transferType: TransferType): String =
            "${from.key}>${to.key}:${transferType.name}".take(MAX_LEG_KEY_LENGTH)
    }
}
