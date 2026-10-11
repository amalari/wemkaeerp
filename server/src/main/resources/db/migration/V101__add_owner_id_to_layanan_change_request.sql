-- TRD-FIELD-004 Q4 (MVP): kolom owner untuk pemilik baris hasil generate.
-- Additif karena V90/V100 sudah mungkin berjalan; nilai lama NULL (belum ada filter owner,
-- data lama tetap terbaca). Filter berbasis owner penuh = Next Phase TRD-FIELD-004-Q4.
ALTER TABLE layanan_change_request.change_requests
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(64);

COMMENT ON COLUMN layanan_change_request.change_requests.owner_id IS
    'Pemilik baris (users.id) untuk scope HIERARCHICAL; NULL = belum ditetapkan.';
