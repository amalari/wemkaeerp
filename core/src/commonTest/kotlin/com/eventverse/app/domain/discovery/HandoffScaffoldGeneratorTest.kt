package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.pack.DomainPackRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan §3 B4: scaffold kandidat PR. Generator murni & deterministik — keluaran hanya teks untuk
 * review manusia; konsistensi byte-per-byte dijaga test agar tidak drift dari pola V76/V64/V77.
 */
class HandoffScaffoldGeneratorTest {

    private val generator = HandoffScaffoldGenerator()

    @AfterTest
    fun cleanup() {
        DomainPackRegistry.unregister(com.eventverse.app.domain.pack.DomainPackCode("klinik"))
    }

    private suspend fun klinikPack() = DeterministicDiscoveryAgent().draft(
        DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik")
    ).getOrThrow().pack

    @Test
    fun `keluaran deterministik dan berisi berkas wajib`() = runTest {
        val pack = klinikPack()
        val first = generator.generate(pack, 79)
        val second = generator.generate(pack, 79)
        assertEquals(first, second)
        assertEquals("klinik", first.packCode)
        assertEquals(4 + pack.modules.size, first.files.size)

        val paths = first.files.map { it.path }
        assertTrue(paths.any { it.endsWith("V079__register_klinik_modules.sql") })
        assertTrue(paths.any { it.endsWith("ModuleSchemaMap.snippet.kt.txt") })
        assertTrue(paths.any { it.endsWith("KlinikStubRoutes.snippet.kt.txt") })
        assertTrue(paths.any { it.endsWith("ModuleScreenRegistry.snippet.kt.txt") })
        assertTrue(paths.all { !it.endsWith(".kt") }, "Tidak ada berkas .kt langsung — semuanya snippet untuk review")
    }

    @Test
    fun `migrasi memuat schema tabel rls grant dan katalog per modul`() = runTest {
        val pack = klinikPack()
        val sql = generator.generate(pack, 79).files.first { it.path.endsWith(".sql") }.content
        val first = pack.modules.first()

        assertTrue(sql.contains("-- KANDIDAT PR"))
        pack.modules.forEach { module ->
            assertTrue(sql.contains("CREATE SCHEMA IF NOT EXISTS ${module.id.value};"))
            assertTrue(sql.contains("SELECT apply_tenant_rls_in('${module.id.value}', '${module.id.value}_records');"))
            assertTrue(sql.contains("GRANT USAGE ON SCHEMA ${module.id.value} TO wemade_app;"))
            assertTrue(sql.contains("'mce-${module.id.value}'"))
        }
        assertTrue(sql.contains("REFERENCES tenants(id)"))
        assertTrue(sql.contains("lifecycle_status"))
        assertTrue(first.id.value.isNotEmpty())
    }

    @Test
    fun `snapshot schema map dan catatan layar menunjuk modul pack`() = runTest {
        val pack = klinikPack()
        val files = generator.generate(pack, 79).files
        val schemaMap = files.first { it.path.contains("ModuleSchemaMap") }.content
        val screens = files.first { it.path.contains("ModuleScreenRegistry") }.content

        pack.modules.forEach { module ->
            assertTrue(schemaMap.contains("ModuleId(\"${module.id.value}\") to setOf(\"${module.id.value}_records\")"))
            assertTrue(screens.contains("// ${module.id.value} to { c ->"))
        }
        assertTrue(screens.contains("layar kerja"))
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}
