package com.eventverse.app.domain.pack

import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRD-PLAT-013 Track A — parity: setiap nilai enum tema punya padanan utuh (resolver & codec pack JSON).
 *
 * Test mengiterasi `entries`, jadi entri enum baru tanpa padanan (mis. renderer Track C, atau cabang codec)
 * langsung gagal saat ditambahkan — buktinya berasal dari `entries` itu sendiri, bukan dari ingatan
 * (tenant-variability-rules Kontrak 7/8).
 */
class PackThemeParityTest {

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
    fun everyVisualStyle_resolvesToItself() {
        assertTrue(VisualStyle.entries.isNotEmpty())
        VisualStyle.entries.forEach { style ->
            assertEquals(style, PackThemeResolver.resolve(themeUji(PackTheme(style = style))).style, "VisualStyle $style")
        }
    }

    @Test
    fun everyChatBubbleStyle_resolvesToItself() {
        assertTrue(ChatBubbleStyle.entries.isNotEmpty())
        ChatBubbleStyle.entries.forEach { bubble ->
            assertEquals(
                bubble,
                PackThemeResolver.resolve(themeUji(PackTheme(chatBubbleStyle = bubble))).chatBubbleStyle,
                "ChatBubbleStyle $bubble"
            )
        }
    }

    @Test
    fun everyCardStyle_resolvesToItself() {
        assertTrue(CardStyle.entries.isNotEmpty())
        CardStyle.entries.forEach { card ->
            assertEquals(card, PackThemeResolver.resolve(themeUji(PackTheme(cardStyle = card))).cardStyle, "CardStyle $card")
        }
    }

    @Test
    fun everyVisualStyle_roundTripsThroughPackCodec() {
        VisualStyle.entries.forEach { style ->
            val pack = themeUji(
                PackTheme(
                    style = style,
                    accentColorHex = 0xFFEA580C,
                    chatBubbleStyle = ChatBubbleStyle.GLASS,
                    cardStyle = CardStyle.ELEVATED
                )
            )
            assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)), "VisualStyle $style")
        }
    }

    @Test
    fun everyChatBubbleStyleAndCardStyle_roundTripsThroughPackCodec() {
        ChatBubbleStyle.entries.forEach { bubble ->
            val pack = themeUji(PackTheme(chatBubbleStyle = bubble))
            assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)), "ChatBubbleStyle $bubble")
        }
        CardStyle.entries.forEach { card ->
            val pack = themeUji(PackTheme(cardStyle = card))
            assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)), "CardStyle $card")
        }
    }

    @Test
    fun packWithoutTheme_encodesWithoutThemeKey_andDecodesNull() {
        val json = DomainPackCodec.encodeToString(themeUji(null))
        assertFalse(json.contains("\"theme\""), "pack tanpa tema tidak menulis kunci theme: $json")
        assertNull(DomainPackCodec.decode(json).theme)
    }

    @Test
    fun codec_rejectsUnknownThemeEnumValues_withPath() {
        val json = DomainPackCodec.encodeToString(
            themeUji(PackTheme(style = VisualStyle.GLASS, accentColorHex = 0xFFEA580C, chatBubbleStyle = ChatBubbleStyle.GLASS, cardStyle = CardStyle.FLAT))
        )
        assertEquals(
            "$.theme.style",
            assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"style\":\"GLASS\"", "\"style\":\"NEON\"")) }.path
        )
        assertEquals(
            "$.theme.chatBubbleStyle",
            assertFailsWith<DomainPackDecodeException> {
                DomainPackCodec.decode(json.replace("\"chatBubbleStyle\":\"GLASS\"", "\"chatBubbleStyle\":\"NEON\""))
            }.path
        )
        assertEquals(
            "$.theme.cardStyle",
            assertFailsWith<DomainPackDecodeException> {
                DomainPackCodec.decode(json.replace("\"cardStyle\":\"FLAT\"", "\"cardStyle\":\"MAGIC\""))
            }.path
        )
    }

    @Test
    fun codec_rejectsMalformedAccentColor_andMissingRequiredThemeField_withPath() {
        val json = DomainPackCodec.encodeToString(themeUji(PackTheme(accentColorHex = 0xFFEA580C)))
        assertEquals(
            "$.theme.accentColor",
            assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"#FFEA580C\"", "\"blue\"")) }.path
        )
        assertEquals(
            "$.theme.style",
            assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"style\":\"CLAY\",", "")) }.path
        )
    }

    @Test
    fun draftWithoutThemeOrWithSameTheme_matchesShippedGarment_butDifferentThemeIsRewrite() {
        val garment = GarmentDomainPack.pack
        assertTrue(garment.copy(theme = null).matchesShipped(garment), "draf lama tanpa tema tetap sah")
        assertTrue(garment.matchesShipped(garment), "tema sama persis tetap sah")
        assertFalse(
            garment.copy(theme = PackTheme(style = VisualStyle.GLASS)).matchesShipped(garment),
            "tema berbeda dari pack bawaan = penulisan ulang"
        )
    }
}
