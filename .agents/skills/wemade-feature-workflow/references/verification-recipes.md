# Resep Verifikasi (terbukti di TRD-FLOW-001)

## Hasil test segar, bukan cache

```bash
date +%T
./gradlew :core:compileTestKotlinJvm --rerun :core:jvmTest --rerun :app:shared:jvmTest --rerun --continue
for d in core app/shared; do r=$d/build/test-results/jvmTest
  echo "$d tests=$(cat $r/*.xml | grep -o '<testsuite[^>]*' | grep -o ' tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}') \
failures=$(cat $r/*.xml | grep -o '<testsuite[^>]*' | grep -o 'failures="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}') \
oldest=$(ls -tr $r/*.xml | head -1 | xargs stat -f %Sm -t %T)"; done
```
`oldest` harus lebih baru dari `date` di awal. Build "2 detik" tanpa ini bukan bukti.

## Error kompilasi yang tidak masuk akal

- `Unresolved reference 'Department'` di test yang tidak disentuh → cache inkremental basi:
  `./gradlew :core:compileTestKotlinJvm --rerun`. **Jangan** mengubah test-nya.
- Server "tidak melihat" paket `domain` sama sekali → cek `ls -la core/build/libs/core-jvm.jar`;
  bila ratusan byte, `./gradlew :core:jvmJar --rerun`.
- `e: Daemon compilation failed` → jalankan ulang perintah yang sama.

## Migrasi di Postgres dev tanpa mengubah data

```bash
{ echo "BEGIN;"; cat server/src/main/resources/db/migration/V<nn>__*.sql; echo "<SELECT pemeriksa>; ROLLBACK;"; } \
  | docker exec -i wemade-postgres psql -U postgres -d wemade_erp -v ON_ERROR_STOP=1
```

## Server & web dev

- Server: `./gradlew :server:run` (port 8080). Mode watch Ktor **tidak** memuat ulang `core` — restart
  proses setelah mengubah `core`.
- Web: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous` sebagai proses latar (port 3000).

## Token uji lewat API

```bash
tok(){ curl -s -X POST localhost:8080/api/public/auth/demo -d "tenantSlug=$1" -d "username=$2" ${3:+-d "role=$3"} \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'; }
SA=$(tok wemade-demo superadmin_apps PLATFORM_SUPERADMIN)        # tambah -H "X-Tenant-Slug: <slug>" untuk act-as
OWN=$(tok bordir-uji "Owner Bordir" role-ten-bordir-uji-owner)
OPR=$(tok bordir-uji "Operator Bordir")                          # peran tak berwenang → harus 403 untuk tulis
```

## Cek visual (Compose/Wasm menggambar ke canvas — tidak ada DOM)

- Login superadmin: `/login`, tombol "Demo Mode: Masuk Cepat (Superadmin Apps)" (≈ 715,628 di 1440×900).
  Ulangi bila halaman masih di `/login`; jangan pernah menilai UI dari layar "Akses Terbatas".
- Tenant lain: `POST /api/public/auth/demo` dengan `role=<id jabatan tenant>`, simpan respons apa adanya
  ke localStorage `wemade_auth_session`, lalu navigasi. Switcher perusahaan & act-as tidak tahan reload.
- Screenshot headless dengan Playwright (`channel: 'chrome'`, profil sementara di scratchpad); klik pakai
  koordinat dari screenshot sebelumnya. Cek di 1440 dan 1280 px.

## Tenant uji dev

| Slug | Template | Isi |
|---|---|---|
| `wemade-demo` | KNIT_SWEATER | data demo utama |
| `bordir-uji` | EMBROIDERY | 3 SPK uji, jabatan Owner `role-ten-bordir-uji-owner` |
| `sablon-uji` | SCREEN_PRINT | kosong |
