package com.eventverse.app.domain.prospect.usecases

import com.eventverse.app.domain.prospect.LeadSource
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import kotlinx.datetime.Instant

/**
 * Records a prospect's description of their factory.
 *
 * The narrative is stored exactly as written. Tidying it on the way in is tempting and
 * irreversible — it is the only input the translator has, and the phrasing a factory owner chooses
 * ("kami cuma jahit, kainnya dari buyer") carries the business model more reliably than any form
 * field we could have asked them to tick.
 */
class SubmitProspectLeadUseCase(
    private val leadRepository: ProspectLeadRepository
) {
    suspend operator fun invoke(
        id: ProspectLeadId,
        companyName: String,
        narrativeRaw: String,
        contactName: String? = null,
        contactEmail: String? = null,
        contactPhone: String? = null,
        source: LeadSource = LeadSource.LANDING_PAGE,
        submittedAt: Instant? = null
    ): Result<ProspectLead> = runCatching {
        require(narrativeRaw.length <= MAX_NARRATIVE_LENGTH) {
            "Narasi terlalu panjang (${narrativeRaw.length} karakter, maksimum " +
                "$MAX_NARRATIVE_LENGTH). Ringkas alur produksinya."
        }

        val lead = ProspectLead(
            id = id,
            companyName = companyName.trim(),
            narrativeRaw = narrativeRaw,
            contactName = contactName?.trim()?.takeIf { it.isNotBlank() },
            contactEmail = contactEmail?.trim()?.takeIf { it.isNotBlank() },
            contactPhone = contactPhone?.trim()?.takeIf { it.isNotBlank() },
            source = source,
            submittedAt = submittedAt
        )

        leadRepository.save(lead)
        lead
    }

    companion object {
        /**
         * Enforced in the domain, not only at the HTTP edge.
         *
         * This narrative becomes a prompt. An unbounded one submitted to an unauthenticated endpoint
         * is somebody else's bill once a real model is wired in, so the limit belongs where it
         * cannot be bypassed by a second caller.
         */
        const val MAX_NARRATIVE_LENGTH = 8_000
    }
}
