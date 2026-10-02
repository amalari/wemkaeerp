# Teaching — Route per Menu di Builder Console (`/builder/<key>`)

**Slug**: `teaching-builder-per-menu-routes` · **Tanggal**: 2026-10-03 · **Repo**: Jalur B (wemkaeerp)

## 1. Masalah

Semua menu sidebar Builder (Overview, Chat AI, Modules, Data Flow, Prototype, dst.) hidup di satu
URL `/builder` dengan state internal `selected`. Reload halaman selalu jatuh ke Overview, dan URL
tidak bisa dibagikan per menu.

## 2. Prinsip: URL adalah Sumber Kebenaran Tunggal

App sudah punya infrastruktur yang tepat — `PlatformNavigation` (`pushState` + `popstate`) dan
`shellPath` di `App.kt` dengan `historyApiFallback = true` di webpack dev server. Yang kurang
hanyalah menyambungkan section Builder ke mekanisme itu. Nol dependensi baru.

## 3. Implementasi

### `BuilderShell.kt` — state jadi cermin URL
- Param baru: `section: String = "overview"` (diturunkan App dari `shellPath`) dan
  `onSectionChange: (String) -> Unit`.
- `selected` diinisialisasi dari `section` (key tak dikenal → `"overview"`, tolak-jangan-fallback ke
  pane ngawur).
- `LaunchedEffect(section)`: Back/Forward browser mengubah `shellPath` → prop berubah → pane ikut.
- `select(key)`: set state + panggil `onSectionChange(key)`; dipakai sidebar **dan** navigasi
  cepat dari `BuilderOverviewPane` supaya keduanya senada.

### `App.kt` — dua titik
1. Call-site `BuilderShell`: `section = shellPath.removePrefix("/builder").trim('/').ifEmpty { "overview" }`,
   `onSectionChange = { goShell("/builder/$key") }` — tiap klik menu mendorong entry history baru.
2. `landAfterLogin`: deep-link dipertahankan saat re-login —
   `goShell(if (shellPath.startsWith("/builder")) shellPath else "/builder")`, bukan reset ke root.

## 4. Verifikasi (dengan mata, Playwright)

| Langkah | Hasil |
|---|---|
| Muat langsung `/builder/modules` (fresh) | Pane Modules tampil + "modul aktif" terlihat |
| Klik Billing | URL → `/builder/billing` |
| Reload di `/builder/billing` | Tetap URL itu, pane sesuai |
| Back browser | Kembali `/builder/modules` + pane Modules tampil |

## 5. Pitfall Operasional (menjalankan dev server di background)

`wasmJsBrowserDevelopmentRun` **mati kalau stdin-nya EOF** (webpack-cli shutdown saat stdin
tutup) dan kalau dibaca tanpa TTY dapat SIGTTIN (suspended). Pola aman:

```bash
nohup script -q /dev/null sh -c \
  'tail -f /dev/null | ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous' \
  > /tmp/wemade-web.log 2>&1 &
```

`script` memberi pseudo-tty, `tail -f /dev/null` menjaga stdin tak pernah EOF.
HistoryApiFallback sudah ada di `app/webApp/webpack.config.d/devServer.js` (dev saja; produksi
disajikan Caddy — jika ingin path dalam di produksi, Caddy perlu `try_files {path} /index.html`).

## 6. Catatan Terkait

- `App.kt` kini 599 baris — **tinggal 1 baris di bawah hard limit 600** (aturan file-size).
  Perubahan berikutnya di file ini sebaiknya sekaligus memecah cabang `when(screen)` ke file
  layar masing-masing.
- Sedang mengerjakan repo ini, `ModuleMapPane.kt`/`DataFlowPane.kt`/`DiscoveryUiModel.kt`
  sempat gagal kompilasi karena diedit bersamaan di editor — bukan bagian dari perubahan ini.
