---
name: threejs-z-fighting-prevention
description: >-
  Diagnose and fix visual flickering, Z-fighting, and coplanar surface artifacts
  in Three.js scenes for this project (AchmadPorto / EventVerse portfolio).
  Activate this skill whenever the user reports: "rerender aneh", "bergerak-gerak warnanya",
  "flickering", "garis segitiga", "shadow acne", "warna putih di bawah object", or any
  visual glitch that appears only when the camera or character moves.
---

# Three.js Z-Fighting Prevention — AchmadPorto Project

Z-fighting (juga disebut *depth-fighting* atau *flickering*) terjadi ketika dua atau lebih
permukaan geometri berada pada posisi Z yang **sama persis** atau **saling menembus** satu sama lain.
GPU tidak bisa menentukan mana yang harus dirender lebih depan, sehingga menghasilkan
pola berkedip segitiga yang acak setiap frame.

---

## Diagnosis Checklist

Sebelum melakukan perbaikan, identifikasi terlebih dahulu penyebabnya:

1. **Tentukan objek yang flickering** — minta user screenshot atau amati area masalah.
2. **Cek posisi dan dimensi geometri** yang saling berhadapan di `FarmhouseInterior.ts`
   (atau world builder lainnya).
3. **Hitung posisi permukaan depan** objek:
   - Permukaan depan Box di axis X: `position.x + (width / 2)` (positif) atau `position.x - (width / 2)` (negatif)
   - Permukaan depan Box di axis Z: `position.z + (depth / 2)` (positif) atau `position.z - (depth / 2)` (negatif)
4. **Bandingkan** posisi permukaan kedua objek — jika selisihnya `< 0.01`, potensi Z-fighting.
5. **Periksa apakah ada floor/wall yang geometry-nya tidak terpangkas** dan menerobos masuk ke objek dekoratif.

---

## Pola Perbaikan

### Pattern 1 — Offset Geometri ("Proud Surface")

Pindahkan objek dekoratif sehingga **menonjol keluar** dari permukaan induknya minimal `0.03m`.

```typescript
// BURUK — batten pada posisi yang sama dengan permukaan dinding
// Permukaan depan dinding di Z = -3.60, tapi batten di Z = -3.56 (DI DALAM dinding)
const batten = new THREE.Mesh(geo, mat);
batten.position.set(x, 1.8, -3.56); // tertanam dalam dinding!

// BENAR — batten 3mm di depan permukaan dinding
// Permukaan depan dinding di Z = -3.60 -> batten di Z = -3.57 (menonjol keluar)
const batten = new THREE.Mesh(geo, mat);
batten.position.set(x, 1.8, -3.57); // proud 3mm dari dinding
```

Rumus cepat untuk menghitung posisi "proud":
```
Permukaan depan dinding belakang (depth = 0.24, center Z = -3.72):
  front face Z = -3.72 + (0.24 / 2) = -3.60
  -> letakkan batten di Z = -3.57 (3mm proud dari -3.60)

Permukaan depan dinding kiri (width = 0.24, center X = -3.72):
  front face X = -3.72 + (0.24 / 2) = -3.60
  -> letakkan batten di X = -3.57 (3mm proud dari -3.60)
```

---

### Pattern 2 — `polygonOffset` Material

Untuk kasus dekoratif yang tidak bisa dipindahkan (mis. karpet di atas lantai, stiker di tembok),
tambahkan `polygonOffset` pada material objek atas (bukan yang di bawah).

```typescript
const decorativeMat = new THREE.MeshLambertMaterial({
  color: 0xd4a373,
  flatShading: true,
  polygonOffset: true,
  polygonOffsetFactor: -1,  // tarik ke depan dalam depth buffer
  polygonOffsetUnits: -1,
});
```

Catatan: Gunakan nilai negatif (-1) agar objek "naik" di depth buffer (terlihat di atas).
Jangan gunakan nilai lebih besar dari -4 karena bisa menimbulkan artefak pada objek lain.

---

### Pattern 3 — Truncate Floor / Geometry agar Tidak Overlap

Jika lantai atau dinding menembus masuk ke objek lain (mis. papan lantai yang terlalu panjang),
pangkas batas geometrinya secara eksplisit.

```typescript
// BURUK — papan lantai ujungnya melewati Z = 3.60 (menerobos ke undakan keluar)
for (let z = -3.3; z <= 3.7; z += 0.37) {
  plank.position.set(0, 0, z); // plank terakhir overlap dengan undakan!
}

// BENAR — batasi agar plank tidak melewati batas step platform
const FLOOR_END_Z = 3.51; // 9cm sebelum step platform di Z = 3.60
for (let z = -3.3; z <= FLOOR_END_Z; z += 0.37) {
  plank.position.set(0, 0, z);
}
```

---

### Pattern 4 — Shadow Bias untuk Shadow Acne

Shadow acne (garis-garis gelap pada permukaan yang menerima bayangan) diperbaiki via
`shadow.bias` dan `shadow.normalBias` pada light.

```typescript
// Untuk PointLight (chandelier dalam ruangan)
pointLight.shadow.bias = -0.0012;
pointLight.shadow.normalBias = 0.03;

// Untuk DirectionalLight (sinar matahari luar ruangan)
dirLight.shadow.bias = -0.0005;
dirLight.shadow.normalBias = 0.02;
```

---

## Quick Diagnostic Commands

```bash
# Cari semua position.set di FarmhouseInterior
grep -n "position.set" src/world/FarmhouseInterior.ts

# Cari semua BoxGeometry untuk mengidentifikasi dimensi
grep -n "BoxGeometry" src/world/FarmhouseInterior.ts
```

---

## Anti-Patterns yang Harus Dihindari

| Anti-Pattern | Solusi |
|---|---|
| Batten/dekorasi posisinya tertanam di dalam dinding | Hitung permukaan depan dinding, offset 3mm ke depan |
| Floor plank berjalan sampai batas yang sama dengan step | Truncate FLOOR_END_Z minimal 9mm sebelum step start |
| Karpet/rug di lantai tanpa polygonOffset | Tambah polygonOffsetFactor: -1, polygonOffsetUnits: -1 |
| Crown beam/trim lebih lebar dari dinding (overlap di sudut) | Sesuaikan panjang beam agar pas dengan lebar ruangan |
| depthWrite: false pada material opaque | Gunakan hanya pada transparent/blended material |

---

## Referensi Koordinat Interior Rumah (Project Ini)

Origin interior: (50, 0, 50). Semua koordinat di bawah adalah lokal (relatif origin).

| Elemen | Posisi Center | Permukaan Depan |
|---|---|---|
| Dinding belakang (Z negatif) | Z = -3.72 (depth 0.24) | Z = -3.60 |
| Dinding kiri (X negatif) | X = -3.72 (width 0.24) | X = -3.60 |
| Lantai utama | Y = -0.03 (height 0.06) | Y = 0 (top face) |
| Step undakan keluar | mulai dari Z = 3.60 | — |
| Batas lantai aman (tidak overlap step) | Z <= 3.51 | — |
