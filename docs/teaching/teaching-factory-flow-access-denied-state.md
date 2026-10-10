# Teaching: Keadaan "Akses Ditolak" di Factory Flow (TRD-PLAT-012 Q2)

## Masalahnya
`FactoryFlowViewModel.loadTenantPipeline` memanggil `fallBackToPreset` pada setiap kegagalan `getPipeline`. Setelah
gerbang server dibuat fail-closed (403), pengguna tanpa wewenang melihat kanvas preset yang tampak normal: penolakan
tertutup, dan data contoh tersangka konfigurasi pabrik.

## Kontrak server (diperiksa dulu, tidak ditebak)
`GET /api/tenant/pipeline` memanggil `GetTenantPipelineUseCase` yang **memprovisi** alur dari preset tenant bila belum
ada, lalu menjawab sukses. Jadi "tenant belum punya pipeline" tidak pernah menjadi 404/galat di klien. Konsekuensinya:
klien tidak butuh jalur galat -> preset sama sekali. Preset hanya muncul lewat pratinjau eksplisit (`SelectPreset`).

## Rancangan
- `PipelineRequestException(action, status, serverMessage)` menggantikan `error("... (HTTP n)")` di klien; teks `message`
  tetap sama, tetapi status kini bisa dibaca tanpa mengurai string (pola `OrgChartRestoreException`).
- `FactoryFlowLoadState` (sealed interface): `Loading`, `Loaded`, `AccessDenied(message)`, `Failed(message)`. Pemetaan
  murni `fromFailure(cause)`: 403 -> AccessDenied (pesan server bila singkat dan bukan noise transport), selain itu
  Failed lewat `FriendlyErrors`.
- ViewModel: `blockLoad` mengisi `loadState`, membuang alur lama (data basi), dan **tidak** menyentuh snapshot preset.
- Layar: `FactoryFlowBlockedCard` (ClayCard, tanpa warna literal) menggantikan kanvas. 403 tanpa tombol coba lagi
  (mengulang tak mengubah jawaban); Failed dengan "Coba Lagi".

## Jebakan
- Menangani 403 lewat string `contains("403")` rapuh; gunakan status bertipe.
- Retry yang gagal setelah sukses harus membuang data lama, kalau tidak pengguna yang baru dicabut aksesnya masih
  melihat alur lama.
- Setelah AccessDenied, API sekunder (katalog, telemetri) tidak boleh dipanggil.

## Tes
`FactoryFlowLoadStateTest` (pemetaan murni + VM dengan fake, tenant `bordir-uji`, blueprint non-default) dan
`FactoryFlowViewModelTenantDataTest` yang diperbarui.
