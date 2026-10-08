package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** C2: papan pilot hidup, berbinding Api, dan selaras dengan entitas `spec` yang dipakai generator server. */
class PilotBoardParityTest {
    private val screen = PrototypeScreen("default-layanan_change_request", LayananPilotPack.CHANGE_REQUEST, "Papan Permintaan", "KANBAN")
    private val built = assertNotNull(WidgetRegistry.interactiveFor(screen, LayananPilotPack.pack), "papan pilot harus bisa dimainkan")

    @Test
    fun board_bindsToTheGeneratedCrudRoute() {
        assertEquals(DataBinding.Api(LayananPilotPack.API_BASE_PATH), built.binding)
    }

    @Test
    fun board_fieldsMatchServerEntity_keyTypeAndOptions() {
        val board = assertNotNull(built.spec.entity("item"))
        val server = LayananPilotPack.entity
        assertEquals(server.fields.map { it.key }.toSet(), board.fields.map { it.key }.toSet(), "kunci baris papan = kunci baris server")
        server.fields.forEach { f ->
            val b = assertNotNull(board.field(f.key))
            assertEquals(f.type, b.type, "tipe ${f.key}")
            assertEquals(f.options, b.options, "opsi ${f.key}")
            // Field kelompok (status) dipaksa ENUM oleh factory papan tanpa flag wajib: papan selalu
            // menempatkan kartu di satu kolom, jadi "wajib" tak bermakna di klien (server tetap menegakkannya).
            if (f.key != "status") assertEquals(f.required, b.required, "wajib ${f.key}")
        }
    }

    @Test
    fun board_transitionsMatchServerStateMachine() {
        assertEquals(LayananPilotPack.entity.stateMachine, assertNotNull(built.spec.entity("item")).stateMachine)
    }

    @Test
    fun board_startsEmpty_dataComesFromServer() {
        assertTrue(built.seed.values.all { it.isEmpty() }, "binding Api tidak membawa seed")
    }

    @Test
    fun board_survivesJsonRoundTrip_withApiBinding() {
        val decoded = assertNotNull(InteractiveScreenCodec.decode(InteractiveScreenCodec.encode(built)))
        assertEquals(built.binding, decoded.binding)
        assertEquals(built.spec, decoded.spec)
    }

    @Test
    fun memoryScreen_keepsNoBindingKey_oldDraftsUnchanged() {
        val memory = assertNotNull(InteractiveScreenFactory.kanban("k", "K", listOf(mapOf("Kolom" to "A", "Judul" to "x"))))
        val json = InteractiveScreenCodec.encode(memory).toString()
        assertTrue("\"type\":\"api\"" !in json, json)
        assertEquals(DataBinding.Memory, assertNotNull(InteractiveScreenCodec.decode(InteractiveScreenCodec.encode(memory))).binding)
    }
}
