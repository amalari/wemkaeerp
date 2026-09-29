package com.eventverse.app.shared.rbac

import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessDecisionCodecTest {

    private val operator = AccessDecision(
        config = ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY, allowedDesks = setOf("HOOPING", "MACHINE_EMBROIDERY")),
        source = AccessSource.DEPARTMENT,
        fromRole = ModuleAccessConfig(),
        fromDepartment = ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY, allowedDesks = setOf("HOOPING", "MACHINE_EMBROIDERY"))
    )

    @Test
    fun roundTrip_keepsLevelScopeDesksAndSource() {
        val decisions = mapOf(BusinessModule.OPERATOR_EXEC to operator, BusinessModule.CRM_SALES to AccessDecision(
            ModuleAccessConfig(), AccessSource.NONE, ModuleAccessConfig(), ModuleAccessConfig()))
        assertEquals(decisions, AccessDecisionCodec.decode(JsonParser.parseObject(AccessDecisionCodec.encode(decisions).encode())))
    }

    @Test
    fun unknownModuleOrLevel_isSkipped_notGuessed() {
        val json = AccessDecisionCodec.encode(mapOf(BusinessModule.OPERATOR_EXEC to operator)).encode()
            .replace("\"modules\":{", "\"modules\":{\"MODUL_BARU\":{\"config\":{\"level\":\"MANAGE\",\"scope\":\"ALL_TENANT_DATA\"},\"source\":\"ROLE\"},")
            .replace("\"level\":\"OPERATE\",\"scope\":\"OWN_DATA_ONLY\"", "\"level\":\"SUPER\",\"scope\":\"OWN_DATA_ONLY\"")
        val decoded = AccessDecisionCodec.decode(JsonParser.parseObject(json))
        assertEquals(emptyMap(), decoded, "modul tak dikenal & level tak dikenal → tidak ada entri (tertutup)")
    }
}
