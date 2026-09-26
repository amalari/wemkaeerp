-- ==============================================================================
-- WeMade ERP — Perbaikan Archetype Slot Tech Pack BOM (V29)
-- ==============================================================================
-- Memisahkan capability slot TECH_PACK_BOM dari costing_hpp menjadi product_engineering.
-- Menyelaraskan module_catalog_entries dan node pipeline tenant_pipelines.
-- ==============================================================================

-- 1. Perbarui module catalog entry untuk Tech Pack BOM
UPDATE module_catalog_entries
   SET archetype_code = 'product_engineering',
       accepted_input_types = '["ApprovedSampleSpecification"]'::jsonb,
       produced_output_type = 'TechPackAndYieldData'
 WHERE module_id = 'tech_pack_bom' OR id = 'mce-tech-pack-bom';

-- 2. Perbarui node-node pipeline yang merujuk tech_pack_bom pada graph_data
UPDATE tenant_pipelines
   SET graph_data = jsonb_set(
       graph_data,
       '{nodes}',
       (
           SELECT jsonb_agg(
               CASE
                   WHEN node->>'moduleId' = 'tech_pack_bom'
                   THEN jsonb_set(node, '{archetype}', '"product_engineering"')
                   ELSE node
               END
           )
           FROM jsonb_array_elements(graph_data->'nodes') AS node
       )
   )
 WHERE graph_data ? 'nodes'
   AND EXISTS (
       SELECT 1
       FROM jsonb_array_elements(graph_data->'nodes') AS elem
       WHERE elem->>'moduleId' = 'tech_pack_bom'
   );
