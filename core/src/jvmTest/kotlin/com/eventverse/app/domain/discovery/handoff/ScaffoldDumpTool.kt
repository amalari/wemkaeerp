package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import java.io.File
import kotlin.test.Test

/**
 * Alat **manual** (bukan test): menulis keluaran generator pilot ke folder agar bisa ditinjau dan
 * diterapkan manusia. Tidak melakukan apa pun kecuali `PILOT_SCAFFOLD_OUT` diisi:
 * `PILOT_SCAFFOLD_OUT=/tmp/out PILOT_MIGRATION_VERSION=90 ./gradlew :core:jvmTest --tests '*ScaffoldDumpTool*'`.
 * Generator tidak pernah menulis ke pohon sumber sendiri (plan §3.4) — langkah menyalin sengaja manual.
 */
class ScaffoldDumpTool {
    @Test
    fun dumpPilotScaffold_whenRequested() {
        val out = System.getenv("PILOT_SCAFFOLD_OUT")?.takeIf { it.isNotBlank() } ?: return
        val version = System.getenv("PILOT_MIGRATION_VERSION")?.toIntOrNull() ?: 90
        val scaffold = HandoffScaffoldGenerator().generateFromSpec(
            LayananPilotPack.spec, LayananPilotPack.module, version, "com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack.pack"
        )
        scaffold.files.forEach { f -> File(out, f.path).apply { parentFile.mkdirs(); writeText(f.content) } }
    }
}
