package com.eventverse.app.domain.pipeline

/**
 * Macro stages in garment manufacturing factory workflow.
 * Groups modules into logical sequential horizons.
 */
enum class PipelineStage(
    val stepOrder: Int,
    val displayName: String,
    val subtitle: String,
    val colorHex: Long
) {
    COMMERCIAL(
        stepOrder = 1,
        displayName = "1. Komersial & Sampling",
        subtitle = "Negosiasi Order & Prototipe Sample",
        colorHex = 0xFF2563EB // Primary Blue
    ),
    ENGINEERING(
        stepOrder = 2,
        displayName = "2. Spesifikasi & HPP",
        subtitle = "Tech Pack, BOM & Kalkulasi Biaya",
        colorHex = 0xFF7C3AED // Purple
    ),
    SUPPLY_CHAIN(
        stepOrder = 3,
        displayName = "3. Rantai Pasok & Bahan Baku",
        subtitle = "Penerimaan Kain Rol & Aksesoris",
        colorHex = 0xFF0D9488 // Teal
    ),
    MANUFACTURING(
        stepOrder = 4,
        displayName = "4. Lantai Produksi",
        subtitle = "Jadwal Mesin, Potong & Jahit",
        colorHex = 0xFFEA580C // Garment Orange
    ),
    ASSURANCE_DELIVERY(
        stepOrder = 5,
        displayName = "5. Mutu & Pengiriman",
        subtitle = "Inspeksi QC, Packing & Surat Jalan",
        colorHex = 0xFF16A34A // Emerald Green
    );
}
