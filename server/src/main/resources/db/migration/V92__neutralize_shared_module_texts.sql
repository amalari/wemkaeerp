-- Netralkan teks katalog modul bersama (org_chart, vendor_contacts) agar bisa dipakai pack non-garment.
-- Sinkron dengan GarmentModules. Hanya mengubah bila teksnya masih persis teks migrasi asal (V18/V64) — suntingan manual tidak ditimpa.
UPDATE module_catalog_entries
SET description = 'Struktur divisi, jenjang jabatan, dan data karyawan.'
WHERE module_id = 'org_chart'
  AND description = 'Struktur divisi, jenjang jabatan, dan data karyawan pabrik.';

UPDATE module_catalog_entries
SET display_name = 'Kontak Vendor'
WHERE module_id = 'vendor_contacts'
  AND display_name = 'Kontak Vendor & Makloon';

UPDATE module_catalog_entries
SET description = 'Buku kontak vendor, daftar harga layanan per vendor, dan penunjukan vendor ke proses yang dikerjakan di luar.'
WHERE module_id = 'vendor_contacts'
  AND description = 'Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.';
