package com.eventverse.app.infrastructure.discovery

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.JSONArray
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.typeToken
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Alat agent discovery Koog (plan §2 A8; diperluas SP-C2): `platform_modules()`, `screen_catalog()`, dan
 * `validate_draft(draft)`.
 *
 * Kenapa argumen di-decode sendiri, bukan lewat kotlinx-serialization seperti contoh Koog pada umumnya:
 * repo ini **sengaja belum memakai plugin serialisasi** (`core` KMP tanpa compiler plugin itu — lihat
 * catatan di `ProspectCodec`/`ModuleDevCodec`). Ketiga alat ini cukup memakai deskriptor eksplisit +
 * `decodeArgs` sendiri, sehingga dependensi Koog tetap tidak menular ke konvensi repo. Deskriptor tetap
 * dibuat tangan **di sini** (bukan hasil introspeksi kelas) — satu tempat, dites langsung.
 */
internal object DiscoveryTools {
    const val PLATFORM_MODULES: String = "platform_modules"
    const val SCREEN_CATALOG: String = "screen_catalog"
    const val VALIDATE_DRAFT: String = "validate_draft"
}

/** Registry alat satu run agent. Alat baru = tambahkan di sini; tidak ada state bersama antarpanggilan. */
internal fun discoveryToolRegistry(): ToolRegistry = ToolRegistry {
    tool(PlatformModulesTool())
    tool(ScreenCatalogTool())
    tool(ValidateDraftTool())
}

/** Katalog pack bawaan platform. Tanpa argumen (argsType hanya formalitas `Unit`). */
internal class PlatformModulesTool(
    private val packs: () -> List<DomainPack> = { DomainPackRegistry.shipped }
) : Tool<Unit, String>(
    argsType = typeToken<Unit>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = DiscoveryTools.PLATFORM_MODULES,
        description = "Katalog pack bawaan platform: kode pack, fase, seksi, slot, dan modul (id, nama, jenis).",
        requiredParameters = emptyList()
    )
) {
    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): Unit = Unit

    /** Hasilnya JSON; melewati encoder default supaya tidak dikutip ganda sebagai string. */
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    override suspend fun execute(args: Unit): String = platformCatalogJson(packs())
}

/**
 * Kosakata tertutup untuk `proposal` (SP-C2): jenis tampilan + bentuk `view`-nya, tipe field, gaya kartu,
 * batas ukuran, dan **petunjuk** peran kerja → jenis tampilan yang dipakai pack bawaan. Petunjuk, bukan
 * aturan keras — aturan tetap milik validator; alat ini hanya mencegah model menebak kosakata atau
 * mengarang kunci `view`. Tanpa argumen; jawabannya deterministik penuh (dites).
 */
internal class ScreenCatalogTool(
    private val packs: () -> List<DomainPack> = { DomainPackRegistry.shipped }
) : Tool<Unit, String>(
    argsType = typeToken<Unit>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = DiscoveryTools.SCREEN_CATALOG,
        description = "Kosakata tertutup untuk proposal layar: jenis tampilan + bentuk view-nya, tipe field, " +
            "gaya kartu, batas ukuran, dan petunjuk peran kerja → jenis tampilan dari pack bawaan.",
        requiredParameters = emptyList()
    )
) {
    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): Unit = Unit

    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    override suspend fun execute(args: Unit): String = screenCatalogJson(packs())
}

/** `entity` menurut widget — cermin `ScreenProposalValidator.checkEntityPresence`, bukan aturan baru. */
private fun entityRule(widget: WidgetKind): String = when (widget) {
    WidgetKind.KANBAN, WidgetKind.TABLE, WidgetKind.FORM, WidgetKind.CHECKLIST -> "wajib"
    WidgetKind.DASHBOARD, WidgetKind.CUSTOM_SCREEN -> "null"
    WidgetKind.PRINT -> "opsional"
}

private fun viewShape(widget: WidgetKind): String = when (widget) {
    WidgetKind.KANBAN ->
        "{\"card\":[{\"field\":\"kunci\",\"style\":\"TITLE|TEXT|BADGE|DATE|NUMBER|FLAG\"}]," +
            "\"columnMeta\":{\"<opsi status>\":{\"tintHex\":16711680,\"wipLimit\":3}},\"detailFormFields\":[kunci]} " +
            "(kolom papan = opsi statusField)"
    WidgetKind.TABLE ->
        "{\"columns\":[kunci],\"inlineCreate\":true|false,\"editableFields\":[kunci]} " +
            "(kolom dan field sunting = kunci field; status bermesin tidak boleh disunting)"
    WidgetKind.FORM -> "{\"fields\":[kunci],\"submitLabel\":\"Simpan …\"} (semua field wajib masuk form)"
    WidgetKind.CHECKLIST -> "{\"labelField\":\"kunci teks\",\"doneField\":\"kunci BOOL\"}"
    WidgetKind.DASHBOARD ->
        "{\"tiles\":[{\"label\":\"…\",\"value\":\"…\",\"count\":{\"moduleId\":\"…\",\"field\":\"…\",\"equals\":\"…\"}}]} " +
            "(maksimal 8 ubin; count menunjuk modul di pack)"
    WidgetKind.PRINT -> "{\"fields\":[kunci]}"
    WidgetKind.CUSTOM_SCREEN -> KoogDiscoverySkeletonVocabulary.viewShape
}

private fun widgetNote(widget: WidgetKind): String = when (widget) {
    WidgetKind.KANBAN -> "untuk antrean/alur kerja yang bergerak antar status"
    WidgetKind.TABLE -> "untuk daftar/ledger yang ditelusuri"
    WidgetKind.FORM -> "untuk pencatatan satu-per-satu"
    WidgetKind.CHECKLIST -> "untuk langkah kerja yang dicentang"
    WidgetKind.DASHBOARD -> "untuk ringkasan angka tanpa daftar"
    WidgetKind.PRINT -> "untuk dokumen yang diserahkan dalam bentuk cetak"
    WidgetKind.CUSTOM_SCREEN -> KoogDiscoverySkeletonVocabulary.widgetNote
}

/**
 * Katalog ringkas: yang dibutuhkan model untuk menyusun `proposal`, tidak lebih. Angka batas diambil
 * dari `ProposalLimits` (satu sumber kebenaran dengan validator), sehingga prompt tidak mungkin bergeser
 * dari aturan produksi.
 */
internal fun screenCatalogJson(packs: List<DomainPack>): String = jsonObjectOf(
    "widgetKinds" to jsonArrayOf(
        WidgetKind.entries.map { w ->
            jsonObjectOf(
                "code" to jsonOf(w.code),
                "displayName" to jsonOf(w.displayName),
                "entity" to jsonOf(entityRule(w)),
                "view" to jsonOf(viewShape(w)),
                "note" to jsonOf(widgetNote(w))
            )
        }
    ),
    "fieldTypes" to jsonArrayOf(
        FieldType.entries.map { t ->
            jsonObjectOf(
                "name" to jsonOf(t.name),
                "note" to jsonOf(KoogDiscoveryFieldTypeVocabulary.note(t))
            )
        }
    ),
    "cardStyles" to jsonArrayOf(CardStyle.entries.map { jsonOf(it.name) }),
    "skeleton" to KoogDiscoverySkeletonVocabulary.catalogJson(),
    "limits" to jsonObjectOf(
        "fields" to jsonOf(ProposalLimits.FIELDS),
        "options" to jsonOf(ProposalLimits.OPTIONS),
        "statuses" to jsonOf(ProposalLimits.STATUSES),
        "seedRows" to jsonOf(ProposalLimits.SEED_ROWS),
        "text" to jsonOf(ProposalLimits.TEXT),
        "tiles" to jsonOf(ProposalLimits.TILES)
    ),
    "roleHints" to jsonObjectOf(
        "note" to jsonOf(
            "Petunjuk peran kerja -> jenis tampilan dari pack bawaan platform. " +
                "Petunjuk, bukan aturan keras: ikuti watak kerja modul pada narasi."
        ),
        "examples" to jsonArrayOf(
            packs.flatMap { pack ->
                val moduleName = pack.modules.associate { it.id.value to it.displayName }
                pack.screenSuggestions.map { s ->
                    jsonObjectOf(
                        "pack" to jsonOf(pack.code.value),
                        "module" to jsonOf(s.moduleId.value),
                        "moduleDisplayName" to jsonOf(moduleName[s.moduleId.value] ?: s.moduleId.value),
                        "screenTitle" to jsonOf(s.title),
                        "widget" to jsonOf(s.widget.code)
                    )
                }
            }
        )
    )
).encode()

/** Validator draf untuk koreksi diri **di dalam** satu run: galat berpath, bukan lemparan. */
internal class ValidateDraftTool : Tool<String, String>(
    argsType = typeToken<String>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = DiscoveryTools.VALIDATE_DRAFT,
        description = "Memvalidasi dokumen draf discovery dan mengembalikan {\"valid\":true} atau galat berpath.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "draft",
                description = "Dokumen draf discovery (objek JSON: pack + blueprint + screens)",
                type = ToolParameterType.Object(properties = emptyList(), additionalProperties = true)
            )
        )
    )
) {
    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): String =
        draftArgument(rawArgs, serializer)

    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    override suspend fun execute(args: String): String = validationReport(args)
}

/**
 * Mengambil argumen `draft` apa adanya. Model kadang mengirim objek, kadang string JSON yang dikutip;
 * keduanya diterima (yang penting isinya JSON), tapi **tidak** ada tebakan di luar itu. Nilai objek
 * di-encode ulang lewat serializer Koog supaya escaping string tetap benar — `JSONObject.toString()`
 * tidak meng-escape, dan dokumen kita penuh teks bebas.
 */
internal fun draftArgument(rawArgs: JSONObject, serializer: JSONSerializer): String {
    val element = rawArgs.entries["draft"]
        ?: throw IllegalArgumentException("Argumen 'draft' wajib diisi (objek dokumen JSON)")
    return when (element) {
        is JSONObject, is JSONArray -> serializer.encodeJSONElementToString(element)
        is JSONPrimitive -> if (element.isString) element.content else element.toString()
        else -> serializer.encodeJSONElementToString(element)
    }
}

/**
 * Laporan validasi sebagai JSON: `{"valid":false,"issues":[{"path":"…","message":"…"}]}`.
 *
 * Alat ini **harus memahami jembatan `useShipped` yang sama** dengan jawaban akhir ([decodeAnswer]); kalau tidak,
 * model menulis jawaban yang benar (`{"pack":{"useShipped":"garment"}}`), ditolak alat ini, lalu menulis ulang
 * seluruh pack dari ingatan sampai kehabisan langkah (ditemukan di eval live 2026-10-07).
 */
internal fun validationReport(draftJson: String): String = reportOf(
    try {
        DiscoveryDraftValidator.validate(
            DiscoveryDraftCodec.decode(applyShippedBlueprintBridge(applyShippedPackBridge(draftJson)))
        )
    } catch (e: DiscoveryDraftDecodeException) {
        listOf(DiscoveryValidationIssue(e.path, e.message ?: "dokumen tidak sah"))
    } catch (e: IllegalArgumentException) {
        listOf(DiscoveryValidationIssue("$", e.message ?: "dokumen tidak sah"))
    }
)

private fun reportOf(issues: List<DiscoveryValidationIssue>): String = jsonObjectOf(
    "valid" to jsonOf(issues.isEmpty()),
    "issues" to jsonArrayOf(
        issues.map { jsonObjectOf("path" to jsonOf(it.path), "message" to jsonOf(it.message)) }
    )
).encode()

/**
 * Katalog ringkas pack bawaan. Hanya yang dibutuhkan model: kode pack (untuk jembatan `useShipped`),
 * kosakata modul/slot/seksi platform, dan aturan identitas yang paling sering dilanggar.
 */
internal fun platformCatalogJson(packs: List<DomainPack>): String = jsonObjectOf(
    "shippedPacks" to jsonArrayOf(
        packs.map { pack ->
            jsonObjectOf(
                "code" to jsonOf(pack.code.value),
                "displayName" to jsonOf(pack.displayName),
                "reuseWith" to jsonOf("{\"pack\":{\"useShipped\":\"${pack.code.value}\"}}"),
                // Starter alur bawaan pack ini: model WAJIB memilih salah satunya (bukan mengarang blueprint baru).
                "starterBlueprints" to jsonArrayOf(
                    shippedStarterBlueprints().filter { it.pack == pack.code }.map { bp ->
                        jsonObjectOf(
                            "code" to jsonOf(bp.code.value), "displayName" to jsonOf(bp.displayName),
                            "description" to jsonOf(bp.description), "targetClientProfile" to jsonOf(bp.targetClientProfile),
                            "reuseWith" to jsonOf("{\"blueprint\":{\"useShipped\":\"${bp.code.value}\"}}")
                        )
                    }
                ),
                "phases" to jsonArrayOf(pack.phases.map { jsonOf(it.code.value) }),
                "sections" to jsonArrayOf(
                    pack.sections.map { jsonObjectOf("code" to jsonOf(it.code.value), "displayName" to jsonOf(it.displayName)) }
                ),
                "slots" to jsonArrayOf(
                    pack.slots.map {
                        jsonObjectOf("code" to jsonOf(it.code.value), "displayName" to jsonOf(it.displayName), "phase" to jsonOf(it.phase.value))
                    }
                ),
                "modules" to jsonArrayOf(
                    pack.modules.map {
                        jsonObjectOf(
                            "id" to jsonOf(it.id.value), "displayName" to jsonOf(it.displayName),
                            "kind" to jsonOf(it.kind.name), "section" to jsonOf(it.section.value),
                            "slot" to jsonOf(it.slot?.value)
                        )
                    }
                )
            )
        }
    ),
    "rules" to jsonArrayOf(
        listOf(
            jsonOf("Modul & slot baru wajib berprefiks '<pack.code>_' (huruf kecil, angka, underscore)"),
            jsonOf("blueprint.modules[].moduleCode wajib ada di pack.modules[].id"),
            jsonOf("Dokumen berkode pack bawaan wajib identik dengan dokumen platform — pakai useShipped")
        )
    )
).encode()
