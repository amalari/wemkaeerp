package com.eventverse.app.infrastructure

import com.eventverse.app.I18nPackFixtures
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.missingTranslations
import com.eventverse.app.infrastructure.tables.DomainPacksTable
import com.eventverse.app.shared.pack.DomainPackCodec
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRD-I18N-002 Track B pada Postgres sungguhan (DB scratch): pack dua bahasa disimpan sebagai dokumen JSONB dan terbaca
 * utuh (tanpa kolom `pack_languages` — tidak ada migrasi), dokumen lama (tanpa kunci `languages`, nilai string biasa)
 * terbaca identik, dan dokumen dengan kode bahasa tak dikenal **melempar** saat dibaca (tidak jatuh ke `id`).
 */
class PostgresDomainPackLocalizationTest {

    private val suffix = abs(System.nanoTime() % 1_000_000).toString()
    private val code = "klinikl10n$suffix"
    private val packs = PostgresDomainPackRepository()

    @BeforeTest
    fun setup() { DatabaseFactory.init() }

    @AfterTest
    fun cleanup() = runBlocking<Unit> {
        DatabaseFactory.dbQuery { DomainPacksTable.deleteWhere { DomainPacksTable.code eq code } }
    }

    @Test
    fun bilingualPack_roundTripsThroughJsonb_andLegacyDocumentReadsIdentically() = runBlocking<Unit> {
        val bilingual = I18nPackFixtures.pack(code)
        packs.save(StoredDomainPack(bilingual, 1, DomainPackStatus.DRAFT, null))
        val read = assertNotNull(packs.findLatest(DomainPackCode(code))).pack
        assertEquals(bilingual, read)
        assertEquals("Service", read.slots.first().displayNameText.textOrNull(LanguageCode.EN))
        assertTrue(read.missingTranslations().isEmpty())

        // Dokumen lama: id saja, tanpa kunci "languages", ditulis dengan format sebelum fase 2.
        val legacy = I18nPackFixtures.pack(code, languages = setOf("id"))
        val legacyJson = DomainPackCodec.encodeToString(legacy)
        assertFalse(legacyJson.contains("\"languages\""))
        DatabaseFactory.dbQuery { DomainPacksTable.update({ DomainPacksTable.code eq code }) { it[definition] = legacyJson } }
        val legacyRead = assertNotNull(packs.findLatest(DomainPackCode(code))).pack
        assertEquals(legacy, legacyRead)
        assertEquals(PackLanguages.ID_ONLY, legacyRead.languages)
        assertEquals(legacyJson, DomainPackCodec.encodeToString(legacyRead))
    }

    @Test
    fun storedDocumentWithUnknownLanguage_throwsOnRead_insteadOfFallingBackToId() = runBlocking<Unit> {
        packs.save(StoredDomainPack(I18nPackFixtures.pack(code), 1, DomainPackStatus.DRAFT, null))
        val corrupt = DomainPackCodec.encodeToString(I18nPackFixtures.pack(code)).replace("\"en\":\"1. Service\"", "\"fr\":\"1. Service\"")
        assertTrue(corrupt.contains("\"fr\""))
        DatabaseFactory.dbQuery { DomainPacksTable.update({ DomainPacksTable.code eq code }) { it[definition] = corrupt } }
        val failure = runCatching { packs.findLatest(DomainPackCode(code)) }.exceptionOrNull()
        assertNotNull(failure, "baris korup wajib melempar (Kontrak 4), bukan dianggap tidak ada")
    }

    @Test
    fun menuLabel_roundTripsThroughJsonb_andDocumentWithoutKeyReadsIdentically() = runBlocking<Unit> {
        val withLabel = I18nPackFixtures.pack(code, menuLabel = true)
        packs.save(StoredDomainPack(withLabel, 1, DomainPackStatus.DRAFT, null))
        val read = assertNotNull(packs.findLatest(DomainPackCode(code))).pack
        assertEquals(withLabel, read)
        assertEquals("Queue", read.modules.last().menuLabelText?.textOrNull(LanguageCode.EN))

        val old = I18nPackFixtures.pack(code, menuLabel = false)
        val oldJson = DomainPackCodec.encodeToString(old)
        assertNull(DomainPackCodec.decode(oldJson).modules.last().menuLabelText) // modul pack uji tanpa menuLabel (org_chart bersama boleh membawanya)
        DatabaseFactory.dbQuery { DomainPacksTable.update({ DomainPacksTable.code eq code }) { it[definition] = oldJson } }
        val oldRead = assertNotNull(packs.findLatest(DomainPackCode(code))).pack
        assertEquals(old, oldRead)
        assertEquals(oldJson, DomainPackCodec.encodeToString(oldRead))
    }
}
