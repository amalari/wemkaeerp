package com.eventverse.app.presentation.discovery.studio

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Studio pola prototype (plan §4, Fase C) menyimpan **resep susun** ke `ops.prototype_patterns`,
 * dan resep itu dibaca kembali oleh dua pihak: Studio sendiri saat pola dibuka lagi, dan
 * `PrototypeRenderer` saat pratinjau digambar. Test di sini mengunci kedua arah itu — termasuk
 * urutan kolom, karena renderer memasangkan blok `CUSTOM_SCREEN` berdasarkan nilai kolom `Lebar`.
 */
class PrototypePatternUiModelTest {

    private val garment = GarmentDomainPack.pack
    private val sampleModuleId = garment.modules.first().id.value

    private fun pattern(
        id: String = "pattern-1",
        name: String = "Antrean per poli",
        widget: String = WidgetKind.TABLE.code,
        packCode: String? = "garment",
        rows: List<PrototypeRowUi> = listOf(PrototypeRowUi(listOf(PrototypeFieldUi("Kolom", "Contoh isi"))))
    ) = PrototypePatternUi(id = id, name = name, widgetCode = widget, packCode = packCode, rows = rows)

    /** Bolak-balik lewat kabel: yang tersimpan di server = yang dibaca Studio, urutannya utuh. */
    @Test
    fun patternJson_roundTripsThroughTheWire() {
        val original = pattern(
            widget = WidgetKind.CUSTOM_SCREEN.code,
            rows = listOf(
                PrototypeRowUi(listOf(PrototypeFieldUi("Blok", "Ringkasan"), PrototypeFieldUi("Lebar", "penuh"))),
                PrototypeRowUi(listOf(PrototypeFieldUi("Blok", "Daftar"), PrototypeFieldUi("Lebar", "separuh")))
            )
        )

        val wire = """{"id":"${original.id}","name":"${original.name}","widget":"${original.widgetCode}",""" +
            """"packCode":"${original.packCode}","pattern":${original.patternJson().encode()}}"""
        val parsed = PrototypePatternUi.fromJson(JsonParser.parseObject(wire))

        assertEquals(original, parsed)
        assertEquals(listOf("Blok", "Lebar"), parsed.rows.first().fields.map { it.label })
    }

    /** Pratinjau memakai baris pola apa adanya — tidak ada penerjemahan yang bisa menyimpang. */
    @Test
    fun previewDraft_carriesRowsInOrderAsRendererSampleRows() {
        val p = pattern(
            rows = listOf(
                PrototypeRowUi(listOf(PrototypeFieldUi("Blok", "Ringkasan"), PrototypeFieldUi("Lebar", "penuh"))),
                PrototypeRowUi(listOf(PrototypeFieldUi("Blok", "Daftar"), PrototypeFieldUi("Lebar", "separuh")))
            )
        )

        val screen = p.toPreviewDraft(sampleModuleId, "Modul asal", "seksi").screens.single()

        assertEquals(sampleModuleId, screen.moduleId)
        assertEquals(p.rows.size, screen.sampleRows.size)
        assertEquals(listOf("Blok", "Lebar"), screen.sampleRows.first().keys.toList())
        assertEquals("Ringkasan", screen.sampleRows.first()["Blok"])
        // Renderer memasangkan blok dari nilai ini; salah urut = tata letak pratinjau berbeda.
        assertEquals("penuh", screen.sampleRows.first()["Lebar"])
    }

    /** Kerangka dipanen dari `WidgetRegistry` — sumber yang sama dengan sampel pratinjau draf. */
    @Test
    fun skeleton_isHarvestedFromTheSameRegistryAsDraftPreview() {
        val rows = PrototypePatternUi.skeletonFrom(garment, sampleModuleId, WidgetKind.CUSTOM_SCREEN.code)

        assertEquals(3, rows.size)
        assertEquals(listOf("Blok", "Lebar"), rows.first().fields.map { it.label })
        assertEquals("penuh", rows.first().fields.last().value)
        assertEquals(listOf("separuh", "separuh"), rows.drop(1).map { it.fields.last().value })
    }

    @Test
    fun skeleton_isEmptyWhenTheRegistryHasNoShapeForThatPair() {
        assertTrue(PrototypePatternUi.skeletonFrom(garment, "modul_hantu", WidgetKind.TABLE.code).isEmpty())
        assertTrue(PrototypePatternUi.skeletonFrom(garment, sampleModuleId, "SPREADSHEET").isEmpty())
    }

    /**
     * Pola lama yang ditulis tangan (`{"kolom":["a"]}`, bentuk yang dipakai test rute server) tidak
     * boleh menggagalkan seluruh daftar — Studio menampilkannya sebagai pola tanpa baris.
     */
    @Test
    fun foreignPatternShape_isSkippedWithoutHidingHealthyPatterns() {
        val list = PrototypePatternUi.listFromJson(
            JsonParser.parse(
                """[{"id":"p1","name":"Tua","widget":"TABLE","pattern":{"kolom":["a"]}},""" +
                    """{"id":"p2","name":"Sehat","widget":"TABLE","pattern":{"rows":[{"Kolom":"Contoh"}]}},123]"""
            )
        )

        assertEquals(listOf("p1", "p2"), list.map { it.id })
        assertTrue(list.first().rows.isEmpty())
        assertEquals(1, list.last().rows.size)
    }

    /** Nilai non-string tetap bisa disunting: kolom JSONB lama tidak selalu berisi teks. */
    @Test
    fun nonStringValues_areCoercedToEditableText() {
        val parsed = PrototypePatternUi.fromJson(
            JsonParser.parseObject(
                """{"id":"p","name":"N","widget":"DASHBOARD",""" +
                    """"pattern":{"rows":[{"Jumlah":12,"Aktif":true,"Kosong":null}]}}"""
            )
        )

        assertEquals(listOf("12", "ya", ""), parsed.rows.single().fields.map { it.value })
    }

    @Test
    fun packLabel_resolvesThroughTheRegistry() {
        assertEquals("Konveksi & Garmen", pattern(packCode = "garment").packLabel)
        assertEquals("tanpa pack", pattern(packCode = null).packLabel)
        // Pack hantu ditampilkan apa adanya, bukan disembunyikan: pola yang menggantung pada pack
        // yang tidak dikenal justru harus terlihat oleh pengguna Studio.
        assertEquals("pack_hantu", pattern(packCode = "pack_hantu").packLabel)
    }
}
