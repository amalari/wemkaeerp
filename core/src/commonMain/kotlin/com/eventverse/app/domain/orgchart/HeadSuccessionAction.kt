package com.eventverse.app.domain.orgchart

/**
 * Action taken on an existing department head when a new head is appointed.
 * Enforces the business rule: Each department may only have 1 active Head at a time.
 */
enum class HeadSuccessionAction(
    val title: String,
    val description: String
) {
    DEMOTE_TO_STAFF(
        title = "Alihkan Menjadi Staf Divisi",
        description = "Pejabat lama ditugaskan sebagai Staf Pelaksana dan melapor ke Kepala Divisi baru"
    ),
    DEACTIVATE(
        title = "Non-Aktifkan Akun Pejabat Lama",
        description = "Pejabat lama dinonaktifkan dan diarsipkan dari bagan struktur aktif"
    )
}
