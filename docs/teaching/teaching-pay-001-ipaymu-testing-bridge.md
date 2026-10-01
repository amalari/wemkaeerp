# Teaching: TRD-PAY-001 — iPaymu Testing Bridge (OCI Jakarta + Pulumi Kotlin + Cloudflare Tunnel)

Rujukan: [`docs/trd/TRD-PAY-001-ipaymu-testing-bridge.md`](../trd/TRD-PAY-001-ipaymu-testing-bridge.md) ·
[`docs/plannings/PLAN-ipaymu-testing-bridge.md`](../plannings/PLAN-ipaymu-testing-bridge.md)

## 1. Masalah yang diselesaikan

Integrasi iPaymu butuh dua hal yang tidak bisa dipenuhi laptop developer: **IP publik statis**
(whitelist API keluar) dan **domain HTTPS** untuk webhook masuk. Solusinya: VM OCI Jakarta dengan
Reserved Public IP sebagai egress (tinyproxy di `127.0.0.1:8888`, dijangkau lewat SSH local forward)
plus Cloudflare Tunnel per developer untuk ingress webhook.

## 2. Struktur kode (`infra/ipaymu-bridge/` — build Gradle mandiri)

| File | Isi |
|---|---|
| `build.gradle.kts` | Dependensi **di-pin**: `com.pulumi:pulumi:1.37.3`, `:oci:5.1.0`, `:cloudflare:6.21.0`, `:random:4.21.2` |
| `Main.kt` | Baca config stack, rakit dua komponen, ekspor output (`egressIp`, `sshForwardCommand`, `webhookUrls`, `tunnelTokens` secret) |
| `BridgeConfig.kt` | Config bertipe + validasi |
| `OciEgressGateway.kt` | VCN/IGW/RouteTable/SecurityList (ingress hanya TCP 22)/Subnet, VM A1 Flex (`assignPublicIp = false`), Reserved IP **`protect = true`** |
| `TinyproxyCloudInit.kt` | Cloud-init tanpa rahasia: `Listen 127.0.0.1`, `ConnectPort 443`, filter allowlist (`my.ipaymu.com`, `sandbox.ipaymu.com`, `api.ipify.org`) |
| `CloudflareWebhookTunnel.kt` | Per developer: tunnel (`tunnelSecret` = RandomBytes 32 byte), config ingress (`path ^/api/payment/ipaymu/notify$` → `localhost:8081`, catch-all 404), CNAME proxied |
| `scripts/test-ipaymu-bridge.sh` | AC-PAY-2/3/4/7 otomatis |

## 3. Pelajaran implementasi (yang bikin compile pertama gagal)

1. **Tidak ada "Pulumi Kotlin SDK" di Maven Central** — yang ada adalah Java SDK
   (`com.pulumi:pulumi`) yang dipakai dari Kotlin. `com.pulumi.kotlin.Pulumi` tidak ada.
2. **Paket provider OCI dikapitalisasi**: `com.pulumi.oci.Core.Vcn`, `com.pulumi.oci.Core.inputs.*`,
   `com.pulumi.oci.Identity.IdentityFunctions`. Cloudflare/random lowercase.
3. **Nama argumen berbeda dari Terraform**: `tunnelSecret` (bukan `secret`); token tunnel diambil lewat
   data source `CloudflareFunctions.getZeroTrustTunnelCloudflaredToken(accountId, tunnelId)`, bukan
   atribut resource. `RandomBytes` mengeluarkan `base64()`/`hex()`.
4. **Catch-all ingress = rule terakhir tanpa hostname/path** dengan service `http_status:404`
   (arg `ingresses(...)`, bukan `catchAll(...)`).
5. **Security list rule**: `SecurityListIngressSecurityRuleArgs` (bukan `IngressSecurityRuleArgs`);
   port tujuan langsung di `tcpOptions.min/max` (port range tidak jadi nested args terpisah);
   `assignPublicIp` bertipe `String` ("false"); `ocpus`/`memoryInGbs` bertipe `Double`;
   `securityListIds` menerima `Output<List<String>>` → bungkus dengan `applyValue`.
6. **Menghindari nested Output** saat export map: helper `mergeMapEntry` memakai
   `Output.all(list).applyValue` lalu cast.
7. `webhookUrl` sengaja **tidak** diambil dari output resource — hostname deterministik dari config,
   jadi bisa diekspor sebagai map biasa.

## 4. Operasi harian

### Mode sandbox (egress OCI TIDAK dibuat — default, `enableOci = false`)

Sandbox iPaymu belum terbukti menegakkan IP whitelist (TRD P3), jadi default stack hanya membuat
**ingress tunnel**; request API iPaymu keluar langsung dari laptop (`IPAYMU_OUTBOUND_PROXY` kosong).

```bash
cd infra/ipaymu-bridge
pulumi stack init dev
pulumi config set cfAccountId "<CF_ACCOUNT_ID>"
pulumi config set cfZoneId "<CF_ZONE_ID>"
pulumi config set domain "wemakeerp.com"
pulumi config set --path 'developers[0]' <namamu>
pulumi config set --secret cloudflare:apiToken "<TOKEN>"   # + export CLOUDFLARE_API_TOKEN saat up
pulumi up                                                  # hanya resource Cloudflare

# jalankan tunnel (tools):
./tunnel.sh          # dari root repo — auto ambil token dari cache / pulumi stack output
./tunnel.sh refresh  # setelah `pulumi up` — tarik ulang token
./tunnel.sh status   # cek proses + port 8081
```

### Mode egress OCI (whitelist ditegakkan / production)

```bash
pulumi config set enableOci true
pulumi config set ociCompartmentId "<OCID_COMPARTMENT>"
pulumi config set --path 'sshPublicKeys[0]' "$(cat ~/.ssh/id_ed25519.pub)"
pulumi up    # + VCN, VM, Reserved IP protect, tinyproxy

ssh -N -L 8888:127.0.0.1:8888 ubuntu@$(pulumi stack output egressIp)   # terminal 1
```

Output `egressIp`/`sshForwardCommand` hanya ada saat `enableOci = true` (output `egressEnabled`
menandai mode). Set `true` menjelang production atau bila sandbox ternyata menegakkan whitelist.

Teardown parsial (komputasi berhenti, IP bertahan): `pulumi destroy` akan **gagal di PublicIp** —
itu disengaja (`protect`). Hapus total = `pulumi state unprotect <urn>` lalu `destroy`, dan **wajib
daftar ulang whitelist iPaymu**.

## 5. Kontrak serah-terima ke L1 (jangan dilanggar)

- `IPAYMU_OUTBOUND_PROXY=http://127.0.0.1:8888` — tanpa kredensial; kosong = koneksi langsung.
- Engine Ktor client harus mendukung HTTP proxy + `CONNECT`.
- Handler `/api/payment/ipaymu/notify`: cek ulang status ke iPaymu per `trx_id`, idempoten,
  nominal = `totalIdr`, reuse `ConfirmSubscriptionPaymentUseCase` (aktor `system:ipaymu`).
- `returnUrl`/`cancelUrl` **tidak** lewat tunnel.

## 6. Prasyarat yang tetap manual (Fase 0, TRD P1–P6)

Home region OCI = `ap-jakarta-1`, akun PAYG, backend state Pulumi, API token Cloudflare
(`Zone.DNS:Edit` + `Account.Cloudflare Tunnel:Edit`), dan whitelist `egressIp` di dashboard iPaymu.
