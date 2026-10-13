package com.eventverse.app.domain.i18n

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Preferensi bahasa tersimpan (PLAN-i18n Fase 1, Track B).
 *
 * Dua tingkat, mengikuti tangga "pengguna menang atas tenant":
 *  - [findUserLanguage]/[setUserLanguage] — pilihan pribadi pemakai;
 *  - [findTenantLanguage]/[setTenantLanguage] — bawaan pabrik.
 *
 * [tenantId] pada operasi `users` bukan sekadar penyaring: tabel `users` ber-RLS,
 * jadi implementasi Postgres memakainya untuk `SET LOCAL app.current_tenant_id`
 * agar baris pengguna benar-benar terlihat/ditulis. `null` berarti pemanggil
 * platform (mis. superadmin tanpa workspace) — dijalankan lewat koneksi owner yang
 * memang menembus RLS.
 *
 * Nilai yang tersimpan adalah [LanguageCode]; implementasi yang menemukan nilai tak
 * dikenal di kolom penyimpanan mengembalikan `null` (pemanggil jatuh ke bawaan)
 * alih-alih menebak — sejalan dengan Kontrak 4 variabilitas (tolak, jangan fallback
 * senyap).
 */
interface LanguagePreferenceRepository {
    suspend fun findUserLanguage(userId: UserId, tenantId: TenantId?): LanguageCode?
    suspend fun setUserLanguage(userId: UserId, language: LanguageCode, tenantId: TenantId?)

    suspend fun findTenantLanguage(tenantId: TenantId): LanguageCode?
    suspend fun setTenantLanguage(tenantId: TenantId, language: LanguageCode)
}
