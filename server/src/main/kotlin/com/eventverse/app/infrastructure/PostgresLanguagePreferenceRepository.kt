package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LanguagePreferenceRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.TenantsTable
import com.eventverse.app.infrastructure.tables.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Implementasi Postgres preferensi bahasa (V103, PLAN-i18n Fase 1 Track B).
 *
 * Operasi `users` dijalankan lewat `dbQuery(tenantId)` supaya `SET LOCAL
 * app.current_tenant_id` terpasang — tabel `users` ber-RLS, jadi tanpa itu baris
 * tidak terlihat. `tenantId == null` berarti pemanggil platform (superadmin tanpa
 * workspace): koneksi owner yang menembus RLS dipakai.
 *
 * Nilai tak dikenal di kolom → `null` (fallback ke bawaan di pemanggil), bukan
 * ditebak. CHECK constraint di DB adalah net terakhir.
 */
class PostgresLanguagePreferenceRepository : LanguagePreferenceRepository {

    override suspend fun findUserLanguage(userId: UserId, tenantId: TenantId?): LanguageCode? =
        DatabaseFactory.dbQuery(tenantId) {
            UsersTable.select(UsersTable.preferredLanguage)
                .where { UsersTable.id eq userId.value }
                .singleOrNull()
                ?.get(UsersTable.preferredLanguage)
        }?.let { LanguageCode.parse(it) }

    override suspend fun setUserLanguage(userId: UserId, language: LanguageCode, tenantId: TenantId?) {
        DatabaseFactory.dbQuery(tenantId) {
            UsersTable.update({ UsersTable.id eq userId.value }) {
                it[preferredLanguage] = language.code
            }
        }
    }

    override suspend fun findTenantLanguage(tenantId: TenantId): LanguageCode? =
        DatabaseFactory.dbQuery(tenantId) {
            TenantsTable.select(TenantsTable.defaultLanguage)
                .where { TenantsTable.id eq tenantId.value }
                .singleOrNull()
                ?.get(TenantsTable.defaultLanguage)
        }?.let { LanguageCode.parse(it) }

    override suspend fun setTenantLanguage(tenantId: TenantId, language: LanguageCode) {
        DatabaseFactory.dbQuery(tenantId) {
            TenantsTable.update({ TenantsTable.id eq tenantId.value }) {
                it[defaultLanguage] = language.code
            }
        }
    }
}
