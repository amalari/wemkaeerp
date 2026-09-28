# 🎓 Modul Pembelajaran: `SamplingRoute` di Atas Kerangka `StageCode` (TRD-FLOW-001 Tahap 2c)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Menghapus kerangka hardcode, satu sumber kebenaran, batas jembatan, cache kompilasi
> **Prasyarat**: [Tahap 2b](teaching-flow-001-tahap-2b-process-anchor.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 2, `sampling` bagian 1/3

---

## 💡 1. Konsep Dasar

`SamplingRoute` menjawab "kartu **ini** diserahkan ke meja mana berikutnya?". Dulu ia berjalan di
atas `SamplingPipelineStage.entries` — kerangka rajut yang tertanam di kode. Lebih parah, server
menulis ulang kerangka yang sama di **empat tempat**: `stages = SamplingPipelineStage.entries`.

Sekarang rute berjalan di atas `frame: List<StageCode>`, dan keempat tempat itu merujuk **satu**
nama: `SamplingRoute.DEFAULT_FRAME`. Di Tahap 3, hanya isi nama itu yang diganti menjadi kerangka
milik tenant.

---

## 🧭 2. "Start dari Mana?"

1. Pilih objek yang menjawab pertanyaan bisnis (di sini: rute), bukan field-nya.
2. Beri objek itu **frame sebagai parameter** dengan default yang identik dengan perilaku lama.
3. Biarkan kompilator menunjukkan semua tempat yang masih memakai kerangka lama.

---

## 🧱 3. Bedah Kode

### Blok A — Frame sebagai parameter

```kotlin
data class SamplingRoute(
    val skipped: Set<StageCode> = emptySet(),
    val frame: List<StageCode> = DEFAULT_FRAME
) {
    fun nextAfter(code: StageCode): StageCode? {
        val index = frame.indexOf(code)
        if (index < 0) return null
        return frame.drop(index + 1).firstOrNull { it !in skipped }
    }
}
```

- Logika lama: *"entri pertama dengan `order` lebih besar yang tidak dilompati"*. Logika baru:
  *"elemen pertama sesudah posisi ini di frame yang tidak dilompati"*. Untuk frame rajut,
  keduanya identik — dan itu dijaga test, bukan diasumsikan.
- `DEFAULT_FRAME` diambil dari `IndustryStageTemplates`, **bukan** dari enum. Jadi paritas
  enum↔template (Tahap 1) otomatis ikut menjaga rute.

### Blok B — Batas jembatan yang harus diketahui

```kotlin
fun nextAfter(stage: SamplingPipelineStage): SamplingPipelineStage? =
    nextAfter(stage.toStageCode())?.toSamplingStageOrNull()
```

- Di frame bordir, sesudah `FLOW_REVIEW` adalah `DIGITIZING` — tidak ada enum-nya → `null`.
  Artinya pemanggil yang masih memakai enum akan mengira kartu **sudah di tahap terakhir**.
- Ini tidak berbahaya hari ini (frame selalu rajut), tapi menjadi syarat keras: template kedua
  baru boleh aktif setelah semua pemanggil pindah. Batas ini ditulis sebagai test
  (`enumBridge_onNonKnitFrame_…`) dan sebagai constraint di TRD — supaya tidak ada yang lupa.

### Blok C — Tipe yang mengalir satu arah

`StagePhaseTags.skippedSamplingStages` → `Set<StageCode>` → `SamplingRoute.skipped` →
`GetFlowTransferLegsQuery.skippedStages`. Mengubah ketiganya bersamaan membuat server tidak perlu
satu pun `.map { toStageCode() }`; nilainya sudah bertipe benar sejak sumbernya.

---

## ⚠️ 4. Jebakan Pemula

1. **Mempercayai error kompilasi yang tidak masuk akal.** Setelah daemon Kotlin crash, test auth
   yang tidak disentuh tiba-tiba "Unresolved reference 'Department'". Itu cache inkremental yang
   rusak, bukan kode. Periksa dulu (`git status` file itu, apakah kelasnya ada), lalu
   `./gradlew <task> --rerun` — jangan "memperbaiki" test yang tidak rusak.
2. **Mempercayai build 12 detik.** Cek hasil test lebih baru dari file yang diubah
   (`find … -newer`), baru klaim "semua test lulus".
3. **Menulis kerangka di banyak tempat.** Empat salinan `SamplingPipelineStage.entries` adalah
   empat tempat yang harus diingat saat kerangka berubah. Satu nama = satu tempat.

---

## 🧪 5. Pembuktian

- **Nol file test lama diubah** di langkah ini; `SamplingPhaseRouteTest` dkk. hijau apa adanya.
- `core` 128/128 → +1 kelas baru `SamplingRouteStageCodeTest` (3 test); `app:shared` 24/24;
  hasil diverifikasi segar.
- Kompilasi `core`/`app:shared` JVM/JS/wasm dan `server` main+test — hijau.

---

## 🏆 6. Tantangan Mandiri

- [ ] `SpkCardBuilder` menghitung `stageNumber = route.stages.indexOf(order.pipelineStage) + 1`.
      Tulis ulang dengan `stageCodes` — apa yang harus terjadi pada `pipelineStage` dulu?
- [ ] Hapus `SamplingRoute.FULL` bila setelah bagian 3 tidak ada pemanggil yang butuh.
