# Teaching: Alur Kerja Deal Garmen — Dual-Tab Modal (Sampling vs Produksi Massal)

> Studi kasus implementasi `docs/plannings/planning-crm-deal-garment-workflow-and-detail-modal.md`:
> mengikat SPK Sampling ke Deal CRM (Golden Sample Lock), gerbang ACC multi-desain, dan
> redesign `DealDetailDialog` menjadi popup modal besar dual-tab.

---

## 1. "Start dari Mana?" — Urutan Menulis dari Nol

Pengerjaan ini mengikuti **dependency rule dari dalam ke luar**. Jangan pernah mulai dari UI —
UI adalah konsumen paling hulu; kalau domainnya berubah, semua kerja UI-mu basi.

```
1. Migrasi Flyway (V35)           ← skema dulu, supaya kolom sudah "ada" di dunia DB
2. core/domain/sampling           ← entity + value objects + domain events (murni, tanpa framework)
3. core/ use cases                ← CreateSamplingOrderFromDealUseCase, ApproveSamplingFromDealUseCase
4. core/shared codec              ← SamplingOrderCodec: kontrak wire yang dipakai server & client
5. server/ infrastructure         ← Exposed table + Postgres repo + InMemory test repo
6. server/ routes                 ← nested route di DealRoutes dengan RBAC CRM yang sudah ada
7. app/shared infrastructure      ← DealRemoteDataSource + DealApiClient
8. app/shared presentation        ← DealUiState (tab + gate) → DealViewModel → DealDetailDialog
9. Kompilasi 4 target + test      ← baru dipercaya
```

Mental model: **setiap lapisan hanya boleh memanggil lapisan yang lebih dalam**. Domain tidak tahu
apa-apa soal Ktor/Exposed/Compose; route mengetahui use case; ViewModel mengetahui data source;
composable hanya mengetahui state + event.

## 2. Bedah Kode Blok per Blok

### a. Entity tetap immutable — mutasi lewat fungsi domain

```kotlin
fun linkToDeal(dealId: String, updatedAt: Instant): SamplingOrder =
    if (this.dealId == dealId) this
    else copy(dealId = dealId, updatedAt = updatedAt)
```

Perhatikan **idempotency-nya**: re-link ke deal yang sama mengembalikan `this`, bukan salinan baru.
Ini melindungi `updatedAt` dari perubahan kosong (audit tetap jujur). Kalau kamu menulis
`copy(...)` tanpa guard, setiap save ulang akan menggeser timestamp dan mengacaukan urutan
`findByDealId` yang mengurutkan berdasarkan `updatedAt`.

`isActiveDesign` juga sengaja jadi *derived property* di entity, bukan logika di ViewModel:

```kotlin
val isActiveDesign: Boolean get() =
    status != SamplingStatus.ACC_APPROVED && status != SamplingStatus.CANCELLED
```

Kalau aturan "desain aktif" hidup di UI, maka Web, Android, Desktop, dan iOS bisa berbeda
interpretasinya. Di entity, aturan itu dihitung **tepat satu kali untuk semua platform**.

### b. Use case mengembalikan agregat, bukan boolean

`ApproveSamplingFromDealUseCase` mengembalikan `Result<List<SamplingOrder>>` — **seluruh daftar
sampling deal**, bukan cuma order yang di-ACC. Alasannya: keputusan gerbang Tab Produksi
("semua desain sudah ACC?") butuh gambaran penuh. Dengan mengembalikan daftar, route layer dan
client tidak butuh round-trip query kedua — dan use case tetap *satu operasi bisnis*.
### c. Gate rule di ViewModel, bukan di Composable

```kotlin
private fun selectDealTab(tab: DealDetailTab) {
    if (tab == DealDetailTab.MASS_PRODUCTION && !state.productionUnlocked) {
        _uiState.update { it.copy(statusMessage = "...") }
        return
    }
    _uiState.update { it.copy(activeTab = tab) }
}
```

Ini MVI yang disiplin: UI mengirim *intent* (`SelectDealTab`), ViewModel memutuskan. Composable
tidak pernah menulis `if (!unlocked) return` sendiri — kalau iya, gerbang bisa dibocori oleh satu
tombol baru yang lupa mengecek.

### d. Dialog besar, konten scrollable sendiri

```kotlin
Dialog(onDismissRequest = onDismiss,
       properties = DialogProperties(usePlatformDefaultWidth = false)) {
    ClayCard(modifier = Modifier
        .fillMaxWidth(0.92f).widthIn(max = 1150.dp).fillMaxHeight(0.88f),
        shape = ClayShapes.Panel, ...)
```

`usePlatformDefaultWidth = false` adalah kuncinya — tanpa itu, Compose memaksa dialog ke lebar
default platform dan semua angka `.fillMaxWidth(0.92f)` menjadi sia-sia. Struktur dalamnya
`Column { header (fixed) → tab switcher (fixed) → content .weight(1f).verticalScroll() }`:
header dan tab **tidak ikut menggulir**, hanya kontennya — persis perilaku modal profesional.

## 3. "The Why" — Teknologi & Pendekatan

| Keputusan | Kenapa | Risiko kalau cara lain |
|---|---|---|
## 4. Jebakan Pemula (Common Pitfalls)

1. **Melupakan `InMemorySamplingOrderRepository` di test source.** Menambah method ke interface
   repository mematahkan SEMUA implementasinya — termasuk fake di test. Compiler yang memberitahu;
   jangan pernah menambah method interface tanpa menjalankan kompilasi test.
2. **Menaruh gate "semua ACC" di UI.** Gerbang bisnis di UI = bisa di-bypass; gerbang di
   ViewModel = satu titik kebenaran; gerbang di use case/route = musnah total peluang bypass.
3. **`Dialog` tanpa `usePlatformDefaultWidth = false`** lalu bingung kenapa 1150dp tidak jalan.
4. **Emoji di string UI** (`🧪`, `🔒` dari mockup) — compile OK, render tofu di Wasm.
5. **Menghitung total DP dari FE tanpa sumber.** Di sini total diambil dari
   `purchaseOrders.sumOf { it.totalValueIdr }` (data PO tersimpan di server), bukan angka yang
   diketik user dan belum tentu tersimpan.

## 5. Verifikasi & Tantangan Mandiri

```bash
# Kompilasi lintas target (wajib, bukan satu)
./gradlew :core:compileKotlinJvm :app:shared:compileKotlinJvm \
          :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs \
          :app:shared:assembleAndroidMain :server:compileKotlin

# Test domain + presentation (server test butuh Postgres lokal berjalan)
./gradlew :core:jvmTest :app:shared:jvmTest
```

Manual: jalankan server + web, buka `/crm-sales` → klik kartu Deal → modal besar muncul →
tambah desain di Tab Sampling → isi resi → "Tandai ACC Desain Ini" per desain → saat semua ACC,
Tab Produksi terbuka otomatis dengan banner Golden Sample → "Terbitkan Invoice DP (50%)"
membuka Invoicing dengan prefill.

Tantangan: (1) tambahkan validasi server bahwa `sampleQuantity` 1–3 — di mana sebaiknya?
(bandingkan `init { require(...) }` pada command vs pengecekan route); (2) kunci Tab Produksi
**juga di sisi server** — rute PO baru menolak deal yang masih punya sampling aktif.

| Codec JSON shared (`SamplingOrderCodec`) | Server & client KMP memakai **satu** encoder/decoder | DTO terpisah → *drift* skema; field baru lupa di satu sisi = data hilang diam-diam |
| Nested route di `DealRoutes` (bukan `SamplingRoutes`) | Sampling-per-deal adalah **sub-resource** deal; authority-nya `CRM_SALES`, bukan `SAMPLING_ORDER` | Duplikasi RBAC (tenant → level → owner-reach) dan dua sumber kebenaran wewenang |
| Repository nullable di `dealRoutes(...)` | Route tetap compile saat modul sampling tak terpasang; respons `503` eksplisit | Hard dependency memaksa seluruh modul sampling ikut ter-deploy di setiap tenant |
| `ClayIcons` Canvas, bukan emoji | Skiko/Wasm tidak punya fallback font emoji OS → tofu `▯` | Mockup terlihat bagus di Figma, rusak di browser |
| Token `ClayShapes`/`ClaySpacing`/`WeMadeColors` | Satu perubahan design token = seluruh modal ikut | Literal warna menyebar; dark mode mustahil dikerjakan |

