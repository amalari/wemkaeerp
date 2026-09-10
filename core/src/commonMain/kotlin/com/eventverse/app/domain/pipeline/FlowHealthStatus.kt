package com.eventverse.app.domain.pipeline

/**
 * Health and operational status of a manufacturing pipeline node.
 */
enum class FlowHealthStatus(
    val label: String,
    val badgeColorHex: Long,
    val bgTintHex: Long,
    val isAlert: Boolean
) {
    HEALTHY(
        label = "Normal",
        badgeColorHex = 0xFF16A34A, // Emerald Green
        bgTintHex = 0xFFF0FDF4,
        isAlert = false
    ),
    BOTTLENECK(
        label = "Bottleneck",
        badgeColorHex = 0xFFD97706, // Amber/Orange
        bgTintHex = 0xFFFFFBEB,
        isAlert = true
    ),
    CRITICAL(
        label = "Stagnan",
        badgeColorHex = 0xFFDC2626, // Red
        bgTintHex = 0xFFFEF2F2,
        isAlert = true
    ),
    BYPASSED(
        label = "Di-bypass",
        badgeColorHex = 0xFF94A3B8, // Slate Muted
        bgTintHex = 0xFFF8FAFC,
        isAlert = false
    );
}
