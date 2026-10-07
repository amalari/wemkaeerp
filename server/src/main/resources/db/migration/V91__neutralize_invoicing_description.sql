-- Netralkan deskripsi katalog modul invoicing agar bisa dipakai pack non-garment (sinkron dengan GarmentModules).
-- Hanya mengubah bila teksnya masih persis teks V32 — suntingan manual superadmin tidak ditimpa.
UPDATE module_catalog_entries
SET description = 'Penerbitan faktur tagihan, termin uang muka (DP), dan pelunasan.'
WHERE module_id = 'invoicing'
  AND description = 'Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas A4.';
