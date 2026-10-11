# 🎓 Modul Pembelajaran: Menyatukan Dua Pendapat Wewenang — TRD-PLAT-012 Q3

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: RBAC fail-closed (Ktor), satu jalur perhitungan wewenang, owner-bypass, TDD untuk perilaku keamanan, delegasi alih-alih duplikasi
> **Prasyarat**: Dasar Kotlin `suspend`, Ktor routing + `ApplicationCall`, konsep RBAC (peran vs jabatan vs divisi), istilah "fail-closed"
> **Referensi Task**: [`TRD-PLAT-012`](../../trd/mvp/TRD-PLAT-012-factory-flow-gates-fail-closed.md) Q3 · rencana [PLAN-plat-012-track-c.md](../../plannings/mvp/PLAN-plat-012-track-c.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Bayangkan satu gedung dengan **dua resepsionis berbeda** yang memutuskan siapa boleh masuk ruang kontrol pabrik:

- **Resepsionis A** (menu klien & `/me/access`) — dia hafal aturan: *"Pemilik gedung selalu boleh masuk."*
- **Resepsionis B** (gerbang Factory Flow: pipeline, lokasi, tahap kerja) — dia punya buku aturan sendiri, dan di bukunya hanya tertulis *"admin pusat selalu boleh masuk"*. Pemilik gedung **tidak** disebut. Jadi pemilik yang ber-divisi (tapi tidak menjabat di jabatan apa pun) ditolak B — padahal A mempersilakannya.

Hasilnya absurd: pemilik mendaftar lewat meja A (dibilang "boleh"), lalu berjalan ke pintu yang dijaga B dan mendapat "TIDAK". Satu orang, satu identitas, **dua jawaban**. Di sistem nyata ini bukan sekadar tidak nyaman — ia merusak kepercayaan pada matriks wewenang, dan pada kasus terburuk **mengunci pemilik keluar dari layar yang hanya dia yang bisa membuka** (RBAC).

### Analogi Sederhana

Dua pintu, satu gedung, **satu kartu akses**. Kartu yang sama harus dibaca oleh **pembaca yang sama**. Kalau setiap pintu punya daftar tamu sendiri, cepat atau lambat daftarnya berbeda, dan penghuni gedung kehilangan akal — *"memangnya saya boleh masuk atau tidak?"*.

### Hasil Akhir yang Diharapkan

Satu fungsi perhitungan wewenang (`callerDecisions`) dipakai **oleh semua** gerbang. Gerbang Factory Flow tidak lagi punya "buku aturan sendiri" — ia bertanya ke buku yang sama dengan menu klien.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

1. **Langkah 0: Pahami gejala dulu, jangan sentuh kode.** Bandingkan dua fungsi yang "katanya sama": `factoryFlowDecision` (FactoryFlowAccessGuard) vs `callerDecisions` (ModuleAccessGuard). Tuliskan perbedaan **satu baris yang menentukan** — di sini: ekspresi `isOwnerOrSuperAdmin`.
2. **Langkah 1: Tulis tes paritas yang MERAH dulu.** Bukan tes "fitur baru", tapi tes yang **menangkap ketidaksesuaian** dua jalur untuk pengguna yang sama. Kalau tes ini tidak bisa merah, berarti dugaan kita salah — bagus, tahu lebih awal.
3. **Langkah 2: Buktikan merahnya adalah merah yang benar.** Baca pesan asert-nya, pastikan angkanya (NONE vs MANAGE) sesuai prediksi, bukan gagal karena DB/compile.
4. **Langkah 3: Baru perbaiki `main`.** Ganti isi fungsi dengan **delegasi**, bukan menyunting dua tempat hingga cocok.
5. **Langkah 4: Kembalikan tes jadi hijau, lalu jalankan tes sekitar (kontrak lama) untuk memastikan tak ada regresi.**

Urutan ini penting: **konsep → bukti → perbaikan**, bukan "ubah dulu, tes menyusul". Tes yang ditulis setelah perbaikan tak pernah membuktikan apa pun tentang bug yang katanya diperbaiki.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Dua Buku Aturan yang Seharusnya Satu

Ini inti bug-nya. Perhatikan baris `isOwnerOrSuperAdmin` di kedua tempat:

```kotlin
// ModuleAccessGuard.kt — callerDecisions (buku resmi: menu klien, /me/access, gerbang modul generik)
isOwnerOrSuperAdmin = (principal.isPlatformSuperadmin || principal.role == Role.TENANT_ADMIN) && role == null,
isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
```

```kotlin
// FactoryFlowAccessGuard.kt — factoryFlowDecision (SEBELUM perbaikan: buku tandingan)
isOwnerOrSuperAdmin = principal.isPlatformSuperadmin && role == null,   // ← TENANT_ADMIN tidak ikut!
isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
```

**Mengapa ini berbahaya?**
- Perbedaannya bukan "tambahan fitur", melainkan **himpunan yang lolos berbeda**: di buku A, `TENANT_ADMIN` tanpa jabatan (`role == null`) mendapat **OWNER_BYPASS → MANAGE**; di buku B, dia jatuh ke matriks dan mendapat **NONE → 403** bila tidak punya jabatan.
- Kasus pemisahnya sempit tapi nyata: token `TENANT_ADMIN` yang **ber-divisi** (`departmentId != null`) **tanpa jabatan kustom** (`customRoleId == null`). Di `factoryFlowDecision`, karena ada divisi, dia **tidak** masuk jalur `moduleDecision` (yang akan menolongnya); dia membangun persona sendiri dan kalah.
- Semua ini terjadi tanpa satu pun tes gagal — karena setiap fungsi diuji sendiri-sendiri. **Bug lahir di celah antar-dua-tes.**

### Blok B: Tes Paritas — Menjaga Celah, Bukan Menjaga Fungsi

```kotlin
@Test
fun tenantAdminWithDepartmentButNoRole_factoryFlowMatchesCallerDecisions() = testApplication {
    installApp()
    val token = TestAuth.principalToken(
        slug.value, Role.TENANT_ADMIN, customRoleId = null, departmentId = "dept-produksi"
    )
    val body = client.get("/parity") {
        header("X-Tenant-Slug", slug.value)
        header(HttpHeaders.Authorization, "Bearer $token")
    }.bodyAsText()
    val (factoryLevel, callerLevel) = body.split("|").map { AccessLevel.valueOf(it) }
    assertEquals(AccessLevel.MANAGE, callerLevel, "...")
    assertEquals(callerLevel, factoryLevel, "...")   // ← inti: dua jalur WAJIB sepakat
}
```

**Mengapa ditulis begini?**
- **Probe route** (`GET /parity`) memanggil **kedua** fungsi untuk token yang **sama** dalam satu permintaan. Ini satu-satunya cara jujur menguji "dua pendapat" — menguji lewat dua endpoint berbeda hanya akan menguji dua kali perjalanan yang punya konteks berbeda.
- **Asert kedua yang paling berharga adalah `assertEquals(callerLevel, factoryLevel)`** — ia tidak peduli nilainya MANAGE atau NONE; ia menuntut **kesepakatan**. Menulis `assertEquals(MANAGE, factoryLevel)` saja akan lulus bahkan bila kelak `callerDecisions` ikut berubah salah. Tes paritas melindungi *hubungan*, bukan *magic value*.
- **Asert pertama (`callerLevel == MANAGE`) adalah jangkar**: memastikan buku resmi memang memberi owner MANAGE. Tanpa itu, bila kelak owner diturunkan haknya, tes paritas akan "lulus" karena keduanya sama-sama NONE — padahal itu regresi keamanan baru.

### Blok C: Perbaikan — Delegasi, Bukan Sinkronisasi Salinan

```kotlin
internal suspend fun ApplicationCall.factoryFlowDecision(
    tenant: TenantContext,
    roleRepository: RoleRepository?,
    moduleAssignmentRepository: ModuleAssignmentRepository?
): AccessDecision? {
    if (roleRepository == null || moduleAssignmentRepository == null) return null
    // TRD-PLAT-012 Q3: satu jalur perhitungan dengan menu klien dan gerbang modul generik.
    return callerDecisions(
        tenant, roleRepository, moduleAssignmentRepository, listOf(GarmentModules.FACTORY_FLOW)
    ).getValue(GarmentModules.FACTORY_FLOW)
}
```

**Mengapa begini, bukan "salin baris `TENANT_ADMIN` ke buku B"?**

| Pendekatan | Akibat |
|---|---|
| Menyalin `|| principal.role == Role.TENANT_ADMIN` ke `factoryFlowDecision` | Dua salinan tetap ada; perbedaan berikutnya (mis. aturan owner pabrik berubah) akan **lagi-lagi** melenceng, dan tak ada yang menangkapnya |
| **Delegasi** (yang kita pilih) | Hanya ada **satu** definisi "siapa owner". Perubahan di satu tempat otomatis berlaku di semua gerbang |

**Memori desain yang perlu diingat:** *kalau sebuah aturan wewenang muncul dua kali, cepat atau lambat keduanya berbeda.* Aturan Tiga Kali design system adalah sepupunya — di sini bahkan **dua kali** sudah cukup untuk menggabungkannya.

Perhatikan juga yang **tetap** dipertahankan:
- `if (repositori == null) return null` — perilaku untuk instalasi yang belum memasang repositori wewenang. Ini **karakterisasi yang disengaja** (guard meloloskan) dan sudah dijaga tes `factoryFlowDecision_withoutAuthorizationRepositories_staysNull`. Delegasi **tidak** boleh menghapusnya.
- `listOf(GarmentModules.FACTORY_FLOW)` — bedanya `callerDecisions` bisa menghitung banyak modul sekaligus; di sini kita minta satu, lalu ambil hasilnya.

### Blok D: Pembersihan yang Wajib — Impor Mati

Setelah badan fungsi menyusut, impor berikut tak lagi dipakai: `AccessDecisionEngine`, `RoleId`, `TestingPersona`, `callerPrincipalOrNull`, `grantedModulesOrNull`.

**Cara aman memastikan**: hitung pemakaian tiap simbol — bila hasilnya **1**, itu impornya sendiri.

```bash
for sym in AccessDecisionEngine TestingPersona RoleId callerPrincipalOrNull grantedModulesOrNull; do
  echo -n "$sym: "; rg -c "\b$sym\b" server/src/main/.../FactoryFlowAccessGuard.kt
done
```

Di sini hasilnya semua `1` → hapus. Ukuran file **138 → 108 baris** (aturan Ratchet file-size: file tak boleh bertambah panjang; ini menyusut).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pilihan | Alternatif | Mengapa Dipilih | Risiko Alternatif |
|---|---|---|---|
| **Delegasi ke `callerDecisions`** | Menyalin baris `TENANT_ADMIN` ke fungsi kedua | Satu sumber kebenaran; mustahil berbeda pendapat lagi | Dua salinan = bug yang sama akan lahir kembali pada perubahan aturan berikutnya |
| **Tes paritas relasional** (`assertEquals(a, b)`) | Tes nilai absolut per fungsi | Menangkap "dua pendapat" — bug yang tak terlihat oleh tes satuan | Tes per-fungsi hijau semua, tapi pengguna tetap ditolak sepihak |
| **TDD: merah → bukti → perbaikan** | Ubah kode lalu tulis tes | Membuktikan tes benar-benar menangkap bug yang diklaim | Tes "hijau sejak lahir" tidak membuktikan apa pun |
| **Pertahankan `null` saat repo tak terpasang** | Longgarkan jadi "lolos selalu" | Instalasi tanpa repositori memang tak bisa menghitung; karakterisasi dipertahankan dan diuji | Menghapus jalur `null` memecah instalasi/pengujian lama dan menyembunyikan asumsi |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Jebakan: memperbaiki dengan menyalin.** Melihat B kurang satu kondisi, lalu menempelkan `|| principal.role == Role.TENANT_ADMIN` ke B. *Kenapa bahaya*: bug jenis ini **muncul kembali**; sekarang ada dua "definisi owner" yang harus dijaga seumur hidup. *Solusi*: delegasikan ke satu fungsi.
2. **Jebakan: tes hijau sejak lahir.** Menulis tes **setelah** memperbaiki, sehingga tes tidak pernah merah. *Kenapa bahaya*: kita tak pernah tahu apakah tes benar-benar menguji bug itu. *Solusi*: tes dulu, catat pesan gagalnya, baru perbaiki.
3. **Jebakan: menguji dua endpoint berbeda untuk "paritas".** Memanggil `GET /api/tenant/locations` dan `GET /api/tenant/me/access` lalu membandingkan status. *Kenapa bahaya*: ada dua plugin autentikasi, dua resolusi tenant, dan setengah lusin lapisan lain di antara — kegagalan bisa muncul dari mana saja. *Solusi*: probe route yang memanggil dua fungsi domain dalam **satu** permintaan.
4. **Jebakan: menghapus jalur `null` "karena tak mungkin".** *Kenapa bahaya*: instalasi/tes lama memang tak memasang repositori wewenang; menghapusnya memecah mereka dan mengubah perilaku diam-diam. *Solusi*: perlakukan sebagai karakterisasi yang disengaja + tes penjaga.
5. **Jebakan: lupa impor mati setelah menyusutkan fungsi.** *Kenapa bahaya*: warning menumpuk, pembaca bingung simbol mana yang masih hidup. *Solusi*: hitung pemakaian (hasil `1` = impornya sendiri) lalu hapus.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

**Strategi**: uji lewat **API nyata** (`testApplication` + token sungguhan), bukan memanggil fungsi dengan objek buatan — karena bug ini hidup di persimpangan **resolusi principal × guard**, dan objek buatan akan melewati separuh jalur.

Tes yang dijalankan dan hasilnya:
- `FactoryFlowTenantAdminParityTest` — **merah sebelum perbaikan** dengan pesan `dapat factory=NONE expected:<MANAGE> but was:<NONE>`; **hijau sesudahnya**.
- `FactoryFlowAccessApiTest` (7 tes) — **hijau sebelum dan sesudah**: bukti bahwa hanya kasus owner-yang-terdampak yang berubah; staf berjabatan VIEW/NONE/MANAGE, isolasi tenant, dan karakterisasi `null` tak terganggu.
- Tes gerbang tetangga (`MyAccess`, `Help`, `RouteOwnership`, stage-flow) — hijau.
- Suite server penuh: 800 tes, 2 gagal — **keduanya di luar perubahan ini** (satu berasal dari perubahan belum-terkomit sesi lain di `PipelinePresetFactory`; satu sudah gagal di `origin/main`, drift `owner_id` FIELD-004). Pelajaran penting: **"2 gagal" harus dibedakan dari "2 gagal karena saya"** — jalankan kontrol di worktree bersih `origin/main` untuk memisahkan keduanya.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tulis tes paritas untuk gerbang lain yang belum dipindahkan ke `callerDecisions` (`OrgChartAccessGuard`, `CrmAccessGuard`). Apakah kali ini mereka sepakat dengan menu klien untuk token `TENANT_ADMIN` ber-divisi tanpa jabatan? Catat temuannya — refactor penyatuannya memang "tersendiri".
- [ ] **Tantangan 2**: Tambahkan kasus token `TENANT_ADMIN` **tanpa divisi dan tanpa jabatan** ke tes paritas. Mana jalur yang dilaluinya di fungsi baru (`callerDecisions`), dan apakah hasilnya sama dengan token **ber-divisi**? Jelaskan mengapa hasilnya masuk akal.
- [ ] **Tantangan 3**: Cari semua tempat di repo yang membangun `TestingPersona` sendiri. Berapa banyak "buku aturan" wewenang yang masih berdiri sendiri? Rancang urutan refactor untuk menyatukannya tanpa memutus tes lama.

---

*Catatan ledger*: task ini perbaikan wewenang kecil tanpa baris `module_build_records`, jadi pencatatan effort per peran/fase dilewati.

---

## 8. Tambahan — Q2: Pesan Galat yang Menuntun (bukan Menghakimi)

Sesudah Q3, pertanyaan berikutnya bukan lagi "boleh atau tidak", melainkan **"kalau tidak boleh, apa yang harus saya lakukan?"**. Pesan galat yang baik adalah dokumentasi yang muncul tepat saat dibutuhkan.

### Masalah pesan lama

```kotlin
"Butuh wewenang ${required.displayName} atas modul \"${...displayName}\"; wewenang Anda saat ini ${...}."
```

Tiga cacat: (1) kata **"modul"** adalah jargon arsitektur, bukan kosakata pengguna; (2) ia menyebut **apa yang salah** tetapi tidak **apa yang harus dilakukan**; (3) di klien, kartu menampilkan lencana literal **`403`** — kode HTTP mentah tak pernah pantas dilihat pengguna.

### Pola yang dipakai: fungsi murni untuk teks yang penting

```kotlin
internal fun factoryFlowDeniedMessage(
    required: AccessLevel,
    current: AccessLevel,
    source: AccessSource
): String =
    "Alur Pabrik belum terbuka untuk peran Anda. Dibutuhkan: ${required.displayName}; " +
        "saat ini: ${current.displayName} (${source.label}). " +
        "Minta pemilik pabrik membukanya lewat jabatan di layar Hak Akses (RBAC)."
```

**Mengapa diekstrak jadi fungsi murni, bukan tetap sebagai literal di dalam handler?**
- Bisa **diuji tanpa HTTP** (3 tes murni), jadi kata "minta ke siapa" tak bisa hilang diam-diam.
- Satu tempat untuk menyunting nada/gaya pesan; handler tetap tipis.
- Uji bisa menegakkan dua aturan tak terlihat: **tanpa jargon "modul"** dan **seluruhnya ASCII** (font Nunito tak punya glyph di luar Latin-1 — ini nyata di repo ini, ada `UiTextLatin1GuardTest`).

### Sisi klien: kosakata pengguna menggantikan kode status

```kotlin
// sebelum: ClayBadge(text = if (denied != null) "403" else "GALAT", …)
// sesudah:
ClayBadge(text = if (denied != null) "Tanpa Akses" else "GALAT", tint = WeMadeColors.Error)
```

Lencana "Tanpa Akses" konsisten dengan `ACCESS_DENIED_BADGE` di `AccessDeniedCard` — **satu kosakata untuk satu keadaan**, bukan tiga sebutan berbeda di tiga layar (Aturan Tiga Kali versi bahasa).

Dan panduan kartu diubah agar **tidak menduplikasi** instruksi yang kini hidup di pesan server:

```kotlin
"Hubungi pemilik pabrik bila Anda merasa seharusnya berwenang."
```

Pelajaran pembagian tanggung jawab teks: **server menyebut sebab & konteks** (level butuh/ada + sumber), **kartu menyebut tindakan**. Kalau keduanya menuliskan instruksi RBAC, pengguna membaca kalimat yang sama dua kali dalam satu kartu 8 baris.

### Jebakan pemula di babak ini

1. **Menampilkan kode HTTP ke pengguna.** "403" adalah istilah kita, bukan istilahnya. *Solusi*: kosakata pengguna ("Tanpa Akses"), status teknis tetap ada di `FactoryFlowLoadState` untuk pemrogram.
2. **Menghapus konteks demi "keramahan".** Pesan ramah yang tak menyebut level & sumber membuat pengguna (dan penguji) menebak apakah salahnya konfigurasi, persona, atau sistem. *Solusi*: ramah **dan** spesifik.
3. **Menulis pesan langsung di handler.** Teks yang penting tapi tak teruji akan rusak tanpa ada yang tahu. *Solusi*: fungsi murni + tes, sekecil apa pun.
4. **Menaruh instruksi di dua tempat.** Server dan klien sama-sama menjelaskan RBAC → duplikasi terbaca sebagai kebingungan. *Solusi*: bagi peran teks (sebab di server, tindakan di kartu).

### Bukti

`FactoryFlowDeniedMessageTest` 3/0 (level butuh, level pemanggil, sumber, cara meminta, tanpa jargon, ASCII); `FactoryFlowTenantAdminParityTest` + `FactoryFlowAccessApiTest` tetap hijau (8/0); kompilasi `app:shared` JVM/WasmJS/JS + `jvmTest` hijau. **Cek mata belum dilakukan** karena sesi ini tak punya browser — langkahnya dicatat di `PLAN-plat-012-track-c.md` §3.

