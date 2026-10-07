package com.eventverse.app.domain.discovery.brief

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BriefRevisionRenderTest {

    private val bare = RequirementsBrief("klinik", emptyList(), emptyList(), emptyList())

    @Test
    fun `tanpa revisi tidak ada bagian revisi, dengan revisi muncul tepat di bawah judul`() {
        assertFalse(BriefRenderer.markdown(bare).contains("Revisi brief"), "brief pertama tidak berubah")
        val md = BriefRenderer.markdown(bare.copy(revision = BriefRevision(2, "br-1", "IN_PROGRESS", listOf("Tambah isian: X"), listOf("Hapus isian: Y"))))
        assertTrue(md.startsWith("# Brief Kebutuhan — klinik\n\n## Revisi brief\nVersi 2 — menggantikan `br-1` (status sebelumnya: IN_PROGRESS)."))
        assertTrue(md.contains("**Ditambahkan**\n- + Tambah isian: X") && md.contains("**Dihapus**\n- - Hapus isian: Y"))
        assertEquals(md, BriefRenderer.markdown(bare.copy(revision = BriefRevision(2, "br-1", "IN_PROGRESS", listOf("Tambah isian: X"), listOf("Hapus isian: Y")))), "deterministik")
    }

    @Test
    fun `selisih mengabaikan judul dan baris kosong, dan revisi lama tidak mencemari pembanding`() {
        val v1 = "# Brief\n\n## Ringkasan\n- Modul: 1\n- isi lama\n"
        val v2withRevision = "# Brief\n\n## Revisi brief\nVersi 2 — x\n**Ditambahkan**\n- + baris dari revisi\n\n## Ringkasan\n- Modul: 1\n- isi baru\n"
        val (added, removed) = BriefRenderer.diff(v1, v2withRevision)
        assertEquals(listOf("- isi baru"), added)
        assertEquals(listOf("- isi lama"), removed)
        assertEquals(v1.trimEnd(), BriefRenderer.withoutRevision(v1).trimEnd(), "tanpa bagian revisi tidak berubah")
        assertFalse(BriefRenderer.withoutRevision(v2withRevision).contains("baris dari revisi"))
        assertTrue(BriefRenderer.withoutRevision(v2withRevision).contains("## Ringkasan"), "bagian setelah revisi tetap ada")
    }

    @Test
    fun `selisih panjang dipotong dengan penanda`() {
        val many = (1..50).map { "baris $it" }
        val md = BriefRenderer.markdown(bare.copy(revision = BriefRevision(2, "br-1", "QUEUED", many, emptyList())))
        assertTrue(md.contains("- ... dan 10 baris lain"))
        assertFalse(md.contains("baris 41"))
    }
}
