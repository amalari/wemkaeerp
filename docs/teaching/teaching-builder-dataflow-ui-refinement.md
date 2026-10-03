# 🎓 Modul Pembelajaran: Penataan Ulang UI Data Flow Builder (Matriks Sambungan & Grid Modul)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Claymorphism Design System, Layout Decomposition, UI Responsiveness  
> **Prasyarat**: Dasar Jetpack Compose / Compose Multiplatform, Pemahaman Token Desain & Claymorphism, Single Responsibility Principle  
> **Referensi Task**: UI Refinement untuk `/builder/dataflow`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada arsitektur alur manufaktur dan bisnis (seperti platform konveksi/garmen WeMake), sistem tersusun atas puluhan modul yang saling mengalirkan data (misal: *PO* menghasilkan data spesifikasi yang dialirkan ke *Sampling*, lalu diteruskan ke *BOM & Tech Pack*, lalu ke *HPP* dan *Cutting*).

Ketika kita menyajikan peta aliran data antar modul di layar konsol Builder:
1. Jika setiap baris sambungan hanya dirender apa adanya tanpa bobot kolom tetap (`weight`), lebar label modul yang dinamis akan membuat panah dan payload **melompat-lompat ke kiri dan kanan (zig-zag)**. Akibatnya, user pusing menelusuri alur.
2. Jika kartu modul membentang penuh 100% lebar layar desktop sementara kontennya hanya memakan separuh lebar, terjadi **pemborosan ruang horizontal hingga 50%**.
3. Jika port masuk dan keluar dicetak berulang-ulang dengan teks `"Masuk"` dan `"Keluar"` per baris, tampilan menjadi mirip *log server mentah* daripada dashboard enterprise yang profesional.

### Analogi Sederhana
Bayangkan sebuah **jadwal penerbangan bandara**:
- Jika nama kota asal, nomor penerbangan, dan kota tujuan dicetak tanpa tabel kolom yang rata, penumpang akan kesulitan mencari rute pesawatnya.
- Sebaliknya, dengan **3 kolom berbobot tetap** (Asal | Nomor Pesawat | Tujuan), mata manusia dapat memindai puluhan baris secara vertikal dalam hitungan detik.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus menata ulang UI halaman kompleks seperti ini dari awal, mulailah dengan urutan berikut:

1. **Step 0 — Inspeksi Visual Langsung (Visual First)**: Buka halaman di browser atau tangkap screenshot untuk menemukan cacat visual (*clutter*, *alignment*, *wasted space*).
2. **Step 1 — Konsistensi Header & KPI Summary**: Selaraskan header dengan halaman tetangga (tab `Modules`) agar user merasakan bahasa visual yang seragam, lalu hadirkan kartu metrik ringkas (*Stat Cards*).
3. **Step 2 — Kolom Berbobot Tetap pada Matriks Aliran (`HandoffMatrix`)**: Ganti layout fleksibel acak dengan pembagian bobot proporsional (misal: `35% Sumber | 30% Payload | 35% Tujuan`).
4. **Step 3 — Redesain Kartu Item (Split In/Out Flow)**: Pisahkan masukan dan keluaran secara internal menjadi dua kolom, hindari repetisi label teks per baris.
5. **Step 4 — Filter Departemen & Grid Responsif**: Sediakan filter chip (`ClayChoiceChip`) dan bungkus dengan `BoxWithConstraints` untuk menghasilkan grid 2-kolom pada layar desktop lebar.
6. **Step 5 — Dekomposisi File (Rule §14 File Size Limit)**: Pecah komponen menjadi berkas terpisah berdasarkan tanggung jawab sebelum melanggar batas baris kode.

---

## 🔍 3. Pembedahan Kode & Mental Model Per Lapisan

### A. Matriks Sambungan Port (`DataFlowHandoffMatrix.kt`)
Untuk memastikan panah dan tag payload berada tepat di tengah tanpa zig-zag, kita menggunakan bobot persentase pada `Row`:

```kotlin
@Composable
private fun HandoffMatrixRow(handoff: PortHandoff, draft: DiscoveryDraftUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kolom 1: 35% Lebar Rata Kiri (Modul Sumber)
        Box(modifier = Modifier.weight(0.35f), contentAlignment = Alignment.CenterStart) {
            ClayBadge(
                text = handoff.from?.displayName ?: "Luar Sistem",
                tint = handoff.from?.let { sectionTint(it, draft) } ?: WeMadeColors.OnSurfaceMuted,
                fontSize = 10.sp
            )
        }

        // Kolom 2: 30% Lebar Rata Tengah (Payload Kontrak Port)
        Row(
            modifier = Modifier.weight(0.30f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconArrowForward(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
            Spacer(Modifier.size(4.dp))
            ClayTag(
                text = if (handoff.isReference) "${handoff.payloadLabel} · acuan" else handoff.payloadLabel,
                tint = WeMadeColors.OnSurface
            )
            Spacer(Modifier.size(4.dp))
            IconArrowForward(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
        }

        // Kolom 3: 35% Lebar Rata Kanan (Modul Tujuan)
        Box(modifier = Modifier.weight(0.35f), contentAlignment = Alignment.CenterEnd) {
            ClayBadge(
                text = handoff.to?.displayName ?: "Keluaran Akhir",
                tint = handoff.to?.let { sectionTint(it, draft) } ?: WeMadeColors.OnSurfaceMuted,
                fontSize = 10.sp
            )
        }
    }
}
```

*Mental Model*: Jangan biarkan elemen teks mengatur lebarnya sendiri ketika berada di dalam baris daftar. Gunakan `Modifier.weight()` pada parent dan `contentAlignment` pada child `Box` untuk mengunci posisi tabel.

### B. Split In/Out Flow pada Kartu Modul (`DataFlowModuleCard.kt`)
Daripada mengulang kata `"Masuk"` dan `"Keluar"` di setiap baris, kartu dibagi menjadi 2 kolom mandiri:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
) {
    // Kolom Kiri: Port Masukan
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("MASUKAN (${flow.incoming.size})", style = typography.bodySmall, fontWeight = FontWeight.Bold)
        flow.incoming.forEach { handoff ->
            FlowItemPill(
                payload = handoff.payloadLabel,
                isReference = handoff.isReference,
                directionLabel = "dari",
                counterpart = handoff.from?.displayName ?: "Luar Sistem",
                counterpartTint = ...
            )
        }
    }

    // Kolom Kanan: Port Keluaran
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("KELUARAN (${flow.outgoing.size})", style = typography.bodySmall, fontWeight = FontWeight.Bold)
        flow.outgoing.forEach { handoff ->
            FlowItemPill(
                payload = handoff.payloadLabel,
                isReference = false,
                directionLabel = "ke",
                counterpart = handoff.to?.displayName ?: "Keluaran Akhir",
                counterpartTint = ...
            )
        }
    }
}
```

### C. Dekomposisi File Mengikuti Aturan Proyek (Rule §14)
Di project ini, aturan hard limit Compose presentation adalah 600 baris, dengan soft limit 400 baris.
Sebelum kode membengkak:
- [DataFlowPane.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DataFlowPane.kt) (223 baris): Shell pane utama, 4 KPI cards, filter bar, dan grid responsif.
- [DataFlowHandoffMatrix.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DataFlowHandoffMatrix.kt) (169 baris): Khusus komponen visual matriks sambungan port.
- [DataFlowModuleCard.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/DataFlowModuleCard.kt) (216 baris): Khusus kartu modul dan pill serah terima data.

Semua file kini ringkas, fokus pada satu tanggung jawab, dan mudah di-maintain.

---

## 🛡️ 4. Jebakan Umum (Common Pitfalls & Gotchas)

1. **Jebakan `ClayFlowRow` vs `FlowRow` Material**:
   - `ClayFlowRow` di project WeMade membungkus `spacing: Dp`, bukan `horizontalArrangement = Arrangement.spacedBy(...)`. Jangan salah memasukkan parameter layout Material mentah ke dalam komponen wrapper design system.
2. **Hardcode Warna Departemen**:
   - Dilarang keras menulis `if (section == "SALES") Color.Blue`. Gunakan selalu `resolveSectionStyle(section, draft)` agar kompatibel dengan Domain Pack industri lain (Klinik, Farmasi, Restoran) tanpa mengubah satu baris pun kode UI.
3. **Z-Fighting dan Border Clutter**:
   - Jangan menumpuk badge neo-brutalis tebal dengan jarak `2dp` atau `4dp` tanpa garis pembatas atau latar kontras. Berikan padding yang cukup (`ClaySpacing.Sm` s/d `ClaySpacing.Md`) agar bayangan dan outline tidak saling tumpang tindih.

---

## ✅ 5. Checklist Verifikasi Mandiri

- [x] Header konsisten dengan tab `Modules` (nama blueprint, deskripsi peran, dan badge status).
- [x] Matriks sambungan port memiliki kolom tetap (Sumber 35%, Payload 30%, Tujuan 35%).
- [x] Kartu modul menggunakan pembagian seksi internal Masukan vs Keluaran.
- [x] Tersedia filter departemen (`ClayChoiceChip`) yang responsif.
- [x] Tata letak kartu menggunakan Grid 2 Kolom pada desktop lebar (`maxWidth >= 860.dp`).
- [x] Seluruh file presentation berada jauh di bawah batas 400 baris.
- [x] Kompilasi multiplatform (`:core:compileKotlinJvm` dan `:app:shared:compileKotlinJvm`) lulus hijau.
