package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.ScenarioCarryRule
import com.eventverse.app.domain.discovery.scenario.ScenarioIssue
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.SampleValues

/** Penunjuk satu baris simulasi yang dibuat pemutar: baris [rowId] di layar [screenId] milik langkah [stepIndex]. */
data class RowRef(val stepIndex: Int, val screenId: String, val rowId: String)

/**
 * Pintu data pemutar (TRD-BLDR-002 FR-6): hanya memori sesi prototype. Sengaja **bukan** klien HTTP - kelas pemutar tidak
 * punya dependensi jaringan, jadi "nol permintaan HTTP selama simulasi" dijamin oleh bentuk tipenya, bukan janji.
 * Adaptor produksi: [SessionScenarioData]; di tes dipakai fake.
 */
interface ScenarioScreenData {
    /** Entitas utama layar; `null` bila layar tak ada, tak bisa dimainkan, atau tak punya entitas (mis. dasbor/kerangka). */
    fun entityOf(screenId: String): EntitySpec?

    fun rowsOf(screenId: String): List<PrototypeRow>

    /** Membuat baris baru di memori layar itu. Gagal dengan pesan siap tampil bila ditolak aturan layar (mis. field wajib). */
    suspend fun createRow(screenId: String, values: Map<String, String>): Result<PrototypeRow>

    /** `true` bila layar [screenId] berbentuk formulir entri baru (tidak menampilkan baris); bawaan `false` = tabel/papan/daftar. */
    fun isFormScreen(screenId: String): Boolean = false

    /** Mengisi isian formulir [screenId] dengan [values] TANPA membuat baris (hanya dipanggil bila [isFormScreen]). */
    fun prefillForm(screenId: String, values: Map<String, String>) {}
}

/** Hasil satu aksi "Lanjut". */
sealed interface ScenarioAdvance {
    /**
     * Pindah ke langkah berikutnya ([run]). [created] = baris baru (null bila tidak ada yang perlu dibawa), [warnings] =
     * pasangan field yang ditolak aturan pembawa (baris tetap dibuat dengan pasangan yang sah), [note] = keterangan netral.
     */
    data class Moved(
        val run: ScenarioRun,
        val created: RowRef?,
        val warnings: List<ScenarioIssue> = emptyList(),
        val note: String? = null,
        /** Label field wajib yang diisi **nilai contoh** karena lahir di langkah ini dan tak tercakup pembawa data. */
        val sampled: List<String> = emptyList(),
        /** `true` bila layar tujuan formulir: isiannya diisi nilai terbawa + contoh, TIDAK ada baris dibuat sampai pengguna menyimpan. */
        val prefilled: Boolean = false
    ) : ScenarioAdvance

    /** Tidak pindah dan tidak ada baris yang dibuat; [reason] ditampilkan di langkah itu. */
    data class Blocked(val reason: String, val issues: List<ScenarioIssue> = emptyList()) : ScenarioAdvance
}

/**
 * Keadaan pemutar simulasi satu skenario - murni, tanpa Compose, tanpa jaringan.
 *
 * **Aturan baris gagal sebagian** (diputuskan di sini, TRD-BLDR-002 FR-6/AC-2):
 * - **Sebagian ditolak, sebagian lolos** -> baris dibuat dengan pasangan yang lolos; pasangan yang ditolak dilaporkan
 *   di [ScenarioAdvance.Moved.warnings] (pengguna melihatnya, tidak dibawa diam-diam).
 * - **Gagal total** (aturan menolak dan tak ada satu pun nilai yang lolos) -> [ScenarioAdvance.Blocked], tidak pindah,
 *   tidak ada baris.
 * - **Field wajib layar tujuan yang tak tercakup pembawa** (mis. nomor antrean, status awal: data baru yang lahir di langkah itu)
 *   -> diisi **nilai contoh** ([SampleValues]) dan dilaporkan di [ScenarioAdvance.Moved.sampled], supaya alur tetap bisa dilihat
 *   (temuan 9, cek visual klinik 2026-10-16: tanpa ini hampir semua alur nyata berhenti di langkah kedua). Tipe yang tak punya
 *   nilai contoh (RELATION/USER_REF/FILE) tetap kosong -> layar menolak -> [ScenarioAdvance.Blocked].
 * - **Layar tujuan berbentuk formulir** (temuan 14, cek visual klinik 2026-10-16: "Dokter periksa pasien"): formulir hanya untuk entri baru dan
 *   tidak menampilkan baris, jadi baris yang dibuat diam-diam di memori membuat pengguna melihat isian KOSONG padahal pesan bilang ada data
 *   contoh. Maka nilai terbawa + status awal + contoh **mengisi isian formulir** ([ScenarioScreenData.prefillForm]) dan baris TIDAK dibuat
 *   ([ScenarioAdvance.Moved.prefilled]); baris lahir saat pengguna menekan simpan, lewat jalur formulir yang sama (validasi reducer, siaran
 *   ke layar sekawan). Alternatif ditolak: strip "baris terbawa" di atas formulir - menambah komponen baru dan data tetap tak bisa disunting
 *   di tempat. Konsekuensi: langkah formulir belum punya baris sumber sampai disimpan, jadi "Lanjut" darinya ditolak dengan "Simpan dulu".
 *   Tabel/papan/daftar TIDAK berubah (baris dibuat). Tipe tanpa nilai contoh (RELATION/USER_REF/FILE) tak dikarang: isian dibiarkan kosong dan
 *   reducer formulir menolak saat simpan; di jalur formulir pemutar tidak memblokir karena pengguna sendiri yang melengkapinya.
 * - **Entitas bermesin-status**: baris baru memulai di status awal ([SampleValues.initialState]) bila statusnya kosong, kalau tidak papan
 *   tidak menampilkannya (baris tanpa status tak masuk kolom mana pun). Ini aturan mesin, bukan nilai contoh, jadi tidak dilaporkan di `sampled`.
 * - **Layar menolak baris** (mis. field wajib bertipe tanpa nilai contoh kosong) -> [ScenarioAdvance.Blocked] dengan pesan layar;
 *   tidak pindah, tidak mencatat jejak.
 * - **Tidak ada yang dibawa** (langkah tanpa pembawa, layar tujuan kerangka/tanpa entitas) -> pindah tanpa membuat baris.
 *
 * Baris sumber: baris yang dibawa pemutar ke langkah ini (jejak terakhir), atau baris terakhir di layar bila langkah ini
 * langkah pertama / barisnya sudah tidak ada.
 *
 * Kembali (tombol "Kembali" atau [rework]) tidak pernah menghapus baris yang sudah dibuat.
 */
data class ScenarioRun(
    val scenario: ScenarioSketch,
    val stepIndex: Int = 0,
    val trail: List<RowRef> = emptyList()
) {
    val currentStep get() = scenario.steps.getOrNull(stepIndex)
    val isLastStep: Boolean get() = stepIndex >= scenario.steps.lastIndex

    /** Indeks langkah tujuan "Kembalikan", hanya bila sah (mundur dan ada); selain itu null. */
    val reworkTarget: Int?
        get() = currentStep?.reworkTo?.takeIf { it in 0 until stepIndex }

    val reworkLabel: String? get() = reworkTarget?.let { scenario.steps.getOrNull(it)?.label }

    /** Aksi "Kembalikan ke <label>": kembali ke langkah rework; baris yang ada tetap. `null` bila langkah ini tak punya rework sah. */
    fun rework(): ScenarioRun? = reworkTarget?.let { copy(stepIndex = it) }

    /** Aksi "Lanjut ke <label>": bawa data lewat [rule] ke layar langkah berikutnya, lalu pindah. */
    suspend fun advance(data: ScenarioScreenData, rule: ScenarioCarryRule): ScenarioAdvance {
        val from = currentStep ?: return ScenarioAdvance.Blocked("Langkah skenario tidak ditemukan.")
        val next = scenario.steps.getOrNull(stepIndex + 1) ?: return ScenarioAdvance.Blocked("Ini langkah terakhir skenario.")
        val toIndex = stepIndex + 1
        val moved = copy(stepIndex = toIndex)
        val sourceScreen = from.screenId
        val targetScreen = next.screenId
        val sourceEntity = sourceScreen?.let(data::entityOf)
        val targetEntity = targetScreen?.let(data::entityOf)
        if (sourceScreen == null || targetScreen == null || sourceEntity == null || targetEntity == null || from.carries.isEmpty()) {
            val note = if (targetScreen == null || targetEntity == null) "Langkah '${next.label}' belum punya layar yang bisa diisi: tidak ada baris dibuat."
            else "Tidak ada data yang dibawa dari '${from.label}'."
            return ScenarioAdvance.Moved(moved, created = null, note = note)
        }
        val source = sourceRow(data, sourceScreen)
            ?: return ScenarioAdvance.Blocked(
                if (data.isFormScreen(sourceScreen)) "Formulir '${from.label}' belum disimpan. Simpan dulu, lalu lanjutkan."
                else "Belum ada baris di '${from.label}'. Buat satu dulu, lalu lanjutkan."
            )
        val outcome = rule.apply(source.values, from.carries, sourceEntity, targetEntity)
        if (outcome.values.isEmpty()) {
            return if (outcome.rejected.isEmpty()) ScenarioAdvance.Moved(moved, created = null, note = "Tidak ada data yang dibawa dari '${from.label}'.")
            else ScenarioAdvance.Blocked("Data dari '${from.label}' tidak dapat dibawa ke '${next.label}'.", outcome.rejected)
        }
        // Urutan penting: status awal dulu (field status wajib pun memulai di status awal, bukan opsi pertama), baru nilai contoh.
        val base = SampleValues.withInitialState(targetEntity, outcome.values)
        val samples = SampleValues.missingRequired(targetEntity, base).mapNotNull { f -> SampleValues.forField(f)?.let { f to it } }
        val filled = base + samples.associate { (f, v) -> f.key to v }
        if (data.isFormScreen(targetScreen)) {
            data.prefillForm(targetScreen, filled)
            return ScenarioAdvance.Moved(moved, created = null, warnings = outcome.rejected, sampled = samples.map { it.first.label }, prefilled = true)
        }
        val row = data.createRow(targetScreen, filled).getOrElse { e ->
            return ScenarioAdvance.Blocked(e.message ?: "Baris di '${next.label}' ditolak.", outcome.rejected)
        }
        val ref = RowRef(toIndex, targetScreen, row.id)
        return ScenarioAdvance.Moved(moved.copy(trail = trail + ref), created = ref, warnings = outcome.rejected, sampled = samples.map { it.first.label })
    }

    private fun sourceRow(data: ScenarioScreenData, screenId: String): PrototypeRow? {
        val rows = data.rowsOf(screenId)
        val carried = trail.lastOrNull { it.stepIndex == stepIndex }
        return rows.firstOrNull { it.id == carried?.rowId } ?: rows.lastOrNull()
    }
}
