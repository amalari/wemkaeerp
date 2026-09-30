// FILE-SIZE-EXEMPT: seed tutorial pack — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialScope
import com.eventverse.app.domain.tutorial.TutorialStep
import com.eventverse.app.domain.pack.GarmentTutorialAnchors as A

/** Tutorial pack garment (TRD-HELP-001 Fase 1: pilot CRM). Modul lain menyusul saat layarnya diberi anchor. */
object GarmentTutorials {
    private val CRM = GarmentModules.CRM_SALES

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
            keywords = listOf("cari", "search", "filter", "saring", "kpi", "performa", "sumber", "pic"),
            steps = listOf(
                TutorialStep("Ringkasan KPI", "Kartu di atas merangkum jumlah lead, konversi, dan nilai pipeline.", anchor = A.CRM_KPI_ROW, screen = CRM),
                TutorialStep("Cari", "Ketik nama brand, kontak, nomor WA, atau kategori. Filter Sales PIC dan Sumber ada di sebelahnya.", anchor = A.CRM_SEARCH),
            ),
        ),
    )
}
