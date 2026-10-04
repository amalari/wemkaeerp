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
                detailForm = FormConfig(listOf("No"), "Simpan")
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

    // ---- SpecOp baru: codec ketat, applier dilaksanakan sejak B4 -------------------------------

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
    fun applier_showFieldOnCard_upsertsElement_onKanbanScreensOnly() {
        fun kanbanOf(s: InteractiveScreen) = s.spec.screens.first().kanban!!
        // Field yang belum tampil ditambahkan di akhir kartu:
        val withNew = SpecOpApplier.apply(rich, SpecOp.AddField("order", FieldSpec("Catatan", "Catatan", FieldType.TEXT))).getOrThrow()
        val added = SpecOpApplier.apply(withNew, SpecOp.ShowFieldOnCard("order", "Catatan", CardStyle.TEXT)).getOrThrow()
        assertEquals("Catatan", kanbanOf(added).card.last().field, "elemen baru di akhir kartu")
        assertEquals(CardStyle.TEXT, kanbanOf(added).card.last().style)
        assertEquals(7, kanbanOf(added).card.size)
        // Field yang sudah tampil (Target di posisi contoh ke-4) diganti gayanya di posisi semula:
        val restyled = SpecOpApplier.apply(added, SpecOp.ShowFieldOnCard("order", "Target", CardStyle.NUMBER)).getOrThrow()
        assertEquals(3, kanbanOf(restyled).card.indexOfFirst { it.field == "Target" }, "posisi semula terjaga")
        assertEquals(CardStyle.NUMBER, kanbanOf(restyled).card[3].style)
        assertEquals(7, kanbanOf(restyled).card.size, "mengganti, bukan menduplikasi")
        assertEquals(rich.spec.screens.last(), added.spec.screens.last(), "layar tabel milik entitas sama tak tersentuh")
        assertTrue(SpecOpApplier.apply(rich, SpecOp.ShowFieldOnCard("order", "Hantu")).isFailure, "field tak dikenal ditolak")
        assertTrue(SpecOpApplier.apply(rich, SpecOp.ShowFieldOnCard("hantu", "Nomor")).isFailure, "entitas tak dikenal ditolak")
        val noKanban = InteractiveScreen(
            PrototypeSpec(
                listOf(PrototypeContractSamples.orderEntity),
                listOf(ScreenSpec("t", "T", WidgetKind.TABLE, "order", table = TableConfig(listOf("Status"), "Status")))
            ),
            emptyMap()
        )
        assertTrue(SpecOpApplier.apply(noKanban, SpecOp.ShowFieldOnCard("order", "Nomor")).isFailure, "tanpa papan kanban tidak ada kartu untuk diubah")
    }

    @Test
    fun applier_setFieldRequired_togglesFlag_andReducerEnforcesOnCreate() {
        val next = SpecOpApplier.apply(rich, SpecOp.SetFieldRequired("order", "Total", true)).getOrThrow()
        assertTrue(next.spec.entity("order")!!.field("Total")!!.required, "Total jadi wajib")
        val store = next.newStore()
        assertTrue(
            PrototypeReducer.reduce(next.spec, store, PrototypeAction.Create("order", PrototypeRow("o-3", mapOf("Nomor" to "SV-3", "Pelanggan" to "Cici", "Status" to "Baru")))).isFailure,
            "create tanpa field wajib baru ditolak"
        )
        val ok = PrototypeReducer.reduce(
            next.spec, store,
            PrototypeAction.Create("order", PrototypeRow("o-4", mapOf("Nomor" to "SV-4", "Pelanggan" to "Dodi", "Total" to "5000", "Status" to "Baru")))
        ).getOrThrow()
        assertEquals("5000", ok.rowsOf("order").first { it.id == "o-4" }["Total"])
        val off = SpecOpApplier.apply(next, SpecOp.SetFieldRequired("order", "Total", false)).getOrThrow()
        assertTrue(!off.spec.entity("order")!!.field("Total")!!.required, "kewajiban bisa dicabut")
        assertTrue(SpecOpApplier.apply(rich, SpecOp.SetFieldRequired("order", "Hantu", true)).isFailure)
        assertTrue(SpecOpApplier.apply(rich, SpecOp.SetFieldRequired("hantu", "Total", true)).isFailure)
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
