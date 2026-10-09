package com.eventverse.app.routes

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Gerbang kontrak route **hasil scaffold** yang ter-check-in (TRD-FIELD-003 §5, Track B butir 5):
 * `LayananChangeRequestRoutes` adalah kandidat generator yang sudah diterapkan; ia wajib memvalidasi
 * nilai di **POST dan PUT** sebelum menyimpan (pola `dateProblem`/`textProblem`/`multiProblem` di
 * `SpecRoutesWriter`). Tes ini membaca berkasnya apa adanya — bila validasi dihilangkan dari salah satu
 * verb, build gagal. Modul berjalan **tidak diubah**.
 */
class LayananChangeRequestRoutesContractTest {

    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private val source: String = File(
        repoRoot,
        "server/src/main/kotlin/com/eventverse/app/tenant/layanan/LayananChangeRequestRoutes.kt"
    ).readText()

    private val postBlock = source.substringAfter("post {").substringBefore("put(\"/{id}\") {")
    private val putBlock = source.substringAfter("put(\"/{id}\") {").substringBefore("delete(\"/{id}\") {")

    @Test
    fun `POST memvalidasi nilai sebelum menyimpan`() {
        assertTrue("dateProblem(values)" in postBlock, "POST wajib memvalidasi nilai (dateProblem): $postBlock")
        assertTrue(
            postBlock.indexOf("dateProblem(values)") < postBlock.indexOf("repository.save"),
            "validasi POST harus mendahului repository.save"
        )
        assertTrue("HttpStatusCode.BadRequest" in postBlock, "nilai tak sah → 400 di POST")
    }

    @Test
    fun `PUT memvalidasi nilai sebelum menyimpan`() {
        assertTrue("dateProblem(values)" in putBlock, "PUT wajib memvalidasi nilai (dateProblem): $putBlock")
        assertTrue(
            putBlock.indexOf("dateProblem(values)") < putBlock.indexOf("repository.save"),
            "validasi PUT harus mendahului repository.save"
        )
        assertTrue("HttpStatusCode.BadRequest" in putBlock, "nilai tak sah → 400 di PUT")
    }
}
