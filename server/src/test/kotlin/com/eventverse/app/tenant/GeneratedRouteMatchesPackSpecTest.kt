package com.eventverse.app.tenant

import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TRD-FIELD-004 FR-5.2: artifact pilot terkomit tidak boleh tertinggal dari spec sumber. */
class GeneratedRouteMatchesPackSpecTest {
    private val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val generated = HandoffScaffoldGenerator().generateFromSpec(
        LayananPilotPack.spec,
        LayananPilotPack.module,
        90,
        "com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack.pack"
    )

    @Test
    fun `route table and repository committed match current pack spec`() {
        assertArtifactMatches(
            "LayananChangeRequestRoutes.kt",
            "server/src/main/kotlin/com/eventverse/app/tenant/layanan/LayananChangeRequestRoutes.kt"
        )
        assertArtifactMatches(
            "LayananChangeRequestTables.kt",
            "server/src/main/kotlin/com/eventverse/app/tenant/layanan/LayananChangeRequestTables.kt"
        )
        assertArtifactMatches(
            "PostgresLayananChangeRequestRepository.kt",
            "server/src/main/kotlin/com/eventverse/app/tenant/layanan/PostgresLayananChangeRequestRepository.kt"
        )
    }

    @Test
    fun `additive migration contains every post-v90 field missing from original table`() {
        val migration = File(
            repoRoot,
            "server/src/main/resources/db/migration/V100__add_file_relation_to_layanan_change_request.sql"
        ).readText()
        assertTrue("ADD COLUMN IF NOT EXISTS lampiran TEXT" in migration)
        assertTrue("ADD COLUMN IF NOT EXISTS rujukan VARCHAR(64)" in migration)
    }

    private fun assertArtifactMatches(generatedSuffix: String, committedPath: String) {
        val expected = generated.files.single { it.path.endsWith(generatedSuffix) }.content
        val actual = File(repoRoot, committedPath).readText()
        assertEquals(
            canonicalCode(expected),
            canonicalCode(actual),
            "$committedPath drift dari LayananPilotPack.spec; generate ulang artifact C1"
        )
    }

    /** Package/import berbeda karena artifact pilot dimiliki package tenant; kode executable harus identik. */
    private fun canonicalCode(source: String): String {
        var inBlockComment = false
        return source.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("/*")) inBlockComment = true
            val ignored = inBlockComment || trimmed.startsWith("package ") || trimmed.startsWith("import ") ||
                trimmed.startsWith("//") || trimmed.isEmpty()
            if (trimmed.endsWith("*/")) inBlockComment = false
            if (ignored) null else trimmed
        }.joinToString("").replace(Regex("\\s+"), "")
    }
}
