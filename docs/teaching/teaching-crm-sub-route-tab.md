# Teaching — Memisahkan Leads / Deal / Kontak jadi Sub-Rute CRM

## Masalahnya

Tab direktori CRM (`Leads | Deal | Kontak`) tadinya hanya hidup sebagai state Compose:

```kotlin
var directoryTab by remember { mutableStateOf(CrmDirectoryTab.LEADS) }
```

Konsekuensinya tiga hal yang semuanya terasa seperti bug oleh pengguna:

1. **Reload halaman** saat membuka Deal selalu kembali ke Leads.
2. **URL tidak bisa dibagikan** — tidak ada alamat yang menunjuk "daftar kontak".
3. **Tombol Back browser** melompat keluar dari modul CRM, bukan pindah tab.

## Step 0 — Kenali pola yang sudah ada sebelum bikin baru

Sebelum menulis apa pun, cari preseden. `InvoiceWorkspaceScreen.kt` sudah punya sub-rute
(`/invoicing/templates/{id}`) dengan pola: baca `PlatformNavigation.getCurrentPath()` saat
komposisi pertama, `listenToPathChanges` untuk Back/Forward, `pushPath` saat berpindah.
Kita ikuti pola itu — bukan karena malas, tapi karena dua mekanisme routing yang berbeda di
satu aplikasi adalah utang yang mahal.

## Step 1 — Jadikan tab punya alamat

Enum tab diberi `slug`, dan rutenya diturunkan dari rute modul agar tidak ada string ganda:

```kotlin
private enum class CrmDirectoryTab(val label: String, val slug: String) {
    LEADS("Leads", "leads"),
    DEALS("Deal", "deals"),
    CONTACTS("Kontak", "contacts");

    val route: String get() = "${AppNavScreen.CRM_SALES.route}/$slug"
}
```

Kalau suatu saat `/crm-sales` berganti nama, ketiga sub-rute ikut tanpa disentuh.

## Step 2 — Baca tab dari URL, cocokkan segmen terakhir saja

```kotlin
private fun resolveCrmDirectoryTab(rawPath: String): CrmDirectoryTab? { … }
```

Kenapa hanya segmen terakhir, bukan `startsWith("/crm-sales/")`? Karena `AppNavScreen.CRM_SALES`
punya alias `/crm` dan `/sales`. Mencocokkan prefiks berarti mendaftar tiga alias × tiga tab =
sembilan kombinasi, dan setiap alias baru menambah tiga. Segmen terakhir menutup semuanya sekaligus,
dengan syarat kita memastikan dulu bahwa layar yang aktif memang CRM — dan itu urusan Step 3.

## Step 3 — Sinkronkan dua arah

```kotlin
LaunchedEffect(Unit) {
    if (resolveCrmDirectoryTab(PlatformNavigation.getCurrentPath()) == null) {
        PlatformNavigation.replacePath(directoryTab.route)   // normalisasi "/crm-sales"
    }
    PlatformNavigation.listenToPathChanges { newPath ->
        if (AppNavScreen.fromPath(newPath) == AppNavScreen.CRM_SALES) { … }
    }
}
```

Dua keputusan yang layak dicermati:

- **`replacePath`, bukan `pushPath`**, untuk normalisasi `/crm-sales` → `/crm-sales/leads`.
  `push` akan menaruh langkah palsu di history: Back pertama tidak akan terasa melakukan apa pun.
- **Penjaga `AppNavScreen.fromPath(newPath) == CRM_SALES`** di dalam listener. Listener terdaftar
  di `window` dan tetap menerima setiap perubahan URL, termasuk saat pengguna sudah berpindah
  modul. Tanpa penjaga itu, URL apa pun yang kebetulan berakhiran `/leads` akan menggeser tab CRM
  diam-diam di latar belakang.

## Step 4 — Kenapa tidak menambah entri `AppNavScreen` baru?

Pilihan lain adalah mendaftar `CRM_SALES_LEADS`, `CRM_SALES_DEALS`, `CRM_SALES_CONTACTS` sebagai
enum tersendiri (seperti `INVOICING_TEMPLATES`). Ditolak karena:

- Ketiganya **satu bounded context, satu entitlement, satu keputusan RBAC** (`BusinessModule.CRM_SALES`).
  Entri terpisah berarti tiga tempat baru yang bisa lupa disamakan gerbangnya.
- `AppNavScreen.fromPath` **sudah** mencocokkan sub-rute lewat prefiks, jadi `/crm-sales/deals`
  otomatis jatuh ke `CRM_SALES` — judul top bar dan gerbang wewenangnya benar tanpa tambahan apa pun.

`INVOICING_TEMPLATES` layak jadi entri sendiri karena ia layar yang benar-benar berbeda (kanvas
desainer), bukan sekadar tab di layar yang sama.

## Jebakan yang dihindari

| Jebakan | Akibat |
|---|---|
| `pushPath` untuk normalisasi URL awal | Back pertama tidak berefek |
| Listener tanpa penjaga modul | Tab bergeser saat pengguna ada di layar lain |
| Hardcode `"/crm-sales/deals"` | Rute ganda yang bisa menyimpang saat modul di-rename |
| `pushPath` saat tab diklik ulang | History penuh entri duplikat |

## Verifikasi

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain :app:shared:jvmTest
```

Kelimanya hijau. Yang masih perlu dilihat dengan mata di browser: reload di `/crm-sales/contacts`
mendarat di Kontak, dan Back dari Kontak → Deal → Leads menelusuri tab, bukan keluar modul.
