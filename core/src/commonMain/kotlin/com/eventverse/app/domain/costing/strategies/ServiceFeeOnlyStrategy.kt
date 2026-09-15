package com.eventverse.app.domain.costing.strategies

import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.CostingFormulaOutput
import com.eventverse.app.domain.costing.CostingFormulaStrategy
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.contracts.CostBucket
import com.eventverse.app.domain.contracts.CostBucketKind
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics

/**
 * Strategi HPP untuk model bisnis **CMT Makloon** (Cut, Make, Trim).
 *
 * Pabrik hanya menyediakan jasa jahit — kain adalah milik klien (konsinyasi).
 * Yang ditagihkan hanya ongkos jasa, bukan material.
 *
 * ## Aturan keras CMT (dua alasan independen)
 * 1. **Kontrak 4 (penagihan)**: klien sudah memiliki dan membayar kainnya sendiri.
 *    Menjumlahkannya ke invoice = menagih klien atas barangnya sendiri.
 * 2. **Kontrak 3 (neraca)**: kain titipan bernilai nol sebagai aset pabrik
 *    (`hasFinancialAssetValue = false`), jadi tidak boleh menggelembungkan `cogsPerUnit`.
 *
 * Nilai kain konsinyasi tidak hilang: ia dicatat di `consignedMaterialValueHandled`
 * dari `BomCostPreview.consignedNotionalValue` — dasar rekonsiliasi perca dan klaim kerusakan.
 *
 * ## Invarian yang ditegakkan di resolver, bukan diulang di sini
 * `CostBucket.material(...)` dengan `CONSIGNED_CLIENT_MATERIAL` sudah memastikan
 * `isBillableToClient = false` dan `amountPerUnit = 0`. Strategi tidak perlu check manual.
 *
 * ## Jika `includeFabricCost = "true"` di override
 * Tetap tidak bisa membuat kain CONSIGNED jadi tertagih —
 * invarian di `CostBucket.init` lebih kuat dan akan melempar sebelum bucket terbentuk.
 */
object ServiceFeeOnlyStrategy : CostingFormulaStrategy {
    override val behavior = CostingBehavior.SERVICE_FEE_ONLY

    override fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput> = runCatching {
        val params = input.parameters
        val currency = input.currency
        val bomPreview = input.bomCostPreview
        val warnings = mutableListOf<String>()

        val buckets = mutableListOf<CostBucket>()

        // --- MATERIAL buckets (kain konsinyasi tetap tampil di daftar tapi Rp 0 dan tidak ditagihkan)
        for (line in bomPreview.lines) {
            val bucket = CostBucket.material(
                label = line.material.displayLabel,
                amountPerUnit = line.costPerGarment,
                ownership = line.ownership,
                behavior = behavior   // SERVICE_FEE_ONLY → OWNED juga tidak ditagihkan (includeFabricCost=false)
            ).copy(sourceRefs = listOf(line.lineId))
            buckets.add(bucket)
        }

        // --- LABOR: ini satu-satunya bucket yang ditagihkan di CMT
        // Jika serviceFeePerUnit dikonfigurasi, itu yang dipakai (lebih presisi untuk tarif borongan)
        // Jika tidak, hitung dari SAM × rate
        val laborCost = if (!params.serviceFeePerUnit.isZero) {
            params.serviceFeePerUnit
        } else {
            val samTotal = input.samBreakdown.totalMinutes()
            if (samTotal.isZero) {
                warnings.add("serviceFeePerUnit belum dikonfigurasi dan SAM = 0 — ongkos jasa nol")
            }
            params.laborRatePerSamMinute * samTotal
        }

        buckets.add(CostBucket(
            kind = CostBucketKind.LABOR,
            label = if (!params.serviceFeePerUnit.isZero) {
                "Ongkos Jasa Makloon (tarif tetap)"
            } else {
                val samTotal = input.samBreakdown.totalMinutes()
                "Ongkos Jasa Makloon (${samTotal.formatted(1)} menit SAM)"
            },
            amountPerUnit = laborCost,
            isBillableToClient = true
        ))

        // --- SUBCONTRACT: sablon/bordir luar tetap biaya nyata dan ditagihkan
        val samSubcon = input.samBreakdown.subcontractMinutes()
        if (!samSubcon.isZero) {
            val subconCost = params.subcontractRatePerSamMinute * samSubcon
            buckets.add(CostBucket(
                kind = CostBucketKind.SUBCONTRACT,
                label = "Jasa Subkon (sablon/bordir)",
                amountPerUnit = subconCost,
                isBillableToClient = true
            ))
        }

        // Notional value kain konsinyasi — untuk rekonsiliasi perca, BUKAN penagihan
        val consignedNotionalPerOrder = bomPreview.consignedNotionalValue

        CostingFormulaOutput(
            buckets = buckets,
            marginRatio = params.marginRatio,    // biasanya 0 di CMT
            consignedMaterialValueHandled = consignedNotionalPerOrder,
            roundingResidual = Money.zero(currency),
            warnings = warnings
        )
    }
}
