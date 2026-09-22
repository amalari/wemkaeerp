-- ==============================================================================
-- WeMade ERP — Membetulkan tenant_id pada seed lokasi demo
-- ==============================================================================
-- V55 menyemai `tenant_locations` dengan `tenant_id = 'demo-tenant'`. Itu **slug percakapan**,
-- bukan id tenant: tenant demo yang sesungguhnya ber-id `ten-demo-001` dengan slug
-- `wemade-demo`. Tidak ada foreign key pada kolom itu di V55, jadi baris yatim tersebut masuk
-- tanpa keluhan dan tidak pernah terbaca oleh siapa pun.
--
-- Kekeliruan itu tidak terlihat selama tabelnya memang tidak pernah di-query. Begitu
-- konfigurasi lokasi mulai dibaca (V59 dan seterusnya), akibatnya menjadi nyata dan senyap:
-- repository mencari dengan `tenant_id = 'ten-demo-001'`, tidak menemukan apa pun, lalu
-- menyimpulkan "tenant ini tidak punya lokasi" — yang secara kebetulan tampak seperti perilaku
-- gagal-terbuka yang benar, sehingga tidak ada satu pun error yang muncul untuk menuntun orang
-- ke penyebabnya.
--
-- V59 ikut menyalin kekeliruan yang sama karena mencontoh V55. Ketiganya dibetulkan di sini
-- sekaligus, bukan dengan menyunting migrasi lama — checksum Flyway sudah tercatat di basis
-- data yang berjalan.
--
-- Aditif dan idempoten: menargetkan nilai yang salah secara eksplisit, jadi menjalankannya dua
-- kali tidak mengubah apa pun.
-- ==============================================================================

UPDATE tenant_locations
SET tenant_id = 'ten-demo-001'
WHERE tenant_id = 'demo-tenant'
  AND EXISTS (SELECT 1 FROM tenants WHERE id = 'ten-demo-001');

UPDATE tenant_location_settings
SET tenant_id = 'ten-demo-001'
WHERE tenant_id = 'demo-tenant'
  AND EXISTS (SELECT 1 FROM tenants WHERE id = 'ten-demo-001')
  AND NOT EXISTS (SELECT 1 FROM tenant_location_settings WHERE tenant_id = 'ten-demo-001');

UPDATE tenant_flow_node_locations
SET tenant_id = 'ten-demo-001'
WHERE tenant_id = 'demo-tenant'
  AND EXISTS (SELECT 1 FROM tenants WHERE id = 'ten-demo-001')
  AND NOT EXISTS (
      SELECT 1 FROM tenant_flow_node_locations existing
      WHERE existing.tenant_id = 'ten-demo-001'
  );

-- Baris 'demo-tenant' yang tersisa (mis. karena tenant demo tidak ada di basis data ini)
-- dibiarkan apa adanya: ia tidak merugikan, dan menghapusnya akan menghilangkan jejak
-- kekeliruan yang berguna saat menelusuri.
