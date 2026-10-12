# 🎓 Modul Pembelajaran: Track 0 — Kontrak Packing Karton & Pengiriman Eksternal (TRD-FUL-001)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Contract-First Development, Value Object, Codec JSON, Strangler Fig ringan, test kontrak terkunci
> **Prasyarat**: Memahami DDD dasar di repo ini (`AGENTS.md` §3), pola codec `InternalTransferCodec`
> **Referensi Task**: [TRD-FUL-001](../trd/features/TRD-FUL-001-packing-dan-pengiriman.md) · [PLAN fulfillment](../plannings/mvp/PLAN-fulfillment-packing-shipping.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Fitur packing & pengiriman eksternal dikerjakan oleh **3 track paralel** (A: domain, B: server, C: UI).
Bayangkan tiga tukang bangunan membangun tiga lantai rumah secara bersamaan — kalau fondasi
(ukuran pintu, letak pipa) tidak dibekukan dulu, lantai dua dan tiga akan saling menabrak dan
jebol saat disatukan.

- **Masalah nyata**: tanpa kontrak yang dibekukan, Track B menulis tabel dengan nama kolom lain
  dari yang dibaca Track C; Track A mengubah signature use case di tengah jalan; integrasi jadi
  neraka refactor.
- **Hasil akhir Track 0**: value object + status enum + interface repository + codec JSON +
  fixture + tes — **dibekukan**. Track A/B/C hanya boleh *mengisi* kontrak, bukan mengubahnya.
  Perubahan kontrak = kembali ke Track 0, disetujui, dites dulu.

**Pelajaran meta**: "Contract First" bukan upacara dokumen — ia adalah *kode yang dikompilasi
dan ditest* sehingga pelanggaran kontrak ketahuan oleh kompilator/CI, bukan oleh bug di produksi.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Baca yang sudah ada (jangan tulis ulang).** Modul `FULFILLMENT` sudah punya
   transfer internal (`InternalTransfer`, TRD-FLOW-003). VO/status/entity kami (commit `70380f4c`)
   sudah mengikuti polanya: immutable `data class`, validasi di `init`, transisi status lewat
   fungsi yang me-`copy`. Aturan emas repo: *tiru pola yang sudah ada*.
2. **Langkah 1 — Value Objects** (`CartonValueObjects.kt`, `ShipmentValueObjects.kt`):
   `CartonNumber`/`ShipmentNumber` membawa **regex format di konstruktor**. Kenapa? Karena nomor
   `CTN-2026-0001` yang korup harus ditolak di gerbang paling awal, bukan lolos sampai SQL.
3. **Langkah 2 — Status enum + invariant entity.** `PackingCarton.init` menolak kombinasi mustahil
   (SEALED tanpa seal number, SEALED kosong). Invariant di konstruktor = mustahil dibuat objek ilegal.
4. **Langkah 3 — Repository interface** (bukan implementasi!). Perhatikan: **tidak ada `delete`** —
   karton salah diperbaiki lewat reopen (jejak audit), bukan dihapus. Tambahan Track 0 kali ini:
   `getNextCartonSequence` — mirror dari `getNextShipmentSequence`, karena U2 butuh nomor urut
   transaksional. Mengapa ada di interface domain? Karena *kebijakan* "nomor unik per tenant" adalah
   aturan bisnis; *cara* transaksionalnya baru urusan Track B (Postgres).
5. **Langkah 4 — Codec** (`PackingCartonCodec`, `DispatchShipmentCodec`): cermin domain.
   Decode **menolak** status/nomor/format qty yang tak dikenal (Kontrak 4 tenant-variability:
   tolak, bukan fallback senyap). Field asing diabaikan demi kompatibilitas depan-belakang.
6. **Langkah 5 — Fixture + tes kontrak.** Lihat bagian 6.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — Format nomor dikunci di value object

```kotlin
@JvmInline
value class CartonNumber(val value: String) {
    init {
        require(FORMAT_REGEX.matches(value)) {
            "CartonNumber wajib format CTN-TAHUN-urutan, contoh: CTN-2026-0001"
        }
    }
    companion object {
        private val FORMAT_REGEX = Regex("^CTN-\\d{4}-\\d{4}$")
        fun generate(year: Int, sequence: Int) =
            CartonNumber("CTN-${year}-${sequence.toString().padStart(4, '0')}")
    }
}
```

**Mengapa begini?**
- Validasi di konstruktor → tidak ada jalan membuat `CartonNumber("KARDUS-BEBAS")` yang lolos.
- `generate()` pusat pembentukan format — tidak ada string template `CTN-...` berserakan di 10 file.
- Decode JSON yang membawa nomor bebas **melempar** `IllegalArgumentException` — bukan dipaksa kosong.

### Blok B — Decode menolak, bukan menebak

```kotlin
status = enumOrNull<CartonStatus>(obj.string("status"))
    ?: throw IllegalArgumentException("Status karton tak dikenal: ${obj.string("status")}")
```

**Mengapa begini?** Fallback senyap = data berubah diam-diam. Contoh nyata di repo ini:
parser `GarmentBusinessPreset` dulu *fallback ke FOB* dan menyebabkan SPK bordir terbaca
sebagai alur FOB. Di sini kita belajar: payload korup **gagal terbaca dengan teriakan**.

### Blok C — Pivot sebagai bagian kontrak

`DispatchShipmentCodec.encode(shipment, cartonIds)` dan `DecodedShipment` mengembalikan
dua hal: shipment + daftar `CartonId` terikat. Pivot `shipment_cartons` milik repository
(Track B), tapi **bentuk JSON-nya** harus dibekukan sekarang — kalau tidak, Track B dan C
akan menemukan dua bentuk berbeda saat integrasi.

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Keputusan | Alternatif | Mengapa | Risiko Alternatif |
|---|---|---|---|
| Fixture JSON **inline di file tes** (pola `HandoverRouteSettingsCodecTest`) | File `.json` di `commonTest/resources` | KMP `commonTest` tidak punya mekanisme baca resource lintas platform yang seragam; nol preseden di repo | Fixture terpisah dari asersi, lebih sulit dibaca |
| `JsonValue` rumahan (`shared/json`) | kotlinx.serialization / Jackson | Pola yang sudah dipakai semua codec repo; objek berurutan, parser ketat; tanpa tambahan dependency | Lebih banyak kode tulis tangan — tertutup tes kontrak |
| Status enum + tolak tak dikenal | String bebas / fallback default | State machine butuh himpunan tertutup; Kontrak 4 variability | Enum untuk konsep bervariasi per tenant adalah anti-pola — status lifecycle *milik sistem*, jadi enum sah (Uji Variabilitas lolos) |
| Nomor unik per tenant, seq transaksional | UUID/global sequence | Nomor SJ yang bisa diucapkan/diketik manusia untuk telepon dengan buyer; unik per tenant cukup | Konkurensi butuh counter dalam transaksi + unique index (mitigasi di TRD §4.5) |

---

## ⚠️ 5. Jebakan Pemula

1. **"Decode diberi default biar gampang"** — *Kenapa bahaya*: lihat Blok B; data korup berubah
   jadi data salah tanpa jejak. *Solusi kita*: lempar `IllegalArgumentException` dengan pesan jelas.
2. **Menulis kode Track A/B/C di PR Track 0** — *Kenapa bahaya*: kontrak bocor berubah di tengah
   tiga track paralel. *Solusi kita*: PLAN §6 — konflik hanya diizinkan pada
   `FulfillmentDomainEvents.kt` (append event), diselesaikan integrator.
3. **Nomor urut dari `max(number)+1` tanpa transaksi** — dua staf packing bersamaan mendapat
   `CTN-2026-0007` ganda. *Solusi kita*: unique index per tenant; bentrok → 409, bukan 500 (TRD §4.5).
4. **Fixture dianggap bebas diubah** — fixture adalah kontrak eksternal; mengubahnya tanpa
   melewati Track 0 = mengubah kontrak diam-diam.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Bekerja?

`PackingShipmentCodecTest` (12 tes, `:core:jvmTest` hijau — 1906 tes, 0 gagal):

- **Bentuk terkunci**: `encode` dibandingkan dengan string JSON literal persis. Ubah urutan kunci
  di `jsonObjectOf` → tes merah. Ini pagar agregat vs codec-nya.
- **Round-trip**: `decode(encode(x)) == x` untuk karton OPEN, SEALED, shipment DELIVERED,
  dan pivot `cartonIds`.
- **Fixture mock eksternal**: dua payload JSON seolah ditulis tenant lain (`bordir-uji`) —
  harus terbaca *persis*, termasuk `StockOwnership.CONSIGNED` dan `deliveredAt: null`.
- **Penolakan payload korup**: status `"LUSUH"`/`"HILANG"`, nomor `"KARDUS-BEBAS"`,
  qty `"dua"` → semua `IllegalArgumentException`. Dan satu tes positif: field asing
  `"futureField":123` diabaikan (kompatibilitas versi).

Jalankan: `./gradlew :core:jvmTest --tests "*PackingShipmentCodecTest*"`.

---

## 🏆 7. Tantangan Mandiri

- [ ] Tulis tes `decode - sealNumber kosong string diperlakukan sebagai null` dan pastikan
      `.takeIf { it.isNotBlank() }` memang punya perilaku itu. Kenapa null lebih jujur daripada `""`?
- [ ] `SealNumber.generate()` memakai `System.currentTimeMillis()` + random. Cari di repo pola
      ID lain yang deterministik. Apa kelemahan segel acak untuk audit? Usulkan alternatif Track 0-
      compatible.
- [ ] `itemBreakdown` menolak nilai non-angka, tapi **menerima qty negatif**. Cek: apakah
      invariant domain menutupnya? Bila belum, di lapisan mana sebaiknya ditutup — codec, entity,
      atau route? (Petunjuk: Kontrak 8 field-component — satu pintu validasi.)
