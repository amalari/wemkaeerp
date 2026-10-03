package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import kotlin.test.Test
import kotlin.test.assertEquals
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
