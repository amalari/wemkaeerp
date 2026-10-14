package com.eventverse.app.domain.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * TRD-PLAT-013 Track A: resolver tema pack dan kebijakan fallback-nya.
 *
 * Fixture-nya pack **non-garment** (tenant-variability-rules Kontrak 6): tema adalah konsep platform,
 * jadi perilakunya tidak boleh bergantung pada vertikal mana pun.
 */
class PackThemeResolverTest {

    private fun themeUji(theme: PackTheme?): DomainPack = DomainPack(
        code = DomainPackCode("theme_uji"),
        displayName = "Uji Tema",
        phases = listOf(PhaseDefinition(PhaseCode("FASE"), 1, "Fase Uji", "Alur uji", 0xFF2563EB)),
        slots = listOf(
            SlotDefinition(SlotCode("theme_uji_slot"), "Slot Uji", PhaseCode("FASE"), PortType("Masuk"), PortType("Keluar"))
        ),
        portTypes = setOf(PortType("Masuk"), PortType("Keluar")),
        wiredPortTypes = setOf(PortType("Masuk"), PortType("Keluar")),
        theme = theme
    )

    @Test
    fun resolve_packWithoutTheme_usesPlatformDefault() {
        val resolved = PackThemeResolver.resolve(themeUji(null))
        assertEquals(PackThemeResolver.default, resolved)
        assertEquals(VisualStyle.CLAY, resolved.style)
        assertEquals(ChatBubbleStyle.CARD, resolved.chatBubbleStyle)
        assertNull(resolved.accentColorHex)
        assertNull(resolved.cardStyle)
    }

    @Test
    fun resolve_unknownPack_usesPlatformDefault() {
        assertEquals(PackThemeResolver.default, PackThemeResolver.resolve(null))
    }

    @Test
    fun resolve_packWithDeclaredTheme_returnsDeclaredThemeVerbatim() {
        val declared = PackTheme(
            style = VisualStyle.GLASS,
            accentColorHex = 0xFFEA580C,
            chatBubbleStyle = ChatBubbleStyle.GLASS,
            cardStyle = CardStyle.ELEVATED
        )
        assertEquals(declared, PackThemeResolver.resolve(themeUji(declared)))
    }

    @Test
    fun resolve_packWithoutTheme_neverBorrowsAnotherPackTheme() {
        val vertikalLain = PackTheme(
            style = VisualStyle.BRUTAL,
            chatBubbleStyle = ChatBubbleStyle.BUBBLE,
            cardStyle = CardStyle.FLAT
        )
        assertNotEquals(PackThemeResolver.resolve(themeUji(vertikalLain)), PackThemeResolver.resolve(themeUji(null)))
        assertEquals(PackTheme(), PackThemeResolver.resolve(themeUji(null)))
    }

    @Test
    fun resolve_declaredThemeWithoutAccent_keepsAccentNull() {
        val resolved = PackThemeResolver.resolve(themeUji(PackTheme(style = VisualStyle.MINIMAL)))
        assertEquals(VisualStyle.MINIMAL, resolved.style)
        assertNull(resolved.accentColorHex)
        assertNull(resolved.cardStyle)
    }
}
