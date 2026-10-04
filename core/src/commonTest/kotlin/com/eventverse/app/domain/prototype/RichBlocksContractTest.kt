package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import com.eventverse.app.shared.pack.ScreenSuggestionCodec
import com.eventverse.app.shared.pack.SpecOpCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Kontrak v2 (blok kaya + port data, plan induk §3.1–§3.5) — bentuk yang A dan C andalkan: elemen
 * kartu bertipe, metadata kolom (tint = data tenant), form detail, tabel inline, `DataBinding`,
 * dan dua operasi spec baru. Kompatibilitas mundur dikunci: JSON lama tanpa kunci baru tetap
 * terbaca dengan nilai bawaan. Fixture non-garment (bengkel servis), sesuai Kontrak 6.
 */
class RichBlocksContractTest {
    private val rich = PrototypeContractSamples.orderScreen
    private val api = PrototypeContractSamples.orderScreenApi

    // ---- validasi konstruktor (plan induk §3.3) ---------------------------------------------

    @Test
    fun columnMeta_wipLimitMustBePositive() {
        assertFailsWith<IllegalArgumentException> { ColumnMeta(wipLimit = 0) }
        assertFailsWith<IllegalArgumentException> { ColumnMeta(wipLimit = -1) }
        ColumnMeta(tintHex = 0xFF2563EB, wipLimit = 3) // sah — tidak melempar
    }

    @Test
    fun kanban_cardAndDetailFormFieldOutsideEntity_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            kanbanSpec(KanbanConfig("Status", listOf("Baru", "Dikerjakan", "Selesai"), "Nomor", card = listOf(CardElement("Hantu"))))
        }
        assertFailsWith<IllegalArgumentException> {
            kanbanSpec(KanbanConfig("Status", listOf("Baru", "Dikerjakan", "Selesai"), "Nomor", detailForm = FormConfig(listOf("Hantu"))))
        }
    }

    @Test
    fun table_editableFieldOutsideEntity_andMachineStatusInEditableFields_areRejected() {
        assertFailsWith<IllegalArgumentException> {
            tableSpec(TableConfig(listOf("Nomor", "Status"), "Status", editableFields = listOf("Hantu")))
        }
        assertFailsWith<IllegalArgumentException> {
            tableSpec(TableConfig(listOf("Nomor", "Status"), "Status", editableFields = listOf("Status")))
        }
    }

    @Test
    fun table_statusEditable_withoutMachine_isAllowed() {
        val free = EntitySpec("bebas", "Bebas", listOf(FieldSpec("Nama", "Nama", FieldType.TEXT), FieldSpec("Status", "Status", FieldType.ENUM, listOf("A", "B"))))
        PrototypeSpec(
            listOf(free),
            listOf(ScreenSpec("s", "S", WidgetKind.TABLE, "bebas", table = TableConfig(listOf("Nama", "Status"), "Status", inlineCreate = true, editableFields = listOf("Nama", "Status"))))
        ) // sah — tidak melempar
    }

    // ---- codec: round-trip & kompatibilitas mundur -------------------------------------------

    @Test
    fun codec_roundTrip_preservesRichKanbanTableAndMemoryBinding() {
        assertEquals(rich, InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(rich).encode())))
    }

    @Test
    fun codec_roundTrip_preservesApiBinding() {
        val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(api).encode()))
        assertEquals(api, decoded)
        assertEquals("/api/tenant/modules/servis/service_orders", (decoded.binding as DataBinding.Api).basePath)
    }

    @Test
    fun codec_legacyJsonWithoutNewKeys_decodesWithV1Defaults() {
        val legacy = """
            {"entities":[{"id":"item","label":"Papan","fields":[
              {"key":"Kolom","label":"Kolom","type":"ENUM","options":["A","B"]},
              {"key":"Judul","label":"Judul","type":"TEXT","options":[]}],"stateMachine":null}],
             "screens":[{"screenId":"s","title":"Papan","widget":"KANBAN","entityId":"item",
               "kanban":{"groupField":"Kolom","columns":["A","B"],"titleField":"Judul","detailFields":[]},"table":null}],
             "seed":{"item":[{"id":"s-1","values":{"Kolom":"A","Judul":"x"}}]}}
        """.trimIndent()
        val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(legacy))
        assertEquals(DataBinding.Memory, decoded.binding, "tanpa kunci binding = memori")
        val k = decoded.spec.screens.single().kanban!!
        assertTrue(k.card.isEmpty() && k.columnMeta.isEmpty() && k.detailForm == null, "kunci v2 kosong")
    }

    @Test
    fun codec_bindingUnknownType_isRejected_notFallback() {
        val json = """{"entities":[],"screens":[],"seed":{},"binding":{"type":"grpc"}}"""
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parseObject(json)) }.isFailure, "tipe binding tak dikenal ditolak")
    }

    @Test
    fun screenSuggestionCodec_dataBinding_roundTrip_andAbsentMeansMemory() {
        val withApi = ScreenSuggestion(ModuleId("servis"), "Order Servis", WidgetKind.TABLE, dataBinding = DataBinding.Api("/api/servis/orders"))
        val plain = ScreenSuggestion(ModuleId("gudang"), "Stok", WidgetKind.TABLE)
        val json = jsonObjectOf("screenSuggestions" to ScreenSuggestionCodec.encode(listOf(withApi, plain))).encode()
        val decoded = ScreenSuggestionCodec.decode(JsonParser.parseObject(json)["screenSuggestions"])
        assertEquals(withApi, decoded[0])
        assertEquals(DataBinding.Memory, decoded[1].dataBinding, "pack lama tanpa kunci = memori")
    }

    @Test
    fun screenSuggestionCodec_richHintKeys_roundTrip_andLegacyJsonStaysReadable() {
        val rich = ScreenSuggestion(
            ModuleId("uji"), "Antrian", WidgetKind.TABLE,
            sampleRows = listOf(mapOf("No" to "1", "Status" to "Baru")),
            kanbanHints = KanbanHints(
                listOf("Baru"),
                card = listOf(CardElement("No", CardStyle.TITLE)),
                columnMeta = mapOf("Baru" to ColumnMeta(0xFF112233, wipLimit = 2)),
                detailForm = FormConfig(listOf("No"), "Simpan"),
                // B2.1 — kunci yang sempat hilang saat pack disimpan→dimuat (temuan jalur C, fix
                // 62782b4): tanpa regresi ini papan pilot jatuh ke mode lama di runtime.
                groupField = "status",
                fields = listOf(FieldHint("Judul", FieldType.TEXT, required = true))
            ),
            tableHints = TableHints(
                "Status", listOf("Baru"),
                fields = listOf(FieldHint("No", FieldType.NUMBER, required = true)),
                inlineCreate = true, editableFields = listOf("No")
            )
        )
        val json = jsonObjectOf("screenSuggestions" to ScreenSuggestionCodec.encode(listOf(rich))).encode()
        assertEquals(rich, ScreenSuggestionCodec.decode(JsonParser.parseObject(json)["screenSuggestions"]).single(), "kunci hint kaya round-trip utuh")

        // Pack JSON lama: tanpa kunci B2 tetap terbaca dengan nilai bawaan (kompatibilitas mundur).
        val legacy = """{"screenSuggestions":[{"moduleId":"lama","title":"Lama","widget":"TABLE","sampleRows":[],"tableHints":{"statusColumn":"Status","options":["A"]}}]}"""
        val decodedLegacy = ScreenSuggestionCodec.decode(JsonParser.parseObject(legacy)["screenSuggestions"]).single()
        val legacyHints = assertNotNullTableHints(decodedLegacy.tableHints)
        assertTrue(legacyHints.fields.isEmpty() && !legacyHints.inlineCreate && legacyHints.editableFields.isEmpty())
    }

    private fun assertNotNullTableHints(hints: TableHints?): TableHints = requireNotNull(hints)

    // ---- SpecOp baru: codec ketat, applier menolak sampai B4 ----------------------------------

    @Test
    fun specOpCodec_newOps_roundTrip_strictly() {
        PrototypeContractSamples.richOps.forEach { op ->
            assertEquals(op, SpecOpCodec.decode(JsonParser.parseObject(SpecOpCodec.encode(op).encode())).getOrThrow())
        }
        val badStyle = SpecOpCodec.decode(JsonParser.parseObject("""{"type":"ShowFieldOnCard","entityId":"order","field":"Mendesak","style":"NEON"}"""))
        assertTrue(badStyle.isFailure, "gaya kartu tak dikenal ditolak")
        val missing = SpecOpCodec.decode(JsonParser.parseObject("""{"type":"SetFieldRequired","entityId":"order","field":"Target"}"""))
        assertTrue(missing.isFailure, "'required' wajib ada")
    }

    @Test
    fun applier_newOps_rejectedWithBelumDidukung_untilB4() {
        PrototypeContractSamples.richOps.forEach { op ->
            val result = SpecOpApplier.apply(rich, op)
            assertTrue(result.isFailure, "${op::class.simpleName} memang belum dilaksanakan")
            assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("belum didukung"))
        }
    }

    // ---- bantu ---------------------------------------------------------------------------------

    private fun kanbanSpec(kanban: KanbanConfig) = PrototypeSpec(
        listOf(PrototypeContractSamples.orderEntity),
        listOf(ScreenSpec("s", "S", WidgetKind.KANBAN, "order", kanban))
    )

    private fun tableSpec(table: TableConfig) = PrototypeSpec(
        listOf(PrototypeContractSamples.orderEntity),
        listOf(ScreenSpec("s", "S", WidgetKind.TABLE, "order", table = table))
    )
}
