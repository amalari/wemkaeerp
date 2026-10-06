# Eval Live Koog — ScreenProposal (SP C5)

- Agent: `koog/deepseek-flash/draft-v2` (model `deepseek-flash`)
- Dibuat: 2026-10-06T17:23:45.870624Z
- Batas koreksi: 3 putaran per run
- Penilaian: `DiscoveryEvalGrader` (kriteria §6); baseline deterministik wajib 100%

## Perkiraan biaya sebelum jalan

```
estimasi | kasus=1 ulangan=1 putaran-maks=4 panggilan-LLM-maks=4 token-maks≈24000
```

## Hasil per kasus

| Kasus | Lulus | Putaran koreksi (min–maks) | Waktu (min–maks ms) | Token | Catatan |
|---|---|---|---|---|---|
| sablon-bordir | 1/1 | 4–4 | 75109–75109 | 79943 | - |

## Variasi antar-ulangan

- sablon-bordir: 1/1 lulus, stabil

## Perbandingan dengan baseline deterministik

`evals | deterministic/keyword-v1 | sablon-bordir | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  

## Rekomendasi (diisi koordinator — G3)

- Skor LLM: 1 dari 1 ulangan; baseline: 100%.
- Keputusan (Koog default / hanya bila deterministik gagal / belum layak): _
