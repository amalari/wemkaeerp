# 🎓 Modul Pembelajaran: Tampilan Proposal Layar (Alasan, Sumber, dan Jalur Aman)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, UI State Parsing, Claymorphism Design System, Backward Compatibility, Fail-Safe Rendering  
> **Prasyarat**: Memahami arsitektur KMP (Kotlin Multiplatform), declarative UI (Jetpack/Compose Multiplatform), dan kontrak domain DDD  
> **Referensi Task**: [PLAN-sp-A-ui.md](file:///Volumes/amalari/Projects/wemkaeerp/docs/plannings/parallel3/PLAN-sp-A-ui.md) (Jalur A: A0–A7)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan kamu sedang mempresentasikan rancangan software kustom (ERP) ke calon klien pemilik klinik atau katering. Sistem secara otomatis menyusun usulan modul dan layar antarmuka (misal: "Papan Antrean Pasien" dalam bentuk KANBAN, atau "Perencanaan Menu" dalam bentuk TABEL).

### Masalah Nyata:
1. **Kurangnya Kepercayaan & Konteks ("Kenapa Layar Ini Muncul Begini?")**:
   Sebelumnya, klien hanya melihat layar bertuliskan judul umum dengan label "contoh 1". Klien tidak tahu *mengapa* sistem memilih kanban alih-alih tabel, dan *dari mana* usulan itu berasal (apakah dari konfigurasi resmi industri, kalkulasi deterministik, atau hasil inferensi AI).
2. **Kerapuhan Ketika Data Usulan Tidak Lengkap**:
   Jika usulan layar yang dihasilkan model AI atau sistem deterministik tidak memiliki tata letak interaktif (misal: widget belum punya renderer atau entitasnya belum lengkap), antarmuka rentan menampilkan kotak kosong yang rusak atau melempar crash di perangkat klien.
3. **Penyalinan Kode Visual (Pelanggaran Aturan Tiga Kali)**:
   Saat menampilkan lencana sumber di beberapa tempat (kartu prototype, wizard draf, langkah konfirmasi), mudah sekali terjebak menduplikasi warna literal `Color(0xFF...)` atau menyalin blok tata letak secara serampangan.

### Mental Model & Solusi Elegan:
- **Provenance (Asal-Usul Data yang Jelas)**: Setiap layar membawa lencana sumber yang jujur: `Pack` (dari Domain Pack resmi), `Deterministik` (dari kalkulasi aturan platform), atau `Agent · <model>` (dari agen AI seperti DeepSeek/Claude).
- **Rationale yang Transparan**: Baris alasan bahasa pemilik usaha (*"Dipilih karena antrean pasien bergerak dari registrasi ke poli dan kasir"*) diposisikan rapi di header layar tanpa mengganggu blok kerja interaktif.
- **Fail-Safe Graceful Degradation**: Bila interactive block tidak tersedia, sistem tidak pernah crash atau menyembunyikan layar diam-diam; melainkan menampilkan kartu peringatan informatif yang ramah prospek (`ProposalIncompleteWarning`).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta mengimplementasikan fitur ini dari layar kosong, ikuti urutan berikut:

1. **Langkah 0: Telusuri Kontrak Domain & Kode Server (Baca-Saja)**
   - Jangan sentuh `core` atau `server` jika tugasmu adalah presentation layer (patuhi boundaries).
   - Periksa tipe kontrak domain yang sudah tersedia: [ScreenProposal](file:///Volumes/amalari/Projects/wemkaeerp/core/src/commonMain/kotlin/com/eventverse/app/domain/discovery/proposal/ScreenProposal.kt), [ProposalSource](file:///Volumes/amalari/Projects/wemkaeerp/core/src/commonMain/kotlin/com/eventverse/app/domain/discovery/proposal/ScreenProposal.kt#L66), dan codec-nya.
2. **Langkah 1: Perluas Model Tampilan Klien ([DiscoveryUiModel.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DiscoveryUiModel.kt))**
   - Tambahkan field `proposal`, `rationale`, dan `source` ke `DiscoveryScreenUi` dengan nilai default `null` agar 100% kompatibel mundur dengan draf lama.
   - Perbarui parser `DiscoveryDraftUi.fromJson` untuk membaca `screens[i].proposal`, `source`, dan `rationale`.
3. **Langkah 2: Uji Parsing dengan Unit Test Murni**
   - Buat unit test ([DiscoveryUiModelParseTest.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/jvmTest/kotlin/com/eventverse/app/presentation/discovery/DiscoveryUiModelParseTest.kt)) untuk memastikan draf lama tanpa proposal tetap terbaca identik, dan draf baru memetakan Pack, Deterministik, serta Agent secara tepat.
4. **Langkah 3: Bangun Komponen Reusable Design System ([ProposalUiComponents.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/ProposalUiComponents.kt))**
   - Angkat komponen visual (lencana sumber, baris alasan, kartu peringatan aman, kartu ringkasan proposal wizard) ke satu file modular.
   - Pastikan **nol literal warna**: semua warna diambil dari semantik `WeMadeColors` (`Primary`, `Teal`, `Purple`, `Warning`, dll.).
5. **Langkah 4: Integrasikan ke Renderer Kartu Prototype ([PrototypeRenderer.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/PrototypeRenderer.kt))**
   - Sematkan lencana sumber dan baris alasan ke header kartu.
   - Gantilah pesan teks mentah lama dengan `ProposalIncompleteWarning` ketika baris dan blok interaktif kosong.
   - Jaga agar baris file tidak membengkak di atas soft limit (400 baris).
6. **Langkah 5: Perkaya Langkah Wizard Sebelum Kunci ([DiscoveryWizardSteps.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DiscoveryWizardSteps.kt))**
   - Di langkah peninjauan akhir sebelum draf dikunci, sajikan daftar ringkasan proposal per modul agar prospek dapat meninjau alur sebelum mengirim permintaan pembangunan.
7. **Langkah 6: Tangani Interaksi Chat Spec ([PrototypeChatEditPanel.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/PrototypeChatEditPanel.kt))**
   - Sediakan chip saran cepat dan pesan penolakan yang ramah jika perubahan jenis widget (misal: ke kanban tanpa status enum) diminta oleh pengguna.
8. **Langkah 7: Verifikasi Multi-Target & Audit Variabilitas**
   - Jalankan test JVM (`./gradlew :app:shared:jvmTest`).
   - Kompilasi target multiplatform (`compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs`).
   - Jalankan skrip audit variabilitas (`./scripts/audit-variability.sh`).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Ekstensi UI Model & Parsing Kompatibel Mundur
Di [`DiscoveryUiModel.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DiscoveryUiModel.kt):

```kotlin
data class DiscoveryScreenUi(
    val screenId: String,
    val moduleId: String,
    val title: String,
    val widget: String,
    val sampleRows: List<Map<String, String>>,
    val interactive: InteractiveScreen? = null,
    val proposal: ScreenProposal? = null,
    val rationale: String? = null,
    val source: ProposalSource? = null
)
```

**Mengapa blok ini ditulis begini?**
- Parameter baru ditempatkan di posisi akhir dengan nilai default `= null`. Kode lama mana pun yang menginstansiasi `DiscoveryScreenUi(screenId, moduleId, title, widget, sampleRows, interactive)` tetap terkompilasi dan berjalan normal tanpa perubahan perilaku.
- Parsing di `DiscoveryDraftUi.fromJson` membaca `proposal` menggunakan `ScreenProposalCodec.decode`, dan memvalidasi `source` baik berupa objek JSON berstruktur (`{"kind": "AGENT", "agentRef": "..."}`) maupun string fallback (`"PACK"` / `"DETERMINISTIC"`).

---

### Blok B: Komponen Lencana Sumber & Alasan yang Murni Token
Di [`ProposalUiComponents.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/ProposalUiComponents.kt):

```kotlin
@Composable
fun ProposalSourceBadge(
    source: ProposalSource,
    modifier: Modifier = Modifier
) {
    val tint = when (source) {
        is ProposalSource.Pack -> WeMadeColors.Primary
        is ProposalSource.Deterministic -> WeMadeColors.Teal
        is ProposalSource.Agent -> WeMadeColors.Purple
    }
    ClayTag(
        text = source.displayName,
        tint = tint,
        modifier = modifier
    )
}
```

**Mengapa blok ini ditulis begini?**
- Komponen menggunakan `ClayTag` yang memiliki radius bersudut kompak (`ClayShapes.Chip`), sehingga muat berdampingan dengan nama modul di layar selebar 360dp tanpa memaksa teks judul turun baris secara tidak wajar.
- **Nol Literal Warna**: `WeMadeColors.Primary` (Biru), `WeMadeColors.Teal` (Hijau-Biru), dan `WeMadeColors.Purple` (Ungu) adalah token tema brand platform yang sah dan konsisten.

---

### Blok C: Graceful Degradation / Safe Incomplete Warning
Di [`ProposalUiComponents.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/ProposalUiComponents.kt) & [`PrototypeRenderer.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/PrototypeRenderer.kt):

```kotlin
// Layar berbinding Api tak punya baris contoh (datanya dari server) tetapi punya blok interaktif.
if (rows.isEmpty() && block == null) {
    ProposalIncompleteWarning(
        widget = widget,
        rationale = rationale,
        modifier = Modifier.padding(top = ClaySpacing.Sm)
    )
    return@ClayCard
}
```

**Mengapa blok ini ditulis begini?**
- Jika baris data kosong dan blok interaktif belum dapat dibentuk (misal: widget tipe `PRINT` atau `CUSTOM_SCREEN`), renderer tidak membiarkan kartu kosong tanpa konteks.
- `ProposalIncompleteWarning` menjelaskan secara sopan bahwa modul ini berjenis widget `$widget` dan masih dalam perancangan, sambil tetap menampilkan alasan awal (`rationale`) agar klien mengerti tujuan fungsional modul tersebut.

---

### Blok D: Tinjauan Proposal Sebelum Kunci di Wizard
Di [`DiscoveryWizardSteps.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DiscoveryWizardSteps.kt):

```kotlin
if (draft != null && draft.screens.isNotEmpty()) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Text("Tinjauan Usulan Antarmuka", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        // ...
        draft.screens.forEach { screen ->
            val module = draft.modules.firstOrNull { it.id == screen.moduleId }
            ModuleProposalSummaryCard(
                screen = screen,
                moduleName = module?.displayName ?: screen.moduleId
            )
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Sebelum prospek memasukkan nama perusahaan dan mengklik tombol "Kunci & Bangun Sistem Ini", mereka disajikan ringkasan visual dari semua modul yang diusulkan.
- Setiap modul memaparkan jenis tampilan, sumber usulan (Pack/Deterministik/Agent), alasan pemilihan, serta ringkasan skema entitas (jumlah field dan diagram alur status seperti `Menunggu → Pemeriksaan → Selesai`).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan / Keputusan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Ekstraksi Komponen ke `ProposalUiComponents.kt`** | Menulis composable langsung di `PrototypeRenderer.kt` | Menjaga batas ukuran file presentation (< 400 baris) dan menerapkan *Aturan Tiga Kali* untuk penggunaan ulang di wizard dan prototype. | `PrototypeRenderer.kt` akan melebihi 400 baris dan sulit direview (God Composable). |
| **Parsing `source` dengan Fallback String & Objek** | Hanya mengizinkan satu format objek JSON kaku | Menjaga ketahanan klien saat menerima payload dari server versi lama, uji coba mock, atau codec v1. | UI crash / `JsonException` saat membaca format respons yang berbeda tipis. |
| **Pemotongan Teks `maxLines = 2` + `TextOverflow.Ellipsis`** | Menampilkan seluruh teks `rationale` tanpa batas | Mempertahankan ergonomi kartu berukuran 360dp agar konten formulir / kanban tidak terdorong keluar layar. | Layout kartu jebol atau scrolling bertingkat yang membingungkan pengguna mobile. |
| **Pemisahan Logika Konversi ke Server** | Mengonversi `proposal` ke `InteractiveScreen` di klien | Mematuhi kontrak Jalur A: klien murni bertanggung jawab menggambar data tampilan, bukan menjalankan mesin logika domain. | Duplikasi algoritma konversi antara Ktor server dan Compose client. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Memakai Warna Literal `Color(0xFF...)`**
   - *Kenapa bahaya*: Melanggar kontrak design system WeMade ERP. Tema gelap atau penyesuaian brand di masa depan akan menyebabkan warna "bocor" atau tidak terbaca.
   - *Solusi kita*: Gunakan selalu token semantik resmi dari `WeMadeColors` (misal: `WeMadeColors.Primary`, `WeMadeColors.Teal`, `WeMadeColors.Purple`).
2. **Jebakan 2: Memodifikasi File di Luar Scope (Boundary Violation)**
   - *Kenapa bahaya*: Menyunting file di `core/**` atau `server/**` saat memegang peran Agent A akan memicu konflik git yang parah dengan agen paralel lain (Agent B dan C).
   - *Solusi kita*: Disiplin pada direktori kepemilikan: `app/shared/**/presentation/**` dan unit test terkait.
3. **Jebakan 3: Mengabaikan Kompatibilitas Mundur Draf Lama**
   - *Kenapa bahaya*: Banyak draf di database yang dibuat sebelum adanya kolom `proposal` dan `source`. Jika parser mewajibkan kunci tersebut, seluruh draf lama akan gagal dimuat (500 atau NullPointerException di UI).
   - *Solusi kita*: Nilai default `null` dan operator safe-call / fallback yang mulus.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan dalam dua lapisan:

### 1. Unit Test Murni ([DiscoveryUiModelParseTest.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/jvmTest/kotlin/com/eventverse/app/presentation/discovery/DiscoveryUiModelParseTest.kt))
- Menguji parse draf lama tanpa proposal: memastikan `proposal`, `rationale`, dan `source` bernilai `null` tanpa galat.
- Menguji parse draf berusulan `Pack`, `Deterministic`, dan `Agent` (lengkap dengan nama model LLM).
- Menguji fallback string source (misal: `"AGENT:koog/claude-3-7-sonnet"`).

### 2. Multi-Target Compilation & Audit
- Menjalankan `./gradlew :app:shared:jvmTest` — memastikan 100% test hijau.
- Menjalankan kompilasi lintas platform (`:app:shared:compileKotlinJvm`, `:app:shared:compileKotlinWasmJs`, `:app:shared:compileKotlinJs`).
- Menjalankan `./scripts/audit-variability.sh` — memastikan 0 temuan variabilitas baru.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka `PrototypeScreenCard` di browser atau pratinjau desktop. Coba ubah ukuran kontainer menjadi 320dp dan pastikan judul, lencana sumber, dan lencana jenis widget tidak saling tumpang tindih.
- [ ] **Tantangan 2**: Buat mock draf baru yang memiliki `proposal` berjenis `CHECKLIST` dan periksa bagaimana `ModuleProposalSummaryCard` menampilkan ringkasan butir periksa pada wizard konfirmasi.
