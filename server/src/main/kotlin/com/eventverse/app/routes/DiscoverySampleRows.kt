package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.discovery.proposal.toSampleRow
import com.eventverse.app.domain.pack.DomainPack

/**
 * Baris contoh untuk layar **ber-proposal** di ringkasan draf (fungsi murni, tanpa DB).
 *
 * Urutan: kerangka blok (`Skeleton`) > `seed` proposal bila tak kosong > untuk `CUSTOM_SCREEN` saja, penanda
 * struktural jalur lama ([WidgetRegistry.sampleRowsFor]: tiga kotak generik, atau baris vertikal pack). Fallback
 * terakhir itu menjaga janji kompatibilitas Irisan 3b: draf modern `CUSTOM_SCREEN` tanpa blok digambar sama
 * dengan draf lama, bukan kartu "belum lengkap". Widget lain dengan seed kosong tetap `[]` — klien memang harus
 * menampilkan peringatan untuk proposal yang tak lengkap.
 */
internal fun proposalSampleRows(
    screen: PrototypeScreen,
    view: ViewProposal,
    seed: List<Map<String, String>>,
    samplePack: DomainPack
): List<Map<String, String>> {
    if (view is ViewProposal.Skeleton) return view.blocks.map { it.toSampleRow() }
    if (seed.isNotEmpty()) return seed
    return if (WidgetKind.fromCode(screen.widget) == WidgetKind.CUSTOM_SCREEN) {
        WidgetRegistry.sampleRowsFor(screen, samplePack)
    } else {
        emptyList()
    }
}
