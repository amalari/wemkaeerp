package com.eventverse.app.domain.moduledev.usecases

import com.eventverse.app.domain.moduledev.BuildPhase
import com.eventverse.app.domain.moduledev.EffortRollup
import com.eventverse.app.domain.moduledev.EffortSource
import com.eventverse.app.domain.moduledev.ModuleBuildEffortEntry
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.WorkHours
import kotlinx.datetime.Instant

/**
 * Records hours spent on a build, by role and phase.
 *
 * Always hours, never days — a working day here is roughly four focused hours, and any layer that
 * stores days invites a conversion at a nominal eight that doubles every downstream figure,
 * including the price.
 */
class LogBuildEffortUseCase(
    private val buildRepository: ModuleBuildRepository
) {
    suspend operator fun invoke(entry: ModuleBuildEffortEntry): Result<ModuleBuildRecord> =
        runCatching {
            val build = buildRepository.findById(entry.buildRecordId)
                ?: error("Build ${entry.buildRecordId.value} tidak ditemukan.")

            require(!build.status.isClosed) {
                "Build ${build.id.value} sudah ditutup (${build.status.code}); jam tidak bisa " +
                    "ditambahkan lagi. Buka build baru untuk pekerjaan lanjutan."
            }

            buildRepository.addEffortEntry(entry)
            build
        }
}

/**
 * Closes a build with what it actually took.
 *
 * Totals are computed from the effort entries and then **stored on the record**. The
 * denormalisation is deliberate: a price was quoted from that cost figure, so correcting an effort
 * row months later must not silently restate what a delivered build cost.
 *
 * The estimate is untouched. The gap between it and these actuals is the only thing this row has
 * to teach the next estimate.
 */
class CompleteModuleBuildUseCase(
    private val buildRepository: ModuleBuildRepository
) {
    suspend operator fun invoke(
        buildId: ModuleBuildId,
        completedAt: Instant,
        effortSource: EffortSource = EffortSource.LOGGED,
        revisionRoundCount: Int = 0,
        leadTimeDays: Int? = null,
        discoveredScopeDelta: String? = null,
        retrospectiveNotes: String? = null,
        gitRef: String? = null,
        reworkPhases: Set<BuildPhase> = DEFAULT_REWORK_PHASES
    ): Result<ModuleBuildRecord> = runCatching {
        val build = buildRepository.findById(buildId)
            ?: error("Build ${buildId.value} tidak ditemukan.")

        val entries = buildRepository.findEffortEntries(buildId)
        require(entries.isNotEmpty()) {
            "Build ${buildId.value} tidak punya catatan jam; tidak bisa ditutup tanpa aktual. " +
                "Isi jam per peran/fase terlebih dahulu."
        }

        val rollup = EffortRollup.of(entries)
        val reworkHours = WorkHours.sum(
            entries.filter { it.phase in reworkPhases }.map { it.hours }
        )

        val completed = build.completeWith(
            actualHours = rollup.totalHours,
            blendedRate = rollup.blendedHourlyRate,
            totalCost = rollup.totalCost,
            completedAt = completedAt,
            effortSource = effortSource,
            reworkHours = reworkHours,
            revisionRoundCount = revisionRoundCount,
            leadTimeDays = leadTimeDays,
            discoveredScopeDelta = discoveredScopeDelta,
            retrospectiveNotes = retrospectiveNotes,
            gitRef = gitRef
        )

        buildRepository.save(completed)
        completed
    }

    companion object {
        /**
         * Hours logged against review count as rework.
         *
         * An approximation, and knowingly so: review hours also cover legitimate first-pass
         * review. It is recorded as an outcome column, never fed back as a predictor, so an
         * imperfect split costs nothing in estimate quality.
         */
        val DEFAULT_REWORK_PHASES = setOf(BuildPhase.REVIEW)
    }
}
