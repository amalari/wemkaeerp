package com.eventverse.app.infrastructure

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B1 + B6 (PLAN-i18n Fase 1, Track B): migrasi V103 **aditif** dan **tidak menyentuh RLS**.
 *
 * Ini tes murni atas isi migrasi (tanpa DB) karena yang dijanjikan adalah bentuk SQL-nya:
 * menambah kolom bahasa dengan CHECK id/en dan bawaan paritas, tanpa mengubah kebijakan
 * Row-Level Security tabel `users`/`tenants` yang sudah ada. Perubahan RLS adalah gerbang
 * keamanan; ia tidak boleh ikut dalam perubahan kosmetik seperti preferensi bahasa.
 */
class I18nMigrationTest {

    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private val sql: String by lazy {
        val file = File(repoRoot, "server/src/main/resources/db/migration/V103__i18n_language_preferences.sql")
        assertTrue(file.exists(), "Migrasi V103 tidak ditemukan di ${file.path}")
        file.readText()
    }

    @Test
    fun `menambah kolom bahasa pada users dan tenants`() {
        assertTrue(
            sql.contains("ALTER TABLE users", ignoreCase = true) &&
                sql.contains("preferred_language", ignoreCase = true),
            "V103 harus menambah users.preferred_language"
        )
        assertTrue(
            sql.contains("ALTER TABLE tenants", ignoreCase = true) &&
                sql.contains("default_language", ignoreCase = true),
            "V103 harus menambah tenants.default_language"
        )
    }

    @Test
    fun `kolom dibatasi CHECK id dan en dengan default id`() {
        assertTrue(sql.contains("users_preferred_language_check"), "CHECK pengguna hilang")
        assertTrue(sql.contains("tenants_default_language_check"), "CHECK tenant hilang")
        assertTrue(
            sql.contains("CHECK (preferred_language IN ('id', 'en'))", ignoreCase = true),
            "CHECK pengguna harus membatasi ke id/en"
        )
        assertTrue(
            sql.contains("CHECK (default_language IN ('id', 'en'))", ignoreCase = true),
            "CHECK tenant harus membatasi ke id/en"
        )
        assertTrue(
            sql.contains("DEFAULT 'id'", ignoreCase = true),
            "Bawaan 'id' menjaga paritas tenant/pengguna lama"
        )
    }

    @Test
    fun `aditif - IF NOT EXISTS untuk idempoten`() {
        assertTrue(
            sql.contains("ADD COLUMN IF NOT EXISTS", ignoreCase = true),
            "Penambahan kolom harus IF NOT EXISTS agar migrasi idempoten"
        )
    }

    @Test
    fun `tidak mengubah Row-Level Security`() {
        val forbidden = listOf(
            "ROW LEVEL SECURITY",
            "CREATE POLICY",
            "DROP POLICY",
            "ENABLE ROW LEVEL SECURITY",
            "DISABLE ROW LEVEL SECURITY",
            "FORCE ROW LEVEL SECURITY",
            "apply_tenant_rls"
        )
        forbidden.forEach { token ->
            assertFalse(
                sql.contains(token, ignoreCase = true),
                "V103 tidak boleh menyentuh RLS, tetapi memuat '$token'"
            )
        }
    }
}
