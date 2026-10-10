package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.handoff.SpecRoutesWriter
import com.eventverse.app.domain.discovery.handoff.SpecTable
import com.eventverse.app.domain.storage.FileRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kepemilikan ref FILE (hardening lintas tenant): `isValid` hanya bentuk; jalur tulis server wajib
 * memeriksa segmen tenant. Tenant uji `abc` vs `abcd` menangkap `startsWith` tanpa pemisah segmen.
 */
class FileOwnershipTest {
    private val entity = EntitySpec("pesanan_bordir", "Pesanan bordir", listOf(
        FieldSpec("nama", "Nama", FieldType.TEXT),
        FieldSpec("lampiran", "Lampiran", FieldType.FILE),
        FieldSpec("sketsa", "Sketsa", FieldType.FILE)
    ))
    private val own = "fields/abc/pesanan_bordir/r-1/lampiran-a1b2c3-scan.pdf"
    private val foreign = "fields/abcd/pesanan_bordir/r-1/lampiran-a1b2c3-scan.pdf"

    // ---- FileRef.isValidFor ---------------------------------------------------------------------

    @Test
    fun isValidFor_acceptsOwnTenantSegment_andModuleWhenGiven() {
        assertTrue(FileRef.isValidFor("abc", own))
        assertTrue(FileRef.isValidFor("abc", own, moduleCode = "pesanan_bordir"))
    }

    @Test
    fun isValidFor_rejectsOtherTenant_includingSharedPrefix() {
        assertFalse(FileRef.isValidFor("abc", foreign), "'abc' tidak boleh cocok dengan 'abcd'")
        assertFalse(FileRef.isValidFor("abcd", own), "'abcd' tidak boleh cocok dengan 'abc'")
        assertFalse(FileRef.isValidFor("ab", own))
    }

    @Test
    fun isValidFor_rejectsWrongModule_blankTenant_andShortOrMalformedShapes() {
        assertFalse(FileRef.isValidFor("abc", own, moduleCode = "procurement"))
        assertFalse(FileRef.isValidFor("", own))
        assertFalse(FileRef.isValidFor("abc", "fields/abc/pesanan_bordir/r-1"), "kurang dari 4 segmen")
        assertFalse(FileRef.isValidFor("abc", "fields/abc//r-1/x.pdf"), "segmen kosong")
        assertFalse(FileRef.isValidFor("abc", "fields/abc/../abcd/r-1/x.pdf"), "traversal")
        assertFalse(FileRef.isValidFor("abc", "uploads/abc/pesanan_bordir/r-1/x.pdf"))
    }

    @Test
    fun build_resultIsAlwaysValidForItsOwnTenant_andNeverForAnother() {
        val ref = FileRef.build("abc", "pesanan_bordir", "r-1", "lampiran", "scan.pdf").value
        assertTrue(FileRef.isValidFor("abc", ref, "pesanan_bordir"))
        assertFalse(FileRef.isValidFor("abcd", ref))
    }

    // ---- EntitySpec.fileOwnershipProblem ----------------------------------------------------------

    @Test
    fun fileOwnershipProblem_foreignRefInAnyFileField_isReported() {
        val problem = entity.fileOwnershipProblem("abc", mapOf("lampiran" to own, "sketsa" to foreign))
        assertNotNull(problem)
        assertTrue("Sketsa" in problem, problem)
    }

    @Test
    fun fileOwnershipProblem_ownEmptyAndMissing_isClean() {
        assertNull(entity.fileOwnershipProblem("abc", mapOf("lampiran" to own, "sketsa" to "", "nama" to "x")))
        assertNull(entity.fileOwnershipProblem("abc", emptyMap()))
    }

    @Test
    fun fileOwnershipProblem_ignoresNonFileFieldsEvenIfTheyLookLikeRefs() {
        assertNull(entity.fileOwnershipProblem("abc", mapOf("nama" to foreign)))
    }

    @Test
    fun fileOwnershipProblem_pack2Tenant_samePackShapeDifferentTenant() {
        val other = EntitySpec("pesanan_sablon", "Pesanan sablon", listOf(FieldSpec("desain", "Desain", FieldType.FILE)))
        assertNull(other.fileOwnershipProblem("sablon-uji", mapOf("desain" to "fields/sablon-uji/pesanan_sablon/r-9/desain-a1b2c3-x.png")))
        assertNotNull(other.fileOwnershipProblem("sablon-uji", mapOf("desain" to "fields/bordir-uji/pesanan_sablon/r-9/desain-a1b2c3-x.png")))
    }

    // ---- Generator rute: kode hasil generate ikut memeriksa kepemilikan -----------------------------

    @Test
    fun generatedRoutes_checkFileOwnershipInPostAndPut_beforeReducer() {
        val routes = SpecRoutesWriter.routesFile("bordir_uji", SpecTable.of("bordir_uji", entity))
        assertTrue("private fun fileProblem(tenantId: String, values: Map<String, String>)" in routes, "fungsi fileProblem harus ada")
        assertEquals(
            2, Regex("fileProblem\\(tenant\\.tenantId\\.value, values\\)").findAll(routes).count(),
            "fileProblem(tenant.tenantId.value, values) dipanggil di POST dan PUT"
        )
        assertTrue("fileOwnershipProblem(tenantId, values)" in routes, "memakai aturan tunggal EntitySpec.fileOwnershipProblem")
        listOf("POST" to "post {", "PUT" to "put(\"/{id}\") {").forEach { (name, marker) ->
            val body = routes.substringAfter(marker)
            assertTrue(body.indexOf("fileProblem(") < body.indexOf("PrototypeReducer.reduce"), "$name: cek kepemilikan sebelum reducer")
        }
    }
}
