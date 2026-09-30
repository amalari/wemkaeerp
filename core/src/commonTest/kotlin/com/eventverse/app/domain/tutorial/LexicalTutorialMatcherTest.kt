package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ShippedTutorialSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TRD-HELP-001 FR-5: pertanyaan Indonesia sehari-hari memetakan ke tutorial yang benar. */
class LexicalTutorialMatcherTest {

    private val all = TutorialCatalog(ShippedTutorialSource).forPack(GarmentDomainPack.pack)
    private val matcher = LexicalTutorialMatcher()

    private fun top(q: String, current: com.eventverse.app.domain.pack.ModuleId? = null) =
        matcher.rank(q, all, current).firstOrNull()?.tutorial?.id?.value

    @Test
    fun everydayQuestions_mapToExpectedTutorial() {
        assertEquals("crm_new_lead", top("gimana cara bikin lead baru?"))
        assertEquals("crm_new_lead", top("ada buyer baru chat WA, dicatat di mana"))
        assertEquals("crm_move_stage", top("cara pindah status leadnya ke qualified"))
        assertEquals("platform_rbac_role_access", top("kenapa staf saya tidak bisa lihat menu"))
        assertEquals("platform_org_chart_basics", top("cara tambah karyawan di divisi"))
    }

    @Test
    fun weakSingleWordMatches_areNotOfferedAsAlternatives() {
        val ids = matcher.rank("ada buyer baru chat WA, dicatat di mana?", all, null).map { it.tutorial.id.value }
        assertEquals("crm_new_lead", ids.first())
        assertTrue("platform_org_chart_basics" !in ids, "cocok hanya lewat kata 'baru' bukan alternatif yang berguna: " + ids)
    }

    @Test
    fun unrelatedOrStopwordOnlyQuestion_returnsNothing() {
        assertTrue(matcher.rank("gimana ya caranya dong", all, null).isEmpty())
        assertTrue(matcher.rank("resep nasi goreng", all, null).isEmpty())
    }

    @Test
    fun stepIndex_pointsAtTheMostRelevantStep() {
        val match = matcher.rank("dimana kolom pencarian, mau cari nomor WA", all, null).first()
        assertEquals("crm_find_lead", match.tutorial.id.value)
        assertEquals(1, match.stepIndex, "langkah 'Cari', bukan ringkasan KPI")
    }

    @Test
    fun currentModuleBonus_breaksTies_butNeverCreatesAMatch() {
        val ranked = matcher.rank("tambah", all, GarmentModules.ORG_CHART)
        assertEquals("platform_org_chart_basics", ranked.first().tutorial.id.value)
        assertTrue(matcher.rank("resep", all, GarmentModules.CRM_SALES).isEmpty())
    }
}
