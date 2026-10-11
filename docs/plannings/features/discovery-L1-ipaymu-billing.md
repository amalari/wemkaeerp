# Discovery Note — L1: Billing iPaymu (Payment Gateway Langganan Platform)

Tanggal: 2026-10-01 · Rujukan: TRD-PAY-001, PLAN-builder-console.md §8, FR-PAY-3

## 1. Kebutuhan bisnis
- **Siapa**: superadmin platform (konfirmasi manual tetap ada) + **iPaymu** sebagai aktor mesin (`system:ipaymu`) yang mengirim callback; tenant membayar invoice `SubscriptionInvoice` via VA/QRIS Hosted Checkout.
- **Data milik**: invoice milik tenant (`builder.subscription_invoices`, V85, RLS); callback publik.
- **Kapan berubah**: per transaksi pembayaran (status invoice ISSUED → PAID).

## 2. Fitur serupa
- **Ada fondasinya**: `SubscriptionBillingUseCases.kt` — `IssueSubscriptionInvoiceUseCase` & `ConfirmSubscriptionPaymentUseCase` (sudah idempoten untuk PAID, menolak VOID) + `BuilderBillingRoutes.kt` (pola gate superadmin/tenant, audit `BUILDER_INVOICE_PAID`). **Extend dari sini, bukan buat paralel.**
- Tidak ada `PaymentGateway` port & tidak ada route publik webhook sebelumnya → bagian benar-benar baru.
- `ktor-client-core` + `ktor-client-mock` sudah di katalog (`libs.versions.toml:63-64`), Ktor 3.5.1, **belum ada engine**.

## 3. Jenis
**Fitur dalam modul builder** (bukan modul baru) — mewarisi gate builder, tanpa entri `BusinessModule`, tanpa kanvas. Route callback berada **di luar `/api/tenant/`** sehingga tidak dijaga `RouteOwnershipTest`, tapi tetap wajib tidak membuka data lain.

## 4. Uji Variabilitas
| Konsep | Beda per tenant? | Per industri? | Admin ubah? | → Kode/Data |
|---|---|---|---|---|
| Status transaksi iPaymu | tidak (API iPaymu) | tidak | tidak | kode (enum peta) |
| TrxId ↔ invoice mapping | tidak | tidak | tidak | kolom data (baru) |
| URL/VA/API key | per environment | — | — | env (`EnvLoader`) |

## 5. Core & titik extend
- **Core**: `core/domain/builder/` — tambah `PaymentGateway` port + `IpaymuCallback` value object + use case orkestrasi (peta `trx_id`→invoice, cek nominal vs `totalIdr`, panggil `ConfirmSubscriptionPaymentUseCase` dengan identitas audit `system:ipaymu`).
- **Server**: `routes/IpaymuCallbackRoutes.kt` (publik, terima form-urlencoded & JSON) didaftarkan via `DomainRouteWiring.registerIn`; `infrastructure/IpaymuPaymentGateway.kt` (Ktor client + engine ber-proxy), config via `EnvLoader` (`IPAYMU_VA`, `IPAYMU_API_KEY`, `IPAYMU_BASE_URL`, `IPAYMU_OUTBOUND_PROXY`, `IPAYMU_NOTIFY_URL`, `IPAYMU_RETURN_URL`).
- **Persistensi**: kolom `trx_id` (nullable, unik) di `builder.subscription_invoices` — migrasi baru (pola V85) + update `PostgresSubscriptionInvoiceRepository`.
- **Contoh pola**: `BuilderBillingRoutes.kt:52-114` (gate, audit, respond), `PostgresSubscriptionInvoiceRepository.kt`, `SubscriptionBillingUseCaseTest.kt`.
- **Jangan sentuh**: file di tabel utang file-size (`DealDetailDialog.kt`, `OrgChartScreen.kt`, dst.), `Application.kt` (ratchet 697 → ≤600; wiring lewat `DomainRouteWiring`, bukan edit besar).

## 6. I/O & kanvas
- Bukan node kanvas (bukan modul operasional). Port: `PaymentGateway.createPayment(invoice)` → URL checkout; callback masuk sebagai HTTP → `ConfirmSubscriptionPaymentUseCase`.
- Telemetri: belum ada.

## 7. Governance
- Route callback **publik** (aktor mesin iPaymu, tanpa JWT) — keamanan lewat **cek ulang status ke API iPaymu per `trx_id`**, bukan percaya payload; nominal wajib = `totalIdr`.
- Operasi lain (issue/confirm manual) tetap di gate superadmin lama.
- `ScopeCapability`: tidak berlaku (bukan modul).
- Test wajib: idempotensi callback, nominal ≠ total ditolak, VOID ditolak, peran tidak berwenang memicu konfirmasi manual tetap 403.
