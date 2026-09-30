# TRD-PAY-001: iPaymu Testing Bridge & Egress Gateway (OCI Jakarta + Pulumi Kotlin + Cloudflare Tunnel)

## 1. Document Context and Administration

- **Title & Unique ID**: iPaymu Testing Bridge & Egress Gateway — `TRD-PAY-001`
- **Revision History**

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 1.0 | 2026-10-01 | Principal Architect (Antigravity) | Spesifikasi teknis arsitektur testing gateway iPaymu menggunakan Pulumi Kotlin, OCI Jakarta, dan Cloudflare Tunnel. Rujukan: [`PLAN-ipaymu-testing-bridge.md`](../plannings/PLAN-ipaymu-testing-bridge.md). |

### Summary & Business Context
Pada integrasi pembayaran platform WeMake ERP / EventVerse (khususnya penagihan langganan Builder Billing M2 dan transaksi operasional tenant), sistem membutuhkan koneksi ke Payment Gateway **iPaymu**.
iPaymu memberlakukan dua gerbang keamanan ketat:
1. **Outbound IP Whitelist**: Setiap HTTP request pembuatan transaksi, VA, QRIS, maupun cek saldo wajib berasal dari IP Publik Statis terdaftar. Request dari IP dinamis (misal laptop developer atau ISP residensial) menghasilkan `403 Forbidden`.
2. **Inbound Domain HTTPS**: Webhook notifikasi pembayaran (`notify_url`) wajib ditembakkan ke domain terverifikasi ber-SSL resmi (HTTPS), menolak raw IP dan domain `localhost`.

Menyewa VPN manual berbayar di laptop terbukti menimbulkan diskoneksi jaringan lokal, tidak menyediakan jalur ingress webhook, dan tidak *reproducible*. TRD ini mendefinisikan infrastruktur jembatan pengujian otomatis berbasis Infrastructure as Code (IaC) **Pulumi Kotlin**, memanfaatkan **OCI Always Free Tier (Region Jakarta)** sebagai egress gateway IP statis, dan **Cloudflare Tunnel** sebagai ingress webhook aman ke laptop lokal.

### Stakeholders & Approvers
- **Product & Tech Lead**: Achmad Jamaludin
- **Implementasi**: Lead Platform / Infrastructure Engineer
- **QA & Verification**: Integration & Payment Gateway Test Suite

### Goals (In-Scope)
- **IaC Pulumi Kotlin**: Proyek Gradle JVM yang mendefinisikan seluruh resource OCI dan Cloudflare secara deklaratif dan typed-safe menggunakan bahasa Kotlin.
- **OCI Egress Gateway (Jakarta `ap-jakarta-1`)**:
  - Virtual Cloud Network (VCN), Internet Gateway, Route Table, dan Security List.
  - Reserved Public IPv4 Statis (permanen) di region Jakarta.
  - Compute Instance Ampere A1 ARM / AMD Micro dengan provisioning `cloud-init` otomatis untuk instalasi dan konfigurasi `tinyproxy`.
- **Cloudflare Ingress Tunnel**:
  - Provisioning Cloudflare Zero Trust Tunnel via Pulumi.
  - DNS CNAME routing dari subdomain `ipaymu-hook.<domain>` ke Tunnel Argo endpoint.
  - Generasi dan ekspor `tunnel_token` untuk dijalankan via daemon `cloudflared` di laptop developer.
- **Ktor Payment Client Integration**:
  - Konfigurasi HTTP Client Ktor (CIO/OkHttp engine) dengan dukungan conditional proxy via environment variable `IPAYMU_OUTBOUND_PROXY`.
  - Endpoint receiver webhook `post("/api/payment/ipaymu/notify")` yang kompatibel menerima callback transaksi dari Cloudflare Tunnel.

### Non-Goals (Out-of-Scope)
- Penyimpanan kartu kredit langsung (PCI-DSS Level 1) — iPaymu menangani pembayaran via Hosted Checkout, VA, QRIS, dan e-wallet.
- Migrasi database PostgreSQL produksi ke OCI (fokus saat ini adalah testing bridge & egress gateway; database tetap mengikuti arsitektur server eksisting).
- Otomatisasi pendaftaran legalitas merchant ke dashboard web iPaymu (dilakukan manual sekali via dashboard iPaymu).

---

## 2. Functional Requirements

### FR-PAY-1: Egress Proxy Forwarding (Outbound)
- **FR-PAY-1.1**: Compute Instance di OCI wajib menjalankan service `tinyproxy` pada port `8888`.
- **FR-PAY-1.2**: Ktor HttpClient di laptop lokal harus dapat meneruskan seluruh request HTTP/HTTPS menuju `https://my.ipaymu.com/*` melalui proxy OCI.
- **FR-PAY-1.3**: Request yang keluar dari proxy OCI menuju iPaymu wajib membawa Source IP yang identik dengan `Reserved Public IP` OCI yang didaftarkan pada whitelist dashboard iPaymu.
- **FR-PAY-1.4**: Konfigurasi `tinyproxy` harus membatasi akses melalui Basic Authentication (`BasicAuth user password`) atau Ingress Security Rule CIDR untuk mencegah open-proxy exploitation.

### FR-PAY-2: Webhook Tunnel Ingress (Inbound)
- **FR-PAY-2.1**: Pulumi harus membuat entitas Cloudflare Tunnel dan mengonfigurasi DNS CNAME pada domain yang terdaftar (contoh: `ipaymu-hook.domain.com`).
- **FR-PAY-2.2**: Developer dapat menjalankan daemon `cloudflared tunnel run --token <TOKEN> --url http://localhost:8081` tanpa konfigurasi router atau port-forwarding NAT.
- **FR-PAY-2.3**: Seluruh HTTP POST event yang dikirimkan oleh iPaymu ke `https://ipaymu-hook.domain.com/api/payment/ipaymu/notify` harus diteruskan secara utuh (headers, body signature, dan payload JSON) ke port `8081` server Ktor lokal.

### FR-PAY-3: Ktor Client & Gateway Configuration
- **FR-PAY-3.1 Conditional Proxy**: Jika `IPAYMU_OUTBOUND_PROXY` diset (misal: `http://user:pass@103.150.x.x:8888`), Ktor HttpClient mengaktifkan `ProxyBuilder.http()`. Jika string kosong (seperti pada server yang sudah berada di host yang sama), client langsung menembak direct TCP.
- **FR-PAY-3.2 Webhook Signature Verification**: Endpoint Ktor wajib memvalidasi hash signature iPaymu (HMAC-SHA256 dari API Key + VA + Body) sebelum menandai status invoice/tagihan menjadi `PAID`.

---

## 3. Non-Functional Requirements (NFRs)

| Kategori | Kebutuhan & Target Metrik | Rasional & Strategi Mitigasi |
| :--- | :--- | :--- |
| **Performance** | Round-trip latency API iPaymu via proxy < 150 ms; Webhook tunnel delay < 50 ms. | Server OCI berlokasi fisik di Jakarta (`ap-jakarta-1`), berada dalam satu ring pertukaran internet domestik (IIX/OpenIXP) dengan server iPaymu. |
| **Scalability** | Mendukung hingga 50 concurrency testing requests & 20 webhook events per detik. | Resource Ampere A1 (1 OCPU, 6 GB RAM) sangat berlebih untuk beban `tinyproxy` yang memory-footprint-nya hanya ~15 MB. |
| **Security** | - Zero exposure untuk database internal.<br>- Proxy dilindungi otentikasi.<br>- Pulumi secrets terenkripsi.<br>- Webhook dienkripsi HTTPS TLS 1.3 via Cloudflare Edge. | Port 8888 di OCI Security List dilindungi kredensial `BasicAuth`. Cloudflare Tunnel mengenkripsi traffic lokal melalui outbound gRPC tunnel (tidak ada port terbuka dari publik ke laptop). |
| **Availability** | Uptime Gateway OCI ≥ 99.5% untuk continuous development testing. | Compute instance Always Free OCI berjalan tanpa batas durasi (persistent), tidak seperti sesi VPN yang sering terputus otomatis. |
| **Maintainability** | 100% kode infrastruktur didefinisikan dalam Kotlin (`.kt`) dan Gradle (`build.gradle.kts`). | Mengeliminasi kebutuhan konfigurasi manual di web UI console OCI/Cloudflare; dapat di-redeploy kapan saja melalui `pulumi up`. |

---

## 4. System Architecture & Technical Design

### 4.1 High-Level Architecture & Data Flow

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer (Local Laptop)
    participant Ktor as Ktor Server (:8081)
    participant OCI as OCI Proxy (103.150.x.x:8888)
    participant iPaymu as iPaymu API & Webhook
    participant CF as Cloudflare Tunnel Edge
    participant Cfd as cloudflared Daemon (Local)

    %% Outbound
    Note over Dev,iPaymu: ALUR KELUAR (OUTBOUND: Create Transaction)
    Dev->>Ktor: Trigger Checkout / Payment
    Ktor->>OCI: HTTP POST via Proxy (Payload Transaksi)
    OCI->>iPaymu: Forward POST https://my.ipaymu.com/api/v2/payment/direct
    Note over OCI,iPaymu: Source IP terverifikasi di Whitelist iPaymu
    iPaymu-->>OCI: 200 OK (Payment URL / VA / QRIS)
    OCI-->>Ktor: 200 OK
    Ktor-->>Dev: Tampilkan QRIS / Link Pembayaran

    %% Inbound
    Note over Dev,iPaymu: ALUR MASUK (INBOUND: Webhook Callback)
    iPaymu->>CF: POST https://ipaymu-hook.domain.com/api/payment/ipaymu/notify
    CF->>Cfd: Stream payload via Argo WebSocket Tunnel
    Cfd->>Ktor: POST http://localhost:8081/api/payment/ipaymu/notify
    Ktor->>Ktor: Verifikasi HMAC Signature & Update Status Invoice
    Ktor-->>Cfd: 200 OK {"status": "success"}
    Cfd-->>CF: 200 OK
    CF-->>iPaymu: 200 OK Callback Acknowledged
```

---

### 4.2 Detailed Component Design (Pulumi Kotlin)

Proyek diletakkan pada direktori `infra/ipaymu-bridge`:

```
infra/ipaymu-bridge/
├── Pulumi.yaml
├── Pulumi.dev.yaml
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/kotlin/com/eventverse/infra/
    ├── Main.kt                       # Entrypoint Pulumi.run { ... }
    ├── OciGatewayComponent.kt        # VCN, Subnet, Reserved IP, Compute Instance
    ├── CloudflareTunnelComponent.kt  # Cloudflare Tunnel, Credentials, CNAME Record
    └── ConfigKeys.kt                 # Typed configuration value classes
```

#### Komponen 1: OCI Gateway (`OciGatewayComponent.kt`)
Bertanggung jawab membuat:
1. `oci.core.Vcn`: CIDR `10.0.0.0/16`.
2. `oci.core.InternetGateway`: Menghubungkan VCN ke internet publik.
3. `oci.core.RouteTable`: Rute default `0.0.0.0/0` diarahkan ke Internet Gateway.
4. `oci.core.SecurityList`:
   - Egress: All traffic allowed.
   - Ingress: TCP 22 (SSH), TCP 8888 (`tinyproxy`).
5. `oci.core.Subnet`: Public Subnet `10.0.1.0/24`.
6. `oci.core.PublicIp`: Reserved Public IPv4 permanen (Always Free).
7. `oci.core.Instance`: VM Standard A1 Flex (1 OCPU, 6 GB RAM, Ubuntu Minimal 24.04 ARM).
8. `UserData` (Cloud-Init):
   ```bash
   #cloud-config
   package_update: true
   packages:
     - tinyproxy
   write_files:
     - path: /etc/tinyproxy/tinyproxy.conf
       content: |
         User tinyproxy
         Group tinyproxy
         Port 8888
         Timeout 600
         DefaultErrorFile "/usr/share/tinyproxy/default.html"
         StatFile "/usr/share/tinyproxy/stats.html"
         LogLevel Info
         MaxClients 100
         Allow 0.0.0.0/0
         BasicAuth ${proxy_user} ${proxy_password}
       permissions: '0644'
   runcmd:
     - systemctl restart tinyproxy
     - systemctl enable tinyproxy
   ```

#### Komponen 2: Cloudflare Tunnel (`CloudflareTunnelComponent.kt`)
Bertanggung jawab membuat:
1. `cloudflare.Tunnel`: Membuat entitas Cloudflare Tunnel terenkripsi dengan 32-byte secure random secret.
2. `cloudflare.Record`: Membuat DNS record tipe `CNAME` mengarahkan `ipaymu-hook.<domain>` ke `<tunnel_id>.cfargotunnel.com`.
3. Output: Mengembalikan `tunnel_token` terenkripsi dan URL lengkap callback.

---

### 4.3 Data Model & External API Contract

#### A. Konfigurasi Environment Ktor (`.env`)
```bash
# iPaymu Credentials
IPAYMU_VA=0000001234567890
IPAYMU_API_KEY=SANDBOX-A1B2C3D4-E5F6-7890-ABCD-1234567890EF
IPAYMU_BASE_URL=https://sandbox.ipaymu.com/api/v2

# Bridge Network Configuration
IPAYMU_OUTBOUND_PROXY=http://wemade:secretPass123@103.150.88.12:8888
IPAYMU_NOTIFY_URL=https://ipaymu-hook.wemakeerp.com/api/payment/ipaymu/notify
IPAYMU_RETURN_URL=https://ipaymu-hook.wemakeerp.com/builder/billing
```

#### B. API Contract: Webhook Callback Receiver
- **Path**: `POST /api/payment/ipaymu/notify`
- **Content-Type**: `application/x-www-form-urlencoded` / `application/json`
- **Payload Schema**:
```json
{
  "trx_id": "128945",
  "sid": "TRX-20261001-0001",
  "reference_id": "inv_sub_698a12bc",
  "status": "berhasil",
  "status_code": "1",
  "via": "qris",
  "channel": "qris",
  "va": "0000001234567890",
  "amount": "1500000",
  "fee": "7500"
}
```
- **Responses**:
  - `200 OK`: `{"status": "success", "message": "payment acknowledged"}`
  - `400 Bad Request`: Format payload tidak valid.
  - `401 Unauthorized`: Signature HMAC tidak cocok dengan API Key terkonfigurasi.

---

### 4.4 Justifikasi Teknologi & Trade-off

1. **Pulumi Kotlin vs Terraform HCL**:
   - *Keputusan*: Memilih **Pulumi Kotlin**.
   - *Alasan*: Seluruh codebase WeMake Flow Platform / EventVerse berbasis Kotlin Multiplatform (KMP). Menggunakan Pulumi Kotlin menjaga konsistensi ekosistem developer, memanfaatkan Gradle sebagai build tool tunggal, serta menyediakan type-safety penuh tanpa sintaks deklaratif HCL yang kaku.
2. **OCI Jakarta vs AWS/GCP**:
   - *Keputusan*: Memilih **OCI (Oracle Cloud) Region Jakarta**.
   - *Alasan*: OCI menyediakan 1 Reserved Static IPv4 publik dan VM Ampere A1 (hingga 4 OCPU, 24 GB RAM) secara permanen di tier *Always Free* (Rp 0). Latensi jaringan domestik Jakarta ke iPaymu sangat rendah (< 10 ms).
3. **Cloudflare Tunnel vs Ngrok**:
   - *Keputusan*: Memilih **Cloudflare Tunnel (`cloudflared`)**.
   - *Alasan*: Ngrok gratis mengubah domain setiap kali restart dan membutuhkan pembayaran untuk custom domain. Cloudflare Tunnel sepenuhnya gratis menggunakan domain bisnis sendiri secara permanen, aman, dan ber-SSL resmi Cloudflare Edge.

---

## 5. Testing, Deployment, and Operations

### 5.1 Acceptance Criteria (AC)

- [ ] **AC-PAY-1**: Perintah `pulumi up` sukses membuat seluruh resource VCN, Reserved IP, Compute Instance di OCI Jakarta, serta Cloudflare Tunnel dan CNAME tanpa intervensi manual web console.
- [ ] **AC-PAY-2**: Host OCI sukses merespons HTTP CONNECT proxy request pada port `8888` dengan kredensial yang valid.
- [ ] **AC-PAY-3**: Request HTTP dari Ktor lokal ke `https://api.ipify.org?format=json` melalui proxy mengembalikan IP yang identik dengan Reserved IP OCI.
- [ ] **AC-PAY-4**: Request pembuatan transaksi iPaymu (`POST /api/v2/payment/direct`) dari laptop developer via proxy berhasil dengan HTTP `200 OK` (tidak lagi `403 Forbidden`).
- [ ] **AC-PAY-5**: Simulasi Webhook iPaymu ke `https://ipaymu-hook.<domain>/api/payment/ipaymu/notify` berhasil diterima oleh instance Ktor lokal pada port `8081` dan tercatat pada log server.

---

### 5.2 Strategi Verifikasi & Testing

1. **Unit Test (Ktor)**:
   - Pengujian `IpaymuSignatureValidatorTest`: Memastikan validasi HMAC SHA256 berjalan deterministik terhadap payload callback.
   - Pengujian `SubscriptionInvoiceStatusTransitionTest`: Memastikan status faktur tagihan berpindah dari `ISSUED` ke `PAID` saat webhook valid tiba.
2. **Integration Verification Script (`scripts/test-ipaymu-bridge.sh`)**:
   ```bash
   #!/usr/bin/env bash
   set -euo pipefail

   echo "==> 1. Memeriksa IP Outbound via OCI Proxy..."
   OUTBOUND_IP=$(curl -s -x "$IPAYMU_OUTBOUND_PROXY" https://api.ipify.org)
   echo "Egress IP: $OUTBOUND_IP"
   if [ "$OUTBOUND_IP" != "$EXPECTED_STATIC_IP" ]; then
       echo "ERROR: IP tidak sesuai dengan OCI Reserved IP!"
       exit 1
   fi
   echo "SUCCESS: Egress IP cocok dengan Whitelist iPaymu."

   echo "==> 2. Memeriksa Inbound Tunnel..."
   HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$IPAYMU_NOTIFY_URL" \
       -H "Content-Type: application/json" \
       -d '{"status_code":"test"}')
   echo "Webhook Response Code: $HTTP_CODE"
   ```

---

### 5.3 Runbook Deployment & Rollback

#### Langkah Deployment (Setup Awal):
1. **Prasyarat**:
   - Pastikan OCI CLI terkonfigurasi (`~/.oci/config`) mengarah ke tenancy dan region `ap-jakarta-1`.
   - Pastikan Cloudflare API Token memiliki hak akses `Zone.DNS` dan `Account.Cloudflare Tunnel`.
2. **Deploy Stack**:
   ```bash
   cd infra/ipaymu-bridge
   pulumi stack init dev
   pulumi config set ociCompartmentId "<OCID_COMPARTMENT>"
   pulumi config set cfAccountId "<CF_ACCOUNT_ID>"
   pulumi config set cfZoneId "<CF_ZONE_ID>"
   pulumi config set domain "domainanda.com"
   pulumi config set --secret proxyPassword "<SECURE_PASSWORD>"

   pulumi up --yes
   ```
3. **Daftarkan ke iPaymu**:
   - Salin output `IPAYMU_WHITELISTED_IP` ke menu **Pengaturan > IP Whitelist** di iPaymu.
   - Salin output `IPAYMU_WEBHOOK_URL` ke menu **Pengaturan > URL Notifikasi**.
4. **Jalankan Tunnel Lokal**:
   ```bash
   cloudflared tunnel run --token $(pulumi stack output CLOUDFLARE_TUNNEL_TOKEN --show-secrets) --url http://localhost:8081
   ```

#### Langkah Rollback / Teardown:
- Jika lingkungan pengujian sudah selesai atau ingin dihancurkan sementara:
  ```bash
  cd infra/ipaymu-bridge
  pulumi destroy --yes
  ```
- Seluruh resource di OCI dan Cloudflare akan dibersihkan tanpa meninggalkan sisa tagihan.
