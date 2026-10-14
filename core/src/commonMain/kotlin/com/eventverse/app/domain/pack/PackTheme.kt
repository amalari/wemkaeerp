package com.eventverse.app.domain.pack

/**
 * Konfigurasi tema visual untuk pack.
 *
 * Setiap pack bisa memiliki tema visual berbeda (CLAY, GLASS, MINIMAL, BRUTAL)
 * tanpa mengubah kode platform. Tema adalah properti pack, bukan tenant.
 *
 * Nullable untuk backward compatibility dengan pack lama yang tidak punya konfigurasi tema.
 */
data class PackTheme(
    val style: VisualStyle = VisualStyle.CLAY,
    val accentColorHex: Long? = null,
    val chatBubbleStyle: ChatBubbleStyle = ChatBubbleStyle.CARD,
    val cardStyle: CardStyle? = null
) {
    init {
        accentColorHex?.let { color ->
            require(color in 0x0..0xFFFFFFFF) {
                "accentColorHex '$color' harus format hex yang valid (0x0 - 0xFFFFFFFF)"
            }
        }
    }
}

/**
 * Gaya visual yang didukung untuk pack.
 *
 * - CLAY: Claymorphism + Neo-Brutalism (default)
 * - GLASS: Glassmorphism dengan backdrop blur
 * - MINIMAL: Flat design yang bersih
 * - BRUTAL: Neo-brutalist bold
 */
enum class VisualStyle {
    /** Claymorphism + Neo-Brutalism — outline tebal, hard shadow, rounded */
    CLAY,

    /** Glassmorphism — frosted glass effect dengan backdrop blur */
    GLASS,

    /** Flat design yang bersih dan minimal */
    MINIMAL,

    /** Neo-brutalist — bold border, high contrast */
    BRUTAL
}

/**
 * Gaya chat bubble untuk komponen chat.
 *
 * - CARD: ClayCard style dengan shadow (existing)
 * - BUBBLE: Simple rounded bubble tanpa shadow
 * - GLASS: Glassmorphism bubble
 */
enum class ChatBubbleStyle {
    /** ClayCard variant — default eksisting */
    CARD,

    /** Simple rounded bubble tanpa shadow */
    BUBBLE,

    /** Glassmorphism bubble */
    GLASS
}

/**
 * Gaya card untuk komponen card.
 *
 * - DEFAULT: ClayCard standard
 * - ELEVATED: Lebih prominent shadow
 * - FLAT: Tanpa shadow, hanya border
 */
enum class CardStyle {
    /** ClayCard standard */
    DEFAULT,

    /** Card dengan shadow lebih prominent */
    ELEVATED,

    /** Flat — tanpa shadow, hanya border */
    FLAT
}
