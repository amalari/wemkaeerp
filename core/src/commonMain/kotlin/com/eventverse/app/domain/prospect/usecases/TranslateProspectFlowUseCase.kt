package com.eventverse.app.domain.prospect.usecases

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.CapabilityRequirement
import com.eventverse.app.domain.prospect.FlowTranslation
import com.eventverse.app.domain.prospect.FlowTranslationId
import com.eventverse.app.domain.prospect.FlowTranslationRepository
import com.eventverse.app.domain.prospect.FlowTranslator
import com.eventverse.app.domain.prospect.ProposedFlowValidator
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.RawCapabilityRequirement
import kotlinx.datetime.Instant

/**
 * Turns a prospect's narrative into a validated pipeline of capability slots.
 *
 * The translator is assumed to be wrong sometimes, so **every field it returns is checked** and
 * each problem becomes a warning rather than an exception. A translation that is mostly right is
 * still worth a reviewer's time; discarding it on the first bad archetype code would throw away the
 * other eight correct ones.
 */
class TranslateProspectFlowUseCase(
    private val translator: FlowTranslator,
    private val translationRepository: FlowTranslationRepository,
    private val leadRepository: ProspectLeadRepository
) {
    suspend operator fun invoke(
        translationId: FlowTranslationId,
        lead: ProspectLead,
        translatedAt: Instant
    ): Result<FlowTranslation> = runCatching {
        val draft = translator.translate(lead.narrativeRaw).getOrThrow()
        val warnings = mutableListOf<String>()

        val requirements = draft.requirements.mapNotNull { raw ->
            toRequirement(raw, warnings)
        }
        require(requirements.isNotEmpty()) {
            "Narasi tidak menghasilkan satu pun kebutuhan yang bisa dikenali. " +
                "Minta calon klien menjelaskan alur produksinya lebih rinci."
        }

        // People do not describe their factory in production order — "QC-nya pakai AQL" often comes
        // before "kainnya dari buyer". The archetype enum is declared in production order, so
        // sorting by it turns a rambling narrative into a sequence a reviewer can read top to bottom.
        val ordered = requirements.sortedBy { GarmentSlots.all.indexOf(it.archetype) }

        warnings += ProposedFlowValidator.warningsFor(ordered)

        val preset = resolvePreset(draft.detectedPresetCode, warnings)

        val translation = FlowTranslation(
            id = translationId,
            leadId = lead.id,
            translatorRef = draft.translatorRef,
            requirements = ordered,
            proposedPipeline = assemblePipeline(lead, ordered, draft.requirements, preset),
            detectedPreset = preset,
            openQuestions = draft.openQuestions,
            validationWarnings = warnings,
            translatedAt = translatedAt
        )

        translationRepository.save(translation)
        leadRepository.save(
            if (translation.needsHumanReview) lead.markNeedsReview() else lead.markTranslated()
        )
        translation
    }

    /**
     * An unknown archetype code becomes `CUSTOM_EXTENSION` with a warning rather than a dropped
     * requirement.
     *
     * The model inventing a slot name usually means it found real work that does not fit our nine
     * slots — which is precisely the thing we want to hear about, not discard.
     */
    private fun toRequirement(
        raw: RawCapabilityRequirement,
        warnings: MutableList<String>
    ): CapabilityRequirement? {
        if (raw.title.isBlank()) {
            warnings += "Sebuah kebutuhan dikembalikan tanpa judul dan diabaikan."
            return null
        }

        val archetype = GarmentSlots.fromCode(raw.archetypeCode)
        if (archetype == null) {
            warnings += "Kode archetype \"${raw.archetypeCode}\" tidak dikenal " +
                "(kebutuhan \"${raw.title}\"); diperlakukan sebagai modul kustom."
        }
        if (raw.sourceQuote.isBlank()) {
            warnings += "Kebutuhan \"${raw.title}\" tidak menyertakan kutipan dari narasi, " +
                "jadi tidak bisa diverifikasi bahwa klien benar-benar menyebutkannya."
        }

        return CapabilityRequirement(
            archetype = archetype ?: GarmentSlots.CUSTOM_EXTENSION,
            title = raw.title,
            description = raw.description,
            sourceQuote = raw.sourceQuote,
            features = raw.features
        )
    }

    /**
     * Resolves the business preset **without** `the legacy `GarmentBusinessPreset.fromCode()` (removed in B4d)`.
     *
     * That helper falls back to `DEFAULT` instead of returning null, so routing an unrecognised code
     * through it would silently file every unknown factory as a full-package exporter — and a CMT
     * workshop quoted as FOB is quoted for buying fabric it will never buy.
     */
    private fun resolvePreset(
        code: String?,
        warnings: MutableList<String>
    ): Blueprint? {
        if (code.isNullOrBlank()) {
            warnings += "Model bisnis pabrik tidak terdeteksi dari narasi."
            return null
        }
        val match = GarmentBlueprints.all.firstOrNull { it.code.value.equals(code, ignoreCase = true) }
        if (match == null) {
            warnings += "Model bisnis \"$code\" tidak dikenal; tidak diasumsikan."
        }
        return match
    }

    /**
     * Builds the proposed pipeline as a straight chain, in production order.
     *
     * Linear by construction, so a cycle is impossible and no graph validation is needed yet.
     * Branching and rework loops are a later concern; when they arrive,
     * `TenantPipelineProjector.project()` followed by `PipelineGraph.from()` already provides cycle
     * and dangling-input detection.
     *
     * The pipeline is assembled against [ProspectLead.placeholderTenantId] and lives only in memory
     * and in this translation's JSON — it must never be handed to a tenant-scoped repository.
     */
    private fun assemblePipeline(
        lead: ProspectLead,
        ordered: List<CapabilityRequirement>,
        raws: List<RawCapabilityRequirement>,
        preset: Blueprint?
    ): CustomTenantPipeline {
        val suggestedByTitle = raws.associate { it.title to it.suggestedModuleId }

        val nodes = ordered.mapIndexed { index, requirement ->
            val moduleId = suggestedByTitle[requirement.title]?.takeIf { it.isNotBlank() }
                ?: proposedModuleIdFor(requirement, index)
            CustomPipelineNode(
                nodeId = "prospect-node-${index + 1}",
                moduleId = moduleId,
                customDisplayName = requirement.title,
                archetype = requirement.archetype,
                stepOrderIndex = index,
                isCustomPlugin = requirement.archetype == GarmentSlots.CUSTOM_EXTENSION
            )
        }

        val edges = nodes.zipWithNext().map { (from, to) ->
            CustomPipelineEdge(
                edgeId = "prospect-edge-${from.nodeId}-${to.nodeId}",
                fromNodeId = from.nodeId,
                toNodeId = to.nodeId,
                expectedDataType = to.archetype.defaultExpectedInputType
            )
        }

        return CustomTenantPipeline(
            tenantId = lead.placeholderTenantId,
            pipelineName = "Usulan alur ${lead.companyName}",
            baseStarterPreset = preset,
            nodes = nodes,
            edges = edges
        )
    }

    /** A stable, readable id for a slot nothing in the catalogue fills yet. */
    private fun proposedModuleIdFor(requirement: CapabilityRequirement, index: Int): String =
        "proposed_${requirement.archetype.code}_${index + 1}"
}
