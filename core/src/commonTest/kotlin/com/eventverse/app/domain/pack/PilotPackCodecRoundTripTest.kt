package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.shared.pack.DomainPackCodec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pack pilot disimpan sebagai JSON di `domain_packs` dan dimuat ulang oleh `ResolveDomainPackUseCase`;
 * layar kanban pilot hanya hidup bila petunjuknya (groupField, fields, binding) selamat dari putaran itu.
 */
class PilotPackCodecRoundTripTest {
    @Test
    fun layananPilotPack_survivesCodecRoundTrip_includingBoardHints() {
        val decoded = DomainPackCodec.decode(DomainPackCodec.encodeToString(LayananPilotPack.pack))
        assertEquals(LayananPilotPack.pack.screenSuggestions, decoded.screenSuggestions)
        assertEquals(LayananPilotPack.pack, decoded)
    }
}
