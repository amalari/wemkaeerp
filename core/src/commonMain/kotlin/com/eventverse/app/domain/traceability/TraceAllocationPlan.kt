package com.eventverse.app.domain.traceability

/** Satu kartu yang akan dicetak: kodenya sudah pasti, isinya belum. */
data class TraceLabelPlan(
    val code: TraceCode,
    val tier: TraceTier,
    val sizeLabel: String,
    val sizeIndex: Int,
    val sequence: Int,
    /** Target isi kartu ini — panduan, bukan batas keras; operator boleh mengikat lebih atau kurang. */
    val targetCapacity: Int
) {
    val humanCode: String get() = TraceCodec.grouped(code)

    /** Baris yang dicetak di bawah QR supaya kartu tetap berguna saat jaringan mati. */
    fun captionFor(spkNumber: String): String =
        "$spkNumber · Size $sizeLabel · ${tier.shortLabel}-${(sequence + 1).toString().padStart(3, '0')}"
}

/**
 * Rencana pra-cetak untuk satu SPK.
 *
 * Kartu dicetak lebih dulu dan barisnya baru lahir saat scan pertama. Urutannya begini karena bundel
 * terbentuk di akhir shift, dan menaruh printer di lantai rajut itu mahal dan rapuh. Konsekuensi yang
 * disengaja: kode bersifat deterministik, sehingga tidak ada perebutan nomor urut di jalur normal —
 * berbeda dari `nextSpkNumber` yang memakai `COUNT(*) + 1` dan rusak begitu ada baris terarsip.
 */
data class TraceAllocationPlan(
    val snapshot: TraceWorkOrderSnapshot,
    val setsPerBundle: Int,
    val pcsPerSack: Int,
    val labels: List<TraceLabelPlan>
) {
    fun labelsFor(tier: TraceTier, sizeLabel: String? = null): List<TraceLabelPlan> =
        labels.filter { it.tier == tier && (sizeLabel == null || it.sizeLabel.equals(sizeLabel, true)) }

    val sheetCountEstimate: Int
        get() {
            val bundles = labelsFor(TraceTier.BUNDLE).size
            val sacks = labelsFor(TraceTier.SACK).size
            return ceilDiv(bundles, BUNDLE_CARDS_PER_SHEET) + ceilDiv(sacks, SACK_CARDS_PER_SHEET)
        }

    companion object {
        const val BUNDLE_CARDS_PER_SHEET = 8
        const val SACK_CARDS_PER_SHEET = 4

        const val DEFAULT_SETS_PER_BUNDLE = 20
        const val DEFAULT_PCS_PER_SACK = 60

        /**
         * Kartu cadangan per size, di atas kebutuhan teoretis.
         *
         * Bukan pemborosan: kartu sobek, basah kena uap, atau terikat ke bundel yang batal. Tanpa
         * cadangan, satu kartu rusak berarti operator berhenti bekerja sampai ada yang mencetak ulang
         * di kantor — dan yang sebenarnya terjadi adalah dia melanjutkan tanpa kartu sama sekali.
         */
        const val DEFAULT_SPARE_PER_SIZE = 2

        fun plan(
            snapshot: TraceWorkOrderSnapshot,
            setsPerBundle: Int = DEFAULT_SETS_PER_BUNDLE,
            pcsPerSack: Int = DEFAULT_PCS_PER_SACK,
            sparePerSize: Int = DEFAULT_SPARE_PER_SIZE
        ): TraceAllocationPlan {
            require(setsPerBundle > 0) { "Set per bundel harus lebih dari 0" }
            require(pcsPerSack > 0) { "Pcs per karung harus lebih dari 0" }
            require(snapshot.sizes.size <= TraceCodec.MAX_SIZE_INDEX) {
                "SPK ini punya ${snapshot.sizes.size} size, melebihi kapasitas kode telusur"
            }
            require(snapshot.ordinal in 0 until TraceCodec.MAX_WORK_ORDER_ORDINAL) {
                "Ordinal SPK ${snapshot.ordinal} melebihi kapasitas kode telusur"
            }
            require(snapshot.tenantOrdinal in 0 until TraceCodec.MAX_TENANT_ORDINAL) {
                "Ordinal tenant ${snapshot.tenantOrdinal} melebihi kapasitas kode telusur"
            }

            val labels = buildList {
                snapshot.sizes.forEachIndexed { sizeIndex, line ->
                    addAll(
                        allocate(
                            snapshot, TraceTier.BUNDLE, sizeIndex, line,
                            perCard = setsPerBundle, sparePerSize = sparePerSize
                        )
                    )
                    addAll(
                        allocate(
                            snapshot, TraceTier.SACK, sizeIndex, line,
                            perCard = pcsPerSack, sparePerSize = sparePerSize
                        )
                    )
                    // Tepat satu lembar kerja per size, tanpa cadangan: kalau sobek, cetak ulang dari
                    // layar SPK. Berbeda dari kartu bundel yang harus tersedia di lantai saat itu juga.
                    add(
                        TraceLabelPlan(
                            code = TraceCodec.encode(
                                kind = snapshot.ref.kind,
                                tier = TraceTier.WORKSHEET,
                                tenantOrdinal = snapshot.tenantOrdinal,
                                workOrderOrdinal = snapshot.ordinal,
                                sizeIndex = sizeIndex,
                                sequence = 0
                            ),
                            tier = TraceTier.WORKSHEET,
                            sizeLabel = line.sizeLabel,
                            sizeIndex = sizeIndex,
                            sequence = 0,
                            targetCapacity = line.orderedPcs
                        )
                    )
                }
            }
            return TraceAllocationPlan(snapshot, setsPerBundle, pcsPerSack, labels)
        }

        private fun allocate(
            snapshot: TraceWorkOrderSnapshot,
            tier: TraceTier,
            sizeIndex: Int,
            line: TraceSizeLine,
            perCard: Int,
            sparePerSize: Int
        ): List<TraceLabelPlan> {
            val needed = ceilDiv(line.orderedPcs, perCard) + sparePerSize
            val capped = needed.coerceAtMost(TraceCodec.MAX_SEQUENCE)
            return (0 until capped).map { sequence ->
                TraceLabelPlan(
                    code = TraceCodec.encode(
                        kind = snapshot.ref.kind,
                        tier = tier,
                        tenantOrdinal = snapshot.tenantOrdinal,
                        workOrderOrdinal = snapshot.ordinal,
                        sizeIndex = sizeIndex,
                        sequence = sequence
                    ),
                    tier = tier,
                    sizeLabel = line.sizeLabel,
                    sizeIndex = sizeIndex,
                    sequence = sequence,
                    targetCapacity = perCard
                )
            }
        }

        private fun ceilDiv(value: Int, divisor: Int): Int =
            if (value <= 0) 0 else (value + divisor - 1) / divisor
    }
}
