package com.eventverse.app

import com.eventverse.app.domain.invoicing.template.TemplateElement
import com.eventverse.app.shared.invoicing.InvoiceTemplateCodec
import com.eventverse.app.shared.json.JsonParser
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Menjaga kesepakatan bentuk JSON antara seed SQL dan [InvoiceTemplateCodec].
 *
 * `V32__register_invoicing_module.sql` menulis kolom `elements` dengan nama kunci yang berbeda
 * dari codec kanonik (`elementId`, `headerText`, `widthRatio` sebagai teks). Sebelum reader-nya
 * dibuat toleran, seluruh 23 elemen template standar dibuang saat dibaca dari PostgreSQL dan
 * kanvas A4 di web tampil **kosong** — semua kolom isian faktur hilang tanpa pesan kesalahan apa
 * pun, karena `mapNotNull` membuang elemen yang gagal decode secara diam-diam.
 *
 * Test ini membaca file migrasi aslinya, bukan salinan, supaya perubahan bentuk seed di masa
 * depan langsung ketahuan di sini alih-alih di layar pengguna.
 */
class InvoiceTemplateSeedDecodeTest {

    private fun readMigration(): String =
        javaClass.classLoader
            .getResourceAsStream("db/migration/V32__register_invoicing_module.sql")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("File migrasi V32 tidak ditemukan di classpath test")

    private fun extractElementsJson(sql: String): String {
        // Setiap literal jsonb di file ini ditulis sebagai '<json>'::jsonb. Literal yang memuat
        // "elementId" adalah array elemen template; literal lain (applicable_kinds, arkeotipe
        // modul) tidak punya kunci itu.
        return Regex("""\[[\s\S]*?]'::jsonb""")
            .findAll(sql)
            .map { it.value.removeSuffix("'::jsonb") }
            .firstOrNull { it.contains("\"elementId\"") }
            ?: error("Literal elemen template tidak ditemukan di migrasi V32")
    }

    @Test
    fun `standard template seed from migration should decode into 23 drawable elements`() {
        val elementsJson = extractElementsJson(readMigration())

        val template = InvoiceTemplateCodec.decode(
            JsonParser.parseObject(
                """{"id":"tpl-std-id-001","tenantId":"ten-demo-001","name":"seed","elements": $elementsJson}"""
            )
        )

        assertEquals(
            23,
            template.elements.size,
            "Seluruh elemen seed harus terbaca; elemen yang gagal decode membuat kanvas A4 kosong"
        )

        val issuerName = template.elements.first { it.elementId == "issuer-name" }
        assertTrue(issuerName is TemplateElement.BoundField)

        val table = template.elements.first { it.elementId == "invoice-table" } as TemplateElement.ItemTable
        assertEquals(5, table.columns.size, "Kolom tabel item tidak boleh hilang saat decode")
        assertEquals("Deskripsi Barang / Jasa", table.columns[1].header)
        assertEquals(5, table.columns[1].widthRatio.numerator)
        assertEquals(12, table.columns[1].widthRatio.denominator)
    }
}
