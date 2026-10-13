package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LanguagePreferenceRepository
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementasi in-memory preferensi bahasa untuk tes (pola `InMemory…Repository`).
 *
 * Meniru semantik Postgres: nilai tak dikenal → `null`, bukan ditebak.
 */
class InMemoryLanguagePreferenceRepository(
    initialUserLanguages: Map<String, String> = emptyMap(),
    initialTenantLanguages: Map<String, String> = emptyMap()
) : LanguagePreferenceRepository {

    private val userLanguages = ConcurrentHashMap<String, String>().apply {
        putAll(initialUserLanguages)
    }
    private val tenantLanguages = ConcurrentHashMap<String, String>().apply {
        putAll(initialTenantLanguages)
    }

    override suspend fun findUserLanguage(userId: UserId, tenantId: TenantId?): LanguageCode? =
        userLanguages[userId.value]?.let { LanguageCode.parse(it) }

    override suspend fun setUserLanguage(userId: UserId, language: LanguageCode, tenantId: TenantId?) {
        userLanguages[userId.value] = language.code
    }

    override suspend fun findTenantLanguage(tenantId: TenantId): LanguageCode? =
        tenantLanguages[tenantId.value]?.let { LanguageCode.parse(it) }

    override suspend fun setTenantLanguage(tenantId: TenantId, language: LanguageCode) {
        tenantLanguages[tenantId.value] = language.code
    }
}
