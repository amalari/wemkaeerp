package com.eventverse.app.domain.invoicing.template

import kotlin.math.roundToInt

/**
 * Rekomendasi pemetaan satu elemen kanvas ke token data dinamis faktur.
 *
 * @param suggestedPrefix Teks label statis yang layak dipertahankan (mis. "Telp / WA: "),
 *   agar label tidak hilang saat elemen dikonversi menjadi [TemplateElement.BoundField].
 * @param isStaticLabelOnly Bernilai true bila teks elemen murni label tanpa isi data
 *   (mis. "DITAGIHKAN KEPADA:"). Elemen seperti ini **tidak** dikonversi otomatis oleh
 *   [InvoiceAiMappingEngine.autoMapAll] karena akan menduplikasi label yang sudah ada di kanvas.
 */
data class AiMappingSuggestion(
    val token: BindingToken,
    val confidence: Double, // 0.0 s/d 1.0
    val explanation: String,
    val suggestedPrefix: String = "",
    val isStaticLabelOnly: Boolean = false
) {
    init {
        require(confidence in 0.0..1.0) {
            "Skor keyakinan AI harus berada di rentang 0.0 s/d 1.0, diterima: $confidence."
        }
    }
}

/**
 * Ringkasan hasil pemetaan massal oleh asisten AI.
 */
data class AiAutoMapSummary(
    val totalProcessed: Int,
    val newlyMappedCount: Int,
    val skippedLabelCount: Int = 0,
    val mappedElements: List<TemplateElement>,
    val details: List<String>
)

/**
 * Pure Kotlin Domain Engine yang menganalisis titik teks, posisi koordinat kanvas (milimeter),
 * dan pola konten bisnis untuk memetakan elemen kanvas ke [BindingToken] secara cerdas.
 *
 * Mesin ini tidak bergantung pada Compose, jaringan, maupun platform, sehingga keputusan
 * pemetaan dapat diuji murni dan dipakai ulang oleh kanvas Wasm, Desktop, Android, dan iOS.
 *
 * Prinsip kerja:
 * 1. **Label vs Nilai** — teks yang murni kata label ("Subtotal", "Kepada Yth.") tidak pernah
 *    dikonversi otomatis, karena nilai datanya biasanya sudah punya elemen sendiri di kanvas.
 * 2. **Preservasi Label** — saat teks bernilai dikonversi, label di depannya dibawa sebagai
 *    `prefix` agar dokumen tetap terbaca ("Telp / WA: 0812…", bukan sekadar "0812…").
 * 3. **Fallback Geometris** — bila tak ada kata kunci yang cocok, posisi elemen di kertas dipakai
 *    sebagai tebakan berkeyakinan rendah (di bawah ambang pemetaan otomatis).
 */
object InvoiceAiMappingEngine {

    /** Ambang minimum keyakinan agar elemen boleh dikonversi otomatis. */
    const val DEFAULT_MIN_CONFIDENCE: Double = 0.65

    /** Batas atas keyakinan untuk teks yang terdeteksi sebagai label statis murni. */
    private const val LABEL_ONLY_CONFIDENCE_CEILING: Double = 0.60

    /** Kata yang menandakan label/istilah dokumen, bukan isi data. */
    private val LABEL_VOCABULARY: Set<String> = setOf(
        "yth", "kepada", "ditagihkan", "ditransfer", "up", "u", "p", "pic", "contact", "person",
        "klien", "pelanggan", "customer", "nama", "alamat", "jalan", "jl", "komplek", "kawasan",
        "industri", "blok", "no", "nomor", "telp", "telepon", "phone", "hp", "wa", "whatsapp",
        "email", "npwp", "faktur", "invoice", "tgl", "tanggal", "terbit", "jatuh", "tempo", "due",
        "date", "spk", "po", "ref", "referensi", "catatan", "keterangan", "notes", "subtotal", "sub",
        "total", "tagihan", "grand", "bayar", "pembayaran", "dpp", "dasar", "pengenaan", "pajak",
        "ppn", "vat", "terbilang", "rupiah", "bank", "rekening", "rek", "atas", "a", "n", "transfer",
        "ke", "hormat", "kami", "bagian", "keuangan", "kasir", "tanda", "tangan", "signature",
        "approval", "disetujui", "dibuat", "oleh", "syarat", "ketentuan", "terms", "jasa", "barang",
        "deskripsi", "item", "qty", "jumlah", "kuantitas", "harga", "satuan", "amount", "diskon",
        "discount", "kode", "currency", "mata", "uang", "idr", "rp", "halaman", "page"
    )

    /** Awalan sapaan yang layak dipertahankan saat nilainya dipetakan ke token klien. */
    private val SALUTATION_PREFIXES: List<String> = listOf(
        "kepada yth.", "kepada yth", "yth.", "yth", "kepada"
    )

    private val PARENTHESIS_GROUP = Regex("\\([^)]*\\)")
    private val LABEL_TOKEN_SPLIT = Regex("[^a-z0-9]+")

    /**
     * Menganalisis elemen teks/kolom tunggal dan memberikan rekomendasi token terbaik.
     */
    fun analyzeElement(element: TemplateElement): AiMappingSuggestion? {
        when (element) {
            is TemplateElement.StaticText -> {
                val text = element.text.trim()
                if (text.isBlank()) return null
                return matchTextAndPosition(text, element.rect)
            }
            is TemplateElement.BoundField -> {
                val descriptor = InvoiceBindingRegistry.descriptorFor(element.binding)
                return AiMappingSuggestion(
                    token = element.binding,
                    confidence = 1.0,
                    explanation = "Sudah terhubung ke ${descriptor?.displayName ?: element.binding.value}"
                )
            }
            else -> return null
        }
    }

    /**
     * Memproses seluruh elemen pada template, otomatis mengonversi StaticText dengan
     * keyakinan tinggi (confidence >= minConfidence) menjadi BoundField dinamis.
     */
    fun autoMapAll(
        elements: List<TemplateElement>,
        minConfidence: Double = DEFAULT_MIN_CONFIDENCE
    ): AiAutoMapSummary {
        var newlyMapped = 0
        var skippedLabels = 0
        val details = mutableListOf<String>()

        val mapped = elements.map { el ->
            if (el !is TemplateElement.StaticText) return@map el

            val suggestion = analyzeElement(el) ?: return@map el
            val descriptor = InvoiceBindingRegistry.descriptorFor(suggestion.token)
            val label = descriptor?.displayName ?: suggestion.token.value
            val percent = (suggestion.confidence * 100).roundToInt()

            if (suggestion.isStaticLabelOnly) {
                skippedLabels++
                details.add("Label '${el.text}' dipertahankan sebagai teks statis (kandidat: [$label] $percent%)")
                return@map el
            }
            if (suggestion.confidence < minConfidence) {
                details.add("Elemen '${el.text}' dilewati: keyakinan $percent% di bawah ambang ${(minConfidence * 100).roundToInt()}%")
                return@map el
            }

            newlyMapped++
            details.add("Elemen '${el.text}' dipetakan ke [$label] ($percent% - ${suggestion.explanation})")
            toBoundField(el, suggestion)
        }

        return AiAutoMapSummary(
            totalProcessed = elements.size,
            newlyMappedCount = newlyMapped,
            skippedLabelCount = skippedLabels,
            mappedElements = mapped,
            details = details
        )
    }

    /**
     * Mengonversi elemen teks statis menjadi kolom dinamis memakai rekomendasi AI,
     * dengan tetap mempertahankan posisi, urutan lapis, gaya huruf, dan label depannya.
     * Dipakai bersama oleh pemetaan massal ([autoMapAll]) dan tombol saran di inspektur.
     */
    fun toBoundField(
        element: TemplateElement.StaticText,
        suggestion: AiMappingSuggestion
    ): TemplateElement.BoundField = TemplateElement.BoundField(
        elementId = element.elementId,
        rect = element.rect,
        zOrder = element.zOrder,
        anchorBelowTable = element.anchorBelowTable,
        binding = suggestion.token,
        prefix = suggestion.suggestedPrefix,
        style = element.style
    )

    /**
     * Menilai apakah sebuah teks murni label dokumen tanpa isi data.
     * Label: "DITAGIHKAN KEPADA:", "Subtotal", "Total Tagihan (Grand Total)".
     * Bukan label: "Telp / WA: 08123456789", "PT Adhi Garmen", "Jl. Raya No. 12".
     */
    fun isStaticLabelOnly(text: String): Boolean {
        if (text.contains('@')) return false
        val withoutParenthesis = PARENTHESIS_GROUP.replace(text.lowercase(), " ")
        val tokens = LABEL_TOKEN_SPLIT.split(withoutParenthesis).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        if (tokens.any { token -> token.any { char -> char.isDigit() } }) return false
        return tokens.all { token -> token in LABEL_VOCABULARY }
    }

    private fun matchTextAndPosition(text: String, rect: TemplateRect): AiMappingSuggestion? {
        val lower = text.lowercase()
        val yMm = rect.y.value / 10 // milimeter
        val labelOnly = isStaticLabelOnly(text)

        val keywordMatch = matchKeyword(lower, yMm)
        val raw = keywordMatch ?: if (labelOnly) null else matchPosition(rect, yMm)
        if (raw == null) return null

        return if (labelOnly) {
            raw.copy(
                confidence = minOf(raw.confidence, LABEL_ONLY_CONFIDENCE_CEILING),
                explanation = "${raw.explanation} — terdeteksi sebagai label statis, bukan nilai data.",
                suggestedPrefix = labelPrefixOf(text),
                isStaticLabelOnly = true
            )
        } else {
            raw.copy(suggestedPrefix = dataPrefixOf(text))
        }
    }

    private fun matchKeyword(lower: String, yMm: Int): AiMappingSuggestion? {
        // 1. Klien / Bill-To Party
        if (lower.contains("yth") || lower.contains("kepada") || lower.startsWith("pt ") ||
            lower.startsWith("cv ") || lower.contains("klien") || lower.contains("pelanggan")
        ) {
            return AiMappingSuggestion(
                token = BindingToken("billTo.name"),
                confidence = 0.95,
                explanation = "Format teks diawali identitas atau salam pembuka klien"
            )
        }
        if (lower.contains("up:") || lower.contains("u.p.") || lower.contains("pic") || lower.contains("contact person")) {
            return AiMappingSuggestion(
                token = BindingToken("billTo.contactPerson"),
                confidence = 0.90,
                explanation = "Kata kunci mengindikasikan PIC/Contact Person klien"
            )
        }
        if (lower.contains("jl.") || lower.contains("jalan") || lower.contains("komplek") || lower.contains("kawasan industri")) {
            return AiMappingSuggestion(
                token = BindingToken("billTo.address"),
                confidence = 0.88,
                explanation = "Format alamat jalan dan kawasan operasional klien"
            )
        }
        if (lower.contains("telp") || lower.contains("phone") || lower.contains("hp:") || lower.contains("wa:") || lower.startsWith("08")) {
            return AiMappingSuggestion(
                token = BindingToken("billTo.phone"),
                confidence = 0.88,
                explanation = "Format nomor kontak telepon / seluler"
            )
        }
        if (lower.contains("@") && (lower.contains(".com") || lower.contains(".co.id") || lower.contains(".id"))) {
            return AiMappingSuggestion(
                token = BindingToken("billTo.email"),
                confidence = 0.95,
                explanation = "Format alamat surat elektronik (email)"
            )
        }
        if (lower.contains("npwp")) {
            return if (yMm < 60) {
                AiMappingSuggestion(BindingToken("issuer.taxId"), 0.80, "NPWP pada bagian header instansi")
            } else {
                AiMappingSuggestion(BindingToken("billTo.taxId"), 0.85, "NPWP pada bagian informasi penagihan klien")
            }
        }

        // 2. Metadata Faktur / Invoice
        if (lower.contains("inv/") || lower.contains("nomor faktur") || lower.contains("no. invoice") || lower.contains("no faktur")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.number"),
                confidence = 0.95,
                explanation = "Pola penomoran registrasi faktur tagihan resmi"
            )
        }
        if (lower.contains("tgl terbit") || lower.contains("tanggal faktur") || lower.contains("tgl invoice") || lower.contains("issue date")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.issueDate"),
                confidence = 0.90,
                explanation = "Pola penanggalan penerbitan tagihan"
            )
        }
        if (lower.contains("jatuh tempo") || lower.contains("due date") || lower.contains("tgl tempo")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.dueDate"),
                confidence = 0.92,
                explanation = "Pola batas akhir jatuh tempo pembayaran tagihan"
            )
        }
        if (lower.contains("spk") || lower.contains("ref:") || lower.contains("po no") || lower.contains("nomor po")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.sourceRef"),
                confidence = 0.85,
                explanation = "Pola nomor referensi SPK atau Purchase Order pelanggan"
            )
        }
        if (lower.contains("catatan:") || lower.contains("notes:") || lower.contains("keterangan:")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.notes"),
                confidence = 0.80,
                explanation = "Bagian instruksi tambahan atau catatan penagihan"
            )
        }

        // 3. Finansial & Pembayaran
        if (lower.contains("subtotal") || lower.contains("sub total")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.subtotal"),
                confidence = 0.95,
                explanation = "Ringkasan akumulasi subtotal sebelum pajak"
            )
        }
        if (lower.contains("ppn (") || lower.contains("pajak (") || lower.contains("ppn 11%") || lower.contains("ppn 12%")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.taxAmount"),
                confidence = 0.92,
                explanation = "Besaran nominal Pajak Pertambahan Nilai (PPN)"
            )
        }
        if (lower.contains("grand total") || lower.contains("total tagihan") || lower.contains("total bayar") || lower == "total") {
            return AiMappingSuggestion(
                token = BindingToken("invoice.total"),
                confidence = 0.95,
                explanation = "Total akhir tagihan yang wajib dibayarkan"
            )
        }
        if (lower.contains("terbilang") || lower.contains("rupiah")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.totalInWords"),
                confidence = 0.90,
                explanation = "Format ejaan angka nominal rupiah tertulis"
            )
        }
        if (lower.contains("sisa tagihan") || lower.contains("sisa pelunasan") || lower.contains("balance due")) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.outstandingAmount"),
                confidence = 0.90,
                explanation = "Sisa saldo yang belum dilunasi"
            )
        }

        // 4. Perbankan Penerbit — nomor rekening diperiksa lebih dulu daripada nama bank,
        //    karena teks seperti "BCA - No. Rek: 8420-123-999" memuat keduanya.
        if (lower.contains("no. rek") || lower.contains("rekening") || lower.contains("rek:") || lower.contains("no rek")) {
            return AiMappingSuggestion(
                token = BindingToken("issuer.bankAccountNumber"),
                confidence = 0.90,
                explanation = "Nomor rekening bank penampung pembayaran"
            )
        }
        if (lower.contains("a/n") || lower.contains("atas nama") || lower.contains("a.n.")) {
            return AiMappingSuggestion(
                token = BindingToken("issuer.bankAccountHolder"),
                confidence = 0.88,
                explanation = "Nama pemegang sah rekening bank perusahaan"
            )
        }
        if (lower.contains("bca") || lower.contains("mandiri") || lower.contains("bni") || lower.contains("bri") || lower.contains("bank")) {
            return AiMappingSuggestion(
                token = BindingToken("issuer.bankName"),
                confidence = 0.85,
                explanation = "Nama bank tujuan transfer penagihan"
            )
        }

        return null
    }

    /**
     * Tebakan berkeyakinan rendah (di bawah ambang pemetaan otomatis) berbasis zona koordinat
     * kertas. Hanya dipakai sebagai saran di inspektur properti, bukan konversi otomatis.
     */
    private fun matchPosition(rect: TemplateRect, yMm: Int): AiMappingSuggestion? {
        val xMm = rect.x.value / 10
        if (yMm in 40..85 && xMm < 100) {
            return AiMappingSuggestion(
                token = BindingToken("billTo.name"),
                confidence = 0.60,
                explanation = "Posisi koordinat kanvas berada di zona informasi klien (Bill-To)"
            )
        }
        if (yMm in 40..85 && xMm >= 110) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.number"),
                confidence = 0.55,
                explanation = "Posisi koordinat kanvas berada di zona metadata faktur"
            )
        }
        if (yMm > 210 && xMm >= 110) {
            return AiMappingSuggestion(
                token = BindingToken("invoice.total"),
                confidence = 0.58,
                explanation = "Posisi koordinat kanvas berada di zona ringkasan total tagihan"
            )
        }
        return null
    }

    /**
     * Mengambil label depan teks bernilai ("Telp / WA: 0812…" → "Telp / WA: ") atau sapaan
     * pembuka ("Kepada Yth. PT Mitra" → "Kepada Yth. ") agar tidak hilang saat dipetakan.
     */
    private fun dataPrefixOf(text: String): String {
        val trimmed = text.trim()
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex in 1 until trimmed.lastIndex) {
            val tail = trimmed.substring(colonIndex + 1).trim()
            if (tail.isNotEmpty()) return trimmed.substring(0, colonIndex + 1) + " "
        }
        val lowered = trimmed.lowercase()
        val salutation = SALUTATION_PREFIXES.firstOrNull { lowered.startsWith(it) && lowered.length > it.length }
        if (salutation != null) return trimmed.substring(0, salutation.length) + " "
        return ""
    }

    /**
     * Membentuk prefix dari teks yang murni label, mis. "DITAGIHKAN KEPADA:" → "DITAGIHKAN KEPADA: ".
     */
    private fun labelPrefixOf(text: String): String {
        val trimmed = text.trim().trimEnd(':', ' ', ',')
        return if (trimmed.isEmpty()) "" else "$trimmed: "
    }
}
