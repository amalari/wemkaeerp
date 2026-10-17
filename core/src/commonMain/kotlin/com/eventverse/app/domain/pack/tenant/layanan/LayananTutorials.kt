package com.eventverse.app.domain.pack.tenant.layanan

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.PackTutorialSource
import com.eventverse.app.domain.pack.TutorialSourceRegistry
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialAnchorId
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialScope
import com.eventverse.app.domain.tutorial.TutorialStep

/**
 * Tutorial pack layanan (J3): untuk modul Permintaan Perubahan.
 *
 * Pack tenant hanya bisa diakses oleh tenant pemilik pack — dijamin karena resolusi pack
 * sudah per-tenant (TenantContext.pack → DomainPackRegistry), dan katalog difilter RBAC.
 */
object LayananTutorials {
    // Anchor for change request module (since it's a new screen, we use a simple anchor)
    val CHANGE_REQUEST_LIST = TutorialAnchorId("layanan_change_request.list")
    val CHANGE_REQUEST_ADD = TutorialAnchorId("layanan_change_request.add")

    val anchors: Set<TutorialAnchorId> = setOf(CHANGE_REQUEST_LIST, CHANGE_REQUEST_ADD)

    val all: List<ModuleTutorial> = listOf(
        ModuleTutorial(
            id = TutorialId("layanan_change_request_basics"),
            scope = TutorialScope.Module(LayananPilotPack.CHANGE_REQUEST),
            title = "Mengelola permintaan perubahan",
            summary = "Membuat, mengelola, dan menyelesaikan permintaan customisasi dari klien.",
            requiredLevel = AccessLevel.OPERATE,
            sampleQuestions = listOf(
                "cara tambah permintaan perubahan",
                "cara ubah status permintaan",
                "cara selesaikan request klien"
            ),
            keywords = listOf("permintaan", "perubahan", "customisasi", "klien", "request", "ubah", "selesai"),
            steps = listOf(
                TutorialStep("Buka papan permintaan", "Lihat semua permintaan perubahan di papan kanban.", anchor = CHANGE_REQUEST_LIST),
                TutorialStep("Tambah permintaan", "Klik '+ Tambah Permintaan' untuk membuat permintaan baru.", anchor = CHANGE_REQUEST_ADD),
                TutorialStep("Isi detail", "Masukkan judul, peminta, prioritas, dan detail lainnya.", placement = CalloutPlacement.CENTER),
                TutorialStep("Kelola status", "Pindahkan kartu antar kolom: Baru → Ditinjau → Disetujui → Selesai.", anchor = CHANGE_REQUEST_LIST),
            ),
        ),
    )
}

/**
 * PackTutorialSource untuk pack layanan.
 */
object LayananTutorialSource : PackTutorialSource {
    override fun tutorialsFor(pack: DomainPack): List<ModuleTutorial> =
        if (pack.code == LayananPilotPack.CODE) LayananTutorials.all else emptyList()

    override fun anchorsFor(pack: DomainPack): Set<TutorialAnchorId> =
        if (pack.code == LayananPilotPack.CODE) LayananTutorials.anchors else emptySet()

    init {
        // Register layanan pack to the registry
        TutorialSourceRegistry.register(LayananPilotPack.CODE, this)
    }
}
