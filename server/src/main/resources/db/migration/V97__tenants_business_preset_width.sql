-- TRD-PLAT-008: kode starter (BlueprintCode) sah sampai 64 karakter, sedangkan kolom V10 hanya 50.
-- Pelebaran murni: tanpa backfill, tanpa perubahan data; blueprint non-garment kini milik pack (domain_packs.definition),
-- tenant tetap hanya menyimpan kodenya di sini.
ALTER TABLE tenants ALTER COLUMN business_preset TYPE VARCHAR(64);
