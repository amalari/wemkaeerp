package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractiveScreenFactoryTest {
    private val rows = listOf(
        mapOf("Kolom" to "A", "Judul" to "k1", "Info" to "i1"),
        mapOf("Kolom" to "B", "Judul" to "k2")
    )

    @Test
    fun kanban_legacyRows_columnsDerivedFromRowsAndCardsMoveFreely() {
        val screen = assertNotNull(InteractiveScreenFactory.kanban("s", "Papan", rows))
        val store = screen.newStore()
        val next = PrototypeReducer.moveCard(screen.spec, store, "item", "s-1", "Kolom", "B").getOrThrow()
        assertEquals("B", next.rowsOf("item").first()["Kolom"])
    }

    @Test
    fun kanban_hintsWithEmptyColumn_allowsDropIntoEmptyColumn() {
        val screen = assertNotNull(InteractiveScreenFactory.kanban("s", "Papan", rows, KanbanHints(listOf("A", "B", "C"))))
        assertTrue(PrototypeReducer.moveCard(screen.spec, screen.newStore(), "item", "s-1", "Kolom", "C").isSuccess)
    }

    @Test
    fun kanban_transitionsFromHints_rejectForbiddenMove() {
        val hints = KanbanHints(listOf("A", "B", "C"), mapOf("A" to setOf("B")))
        val screen = assertNotNull(InteractiveScreenFactory.kanban("s", "Papan", rows, hints))
        assertTrue(PrototypeReducer.moveCard(screen.spec, screen.newStore(), "item", "s-1", "Kolom", "C").isFailure)
    }

    @Test
    fun kanban_rowsWithoutColumnKey_isNull() {
        assertNull(InteractiveScreenFactory.kanban("s", "Papan", listOf(mapOf("x" to "y"))))
    }

    // ---- petunjuk kaya B2 ---------------------------------------------------------------------

    @Test
    fun kanban_richHints_cardColumnMetaDetailForm_flowIntoConfig() {
        val hints = KanbanHints(
            listOf("A", "B", "C"),
            card = listOf(CardElement("Judul", CardStyle.TITLE), CardElement("Kolom", CardStyle.BADGE)),
            columnMeta = mapOf("B" to ColumnMeta(tintHex = 0xFF2563EB, wipLimit = 1)),
            detailForm = FormConfig(listOf("Judul", "Info"), "Simpan perubahan")
        )
        val screen = assertNotNull(InteractiveScreenFactory.kanban("s", "Papan", rows, hints))
        val k = screen.spec.screens.single().kanban!!
        assertEquals(hints.card, k.card, "elemen kartu boleh menunjuk field kelompok (lencana status)")
        assertEquals(hints.columnMeta, k.columnMeta)
        assertEquals(hints.detailForm, k.detailForm)
        screen.newStore() // spec sah
    }

    @Test
    fun kanban_withoutRichHints_keepsLegacyBehavior() {
        val screen = assertNotNull(InteractiveScreenFactory.kanban("s", "Papan", rows, KanbanHints(listOf("A", "B", "C"))))
        val k = screen.spec.screens.single().kanban!!
        assertTrue(k.card.isEmpty() && k.columnMeta.isEmpty() && k.detailForm == null, "tanpa petunjuk kaya = perilaku lama")
    }

    @Test
    fun kanban_incoherentRichHints_areRejected_notIgnored() {
        assertNull(
            InteractiveScreenFactory.kanban("s", "Papan", rows, KanbanHints(listOf("A", "B"), card = listOf(CardElement("Hantu", CardStyle.TEXT)))),
            "elemen kartu di luar field ditolak"
        )
        assertNull(
            InteractiveScreenFactory.kanban("s", "Papan", rows, KanbanHints(listOf("A", "B"), columnMeta = mapOf("Hantu" to ColumnMeta(wipLimit = 1)))),
            "metadata kolom di luar kolom papan ditolak"
        )
        assertNull(
            InteractiveScreenFactory.kanban("s", "Papan", rows, KanbanHints(listOf("A", "B"), detailForm = FormConfig(listOf("Hantu")))),
            "form detail di luar field entitas ditolak"
        )
    }

    @Test
    fun codec_roundTrip_preservesSpecAndSeed() {
        val hints = KanbanHints(listOf("A", "B", "C"), mapOf("A" to setOf("B")))
        val original = assertNotNull(InteractiveScreenFactory.kanban("s", "Papan", rows, hints))
        val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(original).encode()))
        assertEquals(original, decoded)
    }

    @Test
    fun garmentKanbanScreens_areInteractiveWithPackHints() {
        val pack = GarmentDomainPack.pack
        pack.screenSuggestions.filter { it.widget.code == "KANBAN" }.forEach { s ->
            val screen = PrototypeScreen("default-${s.moduleId.value}", s.moduleId, s.title, s.widget.code)
            assertNotNull(WidgetRegistry.interactiveFor(screen, pack), "kanban ${s.moduleId.value} tidak interaktif")
        }
    }
}

class InteractiveTableTest {
    private val rows = listOf(
        mapOf("Bahan" to "Kain A", "Stok" to "420", "Status" to "Tersedia"),
        mapOf("Bahan" to "kain b", "Stok" to "96", "Status" to "Menipis")
    )
    private val hints = TableHints("Status", listOf("Tersedia", "Menipis", "Konsinyasi"))

    @Test
    fun table_statusChange_goesThroughReducerAndRejectsUnknownOption() {
        val screen = assertNotNull(InteractiveScreenFactory.table("t", "Stok", rows, hints))
        val store = screen.newStore()
        assertTrue(PrototypeReducer.moveCard(screen.spec, store, "item", "t-1", "Status", "Konsinyasi").isSuccess)
        assertTrue(PrototypeReducer.moveCard(screen.spec, store, "item", "t-1", "Status", "Hantu").isFailure)
    }

    @Test
    fun table_statusOutsideOptions_isNotInteractive() {
        assertNull(InteractiveScreenFactory.table("t", "Stok", rows, TableHints("Status", listOf("Final"))))
    }

    @Test
    fun table_nonUniformRows_isNull() {
        assertNull(InteractiveScreenFactory.table("t", "x", listOf(mapOf("a" to "1"), mapOf("b" to "2"))))
    }

    // ---- petunjuk kaya B2 ---------------------------------------------------------------------

    @Test
    fun table_typedFieldHints_driveFieldSpecs_requiredAndEnum() {
        val hints = TableHints(
            "Status", listOf("Tersedia", "Menipis", "Konsinyasi"),
            fields = listOf(
                FieldHint("Bahan", FieldType.TEXT, required = true),
                FieldHint("Stok", FieldType.NUMBER),
                FieldHint("Status", FieldType.ENUM, options = listOf("Tersedia", "Menipis", "Konsinyasi"))
            )
        )
        val screen = assertNotNull(InteractiveScreenFactory.table("t", "Stok", rows, hints))
        val fields = screen.spec.entity("item")!!.fields
        assertEquals(listOf(FieldType.TEXT, FieldType.NUMBER, FieldType.ENUM), fields.map { it.type }, "tipe turun dari hints, bukan semua TEXT")
        assertTrue(fields.first { it.key == "Bahan" }.required)
        assertEquals(hints.options, fields.first { it.key == "Status" }.options)
        screen.newStore() // seed lolos validasi bertipe ("420"/"96" angka)
    }

    @Test
    fun table_inlineCreateAndEditableFields_flowIntoConfig() {
        val hints = TableHints("Status", listOf("Tersedia", "Menipis"), inlineCreate = true, editableFields = listOf("Bahan", "Stok"))
        val screen = assertNotNull(InteractiveScreenFactory.table("t", "Stok", rows, hints))
        val t = screen.spec.screens.single().table!!
        assertTrue(t.inlineCreate)
        assertEquals(listOf("Bahan", "Stok"), t.editableFields)
    }

    @Test
    fun table_withoutTypeHints_keepsLegacyAllTextBehavior() {
        val screen = assertNotNull(InteractiveScreenFactory.table("t", "Stok", rows, hints))
        assertEquals(
            listOf(FieldType.TEXT, FieldType.TEXT, FieldType.ENUM),
            screen.spec.entity("item")!!.fields.map { it.type },
            "legacy: semua TEXT kecuali kolom status"
        )
        val t = screen.spec.screens.single().table!!
        assertTrue(!t.inlineCreate && t.editableFields.isEmpty())
    }

    @Test
    fun table_incoherentHints_areRejected_notIgnored() {
        assertNull(
            InteractiveScreenFactory.table("t", "Stok", rows, TableHints("Status", listOf("Tersedia", "Menipis"), fields = listOf(FieldHint("Hantu", FieldType.NUMBER)))),
            "FieldHint di luar kolom ditolak"
        )
        assertNull(
            InteractiveScreenFactory.table("t", "Stok", rows, TableHints("Status", listOf("Tersedia", "Menipis"), editableFields = listOf("Hantu"))),
            "editableFields di luar field ditolak"
        )
        assertNull(
            InteractiveScreenFactory.table(
                "t", "Stok", rows,
                TableHints("Status", listOf("Tersedia", "Menipis"), transitions = mapOf("Tersedia" to setOf("Menipis")), editableFields = listOf("Status"))
            ),
            "status bermesin tidak bisa jadi sel teks"
        )
        assertNull(
            InteractiveScreenFactory.table(
                "t", "Stok", rows.map { it + ("Stok" to "bukan angka") },
                TableHints("Status", listOf("Tersedia", "Menipis"), fields = listOf(FieldHint("Stok", FieldType.NUMBER)))
            ),
            "seed yang tak lolos tipe petunjuk ditolak"
        )
    }

    @Test
    fun tableView_numericColumnSortsAsNumbers_andFilterIsCaseInsensitive() {
        val screen = assertNotNull(InteractiveScreenFactory.table("t", "Stok", rows, hints))
        val all = screen.newStore().rowsOf("item")
        val cols = listOf("Bahan", "Stok", "Status")
        assertEquals(listOf("96", "420"), TableView.apply(all, cols, "", "Stok", true).map { it["Stok"] })
        assertEquals(listOf("kain b"), TableView.apply(all, cols, "MENIPIS", null, true).map { it["Bahan"] })
    }

    @Test
    fun codec_roundTrip_preservesTableConfig() {
        val original = assertNotNull(InteractiveScreenFactory.table("t", "Stok", rows, hints))
        val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(original).encode()))
        assertEquals(original, decoded)
    }

    @Test
    fun garmentTables_withHints_areInteractive() {
        val pack = GarmentDomainPack.pack
        pack.screenSuggestions.filter { it.tableHints != null }.forEach { s ->
            val screen = PrototypeScreen("default-${s.moduleId.value}", s.moduleId, s.title, s.widget.code)
            assertNotNull(WidgetRegistry.interactiveFor(screen, pack), "tabel ${s.moduleId.value} tidak interaktif")
        }
    }
}

class InteractiveDashboardChecklistTest {
    private val checklistRows = listOf(
        mapOf("Butir" to "Jahitan lurus", "Selesai" to "ya"),
        mapOf("Butir" to "Kancing lengkap", "Selesai" to "tidak")
    )
    private val tileRows = listOf(mapOf("Aktif" to "9 tiket"), mapOf("Margin" to "22%"))

    @Test
    fun checklist_toggle_goesThroughReducer_andRejectsNonBoolValue() {
        val screen = assertNotNull(InteractiveScreenFactory.checklist("c", "QC", checklistRows))
        val store = screen.newStore()
        val next = PrototypeReducer.reduce(screen.spec, store, PrototypeAction.SetField("item", "c-2", "Selesai", "ya")).getOrThrow()
        assertEquals("ya", next.rowsOf("item")[1]["Selesai"])
        assertTrue(PrototypeReducer.reduce(screen.spec, store, PrototypeAction.SetField("item", "c-2", "Selesai", "mungkin")).isFailure)
    }

    @Test
    fun checklist_invalidDoneValue_isNotInteractive() {
        assertNull(InteractiveScreenFactory.checklist("c", "QC", listOf(mapOf("Butir" to "x", "Selesai" to "setengah"))))
    }

    @Test
    fun dashboard_countTile_followsSourceRows_andFallsBackWhenSourceMissing() {
        val tile = TileSpec("Aktif", "9 tiket", CountSpec("tiket_mod", "status", notEquals = "Selesai", suffix = " tiket"))
        val rows = listOf(
            PrototypeRow("1", mapOf("status" to "Baru")), PrototypeRow("2", mapOf("status" to "Selesai")), PrototypeRow("3", mapOf("status" to "Diproses"))
        )
        assertEquals("2 tiket", DashboardEvaluator.valueOf(tile) { rows })
        assertEquals("9 tiket", DashboardEvaluator.valueOf(tile) { null })
        assertEquals("22%", DashboardEvaluator.valueOf(TileSpec("Margin", "22%")) { rows })
    }

    @Test
    fun dashboard_allStatic_isNotInteractive_andBoundTileIs() {
        assertNull(InteractiveScreenFactory.dashboard("d", "Dasbor", tileRows, DashboardHints(emptyMap())))
        val screen = assertNotNull(InteractiveScreenFactory.dashboard("d", "Dasbor", tileRows, DashboardHints(mapOf("Aktif" to CountSpec("m", "s", equals = "Baru")))))
        assertEquals(2, screen.spec.screens.single().dashboard?.tiles?.size)
    }

    @Test
    fun codec_roundTrip_preservesChecklistAndDashboard() {
        val c = assertNotNull(InteractiveScreenFactory.checklist("c", "QC", checklistRows))
        val d = assertNotNull(InteractiveScreenFactory.dashboard("d", "D", tileRows, DashboardHints(mapOf("Aktif" to CountSpec("m", "s", notEquals = "Selesai", suffix = " x")))))
        listOf(c, d).forEach { original ->
            assertEquals(original, InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(original).encode())))
        }
    }

    @Test
    fun screenSpec_dashboardWithEntity_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            ScreenSpec("d", "D", com.eventverse.app.domain.discovery.WidgetKind.DASHBOARD, "item", dashboard = DashboardConfig(listOf(TileSpec("a", "1"))))
        }
    }

    @Test
    fun garmentQcChecklistAndHppDashboard_areInteractive() {
        val pack = GarmentDomainPack.pack
        pack.screenSuggestions.filter { it.widget.code == "CHECKLIST" || it.dashboardHints != null }.forEach { s ->
            val screen = PrototypeScreen("default-${s.moduleId.value}", s.moduleId, s.title, s.widget.code)
            assertNotNull(WidgetRegistry.interactiveFor(screen, pack), "${s.moduleId.value} tidak interaktif")
        }
    }
}

/** Butir B2: blok form di factory & registry, termasuk form Stok Kain garment (Bahan/Stok/Kepemilikan wajib). */
class InteractiveFormTest {
    private val hints = FormHints(
        fields = listOf("Bahan", "Stok", "Kepemilikan"),
        required = listOf("Bahan", "Stok", "Kepemilikan"),
        options = mapOf("Kepemilikan" to listOf("Milik pabrik", "Titipan buyer")),
        submitLabel = "Catat bahan"
    )

    @Test
    fun form_createsRowsThroughReducer_andEnforcesRequiredWithUserMessage() {
        val screen = assertNotNull(InteractiveScreenFactory.form("tambah-bahan", "Catat Bahan", hints))
        val store = screen.newStore()
        val kurang = PrototypeReducer.reduce(screen.spec, store, PrototypeAction.Create("item", PrototypeRow("b-1", mapOf("Bahan" to "Kain X", "Stok" to "10 kg"))))
        assertTrue(kurang.isFailure)
        assertTrue(kurang.exceptionOrNull()!!.message!!.contains("Kepemilikan"), "pesan memakai nama field berbahasa pengguna")
        val ok = PrototypeReducer.reduce(
            screen.spec, store,
            PrototypeAction.Create("item", PrototypeRow("b-1", mapOf("Bahan" to "Kain X", "Stok" to "10 kg", "Kepemilikan" to "Milik pabrik")))
        ).getOrThrow()
        assertEquals("Kain X", ok.rowsOf("item").single()["Bahan"])
    }

    @Test
    fun form_enumFieldRejectsUnknownOption_andSubmitLabelFollowsHints() {
        val screen = assertNotNull(InteractiveScreenFactory.form("f", "Catat Bahan", hints))
        assertEquals("Catat bahan", screen.spec.screens.single().form!!.submitLabel)
        assertTrue(
            PrototypeReducer.reduce(screen.spec, screen.newStore(), PrototypeAction.Create("item", PrototypeRow("b-9", mapOf("Bahan" to "K", "Stok" to "1", "Kepemilikan" to "Sewa")))).isFailure
        )
    }

    @Test
    fun form_invalidHints_isNotInteractive() {
        assertNull(InteractiveScreenFactory.form("f", "F", FormHints(emptyList())))
        assertNull(InteractiveScreenFactory.form("f", "F", FormHints(listOf("A", "A"))))
    }

    @Test
    fun garmentInventoryForm_isInteractiveViaRegistry_andSharesEntityWithTableSource() {
        val pack = GarmentDomainPack.pack
        val form = assertNotNull(
            WidgetRegistry.interactiveFor(PrototypeScreen("default-${GarmentModules.INVENTORY.value}-form", GarmentModules.INVENTORY, "Catat Bahan", "FORM"), pack),
            "form stok kain tidak interaktif"
        )
        val table = assertNotNull(
            WidgetRegistry.interactiveFor(PrototypeScreen("default-${GarmentModules.INVENTORY.value}", GarmentModules.INVENTORY, "Stok Kain", "TABLE"), pack)
        )
        // Penghubung form → layar sumber: entityId sama dan field form ada di kolom tabel sumbernya.
        val tableFieldKeys = table.spec.entity(InteractiveScreenFactory.ENTITY_ID)!!.fields.map { it.key }
        assertTrue(
            form.spec.entity(InteractiveScreenFactory.ENTITY_ID)!!.fields.all { it.key in tableFieldKeys },
            "field form wajib jadi kolom tabel sumbernya"
        )
        val next = PrototypeReducer.reduce(
            form.spec, form.newStore(),
            PrototypeAction.Create("item", PrototypeRow("b-1", mapOf("Bahan" to "Katun 30s", "Stok" to "500 kg", "Kepemilikan" to "Milik pabrik")))
        ).getOrThrow()
        assertEquals(1, next.rowsOf("item").size)
    }

    @Test
    fun form_withoutPackHints_staysStatic() {
        val pack = GarmentDomainPack.pack
        assertNull(
            WidgetRegistry.interactiveFor(PrototypeScreen("default-${GarmentModules.CRM_SALES.value}-form", GarmentModules.CRM_SALES, "Form CRM", "FORM"), pack),
            "layar lama berbentuk tak cocok tetap null (statis), bukan ditebak"
        )
    }

    @Test
    fun formHints_survivePackCodecRoundTrip() {
        val pack = GarmentDomainPack.pack
        val decoded = DomainPackCodec.decode(DomainPackCodec.encode(pack).encode())
        pack.screenSuggestions.filter { it.formHints != null }.forEach { s ->
            assertEquals(s.formHints, decoded.screenSuggestions.first { it.moduleId == s.moduleId }.formHints)
        }
        assertTrue(pack.screenSuggestions.any { it.formHints != null }, "fixture garment wajib membawa formHints")
    }
}
