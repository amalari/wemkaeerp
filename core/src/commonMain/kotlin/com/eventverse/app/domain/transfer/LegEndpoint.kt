package com.eventverse.app.domain.transfer

/**
 * Ujung sebuah perpindahan barang: dari mana, atau ke mana.
 *
 * Sengaja bertipe, bukan tiga field nullable (`originLocationId` / `vendorRef` /
 * `customerName`) seperti pada [SuratJalanManifest]. Buyer bukan baris `tenant_locations`, dan
 * vendor makloon juga bukan — memaksakan ketiganya menjadi [LocationId] akan menuntut pembaca
 * menebak kombinasi mana yang sah, dan menuntut penulis mengisi `null` yang benar.
 *
 * Perbandingan kesetaraan ujung inilah yang melahirkan leg: dua simpul alur berurutan yang
 * ujungnya sama tidak menghasilkan perpindahan apa pun.
 */
sealed interface LegEndpoint {

    /** Label untuk ditampilkan pada konektor di panel alur. */
    val displayLabel: String

    /** Gedung atau cabang milik tenant sendiri. */
    data class Site(val locationId: LocationId, val name: String = locationId.value) : LegEndpoint {
        override val displayLabel: String get() = name
    }

    /**
     * Vendor makloon di luar pabrik. Diturunkan dari `vendorRef` proses, bukan dari pemetaan
     * lokasi — vendor tidak perlu didaftarkan sebagai lokasi fisik tenant.
     */
    data class Vendor(val ref: String) : LegEndpoint {
        init {
            require(ref.isNotBlank()) { "Rujukan vendor tidak boleh kosong" }
        }

        override val displayLabel: String get() = ref
    }

    /** Pembeli penerima akhir. Namanya berasal dari order, bukan konfigurasi tenant. */
    data class Customer(val name: String) : LegEndpoint {
        init {
            require(name.isNotBlank()) { "Nama pembeli tidak boleh kosong" }
        }

        override val displayLabel: String get() = name
    }
}
