package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.ChecklistConfig
import com.eventverse.app.domain.prototype.DashboardConfig
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.KanbanConfig
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.StateMachine
import com.eventverse.app.domain.prototype.TableConfig

/** Konversi gagal: [issues] berpath (usulan tak sah) atau satu isu `$` (widget tanpa bentuk interaktif). */
class ProposalConversionException(val issues: List<ProposalIssue>) :
    IllegalArgumentException(issues.joinToString("; ") { "${it.path}: ${it.message}" })

/**
 * Mengubah usulan ringkas menjadi [InteractiveScreen] — dikerjakan **kode**, bukan LLM. Validator dijalankan
 * lebih dulu; usulan tak sah → `Result.failure(ProposalConversionException)` bermesej berpath, **tidak pernah**
 * melempar mentah dan tidak pernah menghasilkan layar setengah jadi (konstruktor `PrototypeSpec` tetap jadi
 * lapisan kedua; kegagalannya dibungkus jadi isu `$`).
 *
 * [source] diteruskan ke validator (hanya [ProposalSource.Pack] memakai aturan kunci longgar, lihat [ProposalLimits.PACK_KEY]).
 *
 * `PRINT` dan `CUSTOM_SCREEN` sah sebagai usulan tetapi **tidak punya bentuk interaktif** (sama seperti
 * `WidgetRegistry.interactiveFor` yang mengembalikan null): konversi gagal dengan pesan jelas dan pemanggil
 * menggambarnya statis — tidak ada tebakan.
 */
fun ScreenProposal.toInteractiveScreen(source: ProposalSource? = null): Result<InteractiveScreen> {
    val issues = ScreenProposalValidator.validate(this, source = source)
    if (issues.isNotEmpty()) return Result.failure(ProposalConversionException(issues))
    if (widget == WidgetKind.PRINT || widget == WidgetKind.CUSTOM_SCREEN) {
        return Result.failure(
            ProposalConversionException(listOf(ProposalIssue("$.widget", "Widget ${widget.code} tidak punya bentuk interaktif; gambar statis")))
        )
    }
    return runCatching {
        val entities = entity?.let { listOf(it.toEntitySpec()) }.orEmpty()
        val spec = PrototypeSpec(entities, listOf(toScreenSpec()))
        // Tanpa seed tidak ada kunci sama sekali (sama dengan layar form lama), bukan daftar kosong.
        val rows = entity?.takeIf { seed.isNotEmpty() }
            ?.let { e -> mapOf(e.id to seed.mapIndexed { i, r -> PrototypeRow("$screenId-${i + 1}", r) }) }.orEmpty()
        InteractiveScreen(spec, rows, binding).also { it.newStore() }
    }.recoverCatching { e ->
        if (e is ProposalConversionException) throw e
        throw ProposalConversionException(listOf(ProposalIssue("$", e.message ?: "usulan tak bisa dibentuk jadi layar")))
    }
}

private fun EntityProposal.toEntitySpec(): EntitySpec = EntitySpec(
    id, label,
    fields.map { FieldSpec(it.key, it.label, it.type, it.options, it.required, it.format, it.currencyCode, it.withTime) },
    // Tanpa transisi = bebas pindah (sama dengan petunjuk papan pack); mesin status hanya bila ada aturan.
    statusField?.takeIf { transitions.isNotEmpty() }?.let { sf -> StateMachine(sf, transitions.mapValues { (_, v) -> v.toSet() }) }
)

private fun ScreenProposal.toScreenSpec(): ScreenSpec {
    val e = entity
    return when (val v = view) {
        is ViewProposal.Kanban -> {
            val e0 = requireNotNull(e) { "Kanban tanpa entity" }
            val group = requireNotNull(e0.statusField) { "Kanban tanpa statusField" }
            val title = v.card.firstOrNull { it.style == CardStyle.TITLE }?.field
                ?: e0.fields.first { it.key != group }.key
            val columns = e0.fields.first { it.key == group }.options
            ScreenSpec(
                screenId, this.title, WidgetKind.KANBAN, e0.id,
                kanban = KanbanConfig(
                    group, columns, title, e0.fields.map { it.key }.filter { it != title && it != group },
                    card = v.card, columnMeta = v.columnMeta,
                    detailForm = v.detailFormFields.takeIf { it.isNotEmpty() }?.let { FormConfig(it, v.detailFormSubmitLabel) }
                )
            )
        }
        is ViewProposal.Table -> ScreenSpec(
            screenId, title, WidgetKind.TABLE, e?.id,
            table = TableConfig(
                v.columns, e?.statusField?.takeIf { it in v.columns },
                inlineCreate = v.inlineCreate, editableFields = v.editableFields
            )
        )
        is ViewProposal.Form -> ScreenSpec(screenId, title, WidgetKind.FORM, e?.id, form = FormConfig(v.fields, v.submitLabel))
        is ViewProposal.Checklist -> ScreenSpec(screenId, title, WidgetKind.CHECKLIST, e?.id, checklist = ChecklistConfig(v.labelField, v.doneField))
        is ViewProposal.Dashboard -> ScreenSpec(screenId, title, WidgetKind.DASHBOARD, null, dashboard = DashboardConfig(v.tiles))
        is ViewProposal.Print, ViewProposal.None, is ViewProposal.Skeleton -> error("Widget ${widget.code} tidak punya bentuk interaktif")
    }
}
