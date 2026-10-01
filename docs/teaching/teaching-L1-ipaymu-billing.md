# Teaching: L1 — Billing iPaymu (PaymentGateway port + callback publik + kolom trx_id)

Rujukan: `docs/plannings/discovery-L1-ipaymu-billing.md` · `docs/trd/TRD-PAY-001` FR-PAY-3 · infra bridge: [`teaching-pay-001-ipaymu-testing-bridge.md`](teaching-pay-001-ipaymu-testing-bridge.md)

## 1. Apa yang dibangun

Billing langganan platform kini bisa dibayar via iPaymu Hosted Checkout:

- **Domain** (`core/domain/builder/IpaymuPayment.kt`, `IpaymuPaymentUseCases.kt`):
  - `PaymentGateway` port (create checkout + cek status → `IpaymuTransactionCheck(status, sessionId)`) — HTTP detail di infrastruktur.
  - `IpaymuTransactionStatus.fromApi` — parser **ketat** (Kontrak 4) dengan **kode resmi docs**: `1,6`→PAID, `0,7`→PENDING, `-2`→EXPIRED, `2,3,4,5`→FAILED; status tak dikenal ditolak, tanpa fallback.
  - `CreateInvoiceCheckoutUseCase` — idempoten: klik "Bayar" dua kali tidak membuat dua transaksi.
  - `HandleIpaymuNotificationUseCase` — urutan kepercayaan callback: cari via **`sid`** (= SessionID tersimpan di `ipaymu_trx_id`) → PAID = no-op → VOID = tolak → **nominal ≠ totalIdr = tolak** → **status dicek ulang via `trx_id` numerik** dan `Data.SessionId` hasil cek **wajib sama** dengan `sid` tersimpan (anti-replay) → `ConfirmSubscriptionPaymentUseCase` reuse.
- **Persistensi**: V86 menambah `ipaymu_trx_id VARCHAR(100) NULL` + unique partial index (invoice manual = NULL). Aditif; teruji `BEGIN…ROLLBACK` di Postgres dev. **Isi kolom = SessionID dari create** (`Data.SessionID`), bukan `trx_id`.
- **Server**:
  - `IpaymuCallbackRoutes.kt` — `POST /api/payment/ipaymu/notify` **publik** (aktor mesin), terima form-urlencoded & JSON datar. **Selalu balas HTTP 200** (docs: non-200 = retry tanpa akhir); penolakan lewat body `accepted:false`. **`X-Signature` diverifikasi** (secret = Nomor VA, via `IPAYMU_VA`): normalisasi tipe → sort key A-Z → JSON.stringify → escape `/` → HMAC-SHA256.
  - `POST /api/builder/billing/invoices/{id}/checkout` (superadmin; **503** bila gateway belum dikonfigurasi).
  - `IpaymuClient.kt` — Ktor **Apache5** (satu-satunya engine di katalog yang mendukung proxy+CONNECT), proxy kondisional dari `IPAYMU_OUTBOUND_PROXY`, **signature resmi `METHOD:VA:sha256hex(body):APIKey`** (HMAC-SHA256, kunci APIKey; header `va`/`timestamp` epoch millis/`signature`), body create `product[]/qty[]/price[]/description[]` + `returnUrl/notifyUrl/cancelUrl/referenceId/buyerName/buyerEmail`, respons dibaca di objek `Data` (case-insensitive), semua kredensial via `EnvLoader`.
- **UI**: belum — tombol "Bayar via iPaymu" & pemakaian `paymentUrl` menyusul di BuilderBillingPane.

## 2. Pelajaran implementasi

1. **SecretKeySpec ambiguitas**: `mac.init(SecretKeySpec(...))` gagal compile karena SecretKeySpec
   mengimplementasikan `Key` *dan* `AlgorithmParameterSpec` (dua overload `init`). Pilih eksplisit
   via variabel bertipe `java.security.Key`.
2. **Pulumi runtime**: `runtime: exec` tidak didukung CLI; pakai `runtime: java` (language host
   mengenali build Gradle mandiri ini).
3. **Uji berbasis kepercayaan, bukan keberhasilan**: test paling penting justru yang callback-nya
   "berhasil" tapi gateway bilang PENDING → invoice tetap ISSUED. Idempotensi dites dengan
   membandingkan `paidAt` dua callback.
4. **Regression triase**: 6 failure di `:server:test` (Costing/MasterData/TechPack/AccessSnapshot)
   ternyata pre-existing — dibuktikan dengan `git stash -u` lalu menjalankan test yang sama di
   baseline (16 FAILED, lebih parah). Jangan asal "perbaiki" test yang merah sebelum triase.
5. Server tidak punya kotlinx-serialization — body request dirakit manual, field respons diekstrak
   dengan regex ketat yang error bila field tidak ada (bukan null).
6. **Audit docs dulu, kode kemudian** (koreksi W1–W7, 2026-10-01): lima asumsi implementasi awal
   ternyata salah dibaca docs resmi — urutan signature (`METHOD:VA:hash:APIKey`, bukan
   `hash:METHOD:VA`), timestamp **epoch millis**, create body (`description[]` wajib; tanpa
   `name/email/phone/amount`), respons create tanpa `trx_id` (yang ada `Data.SessionID`/`Data.Url`),
   dan kode status (`1,6`→PAID vs implementasi awal `1`→PENDING — **terbalik berbahaya**).
   Pelajarannya: halaman Signature/Redirect/Check/Callback dibaca **berurutan** sebelum menulis
   client, bukan menyusul.
7. **`Result` di dalam `Result`**: route membungkus `runCatching { handle(...) }` padahal use case
   sudah mengembalikan `Result` — kegagalan domain lewat jalur `onSuccess` dengan nilai failure,
   sehingga `accepted:false` tak pernah terkirim. Flatten dengan
   `runCatching { handle(n).getOrThrow() }`. (stdlib tidak punya `Result.flatMap`.)
8. **Test pin algoritma eksternal dengan konstanta**: test X-Signature memakai hex yang dihitung
   di luar kode produksi (python) untuk payload tetap — kalau implementasi & test sama-sama salah,
   konstantanya tetap membongkarnya. Hati-hati urutan sort: `transaction_status_code` <
   `trx_id` secara code-unit — salah satu penyebab konstanta pertama meleset.
9. **Route test lulus ≠ rute hidup**: test callback hanya memasang `routing {}` tanpa
   `TenantResolutionPlugin`, sehingga webhook lolos test tapi **401 di server sungguhan** —
   `publicRoutePrefixes` tidak memuat `/api/payment`. Perbaikan: prefix publik diekstrak jadi
   `PublicRoutePrefixes` (satu sumber, dipakai `Application.kt`), plus test regresi yang memasang
   `module()` penuh. Pelajarannya: verifikasi *wiring* (plugin, allowlist, instalasi) hanya bisa
   diuji lewat komposisi yang sama dengan produksi — atau lewat mata/curl di server jalan.
10. **Default `additional_info` masuk hash**: implementasi mematukkan `additional_info: []`
    (docs: default `[]`) bahkan bila callback tidak membawanya — HMAC manual harus mengikutkan
    field default yang sama, kalau tidak signature selalu mismatch. Terbukti live: payload tanpa
    `additional_info` ditolak, dengan `"additional_info":[]` di hash → lolos ke lapisan domain.

## 3. Konfigurasi (.env root, dibaca EnvLoader)

```
IPAYMU_VA=...            IPAYMU_API_KEY=...     IPAYMU_BASE_URL=https://sandbox.ipaymu.com/api/v2
IPAYMU_NOTIFY_URL=https://ipaymu-hook-<dev>.wemakeerp.com/api/payment/ipaymu/notify
IPAYMU_RETURN_URL=http://localhost:3001/builder/billing    # browser pembeli, BUKAN tunnel
IPAYMU_OUTBOUND_PROXY=   # kosong = langsung; isi http://127.0.0.1:8888 saat egress OCI aktif
```

Tanpa kredensial: checkout → 503; callback tetap terpasang dengan gateway stub — callback valid
dijawab 200 + `accepted:false` (fail-closed; selalu 200 sesuai docs). `IPAYMU_VA` juga dipakai
sebagai secret verifikasi `X-Signature` callback.

## 4. Rekonsiliasi (FR-PAY-3.3 butir 7, V87)

Callback adalah *push* sekali jalan — bila tidak pernah sampai (tunnel mati saat pembayaran,
retry iPaymu habis), invoice yang faktanya sudah dibayar selamanya `ISSUED`. Penutupnya:

- **V87**: kolom `ipaymu_trx_numeric` — endpoint cek status iPaymu butuh `trx_id` **numerik**,
  dan angka itu hanya diketahui dari callback. Handler callback kini merekamnya di **setiap**
  callback (bahkan yang PENDING/idempoten), sebelum verifikasi apa pun.
- **`ReconcileSubscriptionInvoicesUseCase`** (`IpaymuReconciliation.kt`): invoice `ISSUED` +
  punya numeric + usia ≥ 1 jam (param `minAge` — callback resmi mungkin masih di antrean) →
  `checkStatus` → anti-replay yang sama (sessionId wajib cocok) → PAID dilunasi lewat
  `ConfirmSubscriptionPaymentUseCase`; PENDING/EXPIRED/FAILED dibiarkan — void/refund adalah
  keputusan manusia.
- **`POST /api/admin/billing/reconcile`** (`IpaymuReconciliationRoutes.kt`): pemicu manual
  superadmin, ter-audit per invoice (`BUILDER_INVOICE_PAID`). Path `/api/admin` dipilih karena
  plugin meloloskannya sebagai administrasi platform — superadmin **tanpa** konteks tenant
  (rekonsiliasi melintasi semua tenant; di path `/api/builder` superadmin tanpa
  `X-Tenant-Slug` malah 404 dari plugin). Gateway `null` → 503 fail-closed.
- **Batas jujur, sudah dieksperimen live (2026-10-01)**: invoice yang belum pernah dikirimi
  callback apa pun tidak punya `trx_id` numerik → tidak bisa dicek — `/transaction` **menolak
  SessionID** (400 "transaction not found", lihat `scripts/ipaymu-live-probe.sh`). Jalurnya:
  dashboard iPaymu + konfirmasi manual superadmin. Scheduler otomatis menunggu `Application.kt`
  bebas; endpoint manual sudah menutup kasus operasional.

## 5. Yang belum tuntas (jujur di Gate 7)

- **Cek visual** belum: belum ada UI baru yang bisa dilihat; jalankan setelah tombol checkout dibuat.
- **Integrasi live iPaymu**: koreksi W1–W7 sudah dilakukan terhadap audit docs.ipaymu.com
  (signature, redirect, check transaction, callback — TRD §6 #1/#2 **terjawab**), tapi **E2E
  sandbox live belum**: isi `.env` (`IPAYMU_VA`, `IPAYMU_API_KEY`, `IPAYMU_BASE_URL`,
  `IPAYMU_NOTIFY_URL`), jalankan `./tunnel.sh` + server, lalu satu transaksi uji dari dashboard
  sandbox iPaymu.
- Endpoint checkout belum punya test rute sendiri (gate superadmin-nya memakai helper lama yang
  sudah ter-coverage di suite billing); callback publik punya **8 test** (termasuk X-Signature
  valid/invalid, `status_code=6`→PAID, penolakan `sessionId` mismatch).
