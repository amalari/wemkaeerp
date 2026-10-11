-- TRD-FIELD-004 C1: V90 sudah mungkin berjalan, jadi penyelarasan spec FILE/RELATION harus aditif.
ALTER TABLE layanan_change_request.change_requests
    ADD COLUMN IF NOT EXISTS lampiran TEXT,
    ADD COLUMN IF NOT EXISTS rujukan VARCHAR(64);

COMMENT ON COLUMN layanan_change_request.change_requests.lampiran IS
    'FileRef fields/<tenant>/<module>/<record>/...; byte disimpan di ObjectStorage.';
COMMENT ON COLUMN layanan_change_request.change_requests.rujukan IS
    'Rujukan logis ke id change_request; keberadaan dan tenant divalidasi sebelum tulis.';
