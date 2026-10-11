# Eval Live Koog — ScreenProposal (SP C5) — RONDE 2 (setelah perbaikan prompt, jembatan blueprint, alat validasi, kalibrasi penilai; 1 ulangan)

- Agent: `koog/deepseek-flash/draft-v2` (model `deepseek-flash`)
- Dibuat: 2026-10-06T17:15:13.272577Z
- Batas koreksi: 3 putaran per run
- Penilaian: `DiscoveryEvalGrader` (kriteria §6); baseline deterministik wajib 100%

## Perkiraan biaya sebelum jalan

```
estimasi | kasus=11 ulangan=1 putaran-maks=4 panggilan-LLM-maks=44 token-maks≈264000
```

## Hasil per kasus

| Kasus | Lulus | Putaran koreksi (min–maks) | Waktu (min–maks ms) | Token | Catatan |
|---|---|---|---|---|---|
| garment-fob | 1/1 | 3–3 | 3217–3217 | 17317 | - |
| garment-cmt | 1/1 | 3–3 | 3707–3707 | 17352 | - |
| garment-d2c | 1/1 | 5–5 | 5055–5055 | 28001 | - |
| sablon-bordir | 0/1 | 7–7 | 108397–108397 | 103898 | cakupan_modul: pack=sablon blueprint=sablon_starter modules=[sablon_pesanan, sablon_gelombang, sablon_stok, sablon_tagihan, sablon_lapo |
| klinik | 1/1 | 4–4 | 50922–50922 | 61834 | - |
| bengkel | 1/1 | 5–5 | 58638–58638 | 87927 | - |
| katering | 1/1 | 3–3 | 35642–35642 | 35389 | - |
| retail | 1/1 | 4–4 | 44420–44420 | 57206 | - |
| jasa-it | 1/1 | 4–4 | 57922–57922 | 65982 | - |
| sekolah | 1/1 | 4–4 | 58853–58853 | 67770 | - |
| logistik | 1/1 | 6–6 | 92801–92801 | 85578 | - |

## Variasi antar-ulangan

- garment-fob: 1/1 lulus, stabil
- garment-cmt: 1/1 lulus, stabil
- garment-d2c: 1/1 lulus, stabil
- sablon-bordir: 0/1 lulus, stabil
- klinik: 1/1 lulus, stabil
- bengkel: 1/1 lulus, stabil
- katering: 1/1 lulus, stabil
- retail: 1/1 lulus, stabil
- jasa-it: 1/1 lulus, stabil
- sekolah: 1/1 lulus, stabil
- logistik: 1/1 lulus, stabil

## Perbandingan dengan baseline deterministik

`evals | deterministic/keyword-v1 | garment-fob | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | garment-cmt | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | garment-d2c | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | sablon-bordir | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | klinik | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | bengkel | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | katering | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | retail | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | jasa-it | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | sekolah | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | logistik | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  

## Rekomendasi (diisi koordinator — G3)

- Skor LLM: 10 dari 11 ulangan; baseline: 100%.
- Keputusan (Koog default / hanya bila deterministik gagal / belum layak): _
