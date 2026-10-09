package com.eventverse.app.presentation.designsystem

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

internal enum class DateTimeStep { CLOSED, DATE, TIME }

/**
 * Keadaan murni alur dua langkah [ClayDateTimePicker] (kalender lalu jam). Tidak menyentuh nilai tersimpan:
 * nilai hanya berubah lewat `onValueChange` saat pengguna menekan Pilih di dialog jam atau Kosongkan.
 *
 * Hari yang dipilih tapi belum dikonfirmasi ([pendingDate]) hidup di sini sehingga tombol Kembali dari dialog jam
 * membuka kalender dengan hari itu masih terpilih, bukan dengan `value` lama yang bisa saja masih kosong.
 * Menutup dialog tanpa memilih ([dismissed]) membuang [pendingDate].
 */
internal data class DateTimeFlow(
    val step: DateTimeStep = DateTimeStep.CLOSED,
    val pendingDate: LocalDate? = null
) {
    val isActive: Boolean get() = step != DateTimeStep.CLOSED

    fun opened(): DateTimeFlow = DateTimeFlow(DateTimeStep.DATE, null)

    fun datePicked(date: LocalDate): DateTimeFlow = DateTimeFlow(DateTimeStep.TIME, date)

    fun backedToDate(): DateTimeFlow = copy(step = DateTimeStep.DATE)

    fun dismissed(): DateTimeFlow = DateTimeFlow()

    /** Hari awal kalender: hari yang baru dipilih bila ada, kalau tidak hari dari nilai tersimpan. */
    fun calendarInitialDate(committed: LocalDateTime?): LocalDate? = pendingDate ?: committed?.date
}
