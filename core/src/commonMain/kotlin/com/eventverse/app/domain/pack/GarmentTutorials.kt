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
 * Tutorial pack garment: CRM, Sampling, Costing, Factory Flow, Master Data, Vendor, Inventory,
 * Tech Pack BOM, Production MRP, Operator, QC, Fulfillment, Invoicing.
 *
 * Nama tahap sampling adalah data tenant (`TenantStageFlow`), jadi teks tutorial Sampling sengaja generik
 * ("kolom tahap", "tombol aksi di kartu") — tidak menyebut tahap rajut/bordir tertentu.
 */
object GarmentTutorials {
    private val CRM = GarmentModules.CRM_SALES
    private val SAMPLING = GarmentModules.SAMPLING_ORDER
    private val COSTING = GarmentModules.COSTING_HPP
    private val FACTORY_FLOW = GarmentModules.FACTORY_FLOW
    private val MASTER_DATA = GarmentModules.MASTER_DATA
    private val VENDOR = GarmentModules.VENDOR_CONTACTS
    private val INVENTORY = GarmentModules.INVENTORY
    private val TECH_PACK = GarmentModules.TECH_PACK_BOM
    private val MRP = GarmentModules.PRODUCTION_MRP
    private val OPERATOR = GarmentModules.OPERATOR_EXEC
    private val QC = GarmentModules.QUALITY_CONTROL
    private val FULFILLMENT = GarmentModules.FULFILLMENT
    private val INVOICE = GarmentModules.INVOICING

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
        // Factory Flow
        ModuleTutorial(
            id = TutorialId("factory_flow_canvas_basics"),
            scope = TutorialScope.Module(FACTORY_FLOW),
            title = "Membaca kanvas alur pabrik",
            summary = "Memahami node, warisan status, dan legenda warna di kanvas Factory Flow.",
            requiredLevel = AccessLevel.VIEW,
            sampleQuestions = listOf("cara baca kanvas", "artinya node merah", "status bottleneck"),
            keywords = listOf("kanvas", "node", "bottleneck", "status", "merah", "hijau", "legend", "alur", "pabrik"),
            steps = listOf(
                TutorialStep("Header & navigasi", "Header menunjukkan nama tenant dan alur yang aktif. Tombol di kanan untuk menyalakan atau menyembunyikan modul.", anchor = A.FACTORY_FLOW_HEADER),
                TutorialStep("Kanvas alur", "Setiap kolom adalah satu fase (Engineering, Procurement, Production, Delivery). Node di dalam kolom adalah modul yang aktif.", anchor = A.FACTORY_FLOW_CANVAS),
                TutorialStep("Warna node", "Hijau = sehat, Kuning = warning, Merah = bottleneck. Klik kartu untuk melihat detail.", anchor = A.FACTORY_FLOW_CANVAS),
            ),
        ),
        // Master Data
        ModuleTutorial(
            id = TutorialId("master_data_add_category"),
            scope = TutorialScope.Module(MASTER_DATA),
            title = "Menambah kategori dan item data induk",
            summary = "Membuat kategori dan item master data seperti bahan baku, finishing, atau ukuran.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("tambah kategori", "isi master data", "cara input data induk"),
            keywords = listOf("master", "data", "kategori", "item", "tambah", "bahan", "ukuran", "finishing"),
            steps = listOf(
                TutorialStep("Buka Master Data", "Klik modul Master Data untuk melihat daftar kategori.", anchor = A.MASTER_DATA_CATEGORIES),
                TutorialStep("Tambah kategori", "Pilih \"+ Tambah Kategori\" lalu isi nama dan deskripsi.", anchor = A.MASTER_DATA_CATEGORIES),
                TutorialStep("Tambah item", "Di dalam kategori, klik \"+ Tambah Item\" dan isi detailnya.", placement = CalloutPlacement.CENTER),
            ),
        ),
        // Vendor Contacts
        ModuleTutorial(
            id = TutorialId("vendor_register_and_assign"),
            scope = TutorialScope.Module(VENDOR),
            title = "Mendaftarkan vendor makloon",
            summary = "Menambah vendor baru dan menugaskannya ke proses produksi.",
            requiredLevel = AccessLevel.MANAGE,
            sampleQuestions = listOf("daftar vendor", "tambah vendor makloon", "tunjuk makloon"),
            keywords = listOf("vendor", "makloon", "daftar", "tambah", "konveksi", "pabrik", "outsource"),
            steps = listOf(
                TutorialStep("Buka Vendor", "Klik modul Vendor untuk melihat daftar vendor.", anchor = A.VENDOR_LIST),
                TutorialStep("Tambah vendor", "Klik \"+ Tambah Vendor\", isi nama, kontak, dan kategori layanan.", anchor = A.VENDOR_ADD),
                TutorialStep("Tugaskan ke proses", "Setelah vendor dibuat, Anda bisa menugaskannya ke modul terkait.", placement = CalloutPlacement.CENTER),
            ),
        ),
        // Inventory
        ModuleTutorial(
            id = TutorialId("inventory_roll_stock"),
            scope = TutorialScope.Module(INVENTORY),
            title = "Mengelola stok kain per roll",
            summary = "Melihat stok kain, mencari roll, dan memahami status persediaan.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("stok kain", "cari roll", "lihat persediaan"),
            keywords = listOf("inventory", "stok", "kain", "roll", "persediaan", "gudang", "cari"),
            steps = listOf(
                TutorialStep("Cari stok", "Gunakan kolom pencarian untuk menemukan kain berdasarkan nama, kode, atau warna.", anchor = A.INVENTORY_SEARCH),
                TutorialStep("Tabel stok", "Tabel menampilkan roll kain dengan detail: nomor roll, warna, gramasi, dan jumlah tersedia.", anchor = A.INVENTORY_TABLE),
                TutorialStep("Filter", "Gunakan filter untuk menyaring berdasarkan gudang, supplier, atau status.", anchor = A.INVENTORY_SEARCH),
            ),
        ),
        // Tech Pack BOM
        ModuleTutorial(
            id = TutorialId("techpack_compose_bom"),
            scope = TutorialScope.Module(TECH_PACK),
            title = "Menyusun komponen BOM",
            summary = "Membuat dan mengelola Bill of Materials untuk Tech Pack.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("bikin bom", "komponen apa saja", "tambah material"),
            keywords = listOf("techpack", "bom", "bill of materials", "komponen", "material", "kain", "aksesoris"),
            steps = listOf(
                TutorialStep("Buka Tech Pack", "Pilih Tech Pack yang ingin diubah, lalu buka tab BOM.", anchor = A.TECH_PACK_BOM_ROWS),
                TutorialStep("Tambah komponen", "Klik \"+ Tambah Komponen\" untuk menambahkan material.", anchor = A.TECH_PACK_BOM_ADD),
                TutorialStep("Isi detail", "Masukkan nama material, jumlah, dan satuan. Simpan setelah selesai.", placement = CalloutPlacement.CENTER),
            ),
        ),
        // Production MRP
        ModuleTutorial(
            id = TutorialId("mrp_allocate_machine"),
            scope = TutorialScope.Module(MRP),
            title = "Menjadwalkan dan mengalokasikan mesin",
            summary = "Mengatur jadwal produksi dan mengalokasikan mesin atau lini produksi.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("jadwal mesin", "alokasi lini", "atur produksi"),
            keywords = listOf("mrp", "jadwal", "mesin", "lini", "alokasi", "produksi", "scheduling"),
            steps = listOf(
                TutorialStep("Buka MRP", "Klik modul Production MRP untuk melihat jadwal.", anchor = A.MRP_BOARD),
                TutorialStep("Lihat jadwal", "Papan menunjukkan jadwal produksi per mesin atau lini.", anchor = A.MRP_SCHEDULE),
                TutorialStep("Alokasikan", "Seret pesanan ke mesin atau lini yang tersedia.", anchor = A.MRP_BOARD),
            ),
        ),
        // Operator
        ModuleTutorial(
            id = TutorialId("operator_start_finish_task"),
            scope = TutorialScope.Module(OPERATOR),
            title = "Mulai dan selesaikan tugas operator",
            summary = "Memulai dan menyelesaikan pekerjaan di lantai produksi.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("mulai tugas", "selesaikan pekerjaan", "input produksi"),
            keywords = listOf("operator", "tugas", "mulai", "selesai", "produksi", "input", "floor"),
            steps = listOf(
                TutorialStep("Daftar tugas", "Lihat daftar tugas yang harus dikerjakan.", anchor = A.OPERATOR_TASK_LIST),
                TutorialStep("Mulai tugas", "Klik \"Mulai\" pada tugas yang akan dikerjakan.", anchor = A.OPERATOR_START_STOP),
                TutorialStep("Selesai", "Setelah selesai, klik \"Selesai\" dan input jumlah yang diproduksi.", anchor = A.OPERATOR_START_STOP),
            ),
        ),
        // Quality Control
        ModuleTutorial(
            id = TutorialId("qc_inspect_and_grade"),
            scope = TutorialScope.Module(QC),
            title = "Inspeksi dan grading hasil QC",
            summary = "Melakukan inspeksi produk dan memberikan grading kualitas.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("inspeksi", "grading cacat", "quality control"),
            keywords = listOf("qc", "quality", "inspeksi", "grading", "cacat", "grade", "aql"),
            steps = listOf(
                TutorialStep("Buka QC", "Klik modul Quality Control untuk melihat antrean inspeksi.", anchor = A.QC_INSPECT),
                TutorialStep("Inspeksi", "Pilih produk yang akan diinspeksi, periksa kondisi.", anchor = A.QC_INSPECT),
                TutorialStep("Berikan grade", "Pilih grade (A, B, C, atau reject) dan simpan.", anchor = A.QC_GRADE),
            ),
        ),
        // Fulfillment
        ModuleTutorial(
            id = TutorialId("fulfillment_packing_ship"),
            scope = TutorialScope.Module(FULFILLMENT),
            title = "Packing dan membuat surat jalan",
            summary = "Mengatur packing produk dan membuat surat jalan pengiriman.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("packing", "bikin surat jalan", "kirim barang"),
            keywords = listOf("fulfillment", "packing", "surat jalan", "kirim", "pengiriman", "pack"),
            steps = listOf(
                TutorialStep("Daftar packing", "Lihat daftar pesanan yang harus dikemas.", anchor = A.FULFILLMENT_PACK_LIST),
                TutorialStep("Buat packing", "Pilih pesanan, buat daftar packing, dan masukkan detail.", anchor = A.FULFILLMENT_PACK_LIST),
                TutorialStep("Cetak surat jalan", "Setelah packing selesai, cetak surat jalan.", anchor = A.FULFILLMENT_SURAT_JALAN),
            ),
        ),
        // Invoicing
        ModuleTutorial(
            id = TutorialId("invoice_create_and_send"),
            scope = TutorialScope.Module(INVOICE),
            title = "Membuat dan mengirim invoice",
            summary = "Membuat invoice untuk pelanggan dan mengirimkannya.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf("bikin invoice", "kirim tagihan", "buat faktur"),
            keywords = listOf("invoice", "faktur", "tagihan", "kirim", "pelanggan", "pembayaran"),
            steps = listOf(
                TutorialStep("Daftar invoice", "Lihat semua invoice yang ada.", anchor = A.INVOICE_LIST),
                TutorialStep("Buat invoice baru", "Klik \"+ Invoice Baru\" dan pilih pesanan.", anchor = A.INVOICE_NEW),
                TutorialStep("Kirim", "Setelah lengkap, klik \"Kirim\" untuk mengirim ke pelanggan.", placement = CalloutPlacement.CENTER),
            ),
        ),
    )
}
