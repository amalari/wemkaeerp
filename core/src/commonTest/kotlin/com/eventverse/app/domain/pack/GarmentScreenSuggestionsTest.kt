package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.rbac.ModuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mock prototype `/builder/prototype` (usulan layar = data pack garment): lengkap untuk semua modul
 * operasional, valid terhadap invarian pack, dan hanya menyentuh modul yang benar-benar operasional.
 * v2: setiap layar juga membawa baris contoh yang terlihat seperti aplikasi jadi (v2), bukan
 * placeholder generik — bentuk barisnya dikontrak per widget di KDoc [ScreenSuggestion].
 */
class GarmentScreenSuggestionsTest {

    private val pack = GarmentDomainPack.pack

    @Test
    fun everySuggestion_referencesOperationalModuleOfThisPack() {
        val modules = pack.modules.associateBy { it.id }
        pack.screenSuggestions.forEach { s ->
            val module = modules[s.moduleId]
            assertTrue(module != null, "Usulan layar '${s.moduleId.value}' tidak ada di pack garment")
            assertEquals(
                ModuleKind.OPERATIONAL, module.kind,
                "Layar mock hanya untuk modul operasional: ${s.moduleId.value}"
            )
            assertTrue(module.slot != null, "Modul ${s.moduleId.value} bukan stasiun kanvas")
        }
    }

    @Test
    fun everyOperationalModule_hasExactlyOneScreenSuggestion() {
        val suggested = pack.screenSuggestions.map { it.moduleId.value }.toSet()
        val operational = pack.modules.filter { it.kind == ModuleKind.OPERATIONAL }.map { it.id.value }.toSet()
        assertEquals(operational, suggested, "Setiap modul operasional garment wajib punya satu usulan layar")
    }

    @Test
    fun titles_areHumanSentences_notRawCodes() {
        pack.screenSuggestions.forEach { s ->
            assertTrue(s.title.isNotBlank() && '_' !in s.title, "Judul '${s.title}' harus kalimat manusiawi, bukan kode")
        }
    }

    @Test
    fun sampleRows_richEnoughToLookLikeTheApp_shapedPerWidget() {
        pack.screenSuggestions.forEach { s ->
            assertTrue(
                s.sampleRows.isNotEmpty(),
                "Layar '${s.title}' tanpa baris contoh — mock kosong tidak terlihat seperti aplikasi"
            )
            s.sampleRows.forEach { row ->
                assertTrue(row.isNotEmpty(), "Baris kosong di '${s.title}'")
                row.forEach { (k, v) ->
                    assertTrue(k.isNotBlank() && v.isNotBlank(), "Kunci/isi kosong di '${s.title}'")
                }
            }
            val joined = s.sampleRows.joinToString()
            assertTrue("contoh" !in joined, "Layar '${s.title}' masih memakai placeholder generik: $joined")
            when (s.widget) {
                WidgetKind.KANBAN -> {
                    assertTrue(s.sampleRows.all { it.containsKey("Kolom") }, "'${s.title}' wajib berkunci Kolom")
                    assertTrue(s.sampleRows.groupBy { it["Kolom"] }.size >= 2, "'${s.title}' papan kanban butuh >=2 kolom")
                }
                WidgetKind.FORM -> assertTrue(s.sampleRows.single().containsKey("Simpan"), "'${s.title}' butuh kunci tombol Simpan")
                WidgetKind.CHECKLIST -> assertTrue(s.sampleRows.all { it.containsKey("Selesai") }, "'${s.title}' butuh kunci Selesai")
                WidgetKind.TABLE -> {
                    assertTrue(s.sampleRows.size >= 3, "'${s.title}' tabel butuh >=3 baris data")
                    assertTrue(s.sampleRows.first().size >= 3, "'${s.title}' tabel butuh >=3 kolom")
                    assertTrue(
                        s.sampleRows.all { it.keys == s.sampleRows.first().keys },
                        "'${s.title}' kolom tabel tidak konsisten antarbaris"
                    )
                }
                WidgetKind.DASHBOARD -> assertTrue(s.sampleRows.all { it.size == 1 }, "'${s.title}' dasbor: satu metrik per tile")
                else -> Unit
            }
        }
    }
}