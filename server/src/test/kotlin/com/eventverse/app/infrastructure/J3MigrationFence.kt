package com.eventverse.app.infrastructure

import com.eventverse.app.tenant.TenantPackContributions

/**
 * Pagar migrasi modul khusus tenant (J3) — TRD-PLAT-004 P4 (U3), PLAN-module-ownership-lanes Track C. **Memblokir.**
 *
 * C1 — deteksi mekanis: sebuah migrasi adalah **migrasi J3** bila ia membuat atau mengubah schema milik modul J3
 * yang terdaftar di [TenantPackContributions] — kontrak yang sama dengan sumber `ModuleSchemaMap.byModule`
 * (sebelum Track B merge deteksi memakai awalan pack `layanan_`; kini registri B2 ada, jadi dibaca langsung).
 *
 * C2 — aturan: migrasi J3 dilarang **mengunci diri ke schema modul lain**. `REFERENCES` / `FROM` / `JOIN` /
 * `SET search_path` hanya boleh menuju:
 *  1. `public.tenants` dan `public.users`;
 *  2. schema milik modul J3 terdaftar (satu pack = satu tenant, jadi schema sesama modul J3 dianggap ranah milik
 *     sendiri; pemisahan antar pack J3 diperketat saat pack J3 kedua lahir — kriteria TRD §5.6);
 *  3. schema modul yang dirujuk lewat `moduleReferences` (B6) pack J3 itu — saat ini kosong.
 *
 * Pilihan pemindaian, supaya tidak ditebak:
 * - `REFERENCES` **tak-terkualifikasi tetap dipindai** (resolve ke `public.<tabel>`): FK adalah kopling tahan lama,
 *   dan V90 memang ber-FK ke `tenants`. `FROM`/`JOIN` tak-terkualifikasi **dilewati**: lewat `search_path` bawaan
 *   ia hanya bisa mendarat di `public`, isi `public` sudah dijaga `ModuleSchemaOwnershipTest` (B8), dan ini
 *   menghindari positif palsu terhadap pemanggilan fungsi (`FROM generate_series(...)`).
 * - `SET search_path` ikut diadili supaya `FROM` tak-terkualifikasi tidak dipakai mem-bypass schema lain.
 * - `INSERT INTO` / `UPDATE` tidak dipindai: pendaftaran katalog & backfill peran (pola V64/V90) memang menulis
 *   tabel platform, dan menulis baris bukan kopling schema.
 *
 * Berkas yang menyentuh schema J3 diadili dengan standar J3 — termasuk migrasi platform yang kebetulan menyentuhnya.
 *
 * **Batas yang diketahui**: pemindai regex menangkap kesalahan lazim, bukan sabotase (SQL dirakit dari string,
 * fungsi dinamis) — batas yang sama dengan yang diterima di `TenantCodeBoundaryTest`.
 *
 * Berkas ini sengaja di sumber **tes** (pemindai P4 adalah "tes server", PLAN Track C) — karena itu bebas mengimpor
 * [TenantPackContributions], hal yang justru dilarang bagi mesin di `src/main` (pagar B4 mengecualikan tes).
 */
object J3MigrationFence {

    /** Tabel `public` yang boleh dirujuk migrasi J3 (PLAN Track C2). Perluasan = keputusan yang dicatat di PR. */
    private val publicWhitelist: Set<String> = setOf("tenants", "users")

    /** Schema milik modul J3 terdaftar — nama schema = kode modul (B8). */
    private val j3Schemas: Set<String> =
        TenantPackContributions.all.flatMap { c -> c.tables.keys.map { it.value } }.toSet()

    /** Schema modul yang dirujuk lewat `moduleReferences` (B6) pack J3 — boleh dirujuk langsung. */
    private val referencedSchemas: Set<String> =
        TenantPackContributions.all.flatMap { c -> c.pack.moduleReferences.map { it.platformModuleId.value } }.toSet()

    /** Satu pelanggaran pagar: di berkas apa, baris berapa, lewat kata kunci apa, menuju target apa. */
    data class Violation(
        val file: String,
        val line: Int,
        val kind: String,
        val target: String,
        val message: String,
    ) {
        override fun toString(): String = "$file:$line: [$kind] $target — $message"
    }

    private const val ID = "[A-Za-z_][A-Za-z0-9_]*"

    private val allowedSchemas: Set<String> get() = j3Schemas + referencedSchemas + "public"

    /** `true` bila [sql] membuat atau mengubah schema milik modul J3 terdaftar (C1). */
    fun isJ3Migration(sql: String): Boolean {
        if (j3Schemas.isEmpty()) return false
        val code = stripComments(sql)
        val alternation = j3Schemas.joinToString("|") { Regex.escape(it) }
        val createsOrAlters = Regex(
            """(?i)\b(?:CREATE\s+SCHEMA\s+(?:IF\s+NOT\s+EXISTS\s+)?|ALTER\s+(?:SCHEMA|TABLE)\s+(?:IF\s+EXISTS\s+)?)($alternation)\b"""
        )
        val qualifiedUse = Regex("""\b($alternation)\s*\.""")
        return createsOrAlters.containsMatchIn(code) || qualifiedUse.containsMatchIn(code)
    }

    /** Semua pelanggaran pagar di satu migrasi; kosong berarti lolos. Berkas non-J3 selalu lolos. */
    fun scan(fileName: String, sql: String): List<Violation> {
        if (!isJ3Migration(sql)) return emptyList()
        val code = stripComments(sql)
        val violations = mutableListOf<Violation>()

        // 1) REFERENCES — terkualifikasi (`crm_sales.deals(id)`) maupun tidak (`tenants(id)` → public).
        Regex("""(?i)\bREFERENCES\s+($ID)(?:\s*\.\s*($ID))?(?=\s*\()""").findAll(code).forEach { m ->
            val first = m.groupValues[1].lowercase()
            val second = m.groupValues[2].takeIf { it.isNotEmpty() }?.lowercase()
            judge(violations, fileName, code, m.range.first, "REFERENCES", first, second)
        }

        // 2) FROM / JOIN — hanya target terkualifikasi (lihat KDoc untuk yang tak-terkualifikasi).
        Regex("""(?i)\b(?:FROM|JOIN)\s+($ID)\s*\.\s*($ID)""").findAll(code).forEach { m ->
            judge(
                violations, fileName, code, m.range.first, "FROM/JOIN",
                m.groupValues[1].lowercase(), m.groupValues[2].lowercase()
            )
        }

        // 3) SET search_path — supaya FROM tak-terkualifikasi tidak diarahkan ke schema modul lain.
        Regex("""(?i)\bSET\s+search_path\s*(?:TO|=)\s*([^;]+)""").findAll(code).forEach { m ->
            m.groupValues[1].split(',')
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotEmpty() }
                .forEach { token ->
                    val schema = token.substringBefore('.').lowercase()
                    if (schema !in allowedSchemas && schema != "\$user") {
                        violations += Violation(
                            file = fileName,
                            line = lineOf(code, m.range.first),
                            kind = "SET search_path",
                            target = schema,
                            message = "search_path mengarah ke schema '$schema' — migrasi J3 hanya boleh bekerja di " +
                                "schema miliknya, public, atau schema moduleReferences (TRD-PLAT-004 P4)",
                        )
                    }
                }
        }
        return violations
    }

    private fun judge(
        out: MutableList<Violation>,
        file: String,
        code: String,
        offset: Int,
        kind: String,
        first: String,
        second: String?,
    ) {
        // REFERENCES tak-terkualifikasi (`tenants(id)`) resolve lewat search_path ke public.
        val schema = if (second == null) "public" else first
        val table = if (second == null) first else second
        val target = "$schema.$table"
        val allowed = when {
            schema == "public" -> table in publicWhitelist
            else -> schema in allowedSchemas
        }
        if (allowed) return
        out += Violation(
            file = file,
            line = lineOf(code, offset),
            kind = kind,
            target = target,
            message = if (schema == "public") {
                "tabel public di luar daftar putih ${publicWhitelist.joinToString()} — migrasi J3 hanya boleh " +
                    "merujuk public.tenants/public.users; perluasan daftar putih diputuskan di PR (PLAN Track C2)"
            } else {
                "migrasi J3 dilarang mengunci diri ke schema modul lain (TRD-PLAT-004 P4) — rujuk lewat " +
                    "moduleReferences pack J3 bila modul itu memang dipakai, atau salin data yang dibutuhkan"
            },
        )
    }

    private fun lineOf(code: String, offset: Int): Int = code.take(offset).count { it == '\n' } + 1

    /**
     * Membuang isi komentar SQL — komentar baris (`--`) dan komentar blok (pembuka garis-miring-bintang dan
     * penutup bintang-garis-miring) — tanpa menggeser offset: karakter komentar diganti spasi dan baris baru
     * dipertahankan, sehingga nomor baris pelanggaran tetap benar. String literal (tanda kutip tunggal dan ganda)
     * dihormati agar tanda kutip atau "--" di dalamnya tidak memicu mode komentar.
     */
    private fun stripComments(sql: String): String {
        val out = sql.toCharArray()
        var i = 0
        var inSingle = false
        var inDouble = false
        while (i < sql.length) {
            val c = sql[i]
            if (inSingle || inDouble) {
                if (inSingle && c == '\'') inSingle = false
                if (inDouble && c == '"') inDouble = false
                i++
                continue
            }
            when {
                c == '\'' -> inSingle = true
                c == '"' -> inDouble = true
                sql.startsWith("--", i) -> {
                    while (i < sql.length && sql[i] != '\n') {
                        out[i] = ' '; i++
                    }
                    continue
                }
                sql.startsWith("/*", i) -> {
                    while (i < sql.length) {
                        if (sql.startsWith("*/", i)) {
                            out[i] = ' '; out[i + 1] = ' '; i += 2; break
                        }
                        if (sql[i] != '\n') out[i] = ' '
                        i++
                    }
                    continue
                }
            }
            i++
        }
        return String(out)
    }
}