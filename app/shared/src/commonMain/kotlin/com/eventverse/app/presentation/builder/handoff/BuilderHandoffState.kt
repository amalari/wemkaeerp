package com.eventverse.app.presentation.builder.handoff

import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.shared.json.JsonValue

/**
 * Model tampilan pane Handoff (TRD-PLAT-014 FR-4): satu `BuildRequest` **milik tenant pemanggil**
 * dari field `buildRequests` di `GET /api/builder/deployments`. Penyaringan tenant ada di server
 * (scope HIERARCHICAL) — parser ini tidak pernah memfilter, apalagi mengirim identitas tenant.
 *
 * Parser sengaja **toleran terhadap field yang belum ada** (permintaan lama pra-V94 tidak punya
 * brief; payload lama tidak punya `reason`/`quoteId`): yang tampil apa adanya, tanpa dipalsukan.
 * Berbeda dengan jejak audit (codec ketat kontrak), ini payload tampilan.
 */
data class BuilderHandoffRow(
    val id: String,
    val moduleId: String,
    val statusRaw: String,
    val reason: String,
    /** Quote platform bila permintaan sudah dikutip; null = belum. */
    val quoteId: String?,
    /** Ada brief beku untuk permintaan ini; false untuk permintaan lama atau bila penyusunan brief gagal. */
    val hasBrief: Boolean,
    /** Versi brief modul ini (naik tiap deploy ulang yang menggantikan permintaan sebelumnya). */
    val briefVersion: Int,
    /** Pengganti bila SUPERSEDED; null = digugurkan tanpa pengganti (atau bukan SUPERSEDED). */
    val supersededBy: String?
) {
    /** Status domain bila [statusRaw] dikenal; null = status tak dikenal → badge abu, tanpa dipalsukan. */
    val status: BuildRequestStatus? get() = BuildRequestStatus.entries.firstOrNull { it.name == statusRaw }

    /** Masih menunggu/sedang dikerjakan (aturan beku [BuildRequestStatus.isPending]). */
    val isPending: Boolean get() = status?.isPending == true
}

/** Baris permintaan dari payload `GET /api/builder/deployments`; `null` bila root bukan objek. */
internal fun parseOwnedBuildRequests(raw: JsonValue?): List<BuilderHandoffRow> {
    val root = raw as? JsonValue.Obj ?: return emptyList()
    return root.objectArray("buildRequests").map { r ->
        BuilderHandoffRow(
            id = r.string("id").orEmpty(),
            moduleId = r.string("moduleId").orEmpty(),
            statusRaw = r.string("status").orEmpty(),
            reason = r.string("reason").orEmpty(),
            quoteId = r.string("quoteId"),
            hasBrief = r.boolean("hasBrief") == true,
            briefVersion = r.int("briefVersion") ?: 1,
            supersededBy = r.string("supersededBy")
        )
    }
}
