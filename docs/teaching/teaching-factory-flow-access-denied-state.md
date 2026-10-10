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

## Tindak lanjut cek visual (header sempit, glyph, label, panggilan sekunder)

1. **Header 360dp (Kontrak 13)**: `FactoryFlowHeader` (baru) menggantikan `Row(SpaceBetween)` di layar. Di bawah 720dp judul dan
   aksi ditumpuk (`Column`), di atasnya berdampingan dengan judul ber-`weight(1f)`. Judul + badge tenant memakai `ClayFlowRow`
   sehingga badge membungkus, bukan menyempit sampai pecah huruf; `TenantModuleActionBar` juga `ClayFlowRow`.
2. **Aksi tulis saat diblokir**: header tidak merender `TenantModuleActionBar` bila `loadState.isBlocked`.
3. **Glyph kotak**: Nunito tidak punya panah/simbol/emoji. `Mapping ↗`, `⤶ ◀- -`, `📥 ◀╌╌`, `✕` diganti ikon vektor
   (`IconArrowRight`, `IconRestore`, `IconInbox`) atau teks; `…`/`•`/`—` pada teks UI diganti ASCII/Latin-1. Seed core
   `PresetNodeSeeds` memuat `⤶` pada tiga lencana QC; dibuang. Sapuan di luar pipeline: ~100+ literal string bertanda non-Latin-1
   (mis. `ModuleSampleRows`, `DiscoveryWizardSteps`, `DealDetailDialog`), sebagian besar tanda baca `—`/`…`; belum diubah
   (di luar lingkup), perlu pemeriksaan visual per layar untuk panah/simbol sungguhan.
4. **Label gerbang**: lencana `NONE` pada `AccessDeniedCard` menjadi `ACCESS_DENIED_BADGE = "Tanpa Akses"`; nama enum mentah
   tidak boleh tampil.
5. **Panggilan sekunder setelah 403**: `FactoryFlowViewModel` memang tidak memanggil API sekunder setelah `AccessDenied`
   (dikunci tes `calls == ["get"]`). Sumber 403 `/departments` dan `/employees` ternyata
   `RbacAccessPolicyRepository.load`, yang dijalankan untuk semua persona. Kini keduanya dilewati bila keputusan server
   menutup Bagan Organisasi (gerbang server yang sama: `moduleGate(ORG_CHART)`); tanpa keputusan server perilaku lama.
   Tes: `RbacAccessPolicyOrgGateTest`.
