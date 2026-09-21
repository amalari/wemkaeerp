package com.eventverse.app.domain.workqueue

import kotlin.jvm.JvmInline

@JvmInline
value class WorkCardId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkCardId cannot be blank" }
        require(value.length <= 64) { "WorkCardId length cannot exceed 64 characters" }
    }
}

@JvmInline
value class WorkDepositId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkDepositId cannot be blank" }
        require(value.length <= 64) { "WorkDepositId length cannot exceed 64 characters" }
    }
}

@JvmInline
value class ReworkTicketId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReworkTicketId cannot be blank" }
        require(value.length <= 64) { "ReworkTicketId length cannot exceed 64 characters" }
    }
}

@JvmInline
value class WorkStationCode(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkStationCode cannot be blank" }
        require(value.length <= 64) { "WorkStationCode length cannot exceed 64 characters" }
    }
}

@JvmInline
value class DefectCode(val value: String) {
    init {
        require(value.isNotBlank()) { "DefectCode cannot be blank" }
        require(value.length <= 64) { "DefectCode length cannot exceed 64 characters" }
    }
}

enum class WorkSubjectKind {
    BULK_WORK_ORDER,
    SAMPLING_ORDER;
}

data class WorkSubjectRef(
    val kind: WorkSubjectKind,
    val subjectId: String,
    val orderNumber: String,
    val articleName: String
) {
    init {
        require(subjectId.isNotBlank()) { "subjectId cannot be blank" }
        require(orderNumber.isNotBlank()) { "orderNumber cannot be blank" }
    }

    val displayLabel: String get() = "$orderNumber - $articleName"
}

/**
 * Satuan pelacakan fisik barang di stasiun kerja.
 *
 * [BUNDLE]: Diikat dalam bendel kecil (±20–24 pcs) berlabel ikat/nomor bundle.
 * [LOT_ACCUMULATION]: Dilebur menjadi hitungan lot per PO + Size (misal saat masuk drum cuci & setrika).
 */
enum class WorkTrackingUnit {
    BUNDLE,
    LOT_ACCUMULATION;
}

enum class WorkCardStatus {
    QUEUED,
    IN_PROGRESS,
    MERGED,
    COMPLETED;
}

enum class WorkExecutionMode {
    IN_HOUSE,
    SUBCONTRACTED;
}

enum class ReworkTicketStatus {
    REWORK_ISSUED,
    IN_REPAIR,
    READY_FOR_RE_CHECK,
    CLOSED,
    SCRAP;
}
