package com.eventverse.app.domain.discovery.scenario

/**
 * Alasan satu percobaan pengusul skenario (model) **tidak dipakai**. Enum sah (Uji Variabilitas): mekanik platform — jenis
 * kegagalan pengusul — bukan kosakata industri.
 *
 * Dulu semuanya diam-diam menjadi "tidak ada usulan": dengan cerita yang meragukan tiga kali propose berturut-turut kosong tanpa jejak,
 * sehingga tak diketahui model yang membalas kosong atau keluarannya yang ditolak validator (cek visual SC3, 2026-10-18).
 */
enum class ProposalRejectionKind {
    /** Pengusul melempar galat atau balasannya tak terbaca (timeout, JSON rusak, kunci terlarang, jaringan). */
    PROPOSER_ERROR,

    /** Model menjawab daftar kosong — jawaban sah ("tidak cukup bahan"), tetapi tak ada skenario; tidak dicoba ulang. */
    EMPTY,

    /** Ada skenario tetapi tak lolos [ScenarioValidator]; [ProposalRejection.detail] memuat temuan berpath. */
    INVALID,

    /** Usulan sah tetapi seluruhnya dibuang saat disimpan (urutan modul ganda atau kuota [ScenarioLimits.SCENARIOS_MAX] penuh); `attempt = 0`. */
    NOT_ADDED,

    /** Tenant belum punya draf kerja (dibuat saat draf pertama dimuat); hasilnya tetap daftar kosong yang sah; `attempt = 0`. */
    NO_DRAFT
}

/** Satu penolakan percobaan ke-[attempt] (mulai 1; 0 = bukan percobaan pengusul, tahap penyimpanan). [detail] ringkas dan **tanpa isi narasi pengguna** (aman untuk log). */
data class ProposalRejection(val kind: ProposalRejectionKind, val attempt: Int, val detail: String) {
    companion object {
        const val DETAIL_MAX_CHARS = 900
        fun of(kind: ProposalRejectionKind, attempt: Int, detail: String) =
            ProposalRejection(kind, attempt, detail.take(DETAIL_MAX_CHARS))
    }
}
