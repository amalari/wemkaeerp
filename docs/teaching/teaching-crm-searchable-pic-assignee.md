# Teaching — Selector PIC yang Bisa Dicari (ala Monday.com) di Modul Leads

> Kasus: dropdown "Tugaskan PIC" di Lead Inspector CRM dulu menampilkan seluruh daftar
> karyawan polos tanpa pencarian. Sekarang jadi selector ala Monday.com: kolom pencarian
> yang otomatis fokus, filter real-time, ceklis PIC aktif, dan opsi "Tanpa PIC".

---

## 1. Start dari Mana? (Order of Operations)

1. **Cari titik nyala UI-nya**: `grep -rn "Tugaskan PIC"` → ketemu `JiraPicAvatar`
   di `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadInspectorPane.kt`.
2. **Cek sumber data-nya sebelum menyentuh BE**: daftar karyawan sudah mengalir dari
   `GET /api/tenant/employees` (`OrgChartApiClient`) → `CrmViewModel(employees = …)` →
   `LeadInspectorPane(employees = …)`. Assignment tetap lewat
   `PATCH /api/tenant/crm/leads/{id}` (`owner_employee_id`). **Kesimpulan: nol perubahan BE.**
3. **Cek aturan file-size**: `LeadInspectorPane.kt` 733 baris — sudah di atas hard limit 600.
   Aturan ratchet melarang menambah baris; menambah fitur di dalamnya = pelanggaran.
   Maka: **ekstrak**, jangan tempel.
4. **Ekstrak** jadi `PicAssigneeSelector.kt` (package fitur CRM, bukan `designsystem/` —
   karena komponen ini tahu `OrgNode`, melanggar Kontrak 6 jika naik ke design system).
5. **Kompilasi 5 target** dan lihat UI dengan mata.

## 2. Bedah Kode Blok per Blok

### Blok A — Trigger avatar (pindahan, tidak berubah perilaku)
Avatar inisial + nama, atau siluet "Tugaskan PIC". Identik dengan versi lama — bagian yang
benar-benar baru hanya popup-nya.

### Blok B — Kolom pencarian + autofocus
```kotlin
LaunchedEffect(expanded) {
    if (expanded) {
        query = ""
        runCatching { searchFocus.requestFocus() }
    }
}
```
- `LaunchedEffect(expanded)` menunggu popup selesai ter-compose sebelum meminta fokus —
  kalau `requestFocus()` dipanggil langsung saat klik, `FocusRequester` belum terpasang.
- `runCatching` adalah jaring pengaman: beberapa platform membatalkan animasi popup dan
  melempar `IllegalStateException` bila fokus diminta di frame pertama.
- `ClayTextField` dapat param opsional `focusRequester` (default `null`) yang diteruskan ke
  `BasicTextField` di dalamnya — perubahan non-breaking, pemanggil lama tidak tersentuh.

### Blok C — Filter client-side
```kotlin
val filtered = employees.filter { it.name.contains(query.trim(), ignoreCase = true) }
```
Filter di memori karena daftar karyawan per tenant kecil dan **sudah termuat penuh**.
Endpoint pencarian server-side hanya masuk akal kalau nanti tenant punya ribuan karyawan.

### Blok D — Ceklis PIC aktif
`trailingIcon = if (owner?.id == emp.id) IconCheck …` — state "yang sekarang" dibedakan lewat
**warna & ikon**, bukan ketebalan border (Kontrak 8 design system).

## 3. The Why (Teknologi & Pendekatan)

| Keputusan | Alternatif yang ditolak | Risiko alternatifnya |
|---|---|---|
| Ekstrak ke package fitur CRM | Naikkan ke `designsystem/` | Kontrak 6: komponen design system wajib buta domain; ini butuh `OrgNode` |
| DropdownMenu M3 + ClayTextField di dalamnya | Modal/bottom sheet sendiri | Perubahan perilaku besar untuk fitur kecil; dropdown ala Monday sudah cukup |
| Filter client-side | Endpoint `/employees?query=` | BE baru tanpa manfaat; data sudah utuh di klien |
| `focusRequester` opsional di `ClayTextField` | Field search custom terpisah | Duplikasi styling; melanggar Aturan Tiga Kali |

## 4. Jebakan Pemula (Common Pitfalls)

1. **Menambah baris ke file yang sudah >600** — aturan ratchet melarang. Ekstrak dulu.
2. **`requestFocus()` tanpa `LaunchedEffect`** — `FocusRequester` belum ter-attach → crash.
3. **Menaruh `ClayTextField` tanpa lebar tetap di `DropdownMenu`** — `fillMaxWidth` internalnya
   runtuh jadi lebar nol; beri `Modifier.width(248.dp)`.
4. **Mengira butuh endpoint BE baru** — telusuri dulu aliran data; `employees` sudah sampai UI.
5. **Melupakan `query = ""` saat menutup/membuka** — query basi membuat daftar kosong palsu.

## 5. Verifikasi & Tantangan Mandiri

- [ ] Buka CRM → Leads → klik lead mana pun → klik avatar PIC: popup terbuka, kursor sudah
      aktif di kolom pencarian tanpa klik kedua.
- [ ] Ketik sebagian nama → daftar menyaring real-time; kosong → muncul
      "Tidak ada karyawan yang cocok".
- [ ] Pilih karyawan → popup tertutup, avatar + nama berganti; buka lagi → ceklis hijau ada
      di karyawan terpilih.
- [ ] Pilih "Tanpa PIC" → avatar kembali siluet "Tugaskan PIC"; cek network: PATCH lead
      `owner_employee_id` = null.
- [ ] User tanpa izin tulis (`canWrite = false`) tidak bisa membuka popup.
- [ ] Ratchet file-size: `wc -l LeadInspectorPane.kt` → 623 (turun dari 733), komponen baru 192.

> **Catatan pre-existing**: `:app:shared:compileAndroidMain` saat ini gagal di
> `MockupCropDialog.kt` (referensi Skia di commonMain) — kerusakan lama yang tidak berhubungan
> dengan perubahan ini. Target `compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs`,
> dan `jvmTest` hijau.
