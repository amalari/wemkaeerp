package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode

/**
 * Keadaan pemuatan Org Chart (TRD-PLAT-010 K1). Empat keadaan yang berbeda dan TIDAK dicampur:
 * [Loading] (belum ada respons), [Empty] (respons sukses, tenant belum punya divisi maupun karyawan),
 * [Loaded] (ada data), dan [Failed] (jaringan/HTTP/isi tak terbaca).
 *
 * Tidak ada jalur yang mengubah [Empty] atau [Failed] menjadi data contoh. Contoh hanya masuk lewat aksi
 * pengguna "Muat contoh" yang dieksekusi server lalu dibaca ulang (K3/K4), atau lewat [OrgChartSeed.GarmentSample]
 * yang khusus tes.
 *
 * [Loaded] membawa potret hasil pemuatan; daftar yang dipakai layar (dan berubah oleh suntingan lokal)
 * tetap `OrgChartUiState.employees`/`departments`, jadi layar hanya membaca JENIS keadaannya.
 */
sealed interface OrgChartLoadState {
    data object Loading : OrgChartLoadState
    data object Empty : OrgChartLoadState
    data class Loaded(val departments: List<Department>, val employees: List<OrgNode>) : OrgChartLoadState
    data class Failed(val message: String) : OrgChartLoadState

    companion object {
        /**
         * Menerjemahkan hasil klien tanpa fallback: salah satu gagal = [Failed] (bukan setengah data);
         * keduanya sukses dan kosong = [Empty]; selain itu [Loaded] (divisi saja atau karyawan saja tetap [Loaded]).
         */
        fun from(departments: Result<List<Department>>, employees: Result<List<OrgNode>>): OrgChartLoadState {
            val depts = departments.getOrElse { return Failed(failureMessage(it)) }
            val emps = employees.getOrElse { return Failed(failureMessage(it)) }
            return if (depts.isEmpty() && emps.isEmpty()) Empty else Loaded(depts, emps)
        }

        private fun failureMessage(cause: Throwable): String =
            cause.message?.takeIf { it.isNotBlank() } ?: "Gagal memuat struktur organisasi."
    }
}

/** True bila server sudah menjawab sukses (kosong atau berisi): daftar di layar boleh dibaca sebagai fakta. */
val OrgChartLoadState.isResolved: Boolean
    get() = this is OrgChartLoadState.Empty || this is OrgChartLoadState.Loaded
