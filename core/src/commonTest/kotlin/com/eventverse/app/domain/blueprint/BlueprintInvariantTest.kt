package com.eventverse.app.domain.blueprint

import com.eventverse.app.domain.pack.DomainPackCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Invariant Blueprint diuji dengan starter **e-learning** fiktif — mesin tidak boleh mengasumsikan garmen. */
class BlueprintInvariantTest {

    private fun blueprint(vararg modules: BlueprintModule) = Blueprint(
        BlueprintCode("bootcamp_intensif"), DomainPackCode("elearning"),
        "Bootcamp Intensif", "Bootcamp", "Kelas singkat dengan penilaian tugas", "Lembaga kursus", modules.toList()
    )

    @Test
    fun elearningStarter_exposesActiveModulesAndParameters() {
        val b = blueprint(
            BlueprintModule("enrollment", active = true),
            BlueprintModule("grading", active = true, parameters = mapOf("gradingScheme" to "RUBRIC")),
            BlueprintModule("certification", active = false, parameters = mapOf("issuer" to "INTERNAL"))
        )
        assertEquals(setOf("enrollment", "grading"), b.activeModuleCodes)
        assertFalse(b.isActive("certification"))
        assertEquals("INTERNAL", b.parametersOf("certification")["issuer"], "modul non-aktif tetap membawa parameter")
        assertTrue(b.parametersOf("unknown").isEmpty())
    }

    @Test
    fun duplicateModule_orEmptyBlueprint_orBadCode_isRejected() {
        assertFailsWith<IllegalStateException> { blueprint(BlueprintModule("grading", true), BlueprintModule("grading", false)) }
        assertFailsWith<IllegalArgumentException> { blueprint() }
        assertFailsWith<IllegalArgumentException> { BlueprintCode("FOB Full") }
        assertFailsWith<IllegalArgumentException> { BlueprintModule(" ", true) }
    }
}
