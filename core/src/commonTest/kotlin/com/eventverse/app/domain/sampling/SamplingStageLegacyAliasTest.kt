package com.eventverse.app.domain.sampling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Jaring pengaman pemecahan `FINISHING_QC` jadi empat tahap.
 *
 * Nilainya bukan pada memastikan enum punya anggota yang benar — kompilator sudah mengerjakan itu.
 * Nilainya ada pada satu skenario yang tidak terlihat sampai produksi: satu baris yang lolos dari
 * `UPDATE` di migrasi V63. Repository membaca nama tahap tak dikenal dengan fallback `NEW_INTAKE`,
 * jadi tanpa alias legacy, SPK yang tinggal dikemas akan muncul di papan sebagai SPK yang baru
 * masuk — dan tidak ada satu pun yang error, yang membuatnya nyaris mustahil ditemukan.
 */
class SamplingStageLegacyAliasTest {

    @Test
    fun `parse nama tahap lama harus mendarat di sub-tahap pertama bukan fallback`() {
        assertEquals(SamplingPipelineStage.CUCI_SOFTENER, SamplingPipelineStage.parseOrNull("FINISHING_QC"))
    }

    @Test
    fun `parse nama tahap yang masih ada harus mengembalikan dirinya sendiri`() {
        SamplingPipelineStage.entries.forEach { stage ->
            assertEquals(stage, SamplingPipelineStage.parseOrNull(stage.name))
        }
    }

    @Test
    fun `parse nama yang benar-benar tidak dikenal harus null bukan tahap tebakan`() {
        assertNull(SamplingPipelineStage.parseOrNull("ENTAH_APA"))
        assertNull(SamplingPipelineStage.parseOrNull(""))
        assertNull(SamplingPipelineStage.parseOrNull(null))
    }

    @Test
    fun `urutan tahap tidak boleh bolong atau bertukar`() {
        // `order` dipakai membandingkan kemajuan (`pipelineStage.order >= ...`) di lima berkas UI.
        // Satu angka yang terlewat saat menyisipkan tahap membuat perbandingan itu diam-diam salah.
        assertEquals(
            SamplingPipelineStage.entries.indices.map { it + 1 },
            SamplingPipelineStage.entries.map { it.order }
        )
    }

    @Test
    fun `tiap tahap lantai finishing punya tahap berikutnya yang berurutan`() {
        // Tombol "Selesai -> serahkan" membaca `nextStage`. Kalau ia melompat, operator akan
        // menyerahkan barang ke meja yang salah tanpa ada yang menolak.
        assertEquals(SamplingPipelineStage.SETRIKA_UAP, SamplingPipelineStage.CUCI_SOFTENER.nextStage)
        assertEquals(SamplingPipelineStage.QC_FINISHING, SamplingPipelineStage.SETRIKA_UAP.nextStage)
        assertEquals(SamplingPipelineStage.PENGEMASAN, SamplingPipelineStage.QC_FINISHING.nextStage)
        // Selesai kemas tidak pernah langsung dikirim: barang disimpan dulu, baru dilepas PIC.
        assertEquals(SamplingPipelineStage.STORAGE_HOLDING, SamplingPipelineStage.PENGEMASAN.nextStage)
        assertEquals(SamplingPipelineStage.IN_DELIVERY, SamplingPipelineStage.STORAGE_HOLDING.nextStage)
        assertNull(SamplingPipelineStage.ACC_APPROVED.nextStage)
    }

    @Test
    fun `lantai penyelesaian akhir membentang dari linking sampai pengemasan`() {
        val onFloor = SamplingPipelineStage.entries.filter { it.isOnFinishingFloor }
        assertEquals(
            listOf(
                SamplingPipelineStage.LINKING_ASSEMBLY,
                SamplingPipelineStage.CUCI_SOFTENER,
                SamplingPipelineStage.SETRIKA_UAP,
                SamplingPipelineStage.QC_FINISHING,
                SamplingPipelineStage.PENGEMASAN
            ),
            onFloor
        )
    }
}
