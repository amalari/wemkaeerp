package com.eventverse.app.presentation.builder.audit

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.shared.builder.BuilderAuditTrailCodec
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Kartu "Postur Security" (TRD-PLAT-014 FR-5-3, keputusan beku PLAN §7.2): **hanya** menurunkan
 * data dari payload yang sudah ada — `GET /api/builder/overview` + `/deployments`. Tanpa agregat
 * baru ("modul dengan RBAC terdaftar vs total" ditunda ke v2.1).
 *
 * [liveModuleCount] `null` = payload belum memuat daftar modul live → tampil "belum tersedia"
 * apa adanya, tidak dipalsukan jadi angka.
 */
data class SecurityPostureUi(
    /** Status deployment aktif; `null` = tidak ada deployment aktif (belum deploy / sudah ROLLED_BACK). */
    val activeDeploymentStatus: String?,
    /** Versi pack ter-pin oleh deployment aktif; `null` = tidak ada yang ter-pin. */
    val pinnedPackVersion: Int?,
    /** Jumlah modul live dari payload; `null` = belum tersedia di payload. */
    val liveModuleCount: Int?,
)

data class BuilderAuditUiState(
    val entries: List<AuditLogEntry> = emptyList(),
    /** Filter jenis aksi; `null` = semua. */
    val filter: AuditAction? = null,
    val posture: SecurityPostureUi? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface BuilderAuditUiEvent {
    data object Load : BuilderAuditUiEvent
    data class Filter(val action: AuditAction?) : BuilderAuditUiEvent
}

/** Entri yang tampil setelah filter jenis aksi (kronologis apa adanya — urutan server, tanpa sortir ulang). */
fun BuilderAuditUiState.visibleEntries(): List<AuditLogEntry> =
    if (filter == null) entries else entries.filter { it.action == filter }

/** Sumber data jejak audit + postur, dipisah dari [BuilderApiClient] agar teruji dengan fake. */
interface BuilderAuditRemote {
    /** Jejak audit milik tenant sesi — dekode ketat lewat [BuilderAuditTrailCodec]. */
    suspend fun fetchAudit(limit: Int = 50): Result<List<AuditLogEntry>>

    /** Payload overview untuk kartu Postur Security (deployment aktif + versi ter-pin + modul live). */
    suspend fun fetchPosture(): Result<SecurityPostureUi>
}

/** Implementasi nyata di atas [BuilderApiClient]; klien tidak pernah mengirim identitas tenant. */
class BuilderAuditRemoteApi(private val client: BuilderApiClient = BuilderApiClient()) : BuilderAuditRemote {
    override suspend fun fetchAudit(limit: Int): Result<List<AuditLogEntry>> =
        client.auditTrail(limit).mapCatching(::decodeAuditEntries)

    override suspend fun fetchPosture(): Result<SecurityPostureUi> =
        client.overview().mapCatching(::parseSecurityPosture)
}

/**
 * Dekode respons `GET /api/builder/audit` — **wajib** lewat codec kontrak Track 0
 * ([BuilderAuditTrailCodec]), bukan parser ad-hoc: aksi/peran/waktu tak dikenal ditolak dengan
 * [com.eventverse.app.shared.builder.BuilderAuditDecodeException], bukan jatuh senyap.
 */
internal fun decodeAuditEntries(raw: JsonValue): List<AuditLogEntry> = BuilderAuditTrailCodec.decode(raw)

/**
 * Kartu Postur Security dari payload `GET /api/builder/overview` yang sudah ada (PLAN §7.2):
 * status deployment aktif + versi ter-pin, dan jumlah modul live **bila payload memuatnya** —
 * tanpa agregat baru, tanpa menebak.
 */
internal fun parseSecurityPosture(raw: JsonValue?): SecurityPostureUi {
    val obj = raw as? JsonValue.Obj
    val active = obj?.obj("activeDeployment")
    // Hanya array sungguhan yang dihitung: JSON null / bentuk lain → null (belum tersedia),
    // bukan jumlah 0 rekayasa.
    val liveCount = obj?.get("liveModuleCount")?.let { (it as? JsonValue.Num)?.asInt }
        ?: (obj?.get("liveModules") as? JsonValue.Arr)?.items?.size
    return SecurityPostureUi(
        activeDeploymentStatus = active?.string("status"),
        pinnedPackVersion = active?.let { (it.get("packVersion") as? JsonValue.Num)?.asInt },
        liveModuleCount = liveCount,
    )
}

/**
 * ViewModel pane Audit (MVI, TRD-PLAT-014 FR-5-2). Hanya menerjemahkan niat layar ke pembacaan
 * API dan menampilkan penolakannya apa adanya — dekode ketat gagal → pesan galat codec, bukan
 * daftar kosong. Gagal memuat postur tidak mematikan daftar audit (kartu saja yang bilang jujur).
 */
class BuilderAuditViewModel(
    private val remote: BuilderAuditRemote = BuilderAuditRemoteApi(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(BuilderAuditUiState())
    val uiState: StateFlow<BuilderAuditUiState> = _uiState.asStateFlow()

    fun onEvent(event: BuilderAuditUiEvent) {
        when (event) {
            BuilderAuditUiEvent.Load -> load()
            is BuilderAuditUiEvent.Filter -> _uiState.update { it.copy(filter = event.action) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true) }
        scope.launch {
            val audit = remote.fetchAudit()
            val posture = remote.fetchPosture()
            audit.fold(
                onSuccess = { entries ->
                    _uiState.update {
                        it.copy(
                            entries = entries,
                            posture = posture.getOrNull() ?: it.posture,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Gagal memuat jejak audit",
                            posture = posture.getOrNull() ?: it.posture,
                        )
                    }
                },
            )
        }
    }
}
