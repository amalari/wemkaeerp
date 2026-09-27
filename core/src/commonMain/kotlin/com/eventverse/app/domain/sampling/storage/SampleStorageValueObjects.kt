package com.eventverse.app.domain.sampling.storage

import kotlin.jvm.JvmInline

@JvmInline
value class SampleStorageRecordId(val value: String) {
    init {
        require(value.isNotBlank()) { "SampleStorageRecordId cannot be blank" }
        require(value.length <= 64) { "SampleStorageRecordId must be at most 64 characters" }
    }
}

/**
 * Tempat fisik barang disimpan — bebas teks karena wujudnya beragam: "Rak Packing A",
 * "Gudang Utama Lt. 2", "Lemari Sampel Sales". Yang wajib bukan jenis tempatnya, tapi bahwa
 * tempatnya disebut: tanpa itu, "sampelnya di mana?" kembali dijawab dengan menebak.
 */
@JvmInline
value class StorageLocationLabel(val value: String) {
    init {
        require(value.isNotBlank()) { "Lokasi penyimpanan wajib diisi" }
        require(value.length <= 80) { "Lokasi penyimpanan maksimal 80 karakter" }
    }
}

/** Orang yang memegang tanggung jawab atas barang pada satu titik kustodi. */
data class StorageCustodian(val email: String, val name: String) {
    init {
        require(email.isNotBlank() || name.isNotBlank()) { "Penanggung jawab wajib dikenali" }
    }

    val displayName: String get() = name.ifBlank { email }
}
