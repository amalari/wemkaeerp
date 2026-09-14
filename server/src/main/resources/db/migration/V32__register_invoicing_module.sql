-- ==============================================================================
-- WeMade ERP — Registrasi BusinessModule.INVOICING & Seed Template Faktur (V32)
-- ==============================================================================

-- 1. Jangan sampai tenant dengan granted_modules eksplisit kehilangan modul baru
UPDATE tenant_module_entitlements
SET granted_modules = (
        SELECT jsonb_agg(DISTINCT combined.m)
        FROM (
            SELECT jsonb_array_elements_text(granted_modules) AS m
            UNION
            SELECT unnest(ARRAY['INVOICING'])
        ) AS combined
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE granted_modules IS NOT NULL;

-- 2. Daftarkan ke katalog modul
INSERT INTO module_catalog_entries (
    id, module_id, archetype_code, display_name, description, category_code,
    scope_capability, stock_ownership, costing_behavior,
    accepted_input_types, produced_output_type,
    is_custom_plugin, origin_tenant_id, lifecycle_status, complexity_tier,
    base_monthly_price_idr, released_at
) VALUES (
    'mce-invoicing', 'invoicing', 'invoicing',
    'Invoice & Penagihan',
    'Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas A4.',
    'FINANCE', 'HIERARCHICAL', 'non_stock_service', 'indirect_overhead',
    '["SampleOrderDocument", "SalesContractSnapshot"]', 'IssuedInvoiceDocument',
    FALSE, NULL, 'RELEASED', 'L', 0, CURRENT_TIMESTAMP
)
ON CONFLICT (module_id) DO NOTHING;

-- 3. Backfill wewenang role RBAC yang sudah ada
-- Owner
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'INVOICING', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%owner'
  AND NOT (module_permissions ? 'INVOICING');

-- PPIC (Perlu melihat status invoice & DP untuk menjadwalkan SPK)
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'INVOICING', jsonb_build_object('level', 'VIEW', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%ppic'
  AND NOT (module_permissions ? 'INVOICING');

-- Head of Sales (Manajemen invoice & approval termin)
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'INVOICING', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%sales-head'
  AND NOT (module_permissions ? 'INVOICING');

-- Sales Executive (Membuat draf invoice pesanan & sample yang ditanganinya)
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'INVOICING', jsonb_build_object('level', 'OPERATE', 'scope', 'ASSIGNED_ONLY')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%sales'
  AND id NOT LIKE '%sales-head'
  AND NOT (module_permissions ? 'INVOICING');

-- 4. Seed Profil Penerbit Faktur untuk Demo Tenant (ten-demo-001)
INSERT INTO invoice_issuer_profiles (
    tenant_id, company_name, tagline, address, phone, email, tax_id,
    bank_name, bank_account_number, bank_account_holder,
    signature_name, signature_title
) VALUES (
    'ten-demo-001',
    'PT WeMade Tekstil & Konveksi',
    'Mitra Produksi Seragam, Kemeja & Kaos Berkualitas',
    'Kawasan Industri Rancaekek Kav. 12, Bandung, Jawa Barat 40394',
    '+62 812-3456-7890',
    'finance@wemade.co.id',
    '01.234.567.8-429.000',
    'BCA (Bank Central Asia)',
    '8420-123-999',
    'PT WEMADE TEKSTIL INDONESIA',
    'Budi Santoso, S.E.',
    'Head of Finance & Accounting'
)
ON CONFLICT (tenant_id) DO NOTHING;

-- 5. Seed Template Standar Indonesia A4 untuk Demo Tenant (ten-demo-001)
INSERT INTO invoice_templates (
    id, tenant_id, name, description, paper_size, margin_mm10,
    applicable_kinds, elements, is_default, created_at, updated_at
) VALUES (
    'tpl-std-id-001',
    'ten-demo-001',
    'Template Faktur Standar Indonesia',
    'Format invoice profesional A4 dengan logo, rincian item, subtotal PPN, terbilang rupiah, dan kolom tanda tangan.',
    'A4_PORTRAIT',
    150,
    '["SAMPLE", "DOWN_PAYMENT", "SETTLEMENT", "FULL"]'::jsonb,
    '[
        {"type":"bound_field","elementId":"issuer-name","rect":{"x":150,"y":150,"width":1000,"height":100},"zOrder":1.0,"anchorBelowTable":false,"binding":"issuer.companyName","prefix":"","suffix":"","style":{"fontFamily":"Fredoka","fontSizePt":14,"isBold":true,"isItalic":false,"align":"LEFT","colorHex":4280191211}},
        {"type":"bound_field","elementId":"issuer-address","rect":{"x":150,"y":260,"width":1000,"height":120},"zOrder":2.0,"anchorBelowTable":false,"binding":"issuer.address","prefix":"","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"LEFT","colorHex":4282865001}},
        {"type":"bound_field","elementId":"issuer-contact","rect":{"x":150,"y":390,"width":1000,"height":80},"zOrder":3.0,"anchorBelowTable":false,"binding":"issuer.phone","prefix":"Telp: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"LEFT","colorHex":4282865001}},
        {"type":"bound_field","elementId":"invoice-title","rect":{"x":1200,"y":150,"width":750,"height":100},"zOrder":4.0,"anchorBelowTable":false,"binding":"invoice.kind","prefix":"","suffix":"","style":{"fontFamily":"Fredoka","fontSizePt":16,"isBold":true,"isItalic":false,"align":"RIGHT","colorHex":4280628203}},
        {"type":"bound_field","elementId":"invoice-number","rect":{"x":1200,"y":260,"width":750,"height":80},"zOrder":5.0,"anchorBelowTable":false,"binding":"invoice.number","prefix":"No: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":10,"isBold":true,"isItalic":false,"align":"RIGHT","colorHex":4280191211}},
        {"type":"bound_field","elementId":"invoice-date","rect":{"x":1200,"y":350,"width":750,"height":70},"zOrder":6.0,"anchorBelowTable":false,"binding":"invoice.issueDate","prefix":"Tgl Terbit: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"RIGHT","colorHex":4282865001}},
        {"type":"bound_field","elementId":"invoice-due-date","rect":{"x":1200,"y":420,"width":750,"height":70},"zOrder":7.0,"anchorBelowTable":false,"binding":"invoice.dueDate","prefix":"Jatuh Tempo: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"RIGHT","colorHex":4292617766}},
        {"type":"line_shape","elementId":"divider-header","rect":{"x":150,"y":520,"width":1800,"height":10},"zOrder":8.0,"anchorBelowTable":false,"strokeHex":4291548641,"strokeMm10":2},
        {"type":"static_text","elementId":"bill-to-label","rect":{"x":150,"y":560,"width":600,"height":60},"zOrder":9.0,"anchorBelowTable":false,"text":"DITAGIHKAN KEPADA:","style":{"fontFamily":"Fredoka","fontSizePt":9,"isBold":true,"isItalic":false,"align":"LEFT","colorHex":4284769419}},
        {"type":"bound_field","elementId":"bill-to-name","rect":{"x":150,"y":630,"width":800,"height":80},"zOrder":10.0,"anchorBelowTable":false,"binding":"billTo.name","prefix":"","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":11,"isBold":true,"isItalic":false,"align":"LEFT","colorHex":4280191211}},
        {"type":"bound_field","elementId":"bill-to-address","rect":{"x":150,"y":720,"width":800,"height":120},"zOrder":11.0,"anchorBelowTable":false,"binding":"billTo.address","prefix":"","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"LEFT","colorHex":4282865001}},
        {"type":"bound_field","elementId":"bill-to-phone","rect":{"x":150,"y":850,"width":800,"height":60},"zOrder":12.0,"anchorBelowTable":false,"binding":"billTo.phone","prefix":"Kontak: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"LEFT","colorHex":4282865001}},
        {"type":"item_table","elementId":"invoice-table","rect":{"x":150,"y":950,"width":1800,"height":600},"zOrder":13.0,"anchorBelowTable":false,"columns":[{"binding":"line.no","headerText":"#","widthRatio":"1/12","align":"CENTER"},{"binding":"line.description","headerText":"Deskripsi Barang / Jasa","widthRatio":"5/12","align":"LEFT"},{"binding":"line.quantity","headerText":"Qty","widthRatio":"2/12","align":"RIGHT"},{"binding":"line.unitPrice","headerText":"Harga Satuan","widthRatio":"2/12","align":"RIGHT"},{"binding":"line.amount","headerText":"Subtotal","widthRatio":"2/12","align":"RIGHT"}],"rowHeight":80,"headerHeight":100,"headerBgHex":4294047225,"headerTextHex":4280191211,"borderColorHex":4291548641,"zebraFillHex":4294507260},
        {"type":"bound_field","elementId":"total-in-words","rect":{"x":150,"y":1600,"width":1050,"height":150},"zOrder":14.0,"anchorBelowTable":true,"binding":"invoice.totalInWords","prefix":"Terbilang: # ","suffix":" #","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":true,"align":"LEFT","colorHex":4281549141}},
        {"type":"bound_field","elementId":"subtotal-label","rect":{"x":1250,"y":1600,"width":700,"height":60},"zOrder":15.0,"anchorBelowTable":true,"binding":"invoice.subtotal","prefix":"Subtotal: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":10,"isBold":false,"isItalic":false,"align":"RIGHT","colorHex":4280191211}},
        {"type":"bound_field","elementId":"tax-amount-label","rect":{"x":1250,"y":1670,"width":700,"height":60},"zOrder":16.0,"anchorBelowTable":true,"binding":"invoice.taxAmount","prefix":"PPN (11%): ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":10,"isBold":false,"isItalic":false,"align":"RIGHT","colorHex":4280191211}},
        {"type":"bound_field","elementId":"grand-total-label","rect":{"x":1250,"y":1750,"width":700,"height":80},"zOrder":17.0,"anchorBelowTable":true,"binding":"invoice.total","prefix":"TOTAL: ","suffix":"","style":{"fontFamily":"Fredoka","fontSizePt":13,"isBold":true,"isItalic":false,"align":"RIGHT","colorHex":4280628203}},
        {"type":"static_text","elementId":"bank-payment-header","rect":{"x":150,"y":1800,"width":800,"height":60},"zOrder":18.0,"anchorBelowTable":true,"text":"PEMBAYARAN DITRANSFER KE:","style":{"fontFamily":"Fredoka","fontSizePt":9,"isBold":true,"isItalic":false,"align":"LEFT","colorHex":4284769419}},
        {"type":"bound_field","elementId":"bank-details","rect":{"x":150,"y":1870,"width":800,"height":100},"zOrder":19.0,"anchorBelowTable":true,"binding":"issuer.bankName","prefix":"","suffix":" A.N. Rekening Penerbit","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":true,"isItalic":false,"align":"LEFT","colorHex":4280191211}},
        {"type":"bound_field","elementId":"bank-acc-no","rect":{"x":150,"y":1980,"width":800,"height":70},"zOrder":20.0,"anchorBelowTable":true,"binding":"issuer.bankAccountNumber","prefix":"No. Rekening: ","suffix":"","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"LEFT","colorHex":4280191211}},
        {"type":"static_text","elementId":"sign-label","rect":{"x":1450,"y":1900,"width":500,"height":60},"zOrder":21.0,"anchorBelowTable":true,"text":"Hormat Kami,","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"CENTER","colorHex":4280191211}},
        {"type":"line_shape","elementId":"sign-line","rect":{"x":1450,"y":2150,"width":500,"height":10},"zOrder":22.0,"anchorBelowTable":true,"strokeHex":4280191211,"strokeMm10":2},
        {"type":"static_text","elementId":"sign-title","rect":{"x":1450,"y":2170,"width":500,"height":60},"zOrder":23.0,"anchorBelowTable":true,"text":"( Bagian Keuangan )","style":{"fontFamily":"Nunito","fontSizePt":9,"isBold":false,"isItalic":false,"align":"CENTER","colorHex":4284769419}}
    ]'::jsonb,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
