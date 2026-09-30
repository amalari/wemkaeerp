package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessLevel

/**
 * Tutorial modul bersama platform — kalimatnya netral industri karena dibaca semua pack yang memakai modulnya.
 * Langkah tanpa anchor tampil sebagai callout tengah; anchor ditambahkan saat layar governance diberi titik sorot.
 */
object PlatformTutorials {
    private val DYNAMIC_RBAC = ModuleId("dynamic_rbac")
    private val ORG_CHART = ModuleId("org_chart")

    val anchors: Set<TutorialAnchorId> = emptySet()

    val all: List<ModuleTutorial> = listOf(
        ModuleTutorial(
            id = TutorialId("platform_rbac_role_access"),
            scope = TutorialScope.Module(DYNAMIC_RBAC),
            title = "Mengatur akses jabatan ke modul",
            summary = "Memberi jabatan level akses Lihat, Input & Kerja, atau Akses Penuh per modul, beserta cakupan datanya.",
            requiredLevel = AccessLevel.MANAGE,
            sampleQuestions = listOf(
                "gimana cara kasih akses modul ke jabatan",
                "kenapa staf saya tidak bisa lihat menu",
                "cara ubah hak akses role",
            ),
            keywords = listOf("akses", "hak", "role", "jabatan", "wewenang", "izin", "rbac", "menu"),
            steps = listOf(
                TutorialStep("Pilih sudut pandang", "Gunakan tab Per Modul, Per Divisi, atau Per Jabatan. Untuk mengatur satu jabatan, buka Per Jabatan.", screen = DYNAMIC_RBAC, placement = CalloutPlacement.CENTER),
                TutorialStep("Tambahkan akses", "Di kartu modul, klik \"+ Tambahkan Akses\", pilih divisi atau jabatan, lalu levelnya: Hanya Lihat, Input & Kerja, atau Akses Penuh.", placement = CalloutPlacement.CENTER),
                TutorialStep("Atur cakupan data", "Modul transaksi bisa dibatasi ke Data Sendiri, Data Bawahan, atau Semua Data. Modul data bersama selalu Semua Data.", placement = CalloutPlacement.CENTER),
                TutorialStep("Simpan & uji", "Simpan, lalu cek lewat pengalih persona apakah menunya muncul sesuai harapan.", placement = CalloutPlacement.CENTER),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("platform_org_chart_basics"),
            scope = TutorialScope.Module(ORG_CHART),
            title = "Menyusun struktur organisasi",
            summary = "Menambah divisi dan karyawan, lalu menentukan atasan supaya cakupan Data Bawahan bekerja.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("cara tambah karyawan", "cara bikin divisi baru", "atasan karyawan diatur di mana"),
            keywords = listOf("organisasi", "divisi", "karyawan", "atasan", "bawahan", "struktur", "departemen"),
            steps = listOf(
                TutorialStep("Buka bagan", "Bagan organisasi menampilkan divisi dan karyawan dari atas ke bawah.", screen = ORG_CHART, placement = CalloutPlacement.CENTER),
                TutorialStep("Tambah divisi", "Buat divisi dulu; karyawan selalu berada di satu divisi.", placement = CalloutPlacement.CENTER),
                TutorialStep("Tambah karyawan & atasan", "Tambahkan karyawan dan pilih atasannya. Hubungan atasan inilah yang dipakai cakupan Data Bawahan.", placement = CalloutPlacement.CENTER),
            ),
        ),
    )
}
