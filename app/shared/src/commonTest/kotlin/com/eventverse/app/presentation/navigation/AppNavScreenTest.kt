package com.eventverse.app.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppNavScreenTest {

    @Test
    fun canonical_paths_resolve_correct_screens() {
        assertEquals(AppNavScreen.ORG_CHART, AppNavScreen.fromPath("/org-chart"))
        assertEquals(AppNavScreen.DYNAMIC_RBAC, AppNavScreen.fromPath("/rbac"))
        assertEquals(AppNavScreen.FACTORY_FLOW, AppNavScreen.fromPath("/factory-flow"))
        assertEquals(AppNavScreen.LOGIN, AppNavScreen.fromPath("/login"))
    }

    @Test
    fun aliases_resolve_correct_screens() {
        assertEquals(AppNavScreen.ORG_CHART, AppNavScreen.fromPath("/orgchart"))
        assertEquals(AppNavScreen.ORG_CHART, AppNavScreen.fromPath("/organization"))
        assertEquals(AppNavScreen.ORG_CHART, AppNavScreen.fromPath("/bagan-organisasi"))

        assertEquals(AppNavScreen.DYNAMIC_RBAC, AppNavScreen.fromPath("/roles"))
        assertEquals(AppNavScreen.DYNAMIC_RBAC, AppNavScreen.fromPath("/hak-akses"))
        assertEquals(AppNavScreen.DYNAMIC_RBAC, AppNavScreen.fromPath("/permissions"))

        assertEquals(AppNavScreen.FACTORY_FLOW, AppNavScreen.fromPath("/pipeline"))
        assertEquals(AppNavScreen.FACTORY_FLOW, AppNavScreen.fromPath("/alur-pabrik"))
        assertEquals(AppNavScreen.FACTORY_FLOW, AppNavScreen.fromPath("/flow"))

        assertEquals(AppNavScreen.LOGIN, AppNavScreen.fromPath("/masuk"))
    }

    @Test
    fun hash_based_routes_resolve_correctly() {
        assertEquals(AppNavScreen.DYNAMIC_RBAC, AppNavScreen.fromPath("#/rbac"))
        assertEquals(AppNavScreen.FACTORY_FLOW, AppNavScreen.fromPath("#/factory-flow"))
        assertEquals(AppNavScreen.ORG_CHART, AppNavScreen.fromPath("#org-chart"))
        assertEquals(AppNavScreen.LOGIN, AppNavScreen.fromPath("#/login"))
    }

    @Test
    fun query_params_and_trailing_slashes_are_handled_cleanly() {
        assertEquals(AppNavScreen.FACTORY_FLOW, AppNavScreen.fromPath("/factory-flow?tab=active&order=desc"))
        assertEquals(AppNavScreen.DYNAMIC_RBAC, AppNavScreen.fromPath("/rbac/"))
        assertEquals(AppNavScreen.LOGIN, AppNavScreen.fromPath("/login?redirect=%2Frbac"))
    }

    @Test
    fun root_and_unrecognized_paths_return_null() {
        assertNull(AppNavScreen.fromPath("/"))
        assertNull(AppNavScreen.fromPath(""))
        assertNull(AppNavScreen.fromPath("/unknown-page"))
    }

    @Test
    fun protected_screen_attribute_is_correct() {
        assertTrue(AppNavScreen.ORG_CHART.isProtected)
        assertTrue(AppNavScreen.DYNAMIC_RBAC.isProtected)
        assertTrue(AppNavScreen.FACTORY_FLOW.isProtected)
        assertTrue(AppNavScreen.INVOICING.isProtected)
        assertTrue(AppNavScreen.INVOICING_TEMPLATES.isProtected)
        assertTrue(!AppNavScreen.LOGIN.isProtected)
    }

    @Test
    fun invoicing_templates_subroute_and_template_id_resolve_correctly() {
        // Base gallery route
        assertEquals(AppNavScreen.INVOICING_TEMPLATES, AppNavScreen.fromPath("/invoicing/templates"))
        assertEquals(AppNavScreen.INVOICING_TEMPLATES, AppNavScreen.fromPath("/invoicing/design"))
        assertEquals(AppNavScreen.INVOICING_TEMPLATES, AppNavScreen.fromPath("#/invoicing/templates"))

        // Subpath with template ID
        assertEquals(AppNavScreen.INVOICING_TEMPLATES, AppNavScreen.fromPath("/invoicing/templates/tpl-sample-001"))
        assertEquals(AppNavScreen.INVOICING_TEMPLATES, AppNavScreen.fromPath("/invoicing/design/tpl-sample-001"))

        // Extraction
        assertEquals("tpl-sample-001", AppNavScreen.extractTemplateId("/invoicing/templates/tpl-sample-001"))
        assertEquals("tpl-sample-001", AppNavScreen.extractTemplateId("/invoicing/design/tpl-sample-001"))
        assertEquals("tpl-sample-001", AppNavScreen.extractTemplateId("/invoicing/templates?templateId=tpl-sample-001"))
        assertEquals("tpl-sample-001", AppNavScreen.extractTemplateId("/invoicing/templates?id=tpl-sample-001"))
        assertNull(AppNavScreen.extractTemplateId("/invoicing/templates"))
        assertNull(AppNavScreen.extractTemplateId("/invoicing"))
    }
}
