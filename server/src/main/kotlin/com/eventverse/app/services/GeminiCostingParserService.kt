package com.eventverse.app.services

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.BenchmarkCostLine
import com.eventverse.app.domain.costing.DesignVisionHints
import com.eventverse.app.domain.costing.KnitCategory
import com.eventverse.app.domain.costing.usecases.ParsedHistoricalCosting
import com.eventverse.app.shared.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** Membaca gambar mockup desain menjadi petunjuk terstruktur untuk estimator. */
interface DesignVisionAnalyzer {
    suspend fun analyze(imageBytes: ByteArray, contentType: String): DesignVisionHints
}

/** Tidak ada kunci API: estimator tetap jalan, hanya tanpa petunjuk visual. */
object NoopDesignVisionAnalyzer : DesignVisionAnalyzer {
    override suspend fun analyze(imageBytes: ByteArray, contentType: String) = DesignVisionHints(
        summary = "Analisis gambar tidak aktif (GEMINI_API_KEY belum diatur).",
        confidence = 0.0
    )
}

/**
 * Normalisasi berkas HPP lama dan pembacaan mockup memakai Gemini Flash.
 *
 * ## Mengapa `java.net.http.HttpClient`, bukan Ktor Client?
 * Modul server belum memuat Ktor Client sama sekali. Menambahkannya hanya demi satu panggilan
 * HTTP berarti menambah dependensi, engine, dan konfigurasi TLS baru ke artefak produksi.
 * Klien bawaan JDK sudah cukup untuk satu `POST` JSON.
 *
 * ## Mengapa hasil AI tidak langsung dipercaya?
 * Balikan model diperlakukan sebagai **data mentah**, sama seperti sel Excel: ia masuk ke
 * [ParsedHistoricalCosting] yang longgar, lalu divalidasi oleh
 * [com.eventverse.app.domain.costing.usecases.ImportHistoricalCostingUseCase]. Model yang
 * berhalusinasi gramasi 40.000 gram akan ditolak di sana, bukan tersimpan diam-diam.
 *
 * ## Mengapa ada [fallback]?
 * Batch 100 berkas akan menabrak rate limit, timeout, atau kuota harian. Berkas yang gagal
 * di AI diturunkan ke parser heuristik alih-alih hilang dari arsip.
 */
class GeminiCostingParserService(
    private val apiKey: String,
    private val model: String = System.getenv("GEMINI_MODEL")?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
    private val fallback: HistoricalCostingParser = HeuristicCostingParser(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()
) : HistoricalCostingParser, DesignVisionAnalyzer {

    override suspend fun parse(
        extract: WorkbookExtract,
        mockupImageUrl: String?
    ): ParsedHistoricalCosting {
        val heuristic = fallback.parse(extract, mockupImageUrl)
        val aiJson = runCatching { requestJson(buildSheetPrompt(extract.gridText)) }.getOrNull()
            ?: return heuristic

        // AI dipakai untuk mengisi apa yang heuristik lewatkan, bukan untuk menimpa yang sudah pasti.
        // Angka yang label-nya jelas terbaca di sheet lebih dapat dipercaya daripada tebakan model.
        return heuristic.copy(
            styleName = heuristic.styleName ?: aiJson.string("styleName"),
            clientName = heuristic.clientName ?: aiJson.string("clientName"),
            categoryText = heuristic.categoryText ?: aiJson.string("category"),
            knitType = heuristic.knitType ?: aiJson.string("knitType"),
            yarnType = heuristic.yarnType ?: aiJson.string("yarnType"),
            gauge = heuristic.gauge ?: aiJson.int("gauge"),
            netWeightGrams = heuristic.netWeightGrams ?: aiJson.double("netWeightGrams"),
            knittingMinutes = heuristic.knittingMinutes ?: aiJson.int("knittingMinutes"),
            buttonCount = heuristic.buttonCount ?: aiJson.int("buttonCount"),
            hppPerUnitMinor = heuristic.hppPerUnitMinor
                ?: aiJson.double("hppPerUnitRupiah")?.toMinorUnits(),
            sellingPricePerUnitMinor = heuristic.sellingPricePerUnitMinor
                ?: aiJson.double("sellingPricePerUnitRupiah")?.toMinorUnits(),
            features = aiJson.stringMap("features").ifEmpty { heuristic.features },
            costBreakdown = heuristic.costBreakdown.ifEmpty {
                aiJson.objectArray("costBreakdown").mapNotNull { line ->
                    val label = line.string("label") ?: return@mapNotNull null
                    val rupiah = line.double("amountRupiah") ?: return@mapNotNull null
                    BenchmarkCostLine(label, Money(rupiah.toMinorUnits()))
                }
            }
        )
    }

    override suspend fun analyze(imageBytes: ByteArray, contentType: String): DesignVisionHints {
        val json = runCatching {
            requestJson(
                prompt = VISION_PROMPT,
                inlineImage = Base64.getEncoder().encodeToString(imageBytes) to contentType
            )
        }.getOrNull() ?: return DesignVisionHints(
            summary = "Gambar tidak dapat dianalisis saat ini.",
            confidence = 0.0
        )

        return DesignVisionHints(
            detectedCategory = json.string("category")?.let { KnitCategory.fromFreeText(it) },
            detectedButtonCount = json.int("buttonCount"),
            detectedFeatures = json.stringArray("features"),
            summary = json.string("summary") ?: "",
            confidence = (json.double("confidence") ?: 0.0).coerceIn(0.0, 1.0)
        )
    }

    // ── Transport ────────────────────────────────────────────────────────────────────────

    private suspend fun requestJson(
        prompt: String,
        inlineImage: Pair<String, String>? = null
    ): JsonValue.Obj = withContext(Dispatchers.IO) {
        val parts = buildList {
            add(jsonObjectOf("text" to jsonOf(prompt)))
            inlineImage?.let { (base64, mimeType) ->
                add(
                    jsonObjectOf(
                        "inline_data" to jsonObjectOf(
                            "mime_type" to jsonOf(mimeType),
                            "data" to jsonOf(base64)
                        )
                    )
                )
            }
        }

        val payload = jsonObjectOf(
            "contents" to jsonArrayOf(listOf(jsonObjectOf("parts" to jsonArrayOf(parts)))),
            "generationConfig" to jsonObjectOf(
                // Suhu 0: tugasnya menyalin angka dari dokumen, bukan mengarang.
                "temperature" to jsonOf(0.0),
                "responseMimeType" to jsonOf("application/json")
            )
        ).encode()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$ENDPOINT_BASE/$model:generateContent"))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Gemini menolak permintaan (HTTP ${response.statusCode()}): ${response.body().take(400)}"
        }

        val envelope = JsonParser.parse(response.body()) as? JsonValue.Obj
            ?: error("Balikan Gemini bukan objek JSON")
        val text = envelope.objectArray("candidates")
            .firstOrNull()
            ?.obj("content")
            ?.objectArray("parts")
            ?.firstNotNullOfOrNull { it.string("text") }
            ?: error("Balikan Gemini tidak memuat teks")

        JsonParser.parse(text.stripCodeFence()) as? JsonValue.Obj
            ?: error("Isi balikan Gemini bukan objek JSON: ${text.take(200)}")
    }

    private fun buildSheetPrompt(gridText: String): String = """
        Kamu membaca satu lembar HPP (Harga Pokok Produksi) pabrik rajut Indonesia yang ditulis
        tangan di Excel. Ekstrak datanya menjadi JSON. Jangan menghitung apa pun sendiri dan
        jangan menebak: kalau sebuah nilai tidak tertulis di lembar ini, isi null.

        Aturan angka:
        - Semua nilai uang dalam RUPIAH PENUH (bukan sen). "Rp 45.000" -> 45000.
        - netWeightGrams adalah berat kain jadi per pcs dalam GRAM.
        - knittingMinutes adalah menit mesin rajut per pcs.
        - gauge adalah jumlah jarum per inci (angka 1-21), sering ditulis "7GG" atau "12G".

        Skema JSON yang wajib dikembalikan:
        {
          "styleName": string|null,
          "clientName": string|null,
          "category": string|null,
          "knitType": string|null,
          "yarnType": string|null,
          "gauge": number|null,
          "netWeightGrams": number|null,
          "knittingMinutes": number|null,
          "buttonCount": number|null,
          "hppPerUnitRupiah": number|null,
          "sellingPricePerUnitRupiah": number|null,
          "features": { "<atribut>": "<nilai>" },
          "costBreakdown": [ { "label": string, "amountRupiah": number } ]
        }

        Isi "costBreakdown" hanya dengan baris komponen biaya (benang, jahit, kancing, packing,
        overhead). Jangan masukkan baris total atau HPP.

        Isi lembar:
        ${gridText.take(MAX_SHEET_CHARS)}
    """.trimIndent()

    private companion object {
        const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        /** Satu lembar HPP jauh di bawah batas ini; pemotongan hanya melindungi dari sheet sampah. */
        const val MAX_SHEET_CHARS = 60_000

        val VISION_PROMPT = """
            Kamu melihat gambar mockup/desain produk pakaian rajut. Jelaskan apa yang TERLIHAT saja.
            Jangan menebak bahan, ukuran, atau harga. Kembalikan JSON:
            {
              "category": "cardigan"|"pullover"|"vest"|"dress"|"top"|"scarf"|"other",
              "buttonCount": number|null,
              "features": [string],
              "summary": string,
              "confidence": number
            }
            "confidence" 0.0-1.0 menyatakan seberapa jelas gambarnya terbaca.
            "features" berisi detail kasat mata, mis. "saku tempel", "motif jacquard", "kerah v".
        """.trimIndent()
    }
}

/** Model kadang membungkus JSON dalam pagar ```json meski sudah diminta `application/json`. */
private fun String.stripCodeFence(): String = trim()
    .removePrefix("```json")
    .removePrefix("```")
    .removeSuffix("```")
    .trim()
