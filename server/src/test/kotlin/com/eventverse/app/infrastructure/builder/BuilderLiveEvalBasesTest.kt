package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import kotlin.test.Test
import kotlin.test.assertEquals

/** Penjaga eval live (tanpa biaya): layar dasar yang dipakai kasus harus sah, kalau tidak setiap sunting gagal karena dasarnya, bukan modelnya. */
class BuilderLiveEvalBasesTest {
    @Test
    fun `layar dasar eval sah menurut validator tunggal`() {
        assertEquals(emptyList(), ScreenProposalValidator.validate(BuilderLiveEvalCases.baseProposal))
        assertEquals(emptyList(), ScreenProposalValidator.validate(BuilderLiveEvalCases.kanbanProposal))
    }
}
