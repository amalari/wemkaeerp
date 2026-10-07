package com.eventverse.app.infrastructure.discovery

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.JSONObject
import ai.koog.serialization.typeToken
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewLimits
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Nama alat wawancara (plan IV-C2): keadaan sesi + katalog modul dengan petunjuk asal. */
internal object InterviewTools {
    const val INTERVIEW_STATE: String = "interview_state"
    const val INTERVIEW_CATALOG: String = "interview_catalog"
}

/** Registry alat satu giliran agent. `draft` dipanggil per eksekusi supaya keadaan selalu yang terbaru. */
internal fun interviewToolRegistry(draft: () -> DiscoveryDraft): ToolRegistry = ToolRegistry {
    tool(InterviewStateTool(draft))
    tool(InterviewCatalogTool { draft().pack })
}

/**
 * Keadaan sesi berjalan. Dokumennya **persis** bentuk yang diterima `KoogInterviewBridge.decodeInterviewAnswer`
 * (parser produksi `DiscoveryDraftCodec`) — alat dan jawaban akhir sepakat pada dokumen berjembatan; model bisa
 * menyalin keadaan, mengubah bagian yang diminta, atau menjawab `{"interview":{"useCurrent":true}}`.
 */
internal class InterviewStateTool(private val draft: () -> DiscoveryDraft) : Tool<Unit, String>(
    argsType = typeToken<Unit>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = InterviewTools.INTERVIEW_STATE,
        description = "Dokumen sesi wawancara berjalan (divisi, peran, tautan, sambungan, jejak giliran) — bentuknya sama dengan dokumen yang harus kamu balas.",
        requiredParameters = emptyList()
    )
) {
    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): Unit = Unit

    /** Hasilnya JSON; lewati encoder default supaya tidak dikutip ganda sebagai string. */
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    override suspend fun execute(args: Unit): String = KoogInterviewBridge.interviewStateJson(draft())
}

/**
 * Katalog modul pack berjalan + petunjuk asal. Satu kebenaran untuk "modul apa yang boleh ditaut": model
 * dilarang mengarang modul di luar katalog ini (validator menolaknya berpath `$.interview.links[i].moduleId`).
 */
internal class InterviewCatalogTool(private val pack: () -> DomainPack) : Tool<Unit, String>(
    argsType = typeToken<Unit>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = InterviewTools.INTERVIEW_CATALOG,
        description = "Katalog modul pack usaha ini (id, nama, jenis) + kosakata asal modul (reuse_platform/reuse_pack/extend/new) dan aturan pentingnya.",
        requiredParameters = emptyList()
    )
) {
    override fun decodeArgs(rawArgs: JSONObject, serializer: JSONSerializer): Unit = Unit

    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    override suspend fun execute(args: Unit): String = interviewCatalogJson(pack())
}

/** Katalog ringkas + kosakata asal + aturan yang paling sering dilanggar (satu tempat, dites langsung). */
internal fun interviewCatalogJson(pack: DomainPack): String {
    val shippedModules = DomainPackRegistry.shipped.flatMap { it.modules }.associateBy { it.id }
    return jsonObjectOf(
        "packCode" to jsonOf(pack.code.value),
        "packName" to jsonOf(pack.displayName),
        "ports" to jsonArrayOf(pack.portTypes.map { jsonOf(it.value) }),
        "wiredPorts" to jsonArrayOf(pack.wiredPortTypes.map { jsonOf(it.value) }),
        "modules" to jsonArrayOf(
            pack.modules.map { m ->
                val shipped = shippedModules.containsKey(m.id)
                jsonObjectOf(
                    "id" to jsonOf(m.id.value),
                    "displayName" to jsonOf(m.displayName),
                    "kind" to jsonOf(m.kind.name),
                    "shipped" to jsonOf(shipped),
                    "originHint" to jsonOf(
                        when {
                            shipped && m.kind != ModuleKind.OPERATIONAL -> "reuse_platform"
                            shipped -> "reuse_pack"
                            else -> "new"
                        }
                    )
                )
            }
        ),
        "originVocabulary" to jsonArrayOf(
            listOf(
                jsonObjectOf("code" to jsonOf("reuse_platform"), "arti" to jsonOf("Modul tata kelola/fondasi bawaan platform (mis. bagan organisasi)")),
                jsonObjectOf("code" to jsonOf("reuse_pack"), "arti" to jsonOf("Modul operasional bawaan pack yang sudah dikirim")),
                jsonObjectOf("code" to jsonOf("extend"), "arti" to jsonOf("Modul bawaan yang fiturnya kurang — wajib menyebut fitur tambahannya")),
                jsonObjectOf("code" to jsonOf("new"), "arti" to jsonOf("Belum ada; dirakit khusus untuk usaha ini"))
            )
        ),
        "limits" to jsonObjectOf(
            "divisions" to jsonOf(InterviewLimits.DIVISIONS),
            "roles" to jsonOf(InterviewLimits.ROLES),
            "links" to jsonOf(InterviewLimits.LINKS),
            "turns" to jsonOf(InterviewLimits.TURNS),
            "featuresPerLink" to jsonOf(InterviewLimits.FEATURES_PER_LINK)
        ),
        "rules" to jsonArrayOf(
            listOf(
                jsonOf("Tautan hanya boleh menunjuk modul di katalog ini; modul lain ditolak validator"),
                jsonOf("Asal jujur: reuse_platform/reuse_pack/extend hanya untuk modul bertanda shipped; selain itu new"),
                jsonOf("extend wajib menyebut fitur tambahan di features; tanpa fitur ditolak validator"),
                jsonOf("Tidak ada tebakan pada langkah ini? Balas {\"interview\":{\"useCurrent\":true}}")
            )
        )
    ).encode()
}