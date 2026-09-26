# Wewenang per Meja Lantai Produksi (RBAC × Operator Floor)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Full-stack vertical slice (Flyway → Exposed → Ktor route → Ktor client → Compose), sumbu wewenang divisi, null sebagai sinyal "tanpa batasan", Aturan Ratchet pada file besar
> **Prasyarat**: Paham `AccessDecisionEngine`, `DepartmentModuleAssignment`, dan layar `OperatorFloorWorkspaceScreen`
> **Referensi Task**: Tab meja Lantai Produksi hanya muncul bila user bisa mengakses lebih dari satu meja

---

## 1. Masalah Nyata

Layar Lantai Produksi (`/operator-exec`) selalu menampilkan enam tab meja (Rajut, Linking, Cuci,
Setrika, QC, Kemas) untuk semua orang. Seorang operator Cuci melihat tab mesin rajut yang tidak
pernah boleh ia sentuh — dan sebaliknya, admin tidak punya cara menutup mejanya.

Kebutuhan produk: **tab meja hanya muncul kalau user bisa mengakses lebih dari satu; kalau cuma
satu, layar cukup menampilkan meja itu tanpa baris tab sama sekali.**

Yang belum ada di sistem: konsep "akses per meja". RBAC berhenti di level modul (`OPERATOR_EXEC`
keseluruhan) plus scope data. Jadi fitur ini dibangun penuh, dari skema database sampai chip Compose.

---

## 2. Keputusan Desain: Di Sumbu Mana Batasan Meja Hidup?

Persona mendapat wewenang dari dua sumbu yang di-union: jabatan (`CustomRole.modulePermissions`)
dan divisi (`DepartmentModuleAssignment`). Keputusan kunci: **batasan meja hidup di sumbu divisi.**

Alasannya: penugasan divisi sudah punya penarget jabatan (`specificRoleIds` — "Per Divisi - Seluruh
Jabatan" vs jabatan tertentu), jadi kebutuhan "per divisi/jabatan" tercakup tanpa melahirkan sumbu
ketiga. Matriks jabatan tidak diubah sama sekali.

Aturan penerapannya (`resolveAccessibleOperatorDesks` di `core/.../domain/sampling/OperatorDeskAccess.kt`):

| Kondisi | Hasil |
|---|---|
| Owner / superadmin (bypass) | Semua meja |
| Sumbu divisi tidak memberi akses (NONE) | Semua meja (tidak membawa batasan) |
| Penugasan divisi tanpa daftar meja (`null`) | Semua meja |
| Penugasan divisi dengan daftar meja | Hanya daftar itu |

> **Kenapa bypass selalu membuka semua?** Menyaring layar untuk owner berarti menyembunyikan
> satu-satunya tempat untuk memperbaiki konfigurasinya — pelajaran anti-lockout yang sama dengan
> modul RBAC di `AccessDecisionEngine`.

### `null` berarti "tanpa batasan", bukan "tidak ada"

`allowedDesks: Set<String>?` sengaja nullable. `null` = seluruh meja — itulah arti seluruh data
lama yang belum mengenal kolom ini, jadi tidak ada backfill perilaku. Daftar **kosong tetap
kosong**: kesalahan konfigurasi admin jujur ditampilkan sebagai "belum diberi akses ke meja mana
pun", bukan diam-diam diperlakukan sebagai "semua".

---

## 3. Urutan Menulis (Order of Operations)

1. **Domain dulu (`core/`)** — pertanyaan pertama selalu "aturnya apa", bukan "UI-nya apa":
   - `ModuleAccessConfig.allowedDesks: Set<String>?` — meja ditumpang konfigurasi akses supaya
     `AccessDecision` memuatnya dan layar bisa memfilter tanpa fetch kedua.
   - `DepartmentModuleAssignment.allowedDesks` — sumber sebenarnya.
   - `AccessDecisionEngine` membawa `it.allowedDesks` saat merakit `departmentAccess`.
   - File baru `OperatorDeskAccess.kt`: `deskStageForCode`, `toAllowedOperatorDesks`, dan
     `resolveAccessibleOperatorDesks` — fungsi murni, tanpa framework, bisa diuji tanpa Compose.
2. **Skema & server (`server/`)** — migrasi `V66__operator_desk_access.sql` menambah kolom
   `allowed_desks JSONB NOT NULL DEFAULT '[]'` (`[]` = semua meja, kompatibel ke belakang), lalu
   kolom Exposed, baca/tulis di `PostgresModuleAssignmentRepository`, dan route
   `PUT /api/tenant/module-assignments` yang mengurai `allowedDesks`.
3. **Kontrak JSON** — `ModulePermissionsSerializer` (server) dan `RbacApiClient` (client)
   dirubah dengan teknik yang sama: cocokkan gumpalan isi tiap modul tanpa brace bersarang
   (`\{([^{}]*)\}`), lalu urai `level`/`scope`/`desks` satu per satu. Entri lama tanpa `desks`
   dan entri baru dengannya hidup berdampingan.
4. **UI konfigurasi** — komponen baru `OperatorDeskAccessPicker` diintegrasikan ke
   `AssignDepartmentModal` (hanya saat `module == OPERATOR_EXEC` dan level ≠ NONE).
5. **UI konsumen** — `OperatorFloorWorkspaceScreen` memfilter `visibleDesks` dari `decision`,
   menyembunyikan baris tab bila `visibleDesks.size <= 1`, dan menampilkan banner pintu keluar
   yang menjelaskan caranya memperbaiki bila nol meja.

---

## 4. Bedah Kode Blok per Blok

### Layar operator (sisi konsumen)

```kotlin
val accessibleDesks = remember(decision) {
    resolveAccessibleOperatorDesks(
        bypass = decision.source == AccessSource.OWNER_BYPASS ||
            decision.source == AccessSource.SUPERADMIN_BYPASS,
        departmentAccess = decision.fromDepartment   // bukan decision.config!
    )
}
```

**Kenapa `fromDepartment`, bukan `config`?** `config` adalah pemenang union hak tingkat level
(jabatan bisa menang). Batasan meja justru harus tetap berlaku walau jabatan memberi level lebih
tinggi — admin yang membatasi meja divisi Produksi tidak ingin jabatan "Operator" membuka semua
meja lewat pintu samping.

```kotlin
if (visibleDesks.isEmpty()) { ClayStatusBanner(...); return }
var desk by remember(visibleDesks) { mutableStateOf(visibleDesks.first()) }
```

Early-return sebelum meja dirender menghindari `firstOrNull()` + null-check yang menular ke seluruh
isi layar. `remember(visibleDesks)` mengembalikan meja aktif ke yang pertama begitu wewenang berubah
(persona diganti) — tanpa itu, `desk` bisa menggantung di meja yang sudah tertutup.

### Picker (sisi konfigurasi)

Keadaan `null` direpresentasikan sebagai chip "Semua Meja" terpilih. Saat admin mengetuk satu meja
dari keadaan `null`, basis seleksi adalah *seluruh kode* lalu meja itu dimatikan — jadi maksud
"aku ingin menutup meja ini" tidak berubah menjadi "aku menutup semua meja lain". Mematikan chip
terakhir mengembalikan `null` (semua), sehingga penugasan tidak pernah bisa mengunci divisi keluar
dari seluruh layar tanpa sisa pintu kembali.

---

## 5. Technology & Approach ("The Why")

- **JSONB `DEFAULT '[]'`, bukan tabel junction.** Meja dibaca utuh pada setiap evaluasi izin dan
  tidak pernah di-query per anggota — alasan yang sama dengan kolom `specific_role_ids` di V17.
  Menambah tabel baru berarti join baru untuk nilai yang selalu dibawa sekalian.
- **Serializer regex, bukan library JSON.** Kontrak `module_permissions` sudah regex di dua sisi
  (server `ModulePermissionsSerializer`, client `RbacApiClient`); bermigrasi ke library JSON adalah
  refactor lintas kontrak yang tidak dibayar task ini. Yang penting: regex baru menerima format
  lama **dan** baru.
- **Filter di Composable, aturan di domain.** Composable hanya bertanya "meja mana yang boleh?"
  ke fungsi murni; kalau aturannya ditulis di Composable, test tidak bisa menjangkaunya.

---

## 6. Jebakan Pemula (Common Pitfalls)

1. **Union sempit vs batasan.** "Hak tertinggi menang" untuk *level* tidak otomatis berarti union
   daftar meja. Union `null` (semua) dengan daftar apa pun menghasilkan semua — batasan admin
   lenyap. Karena itu batasan meja dibaca dari sumbu divisi saja.
2. **`decision.config` ≠ `decision.fromDepartment`.** Pemenang level bisa sumbu jabatan yang tidak
   membawa meja; memilih sumber yang salah membuat fitur diam-diam mati untuk sebagian persona.
3. **Null vs kosong.** Set kosong yang dijadikan "semua meja" membuat data korup tak terlihat.
   Kosong jujur berarti "tidak ada meja" dan layarnya yang menjelaskan.
4. **Aturan Ratchet.** `AssignDepartmentModal.kt` sudah 1057 baris (di atas hard limit). Integrasi
   picker (+14 baris) wajib dikompensasi kondensasi blok verbose — file malah jadi 1056. Menambah
   baris ke file yang sudah melanggar adalah pelanggaran meski diff-nya kecil.

---

## 7. Verifikasi & Tantangan Mandiri

1. `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs
   :app:shared:assembleAndroidMain :app:shared:jvmTest` — hijau.
2. `./gradlew :core:jvmTest` — `OperatorDeskAccessTest` (6 kasus) hijau; satu failure lama di
   `SamplingStageWorkTest` (makloon) **sudah ada sebelum task ini** — bukti: gagal juga di kondisi
   `git stash`.
3. Manual:
   - Login superadmin → `/operator-exec`: enam tab tetap tampil (bypass).
   - RBAC → modul Catatan Kerja Operator → Tugaskan Divisi → divisi Produksi: pilih meja Cuci +
     Setrika saja → simpan.
   - Masuk sebagai persona divisi Produksi: hanya dua tab itu yang muncul; pilih satu meja →
     tanpa baris tab, langsung kanban.
   - Kosongkan semua meja → layar banner "belum diberi akses ke meja mana pun".
4. **Tantangan**: tegakkan batasan meja juga di `SamplingStageWorkRoutes` (server) — saat ini
   penegakan ada di lapisan domain + tampilan; pekerja tahap masih bisa mengirim stage-advance dari
   API untuk meja yang tidak dilihatnya. Sumbu yang dibutuhkan: persona → divisi → penugasan, lalu
   bandingkan `pipelineStage` SPK dengan `allowedDesks`.
