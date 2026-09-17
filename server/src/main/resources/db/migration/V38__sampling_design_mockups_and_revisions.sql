-- ==============================================================================
-- WeMade ERP — Sampling Design Mockups & Revision Counter (V38)
-- ==============================================================================
-- Mulai V38, satu order sampling (= satu desain dalam deal) menyimpan:
--   1. foto mockup baju (di knit_specs.mockup_image_urls, format "s3:{key}")
--   2. penghitung revisi eksplisit, supaya pil "Rev 0 / Rev 1" di UI tidak perlu
--      direkonstruksi dari catatan teks dan tetap benar setelah rename/ACC.
--
-- Sengaja TIDAK mengedit V35: migrasi yang sudah dijalankan Flyway punya checksum,
-- dan perubahan isi migrasi lama akan membuat validasi checksum gagal di tenant lain.
--
-- Dinomori ulang dari V36 ke V38: nomor V36 sudah dipakai
-- V36__create_costing_product_benchmarks.sql dan sudah ter-apply di database dev.
-- Flyway menolak dua migrasi bernomor sama, jadi seluruh migrasi gagal selama bentrok itu ada.
-- ==============================================================================

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS revision_count INT NOT NULL DEFAULT 0;
