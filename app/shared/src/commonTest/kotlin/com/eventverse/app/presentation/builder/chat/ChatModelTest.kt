package com.eventverse.app.presentation.builder.chat

import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.DiscoveryModuleUi
import com.eventverse.app.presentation.discovery.DiscoveryScreenUi
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatModelTest {
    private fun module(id: String, active: Boolean, input: String? = null, output: String? = null) =
        DiscoveryModuleUi(id, id.uppercase(), "sec", "OPERATIONAL", null, "slot", input, output, active)

    private fun draft(screens: List<DiscoveryScreenUi> = emptyList()) = DiscoveryDraftUi(
        id = "d", status = "DRAFT", narrative = null, packCode = "bordir", packDisplayName = "Bordir Uji",
        blueprintCode = "bp", blueprintDescription = "", modules = listOf(
            module("a", true, "In", "Out"), module("b", false), module("c", true)
        ),
        activeModuleCodes = listOf("a", "c"), screens = screens
    )

    @Test
    fun parseMessages_envelope_readsRolesAndPatchFlags() {
        val raw = JsonParser.parse(
            """{"messages":[{"id":"1","role":"USER","text":"hai","summary":[],"hasPendingPatch":false,"appliedDraftId":null},
            {"id":"2","role":"AGENT","text":"ok","summary":["Modul aktif baru: X"],"hasPendingPatch":true,"appliedDraftId":null}]}"""
        )
        val m = parseMessages(raw)
        assertEquals(listOf(true, false), m.map { it.isUser })
        assertTrue(m[1].hasPendingPatch)
        assertEquals(listOf("Modul aktif baru: X"), m[1].summary)
    }

    @Test
    fun parseMessages_bareArray_returnsEmptyNotCrash() {
        assertEquals(emptyList(), parseMessages(JsonParser.parse("[]")))
    }

    @Test
    fun featuresOf_nonGarmentPack_onlyActiveModulesWithPorts() {
        val f = featuresOf(draft(listOf(DiscoveryScreenUi("s", "a", "Daftar A", "table", emptyList()))))
        assertEquals(listOf("A", "C"), f.map { it.moduleName })
        assertEquals(listOf("Layar: Daftar A", "Menerima: In", "Menghasilkan: Out"), f[0].items)
        assertTrue(f[1].items.isEmpty())
    }

    @Test
    fun openingEntry_listsActiveModules() {
        assertEquals(listOf("A", "C"), openingEntryFor(draft()).summary)
    }
}
