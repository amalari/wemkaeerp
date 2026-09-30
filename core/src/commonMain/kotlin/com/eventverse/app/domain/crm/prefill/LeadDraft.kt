package com.eventverse.app.domain.crm.prefill

import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.shared.json.JsonValue

/** Masalah pada satu field draf. [field] = kunci field bawaan ([LeadDraftFields]) atau id field kustom. */
data class DraftIssue(val field: String, val message: String)

/**
 * Draf form lead dari teks bebas (TRD-HELP-002 FR-1). **Tidak pernah disimpan** — user yang menyimpan lewat
 * `POST /api/tenant/crm/leads`. Setiap nilai di sini sudah lolos aturan yang sama dengan form manual;
 * nilai yang gagal dikosongkan dan dicatat di [issues].
 *
 * Tahap tidak ada di draf: lead dari draf selalu `NEW_LEAD` (FR-4).
 */
data class LeadDraft(
    val brandName: String? = null,
    val contactPerson: String? = null,
    val whatsappNumber: WhatsappNumber? = null,
    val email: String? = null,
    val productCategory: String? = null,
    val estimatedPcs: Int? = null,
    val customValues: Map<CustomFieldId, JsonValue.Obj> = emptyMap(),
    val issues: List<DraftIssue> = emptyList(),
    val agentRef: String = "",
    /** True bila LLM gagal dan draf berasal dari ekstraksi deterministik (hanya telepon, email, pcs). */
    val partial: Boolean = false,
)

/** Kunci field bawaan — sama dengan kunci JSON `CreateLeadRequest`, supaya klien tidak butuh tabel pemetaan. */
object LeadDraftFields {
    const val BRAND_NAME = "brandName"
    const val CONTACT_PERSON = "contactPerson"
    const val WHATSAPP = "whatsappNumber"
    const val EMAIL = "email"
    const val PRODUCT_CATEGORY = "productCategory"
    const val ESTIMATED_PCS = "estimatedPcs"
}

/**
 * Jenis nilai yang boleh diisi ekstraktor (pilot: K4). Uji Variabilitas: bentuk teknis nilai untuk prompt, bukan
 * konsep tenant — field kustom tenant tetap data (`CustomFieldDefinition`) dan hanya dipetakan ke tiga bentuk ini.
 */
enum class DraftFieldKind { TEXT, NUMBER, SELECT }

/**
 * Deskripsi satu field untuk ekstraktor. Dibangun dari skema tenant — bukan daftar field tertulis di prompt —
 * supaya field kustom tiap pabrik ikut terbaca (tenant-variability Kontrak 1).
 */
data class DraftFieldSpec(val key: String, val label: String, val kind: DraftFieldKind, val options: List<String> = emptyList())

/** Port ekstraktor. Menerima teks yang **sudah disamarkan**; mengembalikan kunci → nilai mentah (belum divalidasi). */
interface LeadDraftExtractor {
    val agentRef: String
    suspend fun extract(maskedText: String, fields: List<DraftFieldSpec>): Result<Map<String, String>>
}
