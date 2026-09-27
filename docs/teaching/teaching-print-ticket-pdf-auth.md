# 🎓 Modul Pembelajaran: Tiket Cetak untuk PDF yang Dibuka di Tab Browser

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Autentikasi vs Otorisasi, Ktor Application Plugin, JWT berumur pendek, Compose coroutine
> **Prasyarat**: Paham JWT, header `Authorization: Bearer`, dan `TenantResolutionPlugin`
> **Referensi Task**: Bugfix — `GET …/work-orders/SAMPLING/{id}/spk-card.pdf` selalu 401

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Semua tombol cetak (Lembar Kerja Rajut, Kartu Bundel, Kartu Karung, Kartu SPK A6)
  memanggil `openInBrowser(url)`. Tab browser baru **tidak pernah** mengirim header `Authorization`,
  padahal `TenantResolutionPlugin` mewajibkannya. Hasilnya `401 Authentication required` — bahkan untuk
  superadmin, karena server belum tahu *siapa* yang datang, apalagi *apa perannya*.
- **Analogi**: Kamu punya kartu akses gedung (JWT sesi), tapi yang kamu suruh mengambil paket adalah
  kurir (tab browser). Kurir tidak bisa membawa kartumu. Solusinya bukan menitipkan kartu utama
  (bocor!), melainkan memberi **surat kuasa sekali jalan**: berlaku 60 detik, hanya untuk satu loket.
- **Hasil Akhir**: Klien menukar sesi dengan tiket lewat `POST …/print-ticket` (ber-Bearer), lalu membuka
  `…/spk-card.pdf?ticket=…`. Plugin menerima tiket itu sebagai pengganti header, khusus untuk PDF.

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0 — Diagnosis dengan `curl`**: sebelum menyentuh kode, panggil URL-nya langsung. Pesan
   401 dari plugin langsung menunjuk lapisan yang salah: autentikasi, bukan renderer PDF.
2. **Langkah 1 — Kontrak tiket (`PrintTicketService`)**: tentukan dulu apa yang boleh dibuka tiket
   (scope), berapa lama, dan bagaimana ia *tidak* bisa disalahgunakan sebagai sesi.
3. **Langkah 2 — Gerbang (`TenantResolutionPlugin`)**: terima `?ticket=` hanya bila tidak ada Bearer.
4. **Langkah 3 — Penerbit (`POST /print-ticket`)**: diterbitkan setelah plugin me-resolve tenant dari
   request ber-Bearer, jadi act-as superadmin ikut terbawa secara otomatis.
5. **Langkah 4 — Klien**: `*PdfUrl` berubah jadi `suspend … : Result<String>`, dan UI memakai
   `PdfPrintLauncher`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Tiket dengan issuer terpisah — [PrintTicketService.kt](../../server/src/main/kotlin/com/eventverse/app/infrastructure/auth/PrintTicketService.kt)

```kotlin
private const val ISSUER = "wemade-erp-print"

fun verify(ticket: String, requestPath: String): TenantId? {
    val decoded = runCatching { verifier.verify(ticket) }.getOrNull() ?: return null
    val scope = decoded.getClaim(CLAIM_SCOPE).asString()?.takeIf { it.isNotBlank() } ?: return null
    if (!requestPath.endsWith(".pdf") || !requestPath.startsWith("$scope/")) return null
    return decoded.getClaim(CLAIM_TENANT).asString()?.takeIf { it.isNotBlank() }?.let(::TenantId)
}
```

**Mengapa begini?**
- Secret-nya sama dengan sesi, tapi **issuer berbeda**. Verifier `JwtTokenService` mewajibkan issuer
  `wemade-erp`, jadi tiket yang bocor lewat history browser tidak pernah bisa jadi token sesi.
- `startsWith("$scope/")` memakai garis miring penutup. Tanpa itu, tiket untuk `smp-1` ikut membuka `smp-10`.
- `endsWith(".pdf")`: tiket tidak bisa dipakai memanggil `/allocation` atau route JSON lain.

### Blok B: Gerbang plugin — [TenantResolutionPlugin.kt](../../server/src/main/kotlin/com/eventverse/app/plugins/TenantResolutionPlugin.kt)

```kotlin
if (bearerToken.isNullOrBlank() && printTickets != null && !printTicket.isNullOrBlank()) {
    val tenant = printTickets.verify(printTicket, path)?.let { repository.findById(it) }
    when {
        tenant == null -> call.respond(HttpStatusCode.Unauthorized, "Tiket cetak tidak sah …")
        !tenant.isAccessible -> call.respond(HttpStatusCode.Forbidden, …)
        else -> call.attributes.put(TenantContextAttributeKey, TenantContext.fromTenant(tenant))
    }
    return@onCall
}
```

**Mengapa begini?**
- Bearer selalu menang. Tiket hanya jalur cadangan untuk tab browser.
- Tenant yang disuspend tetap ditolak. Tiket tidak boleh jadi jalan belakang melewati pengecekan status tenant.
- Request bertiket **tidak** punya `CallerPrincipal`. Karena itu `POST /print-ticket` mewajibkan principal:
  tiket tidak bisa dipakai untuk menerbitkan tiket baru.

### Blok C: Klien — [TraceabilityApiClient.kt](../../app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/TraceabilityApiClient.kt) & [SpkPrintActions.kt](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/SpkPrintActions.kt)

```kotlin
printer.open { spkCardPdfUrl(it, ref) }   // it = tenantSlug dari sesi
```

**Mengapa begini?** Membuka PDF sekarang butuh satu panggilan jaringan, jadi bisa gagal.
`PdfPrintLauncher` menyatukan coroutine, pengambilan tenant slug, dan pesan error di satu tempat,
lalu dipakai di dua layar (SPK Print Actions dan Detail SPK Sampling).

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Kenapa ini | Risiko alternatif |
|---|---|---|---|
| Tiket 60 detik, scope sempit | JWT sesi di query string | Kebocoran URL hanya membuka 1 PDF selama 1 menit | Token 7 hari tercatat di history & log proxy |
| Tiket stateless, boleh dipakai berulang dalam 60 detik | Tiket benar-benar sekali pakai (simpan `jti`) | Penampil PDF browser bisa meminta ulang URL (range request/reload) | PDF gagal termuat separuh jalan |
| Tiket via URL | Unduh via `httpClient` → Blob/file sementara | Tanpa kode `expect/actual` per platform; dialog cetak browser tetap dipakai | 5 implementasi platform |
| Default `PrintTicketService()` di config plugin & route | Wiring di `Application.kt` | `Application.kt` 698 baris, di atas hard limit → Aturan Ratchet | Melanggar §14 |

---

## ⚠️ 5. Jebakan Pemula

1. **"Saya superadmin, harusnya bisa"** — peran baru dicek *setelah* identitas diketahui. Tanpa header,
   server tidak pernah sampai ke pengecekan peran.
2. **Issuer yang sama untuk tiket dan sesi** — tiket jadi token sesi 60 detik yang bisa memanggil semua API.
3. **Scope tanpa `/` penutup** — prefix match yang bocor ke id lain.
4. **Popup blocker** — `window.open` setelah `await` masih diizinkan browser dalam beberapa detik setelah klik.
   Jangan menaruh operasi berat sebelum membuka tab.

---

## 🧪 6. Pembuktian

[PrintTicketServiceTest.kt](../../server/src/test/kotlin/com/eventverse/app/infrastructure/auth/PrintTicketServiceTest.kt):
PDF di dalam scope diterima; work order lain, id berawalan sama, route non-PDF, dan tiket kedaluwarsa
ditolak; dan yang terpenting: `ticket used as session token should be rejected`.

Manual: restart server → klik "Kartu SPK A6" → tab baru berisi PDF. Tanpa `?ticket=` → 401. Setelah
lebih dari 60 detik → "Tiket cetak tidak sah atau sudah kedaluwarsa".

---

## 🏆 7. Tantangan Mandiri

- [ ] Tambahkan integration test Ktor (`testApplication`) untuk alur penuh: POST tiket → GET PDF.
- [ ] Batasi tiket ke satu dokumen (`spk-card.pdf` saja), bukan semua PDF work order.
- [ ] Catat `sub` tiket ke audit log saat PDF dibuka, supaya cetakan tetap terlacak ke orangnya.
