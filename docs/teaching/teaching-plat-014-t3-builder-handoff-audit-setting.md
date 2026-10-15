# 🎓 Modul Pembelajaran: PLAT-014 T3 — Pane Handoff, Audit + Postur Security, Setting 4 Tab

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, MVI ViewModel, Codec ketat (fail-closed parsing), Scope HIERARCHICAL, Design System Clay
> **Prasyarat**: Memahami struktur KMP repo ini (`core` domain, `app/shared` presentasi, `server` Ktor), aturan design system (`WeMadeColors`), dan kontrak beku Track 0
> **Referensi Task**: TRD-PLAT-014 (FR-4, FR-5, FR-6) · PLAN-builder-console-v2-navigation §7

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Builder Console lama punya 10 menu datar. Tenant pabrik tidak tahu mana yang bicara (chat), mana yang melihat (live), mana yang menyerahkan pekerjaan ke platform (handoff), mana yang membuktikan (audit), dan mana yang mengatur (setting). Lebih parah: pane Antrian Pembuatan lama adalah antrean **lintas tenant** milik superadmin — kalau owner pabrik membukanya, ia melihat bisnis orang lain (atau 403 yang tidak dijelaskan).
- **Analogi Sederhana**: Handoff itu seperti meja serah terima proyek di kontraktor: Anda melihat **hanya pekerjaan Anda sendiri**, lengkap dengan gambar kerja yang **sudah dibekukan** (brief) — bukan papan proyek seluruh kantor kontraktor. Audit itu seperti kamera CCTV yang merekam siapa masuk gudang Anda dan kapan; kamera tidak pernah mengirim data gudang tetangga.
- **Hasil Akhir**: Tiga pane klien v2 — `BuilderHandoffPane` (permintaan milik tenant + brief beku), `BuilderAuditPane` (jejak kronologis + kartu Postur Security), `BuilderSettingPane` (4 tab: Identitas, Pack & Tema, Tagihan, Kolaborator).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Baca kontrak beku dulu** — `BuilderConsoleContract.kt` (aturan badge `handoffActiveCount`) dan `BuilderAuditTrailCodec.kt` (bentuk JSON + dekode ketat). Track T0 membekukannya supaya T1 (server) dan T3 (klien) bisa kerja paralel tanpa saling menunggu.
2. **Langkah 1: Tambahkan method klien** (`BuilderApiClient`) — `auditTrail(limit)` dan `buildRequestBriefOwned(id)`. Append saja; method lama tidak boleh tersentuh.
3. **Langkah 2: Parser tampilan** (`BuilderHandoffState.kt`) — fungsi murni `JsonValue → List<BuilderHandoffRow>` tanpa Compose, supaya teruji tanpa UI.
4. **Langkah 3: ViewModel MVI** (`BuilderAuditViewModel.kt`) — state (`BuilderAuditUiState`) + event (`Load`, `Filter`) + remote interface (`BuilderAuditRemote`) yang dipisah dari `BuilderApiClient` agar bisa di-fake di test.
5. **Langkah 4: Composable pane** — pane membaca `StateFlow`, bukan memanggil API sendiri.
6. **Langkah 5: Test dengan fake** — pola `StageFlowEditorViewModelTest`: `runTest(StandardTestDispatcher())` + fake remote + `advanceUntilIdle()`.

**Kenapa mulai dari parser/ViewModel, bukan langsung UI?** Karena bug paling mahal di pane semacam ini adalah *data yang salah diam-diam* (fallback senyap), bukan layout. Parser murni lebih murah dites daripada composable.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Dekode wajib lewat codec kontrak (bukan parser ad-hoc)

```kotlin
internal fun decodeAuditEntries(raw: JsonValue): List<AuditLogEntry> = BuilderAuditTrailCodec.decode(raw)
```

**Mengapa?**
- Codec Track 0 **menolak** `action`/`actorRole`/`occurredAt` tak dikenal dengan `BuilderAuditDecodeException`. Parser ad-hoc akan jatuh diam-diam ke default — data berubah tanpa jejak (pelajaran "SPK bordir dibaca ulang sebagai NEW_INTAKE").
- Fungsi `internal` ini adalah sambungan tunggal antara HTTP dan domain; test membuktikan nilai tak dikenal **meledak**, bukan diteruskan.

### Blok B: Scope HIERARCHICAL — klien tidak pernah mengirim identitas tenant

```kotlin
suspend fun auditTrail(limit: Int = 50): Result<JsonValue> =
    call(HttpMethod.Get, "/api/builder/audit?limit=$limit")
```

**Mengapa?**
- Penyaringan "milik tenant pemanggil" terjadi **di server** (`findByTenant(tenantContext.tenantId)`). Klien hanya membawa token + `X-Tenant-Slug` (konteks sesi). Kalau klien yang mengirim "tenant saya = X", payload bisa dipalsukan; kalau server yang menghitung dari sesi, tidak ada permukaan spoofing.
- Perhatikan tidak ada parameter `tenantId` sama sekali di signature method klien — itu keputusan keamanan yang terbaca dari API-nya sendiri.

### Blok C: Postur Security dari payload yang ada (keputusan beku §7.2)

```kotlin
internal fun parseSecurityPosture(raw: JsonValue?): SecurityPostureUi {
    val active = obj?.obj("activeDeployment")
    val liveCount = obj?.get("liveModuleCount")?.let { (it as? JsonValue.Num)?.asInt }
        ?: obj?.array("liveModules")?.size?.takeIf { obj.has("liveModules") }
    return SecurityPostureUi(active?.string("status"), active?.int("packVersion"), liveCount)
}
```

**Mengapa?**
- Keputusan PLAN §7.2: kartu ini **hanya** menurunkan data dari payload `GET /api/builder/overview` + `/deployments`. Agregat baru ("modul dengan RBAC terdaftar vs total") ditunda ke v2.1 — membangunnya sekarang berarti janji keamanan yang tidak bisa dibuktikan datanya.
- `liveModuleCount` yang belum ada di payload → `null` → UI menulis "belum tersedia" **apa adanya**, bukan `0` rekayasa. Angka palsu di kartu keamanan lebih berbahaya daripada angka yang tidak ada.

### Blok D: Gagal memuat postur ≠ gagal memuat audit

```kotlin
audit.fold(
    onSuccess = { entries -> _uiState.update { it.copy(entries = entries, posture = posture.getOrNull() ?: it.posture, ...) } },
    onFailure = { e -> _uiState.update { it.copy(error = ..., posture = posture.getOrNull() ?: it.posture) } },
)
```

**Mengapa?**
- NFR: "Pane Audit/Handoff gagal memuat → empty-state jujur + retry, **tidak mematikan shell**". Dua panggilan berdiri sendiri; kegagalan kartu tidak menenggelamkan daftar audit, dan sebaliknya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Remote interface (`BuilderAuditRemote`) di ViewModel** | ViewModel langsung memanggil `BuilderApiClient` | Bisa di-fake di commonTest tanpa HTTP (pola `StageFlowRemoteDataSource`) | Test harus mock `HttpClient` — rapuh dan lambat |
| **Codec ketat untuk audit** | Parse longgar + default | Jejak audit = fitur kepercayaan; nilai tak dikenal harus terlihat, bukan diam-diam diturunkan | Baris audit "hantu" yang merusak kredibilitas fitur |
| **Parser toleran untuk handoff** (field lama boleh absen) | Dekode ketat seperti audit | Payload tampilan, bukan kontrak; permintaan pra-V94 memang tanpa brief — ditampilkan jujur "Tanpa brief" | Dekode ketat membuat pane 500 untuk data lama yang sah |
| **Komposisi `BuilderBillingPane()` di tab Tagihan** | Salin-tempel isi billing ke file baru | Aturan "re-host tanpa menulis ulang" (Aturan Tiga Kali); satu perubahan billing otomatis ikut | Dua salinan billing yang berbeda dalam sebulan |
| **Badge "menunggu" dari `BuilderConsoleBadges.handoffActiveCount`** | Hitung manual `status in listOf(...)` | Aturan beku §7.1 tinggal di SATU tempat di domain; klien dan test memakai fungsi yang sama | Definisi "pending" berbeda antara menu dan pane |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **Jebakan 1: Parser ad-hoc untuk respons audit**
   - *Kenapa bahaya*: `action` tak dikenal jatuh ke cabang `else` → baris audit tampil dengan label salah. Fitur kepercayaan berubah jadi fitur menyesatkan.
   - *Solusi elegan*: codec kontrak + test `decode_shouldGoThroughFrozenCodec_andRejectUnknownAction`.
2. **Jebakan 2: Karakter non-Latin-1 di literal UI**
   - *Kenapa bahaya*: font Nunito hanya punya glyph Latin-1; "—"/"→"/"•" tampil sebagai **kotak**. Test penjaga `UiTextLatin1GuardTest` memindai semua literal string di `presentation/` (komentar boleh).
   - *Solusi elegan*: ganti dengan ":" / "-" / kalimat biasa. Test ini yang menangkap 5 pelanggaran di draft T3 sebelum merge.
3. **Jebakan 3: `rememberSaveable` enum**
   - *Kenapa bahaya*: penyimpanan enum lintas 5 target KMP tidak konsisten (JS/WasmJS).
   - *Solusi elegan*: simpan **kunci string** (`tabKey`), resolve via `fromKey()` — pola route yang sama dengan `BuilderRouteMap`.
4. **Jebakan 4: `LazyColumn` di dalam shell yang sudah `verticalScroll`**
   - *Kenapa bahaya*: daftar bersarang ber-`weight(1f)` di induk ber-tinggi tak terbatas berukuran **nol** — ada di pohon, tak pernah terlihat (sudah pernah terjadi di pane Deployment).
   - *Solusi elegan*: `Column` biasa seperti pane sejawat; shell yang menggulir.
5. **Jebakan 5: Mengirim identitas tenant dari klien**
   - *Kenapa bahaya*: parameter `tenantId` di query = permukaan spoofing; satu lupa validasi = data lintas tenant bocor.
   - *Solusi elegan*: server menghitung tenant dari sesi; signature API klien bahkan tidak punya tempat untuk parameter itu.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Dua suite baru di `app/shared/src/commonTest/.../presentation/builder/` (12 test):

- **`BuilderAuditViewModelTest`** (fake remote, `StandardTestDispatcher`):
  - `load_shouldExposeEntriesChronologicallyAndPosture` — urutan server dipertahankan; default limit 50.
  - `filter_shouldNarrowByActionKindAndReset` — filter per jenis aksi + reset "Semua".
  - `auditFailure_shouldShowHonestErrorAndKeepPosture` — 403 tampil apa adanya, postur tetap hidup.
  - `postureFailure_shouldNotBreakAuditList` — kartu jujur "belum tersedia", daftar aman.
  - `decode_...RejectUnknownAction` — `aksi_ngarang` meledak sebagai `BuilderAuditDecodeException`.
  - `posture_shouldDeriveOnlyFromExistingOverviewPayload` — `liveModuleCount` absen → `null` (bukan 0).
- **`BuilderHandoffStateTest`**: parser membaca semua field, toleran baris lama, root tak dikenal → kosong, badge memakai aturan beku §7.1, status tak dikenal **tidak dipalsukan**, detail SUPERSEDED jujur.

Gerbang kompilasi: `compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs`, `jvmTest` hijau. `assembleAndroidMain` tidak bisa dijalankan di mesin ini (Android SDK tidak terpasang — batasan lingkungan yang sama dengan track sebelumnya; seluruh perubahan ada di `commonMain`/`commonTest` yang dibagikan kelima target).

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambahkan pengurutan "terbaru dulu" sebagai *pilihan* filter di pane Audit tanpa menyentuh urutan default server. Ingat: jangan sortir diam-diam — urutan kronologis adalah kontrak tampilan.
- [ ] **Tantangan 2**: Bila server kelak mengirim `liveModules` di payload overview, pastikan kartu Postur Security menampilkan jumlahnya tanpa perubahan kode klien — lalu tulis test yang membuktikan kedua bentuk payload (`liveModuleCount` vs `liveModules`) menghasilkan angka yang sama.
- [ ] **Tantangan 3**: Route lama `billing` dipetakan ke `setting/billing` oleh `BuilderRouteMap`. Ikuti alurnya dan jelaskan (atau perbaiki) bagaimana deep-link itu membuka tab Tagihan — petunjuk: `BuilderSettingTab.fromKey` dan parameter `initialTab`.
