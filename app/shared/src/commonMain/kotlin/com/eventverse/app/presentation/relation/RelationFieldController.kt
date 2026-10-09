package com.eventverse.app.presentation.relation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.relationTargetFormatError
import com.eventverse.app.infrastructure.api.StoredTenantSlugProvider
import com.eventverse.app.presentation.designsystem.RelationOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kontrak UI pemilih rujukan untuk **satu field** (C7, TRD-FIELD-001). Disiapkan host
 * (form/tabel/kanban prototype atau inspektur CRM) dan dikonsumsi [com.eventverse.app.presentation.designsystem.ClayRelationPicker].
 *
 * Ia menyimpan hasil pencarian, status muat, galat, dan cache label (id target → label) supaya
 * konteks baca bisa menampilkan label, fallback id, atau "tidak ditemukan" tanpa memanggil
 * jaringan berulang.
 */
interface RelationFieldUi {
    val query: String
    val options: List<RelationOption>
    val isLoading: Boolean
    val error: String?

    /** Label tersimpan untuk [id], null bila belum diketahui (bukan berarti target hilang). */
    fun labelFor(id: String): String?

    fun onQueryChange(query: String)

    /** Dipanggil saat pengguna memilih/mengosongkan; host menyimpan label ke cache. */
    fun onSelect(option: RelationOption?)
}

/**
 * Implementasi jaringan [RelationFieldUi]. Pencarian di-*debounce* agar mengetik tidak
 * membanjiri route (NFR performa: lookup `LIMIT 20`).
 */
@Stable
class RelationFieldController(
    private val source: RelationOptionsRemoteDataSource,
    private val tenantSlug: String,
    private val module: String,
    private val entity: String,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 250
) : RelationFieldUi {

    override var query by mutableStateOf("")
        private set
    override var options by mutableStateOf<List<RelationOption>>(emptyList())
        private set
    override var isLoading by mutableStateOf(false)
        private set
    override var error by mutableStateOf<String?>(null)
        private set

    private val labels = mutableStateMapOf<String, String>()
    private var searchJob: Job? = null

    override fun labelFor(id: String): String? = labels[id]

    override fun onQueryChange(query: String) {
        this.query = query
        searchJob?.cancel()
        if (query.isBlank()) {
            options = emptyList()
            error = null
            return
        }
        searchJob = scope.launch {
            delay(debounceMillis)
            runSearch(query)
        }
    }

    /** Muat opsi awal (kueri kosong). Aman dipanggil berkali-kali. */
    fun prime() {
        if (options.isEmpty() && !isLoading) {
            scope.launch { runSearch("") }
        }
    }

    private suspend fun runSearch(query: String) {
        isLoading = true
        error = null
        source.search(tenantSlug, module, entity, query)
            .onSuccess { list ->
                options = list
                list.forEach { labels[it.id] = it.label }
            }
            .onFailure { error = it.message }
        isLoading = false
    }

    override fun onSelect(option: RelationOption?) {
        option?.let { labels[it.id] = it.label }
    }
}

/**
 * Pisah notasi target `entityId` (modul sendiri) atau `moduleId:entityId` (lintas modul) —
 * bentuknya **dikunci satu aturan core** [relationTargetFormatError] supaya UI dan validator
 * tidak berbeda pendapat. Bentuk tak sah mengembalikan entitas kosong; pemanggil lalu fail-closed
 * (`null`), bukan menebak (Kontrak 4 variability: parser tunggal, tanpa fallback senyap).
 */
fun resolveRelationTarget(target: String, defaultModule: String): Pair<String, String> {
    val trimmed = target.trim()
    if (relationTargetFormatError(trimmed) != null) return defaultModule to ""
    val idx = trimmed.indexOf(':')
    return if (idx > 0) trimmed.substring(0, idx) to trimmed.substring(idx + 1)
    else defaultModule to trimmed
}

/**
 * Kontroler untuk layar prototype berbinding API; `null` untuk [DataBinding.Memory] (demo tanpa
 * server — pemilih tidak akan pernah bisa memuat opsi, jangan dipalsukan, field-component Kontrak 8).
 */
fun relationFieldControllerOrNull(
    binding: DataBinding?,
    target: String,
    scope: CoroutineScope
): RelationFieldController? {
    val basePath = (binding as? DataBinding.Api)?.basePath ?: return null
    val currentModule = basePath.substringAfter("/modules/", "").substringBefore('/')
    if (currentModule.isBlank() || target.isBlank()) return null
    val tenant = StoredTenantSlugProvider.currentTenantSlug() ?: return null
    val (module, entity) = resolveRelationTarget(target, currentModule)
    if (entity.isBlank()) return null
    return RelationFieldController(RelationOptionsClients.shared, tenant, module, entity, scope)
}

/**
 * Kontroler untuk kosakata CRM: `targetResource` (R4) bisa berbentuk `entityId` atau
 * `moduleId:entityId`; bentuk tanpa `:` diperlakukan sebagai resource yang mengidentifikasi
 * dirinya sendiri sebagai modulnya (pemetaan eksak resource→modul dimiliki Track B / resolver).
 */
fun relationFieldControllerForResource(
    tenantSlug: String?,
    resource: String,
    scope: CoroutineScope
): RelationFieldController? {
    if (tenantSlug.isNullOrBlank() || resource.isBlank()) return null
    val (module, entity) = resolveRelationTarget(resource, defaultModule = resource)
    if (module.isBlank() || entity.isBlank()) return null
    return RelationFieldController(RelationOptionsClients.shared, tenantSlug, module, entity, scope)
}
