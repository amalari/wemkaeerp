package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.TileSpec

/**
 * Konfigurasi tampilan per jenis widget. Varian **harus** cocok dengan `ScreenProposal.widget`
 * (KANBAN↔[Kanban], TABLE↔[Table], FORM↔[Form], CHECKLIST↔[Checklist], DASHBOARD↔[Dashboard],
 * PRINT↔[Print], CUSTOM_SCREEN↔[None]) — ketidakcocokan dilaporkan validator, tidak ditebak.
 *
 * Merujuk field lewat **kunci** (string) agar mudah ditulis LLM; validator memastikan kunci itu ada.
 * Kolom kanban bukan dikonfigurasi di sini: ia **diturunkan** dari opsi `statusField` entitas, sehingga
 * kolom dan status tidak bisa berselisih.
 */
sealed interface ViewProposal {

    data class Kanban(
        val card: List<CardElement> = emptyList(),
        val columnMeta: Map<String, ColumnMeta> = emptyMap(),
        /** Field yang tampil di form saat kartu diketuk; kosong = tanpa form detail. */
        val detailFormFields: List<String> = emptyList()
    ) : ViewProposal

    data class Table(
        val columns: List<String>,
        val inlineCreate: Boolean = false,
        val editableFields: List<String> = emptyList()
    ) : ViewProposal

    data class Form(val fields: List<String>, val submitLabel: String = "Simpan") : ViewProposal

    data class Checklist(val labelField: String, val doneField: String) : ViewProposal

    data class Dashboard(val tiles: List<TileSpec>) : ViewProposal

    data class Print(val fields: List<String>) : ViewProposal

    data object None : ViewProposal
}
