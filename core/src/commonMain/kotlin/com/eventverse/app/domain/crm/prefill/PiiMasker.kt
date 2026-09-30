package com.eventverse.app.domain.crm.prefill

/**
 * Menyamarkan nomor telepon dan email **sebelum** teks dikirim ke LLM (TRD-HELP-002 K1 opsi B).
 *
 * Pemetaan placeholder → nilai asli hanya hidup di [Masked] (memori satu request) dan tidak pernah disimpan
 * atau dicatat. Nama orang/perusahaan sengaja **tidak** disamarkan: itulah yang perlu dibaca LLM, dan
 * keputusannya sudah disetujui (TRD-HELP-002 §0.9).
 */
object PiiMasker {
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    /** Nomor Indonesia: +62/62/0 lalu 8 dan 7–13 digit, boleh dipisah spasi, titik, atau strip. */
    private val PHONE = Regex("""(?<![\w{])(?:\+62|62|0)[\s.\-]?8(?:[\s.\-]?\d){7,12}(?![\w}])""")

    data class Masked(val text: String, private val originals: Map<String, String>) {
        /** Mengembalikan placeholder di [value] ke nilai asli; placeholder yang tak dikenal dibiarkan. */
        fun unmask(value: String): String =
            originals.entries.fold(value) { acc, (placeholder, original) -> acc.replace(placeholder, original) }

        val placeholders: Set<String> get() = originals.keys
    }

    fun mask(text: String): Masked {
        val originals = linkedMapOf<String, String>()
        var masked = replaceAll(text, EMAIL, "EMAIL", originals)
        masked = replaceAll(masked, PHONE, "TELP", originals)
        return Masked(masked, originals)
    }

    private fun replaceAll(text: String, regex: Regex, label: String, originals: MutableMap<String, String>): String {
        var counter = 0
        val seen = mutableMapOf<String, String>()
        return regex.replace(text) { m ->
            seen.getOrPut(m.value) {
                "{${label}_${++counter}}".also { originals[it] = m.value }
            }
        }
    }
}
