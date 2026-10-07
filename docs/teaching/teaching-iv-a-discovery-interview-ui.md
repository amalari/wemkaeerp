# 🎓 Modul Pembelajaran: Wawancara Studio Discovery di UI (Agent A)

> **Level Target**: Junior to Mid Multiplatform UI Developer  
> **Topik Utama**: Compose Multiplatform, Claymorphism Design System, Wizard State Management, Fail-Closed UI Fallback, Latin-1 Typography  
> **Prasyarat**: Pemahaman dasar Compose state (`remember`, `mutableStateOf`, `mutableStateListOf`), arsitektur wizard funnel, dan token design system WeMade.  
> **Referensi Task**: [PLAN-iv-A-ui.md](../../docs/plannings/parallel4/PLAN-iv-A-ui.md) / [PLAN-discovery-interview-role-module](../../docs/plannings/PLAN-discovery-interview-role-module.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat pemilik usaha (calon klien ERP) menceritakan proses bisnisnya, sistem sering kali langsung menyodorkan konfigurasi teknis yang rumit atau langsung melompat ke draf modul yang tidak bisa dikoreksi. Jika pengguna tidak diberikan kesempatan memvalidasi:
1. Asumsi divisi dan peran sering meleset (misal kasir disamakan dengan staf keuangan).
2. Modul dirakit tanpa kejelasan asal-usul (apakah ini modul bawaan platform, pack, modifikasi, atau modul baru).
3. Prospek merasa terintimidasi oleh formulir panjang yang kaku atau crash ketika koneksi server gagal.

### Analogi Sederhana
Wawancara discovery di UI ini seperti **konsultan bisnis berpengalaman yang mencatat sambil mengonfirmasi ulang**:
- *"Pak, dari cerita Anda, kami menangkap ada 3 bagian utama: Pendaftaran, Poli, dan Kasir. Apakah betul demikian?"*
- Klien cukup mencentang *"Benar"*, mengganti nama istilah internal mereka, atau menambah yang terlewat.
- Bila klien sedang terburu-buru, ada tombol *"Terima Semua Tebakan"* untuk langsung melompat ke ringkasan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun fitur wizard interaktif seperti ini dari nol:

```
[Langkah 1: Parsing UI Data Model]
  └─ DiscoveryUiModel.kt (Baca JSON server secara aman, parsing interview, nextQuestion, origin; fallback null bila draf lama)
       ↓
[Langkah 2: State Holder & Mutasi Interaktif]
  └─ InterviewSessionState.kt (Penyimpan state lokal divisi, peran, modul, fitur, sambungan, jejak giliran)
       ↓
[Langkah 3: Dekomposisi Komponen per Giliran (G1 - G5)]
  ├─ InterviewTurnHeader.kt (Indikator n/8, tombol terima semua, tombol lewati)
  ├─ StepInterviewG1Divisions.kt (Konfirmasi & edit divisi)
  ├─ StepInterviewG2Roles.kt (Konfirmasi peran & penunjukan kepala divisi)
  ├─ StepInterviewG3Modules.kt (Lencana asal modul & kelola fitur)
  ├─ StepInterviewG4Handoffs.kt (Sambungan serah-terima antarmodul)
  ├─ StepInterviewConsultantCard.kt (Saran konsultan F0-F2)
  └─ StepInterviewG5Summary.kt (Ringkasan 1 halaman & tombol kembali ke giliran)
       ↓
[Langkah 4: Orkestrasi Panel Utama]
  └─ DiscoveryInterviewPane.kt (Merakit langkah aktif & menyediakan jalur aman bila ada galat)
       ↓
[Langkah 5: Penyelipan ke Wizard Funnel]
  └─ DiscoveryWizardScreen.kt (Menempatkan Wawancara di langkah 2 antara Narasi dan Draf)
       ↓
[Langkah 6: Pengujian Unit & Regresi Tipografi]
  ├─ DiscoveryUiModelParseTest.kt (Uji draf lama vs draf baru)
  └─ InterviewSessionStateTest.kt (Uji mutasi state & kepatuhan Latin-1)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Toleransi & Ketahanan Parsing (`DiscoveryUiModel.kt`)

```kotlin
val interviewObj = o.obj("interview")
val parsedInterview = interviewObj?.let { obj ->
    runCatching {
        val stepStr = obj.string("step").orEmpty()
        val step = InterviewStep.fromCode(stepStr) ?: InterviewStep.G1_DIVISI
        // ... mapping divisions, roles, links, handoffs, answers
        InterviewSession(step, divisions, roles, links, handoffs, answers)
    }.getOrNull()
}
```

**Mengapa ditulis begini?**
- `runCatching { ... }.getOrNull()` menjamin bahwa jika server mengirim JSON yang rusak atau tidak lengkap, layar klien tidak akan melempar unhandled exception / crash.
- Draf lama yang belum memiliki sesi wawancara menghasilkan `interview = null`, sehingga wizard otomatis melewati langkah wawancara tanpa merusak draf yang sudah ada.

---

### Blok B: Penegakan Invarian Kepala Divisi (`InterviewSessionState.kt`)

```kotlin
fun toggleRoleHead(roleKey: RoleKey) {
    val r = roles.firstOrNull { it.roleKey == roleKey } ?: return
    val targetHead = !r.isHead
    if (targetHead) {
        setHeadOfDivision(r.divisionCode, roleKey)
    } else {
        val idx = roles.indexOfFirst { it.roleKey == roleKey }
        if (idx >= 0) roles[idx] = r.copy(isHead = false)
    }
}

private fun setHeadOfDivision(divCode: DivisionCode, headKey: RoleKey?) {
    for (i in roles.indices) {
        if (roles[i].divisionCode == divCode) {
            roles[i] = roles[i].copy(isHead = roles[i].roleKey == headKey)
        }
    }
}
```

**Mengapa ditulis begini?**
- Invarian domain menyatakan bahwa di satu divisi, paling banyak hanya ada **satu kepala divisi**.
- Saat pengguna menandai peran A sebagai kepala divisi, `setHeadOfDivision` otomatis mencopot tanda kepala dari peran lain di divisi yang sama tanpa perlu intervensi manual yang membingungkan.

---

### Blok C: Lencana Asal Modul & Fleksibilitas Layout (`StepInterviewG3Modules.kt`)

```kotlin
val originColor = when (link.origin) {
    ModuleOrigin.REUSE_PLATFORM -> WeMadeColors.Primary
    ModuleOrigin.REUSE_PACK -> WeMadeColors.Success
    ModuleOrigin.EXTEND -> WeMadeColors.Warning
    ModuleOrigin.NEW -> WeMadeColors.Accent
}

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f, fill = false)) {
        Text("Peran: ${role?.label ?: link.roleKey.value}", fontWeight = FontWeight.Bold)
        Text("Modul: ${link.moduleId.value}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        ClayBadge(text = link.origin.displayName, tint = originColor)
        link.confidence?.let { conf ->
            ClayBadge(text = "Yakin $conf%", tint = WeMadeColors.OnSurfaceMuted)
        }
    }
}
```

**Mengapa ditulis begini?**
- Penggunaan `modifier.weight(1f, fill = false)` mencegah badge asal menyempit dan patah huruf per huruf (character wrapping) saat dibuka pada layar sempit.
- Setiap asal modul memiliki warna semantik dari `WeMadeColors`:
  - `REUSE_PLATFORM`: Biru (`Primary`)
  - `REUSE_PACK`: Hijau (`Success`)
  - `EXTEND`: Kuning/Amber (`Warning`)
  - `NEW`: Oranye (`Accent`)

### 3.3 Integrasi B7: Prinsip "Berdasar Cerita" (`BasisRef`) & Profil Konsultan
Pada tahap B7, wawancara berevolusi menjadi versi 2 (`BASED_ON_STORY`). Prinsip utamanya: **setiap usulan sistem harus bisa ditelusuri ke cerita atau jawaban pengguna**:
- `Basis.NARASI`: Mengutip kalimat langsung dari narasi (`BasisRef.quote`).
- `Basis.JAWABAN`: Berasal dari jawaban pengguna di giliran wawancara (`BasisRef.answerId`).
- `Basis.SARAN_DITERIMA`: Usulan konsultan yang disetujui pengguna (`sug_qc`, dll.).
- `BusinessProfile` & `RequirementSpec`: Menyimpan ringkasan profil usaha, sasaran operasional, titik sakit, dan spesifikasi per area kerja (siapa mengisi, apa dicatat, siapa melihat, kapan selesai).

Di UI (`StepInterviewG5Summary.kt`), dasar ini dirender secara dinamis di bawah tiap modul:
```kotlin
val ref = link.basisRef
val basisText = when (ref?.basis) {
    Basis.NARASI -> "Dasar: Kutipan cerita \"${ref.quote.orEmpty()}\""
    Basis.JAWABAN -> "Dasar: Jawaban Anda pada pertanyaan wawancara (${ref.answerId ?: "wawancara"})"
    Basis.SARAN_DITERIMA -> "Dasar: Saran konsultan yang Anda terima"
    Basis.SARAN_BELUM_DIJAWAB -> "Dasar: Saran konsultan (belum dikonfirmasi)"
    null -> "Dasar: Terhubung dari narasi kebutuhan dan peran operasional Anda"
}
```

### 3.4 Layar Percakapan Konsultan F0-F2 (`StepInterviewF0F1Profile.kt` & `StepInterviewF2Specs.kt`)
Untuk mendukung jalur percakapan konsultan mendalam (F0-F2) sebelum masuk ke struktur modul teknis (G1-G5), kami membangun layar interaktif terdedikasi:
1. **F0 Profil Usaha & Model Bisnis (`StepInterviewF0Bisnis`)**:
   - Menampilkan formulir ringkasan model bisnis (`businessSummary`) dengan badge model (misal FOB/CMT/D2C).
   - Pengguna dapat menyunting ringkasan secara langsung di kartu Claymorphism.
2. **F1 Sasaran & Titik Sakit Operasional (`StepInterviewF1Tujuan`)**:
   - Memisahkan dua daftar dinamis: **Sasaran Operasional** (Goal) dan **Titik Sakit/Kendala** (Pain Point).
   - Dilengkapi input cepat (`OutlinedTextField` + tombol `+ Tambah`) serta tombol hapus per butir.
3. **F2 Spesifikasi Area Kebutuhan (`StepInterviewF2Spek`)**:
   - Mengelola `RequirementSpec` per area kerja (misalnya: Gudang Bahan, Lantai Potong, Jahit).
   - Memetakan 4 dimensi operasional utama:
     - `whoFills`: Siapa yang mencatat data di lapangan.
     - `whatRecorded`: Apa informasi/transaksi yang dicatat.
     - `whoSees`: Siapa yang membutuhkan dan memantau datanya.
     - `doneWhen`: Kriteria selesai / serah terima pekerjaan.
   - Dilengkapi dialog modal pop-up clay untuk menambah atau mengedit spesifikasi area secara mendalam.

**Pemecahan File Sesuai Batas Ukuran (Aturan 14)**:
Awalnya implementasi F0, F1, dan F2 digabung dalam satu file `StepInterviewConsultantPhases.kt` yang mencapai 452 baris (melebihi batas lunak 400 baris). Kami memecahnya secara elegan menurut batas tanggung jawab:
- `StepInterviewF0F1Profile.kt` (270 baris): Bertanggung jawab pada profil usaha, sasaran, dan kendala (`BusinessProfile`).
- `StepInterviewF2Specs.kt` (205 baris): Bertanggung jawab penuh pada spesifikasi area kerja (`RequirementSpec`).
Keduanya tetap jauh di bawah batas 400 baris dan mudah dipelihara!


---

## 🛡️ 4. Jebakan Umum yang Dihindari

1. **Jebakan Glyph Nunito (Font Missing Glyphs)**:
   - Font Nunito tidak memiliki glyph panah non-ASCII (`→`) atau karakter kotak (`▯`).
   - Kode dilarang keras menggunakan simbol UTF non-ASCII. Digunakan representasi ASCII standar (`->`, `x`, `+`) agar teks tidak berubah menjadi kotak tanda tanya di browser.
2. **Jebakan God File di Presentation**:
   - Menaruh kelima giliran wawancara dalam satu file layar akan membuat panjang file membengkak di atas 1000 baris.
   - Dengan memecah menjadi `StepInterviewG1Divisions.kt`, `StepInterviewG2Roles.kt`, `StepInterviewG3Modules.kt`, `StepInterviewG4Handoffs.kt`, dan `StepInterviewG5Summary.kt`, setiap file rata-rata hanya 150–390 baris (di bawah batas soft limit 400).
3. **Jebakan Deadlock Wawancara**:
   - Jika endpoint server wawancara mengalami kendala atau gagal menjawab, pengguna tidak boleh terkunci di layar kosong.
   - Tombol *"Lewati Wawancara"* dan *"Terima Semua Tebakan"* selalu tersedia di setiap giliran sehingga prospek tetap dapat menyelesaikan funnel pendaftaran draf.
4. **Jebakan Smart-Cast Properti Modul Lain di Kotlin**:
   - Properti public API dari modul lain (seperti `link.basisRef` dari `:core`) tidak dapat di-smart-cast langsung karena kompiler tidak dapat menjamin immutabilitasnya di modul lain. Selalu salin ke variabel lokal (`val ref = link.basisRef`) sebelum melakukan pencocokan pola atau evaluasi nullability.

---

## 🎯 5. Latihan Mandiri untuk Junior Developer

1. **Eksplorasi Mutasi Fitur**:
   Buka `InterviewSessionStateTest.kt`, tambahkan skenario uji untuk memastikan fitur yang diduplikasi dengan nama sama tidak dimasukkan dua kali ke dalam `RoleModuleLink.features`.
2. **Uji Penelusuran Dasar (Basis Traceability)**:
   Periksa bagaimana `BasisRef` otomatis diikutsertakan saat pengguna menambahkan divisi baru lewat `state.addDivision()` atau menerima saran konsultan lewat `state.acceptSuggestion()`.
3. **Uji Lebar Layar Responsif**:
   Jalankan preview Compose di resolusi ponsel (lebar ~360dp) dan desktop (lebar ~1280dp). Periksa apakah teks tombol navigasi di `StepInterviewG5Summary` tetap sejajar dan mudah disentuh.

