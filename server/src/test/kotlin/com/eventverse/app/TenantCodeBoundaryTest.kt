package com.eventverse.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pagar impor kode khusus tenant (J3) — TRD-PLAT-004 P2, PLAN-module-ownership-lanes Track B4. **Memblokir.**
 *
 * Mesin (J0) dan pack bawaan (J1) tidak boleh menyebut kode J3 secara langsung; mereka membaca
 * [com.eventverse.app.tenant.TenantPackContributions]. Hanya berkas di paket J3 (`…/domain/pack/tenant/…` dan
 * `…/app/tenant/…`, termasuk registri itu sendiri) yang boleh menyebutnya. Dipindai: `src/commonMain` core dan app,
 * `src/main` server. Tes dikecualikan dengan sengaja — fixture dan tes modul tenant memang mengimpor J3.
 *
 * **Batas yang diketahui**: pagar menangkap sebutan nama paket (impor atau nama lengkap) di sumber, bukan akses lewat
 * refleksi atau susunan string. Ia menangkap kesalahan lazim, bukan sabotase.
 */
class TenantCodeBoundaryTest {

    private val j3Reference = Regex("""com\.eventverse\.app\.(domain\.pack\.tenant|tenant\.[a-z][A-Za-z0-9_]*)\.""")

    /** Berkas yang boleh menyebut J3: yang berada di paket J3. */
    private fun mayReferenceJ3(path: String) = "/domain/pack/tenant/" in path || "/com/eventverse/app/tenant/" in path

    private fun violations(files: Map<String, String>): List<String> = files.flatMap { (path, text) ->
        if (mayReferenceJ3(path)) emptyList()
        else text.lineSequence().withIndex()
            .filter { (_, line) -> j3Reference.containsMatchIn(line) }
            .map { (i, line) -> "$path:${i + 1}: ${line.trim()}" }.toList()
    }

    private fun sources(): Map<String, String> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        return listOf("core/src/commonMain", "app/shared/src/commonMain", "server/src/main")
            .map { File(root, it) }.filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .associate { it.path to it.readText() }
    }

    @Test
    fun `mesin dan pack bawaan tidak menyebut kode khusus tenant`() {
        val found = violations(sources())
        assertEquals(emptyList(), found, "Kode J3 hanya boleh disebut dari paket J3 dan registri TenantPackContributions:\n" + found.joinToString("\n"))
    }

    @Test
    fun `pemindai benar-benar menemukan pelanggar - impor langsung dan nama lengkap`() {
        val bad = mapOf(
            "server/src/main/kotlin/com/eventverse/app/routes/RouteOwnership.kt" to
                "import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack\n",
            "server/src/main/kotlin/com/eventverse/app/routes/X.kt" to
                "val r = com.eventverse.app.tenant.klinik.klinikRoutes()\n",
            "core/src/commonMain/kotlin/com/eventverse/app/domain/pack/GarmentDomainPack.kt" to
                "import com.eventverse.app.domain.pack.tenant.klinik.KlinikPack\n"
        )
        assertEquals(3, violations(bad).size)
    }

    @Test
    fun `pemindai membiarkan paket J3, registri, dan akses ke registri dari mesin`() {
        val ok = mapOf(
            "server/src/main/kotlin/com/eventverse/app/tenant/TenantPackContributions.kt" to
                "import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack\nimport com.eventverse.app.tenant.layanan.layananChangeRequestRoutes\n",
            "server/src/main/kotlin/com/eventverse/app/tenant/layanan/A.kt" to "import com.eventverse.app.tenant.layanan.B\n",
            "core/src/commonMain/kotlin/com/eventverse/app/domain/pack/tenant/layanan/LayananPilotPack.kt" to
                "import com.eventverse.app.domain.pack.tenant.layanan.Z\n",
            "server/src/main/kotlin/com/eventverse/app/infrastructure/ModuleSchemaMap.kt" to
                "val t = com.eventverse.app.tenant.TenantPackContributions.tables\n"
        )
        assertTrue(violations(ok).isEmpty(), violations(ok).toString())
    }
}
