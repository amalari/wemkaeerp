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
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Alat agent discovery Koog (plan §2 A8): `platform_modules()` dan `validate_draft(draft)`.
 *
 * Kenapa argumen di-decode sendiri, bukan lewat kotlinx-serialization seperti contoh Koog pada umumnya:
 * repo ini **sengaja belum memakai plugin serialisasi** (`core` KMP tanpa compiler plugin itu — lihat
 * catatan di `ProspectCodec`/`ModuleDevCodec`). Kedua alat ini cukup memakai deskriptor eksplisit +
 * `decodeArgs` sendiri, sehingga dependensi Koog tetap tidak menular ke konvensi repo. Deskriptor tetap
 * dibuat tangan **di sini** (bukan hasil introspeksi kelas) — satu tempat, dites langsung.
 */
internal object DiscoveryTools {
    const val PLATFORM_MODULES: String = "platform_modules"
    const val VALIDATE_DRAFT: String = "validate_draft"
}

/** Registry alat satu run agent. Alat baru = tambahkan di sini; tidak ada state bersama antarpanggilan. */
internal fun discoveryToolRegistry(): ToolRegistry = ToolRegistry {
    tool(PlatformModulesTool())
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

/** Laporan validasi sebagai JSON: `{"valid":false,"issues":[{"path":"…","message":"…"}]}`. */
internal fun validationReport(draftJson: String): String = reportOf(
    try {
        DiscoveryDraftValidator.validate(DiscoveryDraftCodec.decode(draftJson))
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
