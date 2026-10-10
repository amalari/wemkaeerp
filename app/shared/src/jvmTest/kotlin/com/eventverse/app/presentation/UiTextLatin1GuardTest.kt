package com.eventverse.app.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Penjaga glyph: font Nunito hanya punya glyph Latin-1 (U+0000-U+00FF). Literal string UI berisi
 * karakter di luar itu (panah, bullet, elipsis, em dash, emoji, ...) tampil sebagai KOTAK.
 *
 * Yang dipindai hanya TEKS literal string (termasuk raw string dan literal char); komentar dan KDoc
 * dibuang, dan kode di dalam `${...}` tidak dihitung sebagai teks. Daftar pengecualian di bawah
 * eksplisit dan kosong saat ini: tidak ada bahasa non-Latin di UI.
 */
class UiTextLatin1GuardTest {

    data class Violation(val line: Int, val char: Char, val excerpt: String)

    /** path relatif (dari app/shared/src/commonMain/...) -> alasan; harus beralasan. */
    private val exemptFiles: Map<String, String> = emptyMap()

    @Test
    fun presentationSources_uiLiterals_areLatin1Only() {
        val root = locatePresentationRoot()
        val found = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(root).path
            if (rel in exemptFiles) return@forEach
            scan(f.readText()).forEach { v ->
                found += "${f.path}:${v.line} U+%04X '%s' -> %s".format(v.char.code, v.char, v.excerpt)
            }
        }
        assertTrue(
            found.isEmpty(),
            "Literal UI non-Latin-1 (tampil sebagai kotak di font Nunito). Ganti ikon vektor ClayIcons atau ASCII/Latin-1:\n" +
                found.joinToString("\n"),
        )
    }

    @Test
    fun exemptions_haveReasons() {
        exemptFiles.forEach { (path, reason) -> assertTrue(reason.isNotBlank(), "Pengecualian $path wajib beralasan") }
    }

    @Test
    fun scan_flagsEmDashEllipsisArrowBulletAndEmoji() {
        val src = "val a = \"Gagal — coba lagi…\"\nval b = \"→ lanjut • ok 💡\"\n"
        val chars = scan(src).map { it.char }
        assertTrue('—' in chars && '…' in chars && '→' in chars && '•' in chars)
        assertTrue(chars.any { it.isSurrogate() }, "emoji (surrogate) harus tertangkap")
        assertEquals(listOf(1, 1, 2, 2, 2, 2), scan(src).map { it.line }.take(6))
    }

    @Test
    fun scan_ignoresCommentsAndAllowsLatin1() {
        val src = """
            // komentar — boleh
            /* blok → boleh */
            /** KDoc • boleh */
            val a = "Rp 1.000 · ok é ü ° ±"
        """.trimIndent()
        assertTrue(scan(src).isEmpty())
    }

    @Test
    fun scan_handlesTemplatesAndRawStrings() {
        val ok = "val a = \"x \${m ?: \"-\"} y\" // —\n"
        assertTrue(scan(ok).isEmpty())
        val bad = "val a = \"x \${m ?: \"-\"} → y\"\n"
        assertEquals(1, scan(bad).size)
        val raw = "val r = \"\"\"baris —\nlagi\"\"\"\n"
        assertEquals(1, scan(raw).size)
    }

    private fun locatePresentationRoot(): File {
        val rel = "src/commonMain/kotlin/com/eventverse/app/presentation"
        val candidates = listOf(File(rel), File("app/shared/$rel"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Folder presentation tidak ditemukan dari ${File(".").absolutePath}")
    }

    companion object {
        /** Pemindai literal string Kotlin: komentar dibuang, `${...}` dianggap kode. */
        fun scan(s: String): List<Violation> {
            val out = mutableListOf<Violation>()
            val modes = ArrayDeque<Char>().apply { addLast('c') } // c=code, s=string, r=raw
            val depth = ArrayDeque<Int>().apply { addLast(0) }
            var i = 0
            var line = 1
            fun flag(ch: Char) {
                if (ch.code > 0xFF) {
                    val from = (i - 12).coerceAtLeast(0)
                    val to = (i + 12).coerceAtMost(s.length)
                    out += Violation(line, ch, s.substring(from, to).replace('\n', ' '))
                }
            }
            while (i < s.length) {
                val c = s[i]
                val m = modes.last()
                if (c == '\n') line++
                if (m == 'c') {
                    when {
                        s.startsWith("//", i) -> { while (i < s.length && s[i] != '\n') i++; continue }
                        s.startsWith("/*", i) -> {
                            val end = s.indexOf("*/", i + 2).let { if (it < 0) s.length else it + 2 }
                            line += s.substring(i, end).count { it == '\n' }
                            i = end; continue
                        }
                        s.startsWith("\"\"\"", i) -> { modes.addLast('r'); depth.addLast(0); i += 3; continue }
                        c == '"' -> { modes.addLast('s'); depth.addLast(0); i++; continue }
                        c == '\'' -> {
                            var j = i + 1
                            j += if (j < s.length && s[j] == '\\') 2 else 1
                            if (j < s.length && s[j] == '\'') {
                                for (k in i + 1 until j) if (s[k].code > 0xFF) { val save = i; i = k; flag(s[k]); i = save }
                                i = j + 1; continue
                            }
                        }
                        c == '{' -> depth[depth.lastIndex] = depth.last() + 1
                        c == '}' -> {
                            if (depth.last() == 0 && modes.size > 1) { modes.removeLast(); depth.removeLast(); i++; continue }
                            depth[depth.lastIndex] = depth.last() - 1
                        }
                    }
                    i++; continue
                }
                if (m == 's' && c == '\\') { i += 2; continue }
                if (m == 's' && c == '"') { modes.removeLast(); depth.removeLast(); i++; continue }
                if (m == 'r' && s.startsWith("\"\"\"", i)) { modes.removeLast(); depth.removeLast(); i += 3; continue }
                if (c == '$' && i + 1 < s.length && s[i + 1] == '{') { modes.addLast('c'); depth.addLast(0); i += 2; continue }
                flag(c)
                i++
            }
            return out
        }
    }
}
