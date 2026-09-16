package com.eventverse.app.cli

import com.eventverse.app.domain.costing.CostingBenchmarkRepository
import com.eventverse.app.domain.costing.usecases.ImportHistoricalCostingUseCase
import com.eventverse.app.domain.costing.usecases.ParsedHistoricalCosting
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.PostgresCostingBenchmarkRepository
import com.eventverse.app.infrastructure.storage.BenchmarkImageStorage
import com.eventverse.app.infrastructure.storage.LocalBenchmarkImageStorage
import com.eventverse.app.services.GeminiCostingParserService
import com.eventverse.app.services.HeuristicCostingParser
import com.eventverse.app.services.HistoricalCostingParser
import com.eventverse.app.services.HistoricalCostingWorkbookReader
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Impor batch berkas `.xlsx` HPP lama ke Knowledge Base tenant.
 *
 * ```bash
 * ./gradlew :server:importHistoricalCosting \
 *     --args="--dir=data/excel-hpp --tenant=<tenant-id> [--tenant-slug=parinara] [--reimport]"
 * ```
 *
 * `GEMINI_API_KEY` opsional: bila ada, normalisasi memakai Gemini Flash dengan heuristik sebagai
 * jaring pengaman per berkas; bila tidak ada, seluruh batch memakai parser heuristik.
 *
 * ## Mengapa progres dicetak per berkas, bukan hanya ringkasan akhir?
 * Impor 100 berkas dengan panggilan AI berjalan beberapa menit. Tanpa baris per berkas,
 * operator tidak bisa membedakan "sedang jalan" dari "menggantung", dan akan membatalkannya
 * di tengah.
 */
object HistoricalCostingCliImporter {

    @JvmStatic
    fun main(args: Array<String>) {
        val options = parseArgs(args)
        if (options == null) {
            printUsage()
            exitProcess(1)
        }

        val directory = File(options.directory)
        if (!directory.isDirectory) {
            System.err.println("Direktori tidak ditemukan: ${directory.absolutePath}")
            exitProcess(1)
        }

        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals("xlsx", ignoreCase = true) && !file.name.startsWith("~$")
        }?.sortedBy { it.name }.orEmpty()

        if (files.isEmpty()) {
            println("Tidak ada berkas .xlsx di ${directory.absolutePath}")
            return
        }

        DatabaseFactory.init()

        val apiKey = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
        val parser: HistoricalCostingParser = apiKey
            ?.let { GeminiCostingParserService(apiKey = it) }
            ?: HeuristicCostingParser()

        println("═".repeat(72))
        println("Impor arsip HPP historis")
        println("  Direktori : ${directory.absolutePath}")
        println("  Berkas    : ${files.size} file .xlsx")
        println("  Tenant    : ${options.tenantId}")
        println("  Parser    : ${if (apiKey != null) "Gemini Flash (+ heuristik cadangan)" else "Heuristik label (tanpa AI)"}")
        println("═".repeat(72))

        runBlocking {
            runImport(
                tenantId = TenantId(options.tenantId),
                tenantSlug = options.tenantSlug ?: options.tenantId,
                files = files,
                parser = parser,
                benchmarkRepository = PostgresCostingBenchmarkRepository(),
                imageStorage = LocalBenchmarkImageStorage(),
                skipAlreadyImported = !options.reimport
            )
        }
    }

    /**
     * Terpisah dari [main] supaya bisa dipanggil dari test dan dari endpoint upload web dengan
     * repository dan storage yang berbeda.
     */
    suspend fun runImport(
        tenantId: TenantId,
        tenantSlug: String,
        files: List<File>,
        parser: HistoricalCostingParser,
        benchmarkRepository: CostingBenchmarkRepository,
        imageStorage: BenchmarkImageStorage,
        skipAlreadyImported: Boolean = true,
        log: (String) -> Unit = ::println
    ) {
        val reader = HistoricalCostingWorkbookReader()
        val parsed = mutableListOf<ParsedHistoricalCosting>()
        val unreadable = mutableListOf<Pair<String, String>>()

        files.forEachIndexed { index, file ->
            val progress = "[${(index + 1).toString().padStart(files.size.toString().length)}/${files.size}]"
            runCatching {
                val extract = reader.read(file)
                val imageUrl = extract.embeddedImages.firstOrNull()?.let { image ->
                    imageStorage.store(tenantSlug, image.suggestedFileName, image.contentType, image.bytes)
                }
                parser.parse(extract, imageUrl)
            }.onSuccess { record ->
                parsed += record
                log("$progress ✓ ${file.name} — ${record.styleName ?: "(tanpa nama)"}, " +
                    "${record.netWeightGrams?.let { "${it.toInt()} g" } ?: "gramasi ?"}")
            }.onFailure { error ->
                unreadable += file.name to (error.message ?: error::class.simpleName.orEmpty())
                log("$progress ✗ ${file.name} — ${error.message}")
            }
        }

        val useCase = ImportHistoricalCostingUseCase(
            benchmarkRepository = benchmarkRepository,
            idGenerator = { "bmk-${java.util.UUID.randomUUID()}" }
        )

        val report = useCase(tenantId, parsed, skipAlreadyImported).getOrElse { error ->
            log("Impor gagal disimpan: ${error.message}")
            return
        }

        log("─".repeat(72))
        log("Tersimpan  : ${report.importedCount} artikel")
        log("Dilewati   : ${report.skippedCount} berkas")
        report.skipped.forEach { log("  · ${it.sourceFileName}: ${it.reason}") }
        if (unreadable.isNotEmpty()) {
            log("Tidak terbaca : ${unreadable.size} berkas")
            unreadable.forEach { (name, reason) -> log("  · $name: $reason") }
        }
        log("Total arsip tenant sekarang: ${benchmarkRepository.count(tenantId)}")
    }

    private data class Options(
        val directory: String,
        val tenantId: String,
        val tenantSlug: String?,
        val reimport: Boolean
    )

    private fun parseArgs(args: Array<String>): Options? {
        val map = args.mapNotNull { arg ->
            if (!arg.startsWith("--")) return@mapNotNull null
            val body = arg.removePrefix("--")
            val key = body.substringBefore('=')
            val value = if (body.contains('=')) body.substringAfter('=') else "true"
            key to value
        }.toMap()

        val directory = map["dir"]?.takeIf { it.isNotBlank() } ?: return null
        val tenantId = map["tenant"]?.takeIf { it.isNotBlank() }
            ?: System.getenv("WEMADE_IMPORT_TENANT_ID")?.takeIf { it.isNotBlank() }
            ?: return null

        return Options(
            directory = directory,
            tenantId = tenantId,
            tenantSlug = map["tenant-slug"]?.takeIf { it.isNotBlank() },
            reimport = map["reimport"] == "true"
        )
    }

    private fun printUsage() {
        println(
            """
            Pemakaian:
              ./gradlew :server:importHistoricalCosting --args="--dir=<folder> --tenant=<tenantId> [opsi]"

            Wajib:
              --dir=<folder>        Folder berisi berkas .xlsx HPP lama
              --tenant=<tenantId>   Id tenant tujuan (atau env WEMADE_IMPORT_TENANT_ID)

            Opsional:
              --tenant-slug=<slug>  Slug tenant untuk path penyimpanan gambar (default: tenantId)
              --reimport            Impor ulang berkas yang namanya sudah pernah tercatat

            Environment:
              GEMINI_API_KEY        Aktifkan normalisasi AI (opsional)
              WEMADE_UPLOAD_DIR     Folder penyimpanan gambar mockup (default: data/uploads)
            """.trimIndent()
        )
    }
}
