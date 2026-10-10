package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.crm.LeadCreationChannel
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.crm.prefill.LeadDraft
import com.eventverse.app.domain.crm.prefill.LeadDraftFields
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.customfield.SelectOption
import com.eventverse.app.domain.customfield.SelectOptionId
import com.eventverse.app.infrastructure.api.LeadDraftDisabledException
import com.eventverse.app.infrastructure.api.LeadDraftGateway
import com.eventverse.app.presentation.crm.components.LeadFormState
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftUiEffect
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftUiEvent
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftViewModel
import com.eventverse.app.shared.crm.LeadDraftCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-HELP-002 FR-5: draf diterapkan ke form, user tetap yang menyimpan; koreksi user tidak tertimpa. */
class LeadDraftViewModelTest {

    private val testScope = TestScope(StandardTestDispatcher())

    private class FakeGateway(var enabled: Boolean, val canManage: Boolean, var reply: Result<LeadDraft>) : LeadDraftGateway {
        var drafted = 0
        override suspend fun settings() = Result.success(LeadDraftCodec.AiSettings(enabled, canManage))
        override suspend fun setEnabled(enabled: Boolean) = Result.success(LeadDraftCodec.AiSettings(enabled.also { this.enabled = it }, canManage))
        override suspend fun draft(text: String): Result<LeadDraft> { drafted++; return reply }
    }

    // Skema kustom tenant bordir (non-garment).
    private val jenis = LeadFieldDescriptor("cf-jenis", "Jenis Bordir", CrmFieldType(FieldType.ENUM, options = listOf(SelectOption(SelectOptionId("opt_komputer"), "Bordir Komputer", "#2563EB"))), isRequired = true, isEditable = true, isDeletable = true, isCore = false)
    private val draft = LeadDraft(
        brandName = "Batik Sekar", whatsappNumber = WhatsappNumber("6281234567890"), estimatedPcs = 24,
        customValues = mapOf(CustomFieldId("cf-jenis") to CustomAttributes.selectCell(SelectOptionId("opt_komputer"))), agentRef = "scripted",
    )

    @Test
    fun extract_emitsApply_andFormGetsAiMarkedValues() = testScope.runTest {
        val gateway = FakeGateway(enabled = true, canManage = false, reply = Result.success(draft))
        val vm = LeadDraftViewModel(gateway, testScope)
        vm.onEvent(LeadDraftUiEvent.Load); advanceUntilIdle()
        vm.onEvent(LeadDraftUiEvent.UpdateText("chat WA …"))
        vm.onEvent(LeadDraftUiEvent.Extract); advanceUntilIdle()

        val effect = vm.effects.first() as LeadDraftUiEffect.Apply
        val form = LeadFormState(LeadStage.NEW_LEAD).apply { update(LeadDraftFields.CONTACT_PERSON, "diketik user") }
        form.applyDraft(effect.draft)

        assertEquals("Batik Sekar", form.brandName)
        assertEquals("081234567890", form.phone)
        assertEquals("diketik user", form.contactPerson, "field yang tidak diisi draf tidak ditimpa")
        assertTrue(form.isAi(LeadDraftFields.BRAND_NAME) && !form.isAi(LeadDraftFields.CONTACT_PERSON))
        assertEquals(mapOf(CustomFieldId("cf-jenis") to CustomAttributes.selectCell(SelectOptionId("opt_komputer"))), form.customValues(listOf(jenis)))
        assertTrue(form.canSubmit(listOf(jenis)))
        assertEquals(LeadCreationChannel.AI_DRAFT, form.createdVia)

        form.update(LeadDraftFields.BRAND_NAME, "Batik Sekar Jaya")
        assertFalse(form.isAi(LeadDraftFields.BRAND_NAME), "mengetik menghapus tanda AI")
        assertEquals(LeadCreationChannel.AI_DRAFT, form.createdVia, "asal tetap jujur walau dikoreksi")
    }

    @Test
    fun extractWhenReady_waitsForSettings_thenExtractsOnlyIfEnabled() = testScope.runTest {
        val on = FakeGateway(enabled = true, canManage = false, reply = Result.success(draft))
        val vm = LeadDraftViewModel(on, testScope)
        vm.onEvent(LeadDraftUiEvent.Load)
        vm.onEvent(LeadDraftUiEvent.UpdateText("catat lead dari chat"))
        vm.onEvent(LeadDraftUiEvent.ExtractWhenReady)   // pengaturan belum termuat
        advanceUntilIdle()
        assertEquals(1, on.drafted)

        val off = FakeGateway(enabled = false, canManage = true, reply = Result.success(draft))
        val vm2 = LeadDraftViewModel(off, testScope)
        vm2.onEvent(LeadDraftUiEvent.Load); vm2.onEvent(LeadDraftUiEvent.UpdateText("x")); vm2.onEvent(LeadDraftUiEvent.ExtractWhenReady)
        advanceUntilIdle()
        assertEquals(0, off.drafted, "tenant belum opt-in: teks tertempel, tidak dikirim")
        assertEquals("x", vm2.uiState.value.text)
    }

    @Test
    fun manualForm_isManual_andRequiredCustomFieldBlocksSubmit() {
        val form = LeadFormState(LeadStage.NEW_LEAD).apply { update(LeadDraftFields.CONTACT_PERSON, "Rina") }
        assertEquals(LeadCreationChannel.MANUAL, form.createdVia)
        assertFalse(form.canSubmit(listOf(jenis)), "kolom wajib tenant kosong")
    }

    @Test
    fun disabledTenant_viewerCannotEnable_andExtractIsBlocked() = testScope.runTest {
        val gateway = FakeGateway(enabled = false, canManage = false, reply = Result.success(draft))
        val vm = LeadDraftViewModel(gateway, testScope)
        vm.onEvent(LeadDraftUiEvent.Load); advanceUntilIdle()
        vm.onEvent(LeadDraftUiEvent.UpdateText("x")); vm.onEvent(LeadDraftUiEvent.Extract)
        vm.onEvent(LeadDraftUiEvent.Enable); advanceUntilIdle()
        assertEquals(0, gateway.drafted)
        assertEquals(false, vm.uiState.value.enabled)
    }

    @Test
    fun serverSaysDisabled_flipsStateInsteadOfShowingError() = testScope.runTest {
        val vm = LeadDraftViewModel(FakeGateway(enabled = true, canManage = true, reply = Result.failure(LeadDraftDisabledException())), testScope)
        vm.onEvent(LeadDraftUiEvent.Load); advanceUntilIdle()
        vm.onEvent(LeadDraftUiEvent.UpdateText("x")); vm.onEvent(LeadDraftUiEvent.Extract); advanceUntilIdle()
        assertEquals(false, vm.uiState.value.enabled)
        assertNull(vm.uiState.value.error)
    }
}
