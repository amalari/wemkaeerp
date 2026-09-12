-- ==============================================================================
-- WeMade ERP — Seed the module catalog and the initial sizing weights
-- ==============================================================================
-- The catalog table would otherwise start empty while BusinessModule already lists nine
-- modules, leaving enum and table disagreeing about what the product is. Seeding here
-- keeps them aligned from the first boot, and gives the billing preview something real
-- to sum.
--
-- Values mirror BusinessModule and OperationalModuleCatalog in :core. When a module is
-- added to the enum, add it here too — until the catalog becomes the source of truth and
-- the enum is retired.
--
-- base_monthly_price_idr are STARTING FIGURES, not researched prices. They exist so the
-- billing path is exercisable end to end; expect to revise them once the first real
-- build costs land in the ledger.
-- ==============================================================================

INSERT INTO module_catalog_entries (
    id, module_id, archetype_code, display_name, description, category_code,
    scope_capability, stock_ownership, costing_behavior,
    accepted_input_types, produced_output_type,
    is_custom_plugin, origin_tenant_id, lifecycle_status, complexity_tier,
    base_monthly_price_idr, released_at
) VALUES
    ('mce-crm-sales', 'crm_sales', 'order_ingestion',
     'Pelanggan & Prospek Sales',
     'Pencatatan prospek, riwayat follow-up negosiasi, dan kontak pelanggan konveksi.',
     'SALES', 'HIERARCHICAL', 'non_stock_service', 'indirect_overhead',
     '["CommercialInquiry"]', 'ProductionOrderDraft',
     FALSE, NULL, 'RELEASED', 'M', 250000, CURRENT_TIMESTAMP),

    ('mce-sampling-order', 'sampling_order', 'order_ingestion',
     'Pola & Sampling Order',
     'Pembuatan SPK sampling prototipe baju, pola potong awal, dan persetujuan sample.',
     'SALES', 'HIERARCHICAL', 'non_stock_service', 'service_fee_only',
     '["ProductionOrderDraft"]', 'ApprovedSampleSpecification',
     FALSE, NULL, 'RELEASED', 'M', 300000, CURRENT_TIMESTAMP),

    ('mce-inventory', 'inventory', 'raw_material',
     'Bahan Baku & Stok Kain',
     'Penerimaan kain rol, stok benang, kancing, zipper, dan multi-satuan (Yard/Kg/Pcs).',
     'LOGISTICS', 'GLOBAL_ONLY', 'owned_raw_material', 'full_package_cogs',
     '["MaterialRequisition"]', 'VerifiedMaterialStock',
     FALSE, NULL, 'RELEASED', 'L', 400000, CURRENT_TIMESTAMP),

    ('mce-tech-pack-bom', 'tech_pack_bom', 'costing_hpp',
     'Spesifikasi BOM & Tech Pack',
     'Lembar kerja spesifikasi jahitan, Bill of Materials (BOM), dan panduan ukuran.',
     'TECHNICAL', 'GLOBAL_ONLY', 'non_stock_service', 'full_package_cogs',
     '["ApprovedSampleSpecification"]', 'TechPackAndYieldData',
     FALSE, NULL, 'RELEASED', 'L', 350000, CURRENT_TIMESTAMP),

    ('mce-costing-hpp', 'costing_hpp', 'costing_hpp',
     'Kalkulasi HPP & Biaya',
     'Perhitungan HPP otomatis: bahan baku + ongkos jahit per menit + finishing & margin laba.',
     'TECHNICAL', 'GLOBAL_ONLY', 'non_stock_service', 'full_package_cogs',
     '["TechPackAndYieldData"]', 'CostingCalculationResult',
     FALSE, NULL, 'RELEASED', 'XL', 500000, CURRENT_TIMESTAMP),

    ('mce-production-mrp', 'production_mrp', 'cutting',
     'Jadwal Mesin & SPK Massal',
     'Alokasi antrean mesin jahit, target jam kerja harian, dan penerbitan SPK potong/jahit.',
     'PRODUCTION', 'GLOBAL_ONLY', 'owned_raw_material', 'indirect_overhead',
     '["CuttingOrderWithFabric"]', 'CutPiecesBundle',
     FALSE, NULL, 'RELEASED', 'XL', 550000, CURRENT_TIMESTAMP),

    ('mce-operator-exec', 'operator_exec', 'sewing',
     'Catatan Kerja Operator',
     'Antarmuka ringkas operator jahit untuk input output potong, jahit, dan progres harian.',
     'PRODUCTION', 'HIERARCHICAL', 'owned_raw_material', 'service_fee_only',
     '["CutPiecesBundle"]', 'AssembledGarmentBundle',
     FALSE, NULL, 'RELEASED', 'M', 300000, CURRENT_TIMESTAMP),

    ('mce-quality-control', 'quality_control', 'quality_control',
     'Inspeksi QC & Defect',
     'Pencatatan baju cacat (reject/scrap), cetak label barcode lolos inspeksi, dan grading.',
     'QUALITY', 'GLOBAL_ONLY', 'owned_raw_material', 'indirect_overhead',
     '["FinishedGarmentUnit"]', 'InspectedAndGradedUnit',
     FALSE, NULL, 'RELEASED', 'L', 400000, CURRENT_TIMESTAMP),

    ('mce-fulfillment', 'fulfillment', 'fulfillment',
     'Packing & Surat Jalan',
     'Finishing setrika uap, verifikasi kuantitas per karton, dan cetak Surat Jalan ekspedisi.',
     'LOGISTICS', 'GLOBAL_ONLY', 'internal_finished_goods', 'retail_valuation_with_fees',
     '["InspectedAndGradedUnit"]', 'DispatchedShipmentManifest',
     FALSE, NULL, 'RELEASED', 'L', 350000, CURRENT_TIMESTAMP),

    -- The screen-printing plugin already running in the seeded D2C tenant (V10/V11).
    -- Without a catalog row its pipeline node would have no price, and the billing
    -- preview would silently undercount that tenant.
    ('mce-sablon-bordir', 'sablon_bordir_custom', 'custom_extension',
     'Sablon Manual & Bordir Komputer',
     'Node kustom: sablon manual per warna dan bordir komputer per 1000 tusuk.',
     NULL, 'GLOBAL_ONLY', 'non_stock_service', 'service_fee_only',
     '["AnyOperationalPayload"]', 'AnyOperationalPayload',
     TRUE, 'ten-demo-d2c', 'RELEASED', 'M', 275000, CURRENT_TIMESTAMP)
ON CONFLICT (module_id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- Sizing weights v1 — deliberate guesses, to be replaced by regression
-- ------------------------------------------------------------------------------
-- These convert countable features into size_points, which in turn lets a past build's
-- hours be reused as a RATE ("hours per point") rather than as a raw total. Borrowing
-- raw hours from a neighbour ignores that the neighbour may have been half the size.
--
-- Do not tune these by intuition once builds accumulate: fit them against actual_hours
-- and store the result as a new weights_version, so historical size_points stay readable
-- under the weights that produced them.
INSERT INTO module_sizing_weights (weights_version, feature_key, weight, calibration_note)
VALUES
    ('v1', 'entity_count', 3, 'Guess. Recalibrate after ~30 builds with recorded hours.'),
    ('v1', 'use_case_count', 2, 'Guess.'),
    ('v1', 'screen_count', 5, 'Guess. UI work dominates in this codebase; watch this one.'),
    ('v1', 'api_endpoint_count', 2, 'Guess.'),
    ('v1', 'db_table_count', 3, 'Guess. Includes the migration plus Exposed table plus repo.'),
    ('v1', 'report_count', 6, 'Guess. Document/PDF output has been consistently underestimated.'),
    ('v1', 'integration_count', 8, 'Guess. External systems carry the widest variance.'),
    ('v1', 'requires_custom_formula', 5, 'Flag weight, applied once when true.'),
    ('v1', 'requires_external_integration', 6, 'Flag weight.'),
    ('v1', 'requires_realtime', 7, 'Flag weight.'),
    ('v1', 'requires_offline_sync', 9, 'Flag weight. Sync conflicts are their own project.'),
    ('v1', 'requires_file_upload', 6, 'Flag weight. Storage, compression and EXIF all hide here.'),
    ('v1', 'requires_new_design_component', 4, 'Flag weight. Clay components must be lifted, not copied.'),
    ('v1', 'target_platform_extra', 2, 'Per KMP target beyond the first.'),
    ('v1', 'affected_existing_module_count', 4, 'Per neighbouring module touched.')
ON CONFLICT (weights_version, feature_key) DO NOTHING;
