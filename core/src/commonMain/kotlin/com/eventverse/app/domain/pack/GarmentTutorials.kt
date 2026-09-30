// FILE-SIZE-EXEMPT: seed tutorial pack — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialScope
import com.eventverse.app.domain.tutorial.TutorialStep
import com.eventverse.app.domain.pack.GarmentTutorialAnchors as A

/**
 * Tutorial pack garment: CRM, Sampling, Costing. Modul lain menyusul saat layarnya diberi anchor.
 *
 * Nama tahap sampling adalah data tenant (`TenantStageFlow`), jadi teks tutorial Sampling sengaja generik
 * ("kolom tahap", "tombol aksi di kartu") — tidak menyebut tahap rajut/bordir tertentu.
 */
object GarmentTutorials {
    private val CRM = GarmentModules.CRM_SALES
    private val SAMPLING = GarmentModules.SAMPLING_ORDER
    private val COSTING = GarmentModules.COSTING_HPP

    val all: List<ModuleTutorial> = listOf(
        ModuleTutorial(
            id = TutorialId("crm_new_lead"),
            scope = TutorialScope.Module(CRM),
            title = "Mencatat lead baru",
            summary = "Membuat lead dari calon pembeli (brand, kontak, kategori produk, estimasi pcs) di papan CRM.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf(
                "gimana cara bikin lead baru",
                "cara input calon customer",
                "ada buyer baru chat WA, dicatat di mana",
            ),
            keywords = listOf("lead", "prospek", "calon", "pembeli", "buyer", "customer", "pelanggan", "tambah", "baru", "input"),
            steps = listOf(
                TutorialStep("Buka tab Leads", "Semua calon pembeli dikelola di tab Leads.", anchor = A.CRM_DIRECTORY_TABS, screen = CRM),
                TutorialStep("Tambah lead", "Klik \"+ Tambah Lead\". Lead baru selalu masuk ke kolom New Lead.", anchor = A.CRM_ADD_LEAD, placement = CalloutPlacement.START),
                TutorialStep("Isi data pembeli", "Isi nama brand, kontak, nomor WA, kategori produk, dan estimasi pcs, lalu simpan.", placement = CalloutPlacement.CENTER),
                TutorialStep("Cek di papan", "Lead muncul di kolom New Lead. Klik kartunya untuk melihat detail dan mencatat aktivitas.", anchor = A.CRM_KANBAN_COLUMNS, placement = CalloutPlacement.TOP),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("crm_move_stage"),
            scope = TutorialScope.Module(CRM),
            title = "Memindahkan lead antar tahap",
            summary = "Menggeser kartu lead dari New Lead sampai Qualified; saat Qualified, kontak dan deal dibuat otomatis.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("cara pindah status lead", "lead sudah deal gimana", "kenapa lead tidak bisa langsung qualified"),
            keywords = listOf("tahap", "stage", "status", "pindah", "geser", "qualified", "deal", "kanban", "kolom"),
            steps = listOf(
                TutorialStep("Papan tahap", "Setiap kolom adalah satu tahap lead, dari kiri ke kanan.", anchor = A.CRM_KANBAN_COLUMNS, screen = CRM, placement = CalloutPlacement.TOP),
                TutorialStep("Geser kartu", "Seret kartu ke kolom berikutnya, atau buka kartu dan pilih tahap dari menunya.", anchor = A.CRM_KANBAN_COLUMNS, placement = CalloutPlacement.TOP),
                TutorialStep("Qualified membuat deal", "Saat lead masuk Qualified, kontak dan deal dibuat bersamaan dan bisa dilihat di tab Deal.", anchor = A.CRM_DIRECTORY_TABS),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("crm_find_lead"),
            scope = TutorialScope.Module(CRM),
            title = "Mencari dan menyaring lead",
            summary = "Mencari lead berdasarkan brand, kontak, atau nomor WA, dan membaca ringkasan KPI penjualan.",
            sampleQuestions = listOf("cara cari lead", "lead saya hilang", "lihat performa sales"),
            keywords = listOf("cari", "search", "filter", "saring", "kpi", "performa", "sumber", "pic", "wa", "whatsapp", "nomor", "kontak", "brand", "lead"),
            steps = listOf(
                TutorialStep("Ringkasan KPI", "Kartu di atas merangkum jumlah lead, konversi, dan nilai pipeline.", anchor = A.CRM_KPI_ROW, screen = CRM),
                TutorialStep("Cari", "Ketik nama brand, kontak, nomor WA, atau kategori. Filter Sales PIC dan Sumber ada di sebelahnya.", anchor = A.CRM_SEARCH),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("sampling_board_overview"),
            scope = TutorialScope.Module(SAMPLING),
            title = "Membaca papan SPK sampling",
            summary = "Memahami asal SPK sampling, kolom tahap di papan, dan cara membuka detail satu SPK.",
            sampleQuestions = listOf("spk sampling dari mana", "cara lihat detail spk", "kenapa spk sampling kosong", "status sampel sudah sampai mana"),
            keywords = listOf("spk", "sampling", "sampel", "sample", "papan", "kanban", "tahap", "detail", "status"),
            steps = listOf(
                TutorialStep("Asal SPK", "SPK sampling masuk otomatis saat pesanan sampling disetujui di Deal CRM. Jumlahnya tampil di sini.", anchor = A.SAMPLING_HEADER, screen = SAMPLING),
                TutorialStep("Kolom tahap", "Setiap kolom adalah satu tahap kerja pabrik Anda, dari SPK masuk sampai ACC buyer. Nama tahap mengikuti alur pabrik.", anchor = A.SAMPLING_BOARD, placement = CalloutPlacement.TOP),
                TutorialStep("Buka detail", "Klik kartu SPK untuk melihat detail desain, alur, dan riwayatnya.", anchor = A.SAMPLING_BOARD, placement = CalloutPlacement.TOP),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("sampling_move_stage"),
            scope = TutorialScope.Module(SAMPLING),
            title = "Memajukan SPK ke tahap berikutnya",
            summary = "Memindahkan kartu SPK antar tahap dengan diseret atau lewat tombol aksi di kartu, sampai ACC atau revisi buyer.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("cara pindah tahap spk", "sampel sudah selesai dijahit gimana", "cara acc buyer", "sampel direvisi buyer"),
            keywords = listOf("pindah", "maju", "tahap", "geser", "seret", "acc", "revisi", "mulai", "selesai", "spk"),
            steps = listOf(
                TutorialStep("Seret kartu", "Seret kartu SPK ke kolom tahap berikutnya. Kolom tujuan menandai \"Lepas di sini\".", anchor = A.SAMPLING_BOARD, screen = SAMPLING, placement = CalloutPlacement.TOP),
                TutorialStep("Atau tombol di kartu", "Kartu punya tombol aksi sesuai tahapnya, misalnya menentukan alur desain, memulai pembuatan, ACC buyer, atau revisi.", anchor = A.SAMPLING_BOARD, placement = CalloutPlacement.TOP),
                TutorialStep("Isi dialog bila diminta", "Beberapa tahap meminta data dulu (lembar kerja, penyimpanan, atau pengiriman) sebelum kartu pindah.", placement = CalloutPlacement.CENTER),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("sampling_flow_template"),
            scope = TutorialScope.Module(SAMPLING),
            title = "Mengatur template alur pabrik",
            summary = "Menentukan urutan tahap kerja default yang dipakai SPK sampling baru di pabrik Anda.",
            requiredLevel = AccessLevel.MANAGE,
            sampleQuestions = listOf("cara ubah urutan tahap sampling", "tambah tahap di alur pabrik", "template alur"),
            keywords = listOf("template", "alur", "urutan", "tahap", "atur", "pabrik", "flow"),
            steps = listOf(
                TutorialStep("Buka template", "Klik \"Template Alur Pabrik\" untuk mengatur urutan tahap default.", anchor = A.SAMPLING_FLOW_TEMPLATE, screen = SAMPLING),
                TutorialStep("Berlaku untuk SPK baru", "Perubahan template dipakai SPK yang baru mulai. SPK yang sudah berjalan tetap memakai alur saat ia dimulai.", placement = CalloutPlacement.CENTER),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("costing_new_sheet"),
            scope = TutorialScope.Module(COSTING),
            title = "Menghitung HPP baru",
            summary = "Membuat lembar HPP dari Tech Pack, menghitung biayanya, lalu mengajukan persetujuan.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("gimana cara hitung hpp", "cara bikin costing baru", "hitung harga pokok produksi", "ajukan hpp ke atasan"),
            keywords = listOf("hpp", "costing", "biaya", "harga", "pokok", "hitung", "baru", "ajukan", "persetujuan"),
            steps = listOf(
                TutorialStep("Buat lembar HPP", "Klik \"+ Hitung HPP Baru\" lalu pilih Tech Pack dan tekan \"Buat Draft\".", anchor = A.COSTING_NEW_SHEET, screen = COSTING, placement = CalloutPlacement.START),
                TutorialStep("Hitung", "Di tab Rincian HPP, tekan \"Hitung HPP\". Rumusnya mengikuti preset bisnis dan rate card pabrik Anda.", placement = CalloutPlacement.CENTER),
                TutorialStep("Ajukan persetujuan", "Tekan \"Ajukan Persetujuan\". Pemegang akses penuh modul ini yang menyetujui atau menolak.", placement = CalloutPlacement.CENTER),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("costing_find_sheet"),
            scope = TutorialScope.Module(COSTING),
            title = "Mencari lembar HPP",
            summary = "Mencari lembar HPP berdasarkan nomor atau Tech Pack dan menyaring statusnya.",
            sampleQuestions = listOf("cari hpp", "hpp saya di mana", "lihat hpp yang sudah disetujui"),
            keywords = listOf("cari", "hpp", "nomor", "status", "filter", "saring", "daftar"),
            steps = listOf(
                TutorialStep("Cari", "Ketik nomor HPP atau Tech Pack. Lencana status di bawahnya menyaring daftar.", anchor = A.COSTING_SEARCH, screen = COSTING, placement = CalloutPlacement.END),
            ),
        ),
        ModuleTutorial(
            id = TutorialId("costing_quick_estimate"),
            scope = TutorialScope.Module(COSTING),
            title = "Estimasi harga cepat untuk pelanggan",
            summary = "Memakai Estimator Cepat dan rate card untuk memberi perkiraan harga sebelum ada Tech Pack.",
            sampleQuestions = listOf("customer tanya harga kira kira", "estimasi harga cepat", "rate card di mana"),
            keywords = listOf("estimasi", "estimator", "cepat", "perkiraan", "harga", "rate", "card", "tarif", "simulasi"),
            steps = listOf(
                TutorialStep("Tab kerja", "Tab di sini berisi Rincian, Rate Card, Simulasi, dan Estimator Cepat.", anchor = A.COSTING_TABS, screen = COSTING),
                TutorialStep("Estimator Cepat", "Buka \"Estimator Cepat (CS)\". Tab ini bisa dipakai tanpa memilih lembar HPP.", anchor = A.COSTING_TABS),
                TutorialStep("Rate card", "Tarif yang dipakai estimasi ada di \"Rate Card Tenant\".", anchor = A.COSTING_TABS),
            ),
        ),
    )
}
