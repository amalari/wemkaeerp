package com.eventverse.app.domain.traceability

import kotlin.jvm.JvmInline

/**
 * Objek yang boleh memegang kode telusur.
 *
 * Dua di antaranya wadah fisik ([BUNDLE], [SACK]); [WORKSHEET] adalah dokumen, bukan wadah — ia tidak
 * pernah berisi apa pun dan karenanya tidak boleh dibuka sebagai [TraceContainer]. Ia ikut di sini,
 * bukan memakai skema QR sendiri, supaya operator hanya belajar satu cara memindai: apa pun kertas
 * yang dipegangnya, kameranya diarahkan ke kotak yang sama dan aplikasi yang memutuskan artinya.
 */
enum class TraceTier(val symbol: Char, val displayName: String, val shortLabel: String) {
    BUNDLE('B', "Bundel Panel", "BDL"),
    SACK('K', "Karung Setoran", "KRG"),
    WORKSHEET('R', "Lembar Kerja Rajut", "LKR");

    val isContainer: Boolean get() = this != WORKSHEET

    companion object {
        fun fromSymbol(c: Char): TraceTier? = entries.firstOrNull { it.symbol == c }
    }
}

/**
 * Jenis SPK yang menaungi kode. Dua agregat berbeda ([com.eventverse.app.domain.sampling.SamplingOrder]
 * dan [com.eventverse.app.domain.production.BulkWorkOrder]) berbagi satu ruang kode, dan inilah yang
 * membedakan keduanya tanpa membuat traceability mengimpor salah satunya.
 */
enum class TraceWorkOrderKind(val symbol: Char, val displayName: String) {
    SAMPLING('S', "SPK Sampling"),
    BULK('M', "SPK Massal");

    companion object {
        fun fromSymbol(c: Char): TraceWorkOrderKind? = entries.firstOrNull { it.symbol == c }
    }
}

/**
 * Kode telusur 16 karakter yang tercetak sebagai QR sekaligus teks yang bisa diketik ulang.
 *
 * Nilai di dalamnya sengaja disimpan rapat tanpa tanda hubung; bentuk berkelompok untuk mata manusia
 * dihasilkan [TraceCodec.grouped]. Satu bentuk kanonik berarti perbandingan, indeks unik database,
 * dan pencocokan hasil scan tidak perlu menormalkan apa pun lebih dulu.
 */
@JvmInline
value class TraceCode(val value: String) {
    init {
        require(value.length == TraceCodec.LENGTH) {
            "Kode telusur harus ${TraceCodec.LENGTH} karakter, diterima ${value.length}: '$value'"
        }
    }
}

/**
 * Bagian-bagian kode setelah diurai. Semua angkanya ordinal, bukan teks — nama SPK dan nama size
 * TIDAK ikut terkode, karena memasukkannya akan melipatgandakan panjang kode dan membuatnya tidak
 * seragam. Itulah sebabnya kartu wajib mencetak baris teks manusiawi di bawah kodenya.
 */
data class TraceCodeParts(
    val version: Int,
    val workOrderKind: TraceWorkOrderKind,
    val tier: TraceTier,
    val tenantOrdinal: Int,
    val workOrderOrdinal: Int,
    val sizeIndex: Int,
    val sequence: Int
)

/**
 * Format kode telusur — satu-satunya tempat di seluruh sistem yang boleh tahu susunan karakternya.
 *
 * ```
 * W 1 S B tt oooo ss qqq c
 * │ │ │ │ │   │   │   │  └─ check digit
 * │ │ │ │ │   │   │   └──── urutan wadah dalam (SPK, size, tier)
 * │ │ │ │ │   │   └──────── indeks size pada daftar size beku milik SPK
 * │ │ │ │ │   └──────────── ordinal SPK dalam tenant
 * │ │ │ │ └──────────────── ordinal tenant
 * │ │ │ └────────────────── tier wadah
 * │ │ └──────────────────── jenis SPK
 * │ └────────────────────── versi format
 * └──────────────────────── magic 'W'
 * ```
 */
object TraceCodec {

    /** Crockford Base32: tanpa I, L, O, U supaya tidak tertukar dengan 1/0 dan tidak membentuk kata. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    const val LENGTH = 16
    const val MAGIC = 'W'
    const val CURRENT_VERSION = 1

    private const val TENANT_WIDTH = 2
    private const val ORDER_WIDTH = 3
    private const val SIZE_WIDTH = 2
    private const val SEQUENCE_WIDTH = 3
    private const val CHECK_WIDTH = 2

    /**
     * Check digit memakai DUA karakter dengan modulus prima 1021, bukan satu karakter mod 31.
     *
     * Satu karakter tidak bisa bekerja di alfabet 32 simbol: modulus mana pun yang <= 32 membuat dua
     * simbol saling kongruen (dengan mod 31, `0` dan `Z` berselisih tepat 31), sehingga substitusi
     * antar keduanya lolos di posisi mana pun berapa pun bobotnya. Ini bukan teori — test
     * `checksum rejects every single character substitution` menangkapnya.
     *
     * Dengan bobot (i+1) atas 14 karakter isi, dua sifat berikut terbukti, bukan sekadar diharapkan:
     * - substitusi tunggal menggeser jumlah sebesar delta*bobot, maksimal 31*14 = 434 < 1021, jadi
     *   tidak pernah kongruen nol;
     * - transposisi dua karakter bersebelahan menggeser jumlah sebesar selisih keduanya (bobotnya
     *   berbeda tepat satu), maksimal 31 < 1021, jadi juga tidak pernah kongruen nol.
     */
    private const val CHECK_MODULUS = 1021

    val MAX_TENANT_ORDINAL: Int = capacity(TENANT_WIDTH)
    val MAX_WORK_ORDER_ORDINAL: Int = capacity(ORDER_WIDTH)
    val MAX_SIZE_INDEX: Int = capacity(SIZE_WIDTH)
    val MAX_SEQUENCE: Int = capacity(SEQUENCE_WIDTH)

    private fun capacity(width: Int): Int {
        var result = 1
        repeat(width) { result *= ALPHABET.length }
        return result
    }

    fun encode(
        kind: TraceWorkOrderKind,
        tier: TraceTier,
        tenantOrdinal: Int,
        workOrderOrdinal: Int,
        sizeIndex: Int,
        sequence: Int
    ): TraceCode {
        requireRange("Ordinal tenant", tenantOrdinal, MAX_TENANT_ORDINAL)
        requireRange("Ordinal SPK", workOrderOrdinal, MAX_WORK_ORDER_ORDINAL)
        requireRange("Indeks size", sizeIndex, MAX_SIZE_INDEX)
        requireRange("Urutan wadah", sequence, MAX_SEQUENCE)

        val body = buildString {
            append(MAGIC)
            append(ALPHABET[CURRENT_VERSION])
            append(kind.symbol)
            append(tier.symbol)
            append(toBase32(tenantOrdinal, TENANT_WIDTH))
            append(toBase32(workOrderOrdinal, ORDER_WIDTH))
            append(toBase32(sizeIndex, SIZE_WIDTH))
            append(toBase32(sequence, SEQUENCE_WIDTH))
        }
        return TraceCode(body + checkChars(body))
    }

    /** Mengembalikan null untuk kode yang tidak sah, bukan melempar — input ini datang dari mesin scan dan jari manusia. */
    fun parse(raw: String): TraceCodeParts? {
        val normalized = normalize(raw)
        if (normalized.length != LENGTH) return null
        if (normalized.any { it !in ALPHABET }) return null
        if (normalized[0] != MAGIC) return null
        val bodyLength = LENGTH - CHECK_WIDTH
        if (checkChars(normalized.substring(0, bodyLength)) != normalized.substring(bodyLength)) return null

        val version = ALPHABET.indexOf(normalized[1]).takeIf { it >= 0 } ?: return null
        val kind = TraceWorkOrderKind.fromSymbol(normalized[2]) ?: return null
        val tier = TraceTier.fromSymbol(normalized[3]) ?: return null

        var cursor = 4
        val tenantOrdinal = fromBase32(normalized, cursor, TENANT_WIDTH) ?: return null
        cursor += TENANT_WIDTH
        val workOrderOrdinal = fromBase32(normalized, cursor, ORDER_WIDTH) ?: return null
        cursor += ORDER_WIDTH
        val sizeIndex = fromBase32(normalized, cursor, SIZE_WIDTH) ?: return null
        cursor += SIZE_WIDTH
        val sequence = fromBase32(normalized, cursor, SEQUENCE_WIDTH) ?: return null

        return TraceCodeParts(version, kind, tier, tenantOrdinal, workOrderOrdinal, sizeIndex, sequence)
    }

    fun parseCode(raw: String): TraceCode? =
        parse(raw)?.let { TraceCode(normalize(raw)) }

    /**
     * Menaikkan huruf, mengoreksi karakter yang mudah tertukar, lalu membuang sisanya.
     *
     * Koreksi I/L -> 1 dan O -> 0 adalah inti Crockford Base32: keempat karakter itu sengaja tidak
     * dipakai saat mencetak, sehingga kalau ada yang mengetiknya, maksudnya pasti angka. Membuangnya
     * begitu saja justru menghasilkan kode terlalu pendek yang ditolak tanpa penjelasan, padahal
     * jarinyalah yang meleset satu tuts.
     *
     * Ini yang membuat hasil scan (`W1SB…`), ketikan manual (`w1sb-0a34-…`), dan tempelan URL penuh
     * bermuara ke satu string yang sama.
     */
    fun normalize(raw: String): String = raw
        .uppercase()
        .map { c ->
            when (c) {
                'I', 'L' -> '1'
                'O' -> '0'
                else -> c
            }
        }
        .filter { it in ALPHABET }
        .joinToString("")

    /** Bentuk berkelompok empat untuk dicetak dan diketik ulang — bukan bentuk kanonik. */
    fun grouped(code: TraceCode): String = groupedRaw(code.value)

    /**
     * Versi untuk kode yang masih separuh diketik.
     *
     * Dipakai kolom entri manual: pengelompokan harus muncul sejak karakter kelima, bukan setelah
     * keenam belas, karena justru saat mengetiklah pengelompokan itu membantu mata menjaga posisi.
     */
    fun groupedRaw(raw: String): String = normalize(raw).chunked(4).joinToString("-")

    fun toScanUrl(code: TraceCode, host: String): String =
        "HTTPS://${host.uppercase().trimEnd('/')}/T/${code.value}"

    /** Menerima URL penuh hasil scan kamera maupun kode telanjang. */
    fun fromScanPayload(payload: String): TraceCode? {
        val tail = payload.substringAfterLast('/', payload)
        return parseCode(tail) ?: parseCode(payload)
    }

    private fun checkChars(body: String): String {
        var sum = 0
        body.forEachIndexed { index, char ->
            sum += ALPHABET.indexOf(char).coerceAtLeast(0) * (index + 1)
        }
        return toBase32(sum % CHECK_MODULUS, CHECK_WIDTH)
    }

    private fun toBase32(value: Int, width: Int): String {
        val chars = CharArray(width)
        var remaining = value
        for (i in width - 1 downTo 0) {
            chars[i] = ALPHABET[remaining % ALPHABET.length]
            remaining /= ALPHABET.length
        }
        return chars.concatToString()
    }

    private fun fromBase32(source: String, offset: Int, width: Int): Int? {
        var result = 0
        for (i in offset until offset + width) {
            val digit = ALPHABET.indexOf(source[i])
            if (digit < 0) return null
            result = result * ALPHABET.length + digit
        }
        return result
    }

    private fun requireRange(label: String, value: Int, limit: Int) {
        require(value in 0 until limit) { "$label di luar kapasitas kode telusur (0..${limit - 1}): $value" }
    }
}
