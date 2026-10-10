# Modul Pembelajaran: Modul Tata Kelola Wajib di Pack Hasil Handoff (TRD-PLAT-009)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain Pack runtime vs draf, modul GOVERNANCE, entitlement sebelum bypass Owner, fungsi murni idempoten, "salin identik, bukan rujukan"
> **Prasyarat**: Tahu bahwa `DomainPack` memuat daftar `modules`, bahwa `moduleGate` memeriksa entitlement, dan alur discovery -> draf -> kunci -> handoff
> **Referensi Task**: `docs/trd/TRD-PLAT-009-governance-modules-in-generated-packs.md`. Komit: `818658d4` (fitur), `f73f982b` (merge), `5cc772b6` (TRD disetujui)

---

## 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: tenant yang lahir dari handoff (mis. `klinik`) tidak bisa membuka layar Peran/Penugasan. Owner-nya sendiri mendapat 403 beruntun di `/api/tenant/roles` dan `/api/tenant/module-assignments`. Penyebabnya bukan mesin RBAC yang salah, tetapi **datanya kurang**: pack hasil handoff tidak memuat `org_chart` maupun `dynamic_rbac`. `AccessDecisionEngine.explain` memeriksa entitlement (`grantedModules`) **sebelum** bypass Owner, dan `grantedModules` bawaan = modul pack tenant. Modul tak ada di pack -> `NOT_ENTITLED` -> 403, siapa pun orangnya.
- **Analogi**: sebuah gedung baru dibangun tanpa pintu ke ruang kendali, jadi bahkan pemilik gedung (kunci induk) tidak bisa masuk: kunci induk hanya berlaku bila pintunya ada di denah.
- **Hasil akhir**: setiap pack **runtime** yang terbentuk lewat handoff otomatis membawa seksi `GOVERNANCE` dan dua modulnya, sebagai salinan identik dari definisi platform.

---

## 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 - Cari akar, bukan gejala.** Gejala: 403 untuk Owner. Akar ditemukan dengan memeriksa `domain_packs.definition` (tidak memuat `dynamic_rbac`) dan membaca urutan pemeriksaan di mesin akses.
2. **Langkah 1 - Putuskan titik penyuntikan.** Banyak pembuat pack (wawancara, deterministik, LLM, UI, fixture), tetapi hanya satu titik tempat pack menjadi pack runtime: **handoff**. Di situlah penyuntikan dilakukan.
3. **Langkah 2 - Fungsi murni di `core`**: `DomainPack.withGovernanceModules()`.
4. **Langkah 3 - Pasang di handoff** pada pack non-bawaan.
5. **Langkah 4 - Tes tingkat domain** (idempoten, garment tak tersentuh, handoff ulang) dan **tes API** (Owner 200, peran tak berwenang tetap 403).

---

## 3. Bedah Blok Kode

### Blok A: Daftar modul wajib dan fungsi penyuntik (`core/.../domain/pack/GovernanceModules.kt`)

```kotlin
object GovernanceModules {
    val required: List<ModuleId> = listOf(GarmentModules.ORG_CHART, GarmentModules.DYNAMIC_RBAC)
}

fun DomainPack.withGovernanceModules(): DomainPack {
    val missing = GovernanceModules.required.filter { id -> module(id) == null }
    if (missing.isEmpty()) return this
    val section = GarmentModules.sections.first { it.code.value == "GOVERNANCE" }
    val definitions = missing.map { id ->
        requireNotNull(GarmentModules.modules.firstOrNull { it.id == id }) { "..." }
    }
    return copy(
        sections = if (sections.any { it.code == section.code }) sections else listOf(section) + sections,
        modules = definitions + modules
    )
}
```

**Kenapa begini?**
- `missing.isEmpty() -> return this`: **idempoten**. Memanggilnya dua kali, atau pada pack tulisan tangan yang sudah memuatnya, tidak mengubah apa pun dan tidak menggandakan.
- Modul yang sudah ada **tidak ditimpa**. Kalau definisinya berbeda dari milik platform, yang menolaknya adalah `DomainPackRegistry.identityViolations` (identitas global `ModuleId`), bukan fungsi ini. Satu penanggung jawab per aturan.
- Seksi `GOVERNANCE` ditambahkan bila belum ada, karena invarian `DomainPack` mewajibkan setiap modul menunjuk seksi yang ada.
- `requireNotNull(...) { pesan }`, bukan `!!` (aturan proyek).
- Disalin **identik**, bukan dirujuk lewat `moduleReferences`: aturan R1 menyatakan tata kelola harus salinan identik, dan `sharedModules` hanya untuk modul operasional.
- `factory_flow` sengaja **tidak** ikut: bukan syarat masalah ini, dan menyalinnya mengubah kanvas.

### Blok B: Titik pemasangan di handoff (`DiscoveryHandoffUseCases.kt`)

```kotlin
} else {
    // TRD-PLAT-009: pack runtime wajib membawa modul tata kelola (org_chart + dynamic_rbac), salinan identik.
    stored.draft.pack.let { it.copy(blueprints = it.blueprints.filter { b -> b.code != blueprint.code } + blueprint) }
        .withGovernanceModules()
}
```

**Kenapa di sini dan bukan di tempat lain?**
- Draf adalah **catatan hasil wawancara** (domain bisnis klien); draf LOCKED tidak boleh berubah (Kontrak 5). Tata kelola milik platform, jadi ditambahkan saat **pack runtime** dibentuk, bukan di draf.
- Ditolak: menaruhnya di pembuat draf/agent (LLM bisa lupa, draf terkunci lama tak tertangani), validator yang memaksa (menggagalkan draf lama), atau memperkaya pack di memori saat tenant dimuat (dua sumber kebenaran; menyentuh jalur resolusi tenant).
- Pack bawaan (garment) tidak melewati cabang ini, sehingga byte-nya tak berubah.

---

## 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Suntik saat handoff pada salinan pack | Ubah draf | Draf terkunci membeku | Draf LOCKED berubah |
| Salinan identik | `moduleReferences` | Aturan R1 untuk tata kelola | Invarian `sharedModules` (operasional saja) dilanggar |
| Jangan timpa, biarkan registry menolak | Timpa definisi | Satu penegak identitas | Definisi tenant diam-diam diganti |
| Tidak menyentuh entitlement tersimpan | Migrasi data | Grant tersimpan hanya bila beda dari katalog (`null` = semua) | Migrasi berisiko tanpa kebutuhan terbukti |

---

## 5. Jebakan Nyata yang Ditemukan

1. **"Owner pun 403" bukan bug mesin.** Entitlement diperiksa sebelum bypass Owner; ini benar. Memperbaiki mesin akan melemahkan keamanan. Yang diperbaiki adalah data pack.
2. **Pack lama tidak ikut sembuh.** Handoff ulang pada pack hasil handoff lama (tanpa governance) menghasilkan pack berbeda dari yang efektif -> 409 "butuh review manual". Itu sengaja (versi terkunci tak berubah). Perbaikan: simpan ulang pack dengan `withGovernanceModules()` sebagai **versi baru**, kunci, pin tenant. TRD mencatat DB dev tidak punya kasus ini (semua tenant garment); di lingkungan lain tidak diverifikasi.
3. **Tenant lama dengan grant yang sudah disempitkan manual** tidak otomatis mendapat governance; perlu grant ulang oleh superadmin.
4. **Catatan belum diverifikasi** di TRD: urutan seksi/modul governance di depan daftar pack belum dilihat di UI.
5. Menambahkan governance **tidak membuat peran lain berwenang**: tes API memastikan `SALES` tetap 403.

---

## 6. Cara Memverifikasi

- Tes domain (`DiscoveryHandoffUseCasesTest`): handoff klinik menyimpan pack dengan `org_chart` dan `dynamic_rbac` berjenis `GOVERNANCE`; `withGovernanceModules` idempoten; handoff ulang identik OK sedangkan pack lama butuh review manual; handoff garment tidak menyentuh pack bawaan.
- Tes API `server/src/test/.../GeneratedPackGovernanceApiTest.kt`:
  - `handoffTenant_ownerManagesRolesAndAssignments_unauthorizedRoleStillForbidden`: Owner 200 pada `/api/tenant/roles` dan `/api/tenant/module-assignments`, `SALES` 403.
  - `legacyPackWithoutGovernance_staysForbiddenForOwner`: pack lama tanpa governance tetap 403 bagi Owner (bukti bahwa mekanismenya entitlement).
- Perintah: `./gradlew :core:jvmTest --tests '*DiscoveryHandoffUseCasesTest*'` dan `./gradlew :server:test --tests '*GeneratedPackGovernanceApiTest*'` (tidak dijalankan saat menulis dokumen ini).

---

## 7. Tantangan Mandiri

- [ ] Tulis tes yang menunjukkan bahwa pack berisi `org_chart` dengan definisi berbeda ditolak registry, bukan ditimpa.
- [ ] Rancang skrip perbaikan untuk pack lama (versi baru + kunci + pin tenant) dan jelaskan mengapa tidak dibuat migrasi otomatis.
- [ ] Mengapa `factory_flow` tidak ikut disuntik? Jelaskan dampaknya pada kanvas dan kuota.
